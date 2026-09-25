package de.dahaem.shopping;

import de.dahaem.inventory.InventoryRepository;
import de.dahaem.inventory.Item;
import de.dahaem.mealplanner.MealPlanRecipe;
import de.dahaem.mealplanner.MealPlanRepository;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns a set of recipes into a shopping list: sums what they need, then for each item collects
 * every store's current option for it. It surfaces those options; it never picks one for the user.
 * No SQL here, no UI here; it only asks the repository for data.
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
            List<StoreOption> storeOptions = buildStoreOptions(candidates);
            items.add(new ShoppingListItem(
                    ingredient.inventoryItemId(),
                    ingredient.nameDeutsch(),
                    ingredient.totalQuantity(),
                    ingredient.unit(),
                    ingredient.sourceRecipeIds(),
                    storeOptions));
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
     * line. No store item is chosen at generation time — shopping_list_item_store_item_id starts out
     * null (unselected) and stays that way until the user picks one via {@link #selectStoreItem}.
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
                if (item.storeOptions().isEmpty()) {
                    continue; // nothing currently sells it; nothing meaningful to persist
                }
                int quantityNeeded = (int) Math.ceil(missing);
                repository.insertShoppingListItem(con, shoppingListId, null, null,
                        quantityNeeded, null, false, false);
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
     * Records the store_item the user picked for a persisted shopping_list_item line, or clears the
     * choice when {@code storeItemId} is null. An unknown storeItemId violates the store_item foreign
     * key; the caller sees that as a constraint-violation failure.
     */
    public void selectStoreItem(int shoppingListItemId, Integer storeItemId) {
        repository.setChosenStoreItem(shoppingListItemId, storeItemId);
    }

    /** The shopping_list a persisted shopping_list_item belongs to, or empty if the item doesn't exist. */
    public Optional<Integer> findShoppingListIdForItem(int shoppingListItemId) {
        return repository.findShoppingListIdForItem(shoppingListItemId);
    }

    /**
     * Collapses duplicate listings within the same store (same brand + pack, e.g. a stale relisting
     * next to a fresh one) by recency/availability rather than price, then returns every surviving,
     * genuinely different store's option, cheapest till-price first — for the user to choose from,
     * never pre-chosen.
     */
    private List<StoreOption> buildStoreOptions(List<StoreItemCandidate> candidates) {
        Map<String, StoreItemCandidate> currentPerListing = new LinkedHashMap<>();
        for (StoreItemCandidate candidate : candidates) {
            String key = candidate.storeName() + "|" + candidate.brandName() + "|"
                    + candidate.packageUnit() + "|" + candidate.packageSize();
            StoreItemCandidate incumbent = currentPerListing.get(key);
            if (incumbent == null || isMoreCurrent(candidate, incumbent)) {
                currentPerListing.put(key, candidate);
            }
        }

        return currentPerListing.values().stream()
                .map(this::toOption)
                .sorted(Comparator.comparing(StoreOption::tillPrice, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
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
