package de.dahaem.shopping;

import de.dahaem.inventory.InventoryRepository;
import de.dahaem.inventory.Item;
import de.dahaem.mealplanner.MealPlanRecipe;
import de.dahaem.mealplanner.MealPlanRepository;
import de.dahaem.shared.Units;

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
        return buildFromRecipes(today, repository.aggregateIngredients(recipeIds));
    }

    /**
     * Scales each recipe's ingredient quantities by {@code people / recipe_servings} before summing
     * across recipes (see {@link ShoppingListRepository#aggregateIngredients(List, int)}).
     */
    public ShoppingList buildFromRecipes(List<Integer> recipeIds, LocalDate today, int people) {
        return buildFromRecipes(today, repository.aggregateIngredients(recipeIds, people));
    }

    private ShoppingList buildFromRecipes(LocalDate today, List<AggregatedIngredient> aggregated) {
        List<ShoppingListItem> items = new ArrayList<>();
        for (AggregatedIngredient ingredient : aggregated) {
            List<StoreItemCandidate> candidates = repository.loadStoreCandidates(ingredient.inventoryItemId(), today);
            List<StoreOption> storeOptions =
                    buildStoreOptions(candidates, ingredient.totalQuantity(), ingredient.unit());
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
        List<Integer> recipeIds = recipeIdsForPlan(mealPlanId);
        return persistGeneratedList(mealPlanId, today, buildFromRecipes(recipeIds, today));
    }

    /**
     * Scales each recipe's ingredient quantities by {@code people / recipe_servings} before summing
     * (see {@link #buildFromRecipes(List, LocalDate, int)}), otherwise identical to
     * {@link #generateFromMealPlan(int, LocalDate)}.
     */
    public int generateFromMealPlan(int mealPlanId, LocalDate today, int people) {
        List<Integer> recipeIds = recipeIdsForPlan(mealPlanId);
        return persistGeneratedList(mealPlanId, today, buildFromRecipes(recipeIds, today, people));
    }

    private List<Integer> recipeIdsForPlan(int mealPlanId) {
        List<MealPlanRecipe> planRecipes = mealPlanRepository.findRecipesForPlan(mealPlanId);
        return planRecipes.stream().map(MealPlanRecipe::getRecipeId).distinct().toList();
    }

    private int persistGeneratedList(int mealPlanId, LocalDate today, ShoppingList list) {
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
                // TODO: shopping_list_item_quantity_needed is INTEGER; fractional needs (kg, L) are
                // rounded up until that column is widened to a real number type.
                int quantityNeeded = (int) Math.ceil(missing);
                repository.insertShoppingListItem(con, shoppingListId, null, item.inventoryItemId(), null,
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
     * choice when {@code storeItemId} is null. Rejected when storeItemId is not actually a current
     * option for the line's inventory item (an {@link IllegalArgumentException}, for the caller to
     * turn into 400) — checked here defensively, since the DAHÄM "surface, don't decide" rule means
     * only the user's own pick may ever be stored. An unknown storeItemId also violates the
     * store_item foreign key regardless, which the caller sees as a constraint-violation failure.
     */
    public void selectStoreItem(int shoppingListItemId, Integer storeItemId) {
        if (storeItemId != null) {
            repository.findInventoryItemIdForItem(shoppingListItemId).ifPresent(inventoryItemId -> {
                boolean isCandidate = repository.loadStoreCandidates(inventoryItemId, LocalDate.now()).stream()
                        .anyMatch(candidate -> candidate.storeItemId() == storeItemId);
                if (!isCandidate) {
                    throw new IllegalArgumentException("store_item " + storeItemId
                            + " is not a current option for shopping list item " + shoppingListItemId);
                }
            });
        }
        repository.setChosenStoreItem(shoppingListItemId, storeItemId);
    }

    /** The shopping_list a persisted shopping_list_item belongs to, or empty if the item doesn't exist. */
    public Optional<Integer> findShoppingListIdForItem(int shoppingListItemId) {
        return repository.findShoppingListIdForItem(shoppingListItemId);
    }

    public Optional<PersistedShoppingList> loadPersistedList(int shoppingListId) {
        return loadPersistedList(shoppingListId, LocalDate.now());
    }

    /**
     * Reloads a persisted shopping_list by id with its items, or empty if no such list exists. Each
     * item comes back with the same store options a freshly built line would show (cheapest
     * till-price first, none pre-chosen) so a reloaded line surfaces exactly the same candidates as a
     * newly generated one — plus the store_item the user selected already, if any.
     */
    public Optional<PersistedShoppingList> loadPersistedList(int shoppingListId, LocalDate today) {
        return repository.findShoppingListById(shoppingListId).map(row -> {
            List<PersistedShoppingListItem> items = repository.findItemsForShoppingList(shoppingListId).stream()
                    .map(itemRow -> toPersistedItem(itemRow, today))
                    .toList();
            return new PersistedShoppingList(row.id(), row.mealPlanId(), row.createdDate(), row.status(), items);
        });
    }

    private PersistedShoppingListItem toPersistedItem(ShoppingListItemRow row, LocalDate today) {
        List<StoreOption> options = row.inventoryItemId() == null
                ? List.of()
                : buildStoreOptions(repository.loadStoreCandidates(row.inventoryItemId(), today),
                        row.quantityNeeded(), row.unit());
        return new PersistedShoppingListItem(row.itemId(), row.inventoryItemId(), row.nameDeutsch(),
                row.nameEnglish(), row.quantityNeeded(), row.isManual(), options, row.selectedStoreItemId());
    }

    /**
     * Collapses duplicate listings within the same store (same brand + pack, e.g. a stale relisting
     * next to a fresh one) by recency/availability rather than price, then returns every surviving,
     * genuinely different store's option, cheapest till-price first — for the user to choose from,
     * never pre-chosen.
     */
    private List<StoreOption> buildStoreOptions(List<StoreItemCandidate> candidates, double neededQuantity,
                                                 String neededUnit) {
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
                .map(candidate -> toOption(candidate, neededQuantity, neededUnit))
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

    private StoreOption toOption(StoreItemCandidate candidate, double neededQuantity, String neededUnit) {
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

        boolean unitCompatible = Units.compatible(neededUnit, candidate.packageUnit());
        Integer packsNeeded = null;
        Double packsTotalPrice = null;
        if (unitCompatible) {
            Double neededBase = Units.toBase(neededQuantity, neededUnit);
            Double packageBase = Units.toBase(candidate.packageSize(), candidate.packageUnit());
            if (neededBase != null && packageBase != null && packageBase > 0) {
                packsNeeded = Math.max(1, (int) Math.ceil(neededBase / packageBase));
                packsTotalPrice = tillPrice == null ? null : packsNeeded * tillPrice;
            }
        }

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
                tillPrice,
                unitCompatible,
                packsNeeded,
                packsTotalPrice);
    }
}
