package de.dahaem.shopping;

import de.dahaem.inventory.InventoryRepository;
import de.dahaem.inventory.Item;
import de.dahaem.mealplanner.MealPlanRecipe;
import de.dahaem.mealplanner.MealPlanRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a set of recipes into a shopping list: sums what they need, then for each item picks
 * the best place to buy it. No SQL here, no UI here; it only asks the repository for data.
 */
public class ShoppingListService {

    private final ShoppingListRepository repository;
    private final MealPlanRepository mealPlanRepository;
    private final InventoryRepository inventoryRepository;

    public ShoppingListService(ShoppingListRepository repository) {
        this(repository, new MealPlanRepository(), new InventoryRepository());
    }

    public ShoppingListService(ShoppingListRepository repository, MealPlanRepository mealPlanRepository,
                                InventoryRepository inventoryRepository) {
        this.repository = repository;
        this.mealPlanRepository = mealPlanRepository;
        this.inventoryRepository = inventoryRepository;
    }

    public ShoppingList buildFromRecipes(List<Integer> recipeIds) {
        return buildFromRecipes(recipeIds, LocalDate.now());
    }

    /** Overload that takes "today" explicitly, so callers (and tests) control which discounts count as current. */
    public ShoppingList buildFromRecipes(List<Integer> recipeIds, LocalDate today) {
        List<AggregatedIngredient> aggregated = repository.aggregateIngredients(recipeIds);
        List<ShoppingListItem> items = new ArrayList<>();
        for (AggregatedIngredient ingredient : aggregated) {
            List<StoreItemCandidate> candidates = repository.loadStoreCandidates(ingredient.inventoryItemId(), today);
            StoreOption bestOption = pickBestOption(candidates);
            items.add(new ShoppingListItem(
                    ingredient.inventoryItemId(),
                    ingredient.nameDeutsch(),
                    ingredient.totalQuantity(),
                    ingredient.unit(),
                    ingredient.sourceRecipeIds(),
                    bestOption));
        }
        return new ShoppingList(items);
    }

    public int generateFromMealPlan(int mealPlanId) {
        return generateFromMealPlan(mealPlanId, LocalDate.now());
    }

    /**
     * Builds a shopping list from a meal plan's recipes, subtracts what is already in inventory, and
     * persists it: a shopping_list row linked to the plan, plus one shopping_list_item per item still
     * missing (an item already fully in stock is dropped). Aggregation and store matching are the same
     * as {@link #buildFromRecipes}; only items a current store option was found for get a persisted
     * line, since shopping_list_item_store_item_id is a required (NOT NULL) reference to store_item.
     * Runs as one transaction: either the whole list is saved or none of it is. Returns the new
     * shopping_list's id.
     */
    public int generateFromMealPlan(int mealPlanId, LocalDate today) {
        List<MealPlanRecipe> planRecipes = mealPlanRepository.findRecipesForPlan(mealPlanId);
        List<Integer> recipeIds = planRecipes.stream().map(MealPlanRecipe::getRecipeId).distinct().toList();
        ShoppingList list = buildFromRecipes(recipeIds, today);

        Connection con = repository.openTransaction();
        try {
            int shoppingListId = repository.insertShoppingList(con, mealPlanId, today, false);

            for (ShoppingListItem item : list.items()) {
                int inStock = inventoryRepository.findById(item.inventoryItemId())
                        .map(Item::getQuantity)
                        .orElse(0);
                double missing = item.totalQuantity() - inStock;
                if (missing <= 0) {
                    continue; // already fully covered by inventory
                }
                StoreOption bestOption = item.bestOption();
                if (bestOption == null) {
                    continue; // no current store option to reference; nothing we can persist
                }
                int quantityNeeded = (int) Math.ceil(missing);
                repository.insertShoppingListItem(con, shoppingListId, bestOption.storeItemId(), null,
                        quantityNeeded, bestOption.onSale(), false, false);
            }

            con.commit();
            return shoppingListId;
        } catch (SQLException e) {
            rollback(con);
            throw new RuntimeException("Could not generate shopping list for meal plan " + mealPlanId, e);
        } catch (RuntimeException e) {
            rollback(con);
            throw e;
        } finally {
            close(con);
        }
    }

    private static void rollback(Connection con) {
        try {
            con.rollback();
        } catch (SQLException ignored) {
        }
    }

    private static void close(Connection con) {
        try {
            con.close();
        } catch (SQLException ignored) {
        }
    }

    /**
     * Collapses duplicate listings within the same store (same brand + pack, e.g. a stale relisting
     * next to a fresh one) by recency/availability rather than price, then picks the cheapest
     * (till-price) option across the surviving, genuinely different stores.
     */
    private StoreOption pickBestOption(List<StoreItemCandidate> candidates) {
        Map<String, StoreItemCandidate> currentPerListing = new LinkedHashMap<>();
        for (StoreItemCandidate candidate : candidates) {
            String key = candidate.storeName() + "|" + candidate.brandName() + "|"
                    + candidate.packageUnit() + "|" + candidate.packageSize();
            StoreItemCandidate incumbent = currentPerListing.get(key);
            if (incumbent == null || isMoreCurrent(candidate, incumbent)) {
                currentPerListing.put(key, candidate);
            }
        }

        StoreOption best = null;
        for (StoreItemCandidate candidate : currentPerListing.values()) {
            StoreOption option = toOption(candidate);
            if (option.tillPrice() == null) {
                continue;
            }
            if (best == null || best.tillPrice() == null || option.tillPrice() < best.tillPrice()) {
                best = option;
            }
        }
        return best;
    }

    /** True if `candidate` should replace `incumbent` as the listing to trust for this (store, brand, pack). */
    private boolean isMoreCurrent(StoreItemCandidate candidate, StoreItemCandidate incumbent) {
        boolean candidateAvailable = isEffectivelyAvailable(candidate);
        boolean incumbentAvailable = isEffectivelyAvailable(incumbent);
        if (candidateAvailable != incumbentAvailable) {
            return candidateAvailable;
        }
        LocalDate candidateSeen = candidate.productLastSeen();
        LocalDate incumbentSeen = incumbent.productLastSeen();
        if (candidateSeen == null) {
            return false;
        }
        if (incumbentSeen == null) {
            return true;
        }
        return candidateSeen.isAfter(incumbentSeen);
    }

    /** store_product availability wins when known; a store_item with no linked product falls back to its own flag. */
    private boolean isEffectivelyAvailable(StoreItemCandidate candidate) {
        return candidate.productAvailable() != null ? candidate.productAvailable() : candidate.storeItemAvailable();
    }

    private StoreOption toOption(StoreItemCandidate candidate) {
        boolean onSale = candidate.discountPrice() != null;
        Double effectivePrice;
        if (candidate.dealPrice() != null && candidate.dealQuantity() > 0) {
            effectivePrice = candidate.dealPrice() / candidate.dealQuantity();
        } else if (onSale) {
            effectivePrice = candidate.discountPrice();
        } else {
            effectivePrice = candidate.normalPrice();
        }
        Double deposit = candidate.deposit();
        Double tillPrice = effectivePrice == null ? null : effectivePrice + (deposit != null ? deposit : 0.0);

        return new StoreOption(
                candidate.storeItemId(),
                candidate.storeName(),
                candidate.brandName(),
                candidate.packageUnit(),
                candidate.packageSize(),
                candidate.normalPrice(),
                onSale,
                candidate.discountPrice(),
                candidate.dealQuantity(),
                candidate.dealPrice(),
                candidate.discountValidUntil(),
                deposit,
                tillPrice);
    }
}
