package de.dahaem.shopping;

import de.dahaem.inventory.InventoryRepository;
import de.dahaem.inventory.Item;
import de.dahaem.mealplanner.MealPlanRepository;
import de.dahaem.mealplanner.MealServings;
import de.dahaem.mealplanner.PlannedMeal;
import de.dahaem.shared.Units;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
        return persistGeneratedList(mealPlanId, today, today, null,
                buildFromRecipes(recipeIdsForPlan(mealPlanId, null), today));
    }

    /**
     * Scales each recipe's ingredient quantities by {@code people / recipe_servings} before summing
     * (see {@link #buildFromRecipes(List, LocalDate, int)}), otherwise identical to
     * {@link #generateFromMealPlan(int, LocalDate)}.
     */
    public int generateFromMealPlan(int mealPlanId, LocalDate today, int people) {
        return generateFromMealPlan(mealPlanId, today, people, today, null);
    }

    /**
     * As {@link #generateFromMealPlan(int, LocalDate, int)} for a shopping trip on {@code shoppingDate} (offers are
     * judged on that day, and stock that has expired by then is not subtracted) counting the meals from {@code from}
     * on ({@code null} = all meals of the plan). {@code today} is the day the list is made.
     *
     * @throws IllegalArgumentException when the shopping day is before today
     */
    public int generateFromMealPlan(int mealPlanId, LocalDate today, int people, LocalDate shoppingDate,
                                    LocalDate from) {
        requireNotPast(shoppingDate, today);
        return persistGeneratedList(mealPlanId, today, shoppingDate, from,
                buildFromRecipes(recipeIdsForPlan(mealPlanId, from), shoppingDate, people));
    }

    /**
     * Builds and persists a shopping list in which every planned meal is scaled by its OWN servings: the meal's
     * own number, then its recipe's preferred servings, then {@code householdPeople}, then 2 (see
     * {@link MealServings}). Each meal adds {@code servings / recipe_servings} of its recipe's quantities, so a
     * recipe planned twice counts twice, and a recipe stored for 2 and planned for 2 is taken as written.
     * Otherwise identical to {@link #generateFromMealPlan(int, LocalDate)}. {@code householdPeople} may be null.
     */
    public int generateFromMealPlanByServings(int mealPlanId, LocalDate today, Integer householdPeople) {
        return generateFromMealPlanByServings(mealPlanId, today, householdPeople, today, today);
    }

    /**
     * As {@link #generateFromMealPlanByServings(int, LocalDate, Integer)} for a shopping trip on {@code shoppingDate}
     * (offers are judged on that day, stock that has expired by then is not subtracted), counting the meals from
     * {@code from} on ({@code null} = all). A list is always a new row; earlier lists of the plan stay as they are.
     * Stock is subtracted only when its unit is the line's unit; if the units differ nothing is subtracted and the
     * line is flagged {@code needsReview}. A line no store sells is kept.
     *
     * @throws IllegalArgumentException when the shopping day is before today
     */
    public int generateFromMealPlanByServings(int mealPlanId, LocalDate today, Integer householdPeople,
                                              LocalDate shoppingDate, LocalDate from) {
        requireNotPast(shoppingDate, today);
        Map<Integer, Integer> servingsPerRecipe = servingsPerRecipe(mealPlanId, householdPeople, from);
        return persistGeneratedList(mealPlanId, today, shoppingDate, from,
                buildFromRecipes(shoppingDate, repository.aggregateIngredientsByServings(servingsPerRecipe)));
    }

    /** Per recipe on the plan, the servings of all its meals added up (a recipe planned for 2 and for 4 is 6). */
    Map<Integer, Integer> servingsPerRecipe(int mealPlanId, Integer householdPeople) {
        return servingsPerRecipe(mealPlanId, householdPeople, null);
    }

    // Only the meals on or after 'from' count (null = all of them).
    private Map<Integer, Integer> servingsPerRecipe(int mealPlanId, Integer householdPeople, LocalDate from) {
        Map<Integer, Integer> servingsPerRecipe = new LinkedHashMap<>();
        for (PlannedMeal meal : mealsFrom(mealPlanId, from)) {
            servingsPerRecipe.merge(meal.getRecipeId(), MealServings.resolve(meal, householdPeople).servings(),
                    Integer::sum);
        }
        return servingsPerRecipe;
    }

    private List<Integer> recipeIdsForPlan(int mealPlanId, LocalDate from) {
        return mealsFrom(mealPlanId, from).stream().map(PlannedMeal::getRecipeId).distinct().toList();
    }

    private List<PlannedMeal> mealsFrom(int mealPlanId, LocalDate from) {
        return mealPlanRepository.findMeals(mealPlanId).stream()
                .filter(meal -> from == null || !meal.getDate().isBefore(from))
                .toList();
    }

    private static void requireNotPast(LocalDate shoppingDate, LocalDate today) {
        if (shoppingDate.isBefore(today)) {
            throw new IllegalArgumentException("'shoppingDate' must not be before today");
        }
    }

    private int persistGeneratedList(int mealPlanId, LocalDate today, LocalDate shoppingDate, LocalDate from,
                                     ShoppingList list) {
        Connection con = repository.openTransaction();
        try {
            int shoppingListId = repository.insertShoppingList(con, mealPlanId, today, shoppingDate, from, false);

            for (ShoppingListItem item : list.items()) {
                Item stock = inventoryRepository.findById(item.inventoryItemId()).orElse(null);
                double usable = usableStock(stock, shoppingDate);
                boolean sameUnit = stock != null && sameUnit(item.unit(), stock.getUnit());
                // Stock is subtracted only in the same unit; otherwise nothing is, and a person reviews the line.
                boolean needsReview = usable > 0 && !sameUnit;
                double missing = item.totalQuantity() - (sameUnit ? usable : 0);
                if (missing <= 0) {
                    continue; // already fully covered by inventory
                }
                // a line no store sells stays on the list: it is shown as unavailable when the list is read
                int lineId = repository.insertShoppingListItem(con, shoppingListId, null, item.inventoryItemId(), null,
                        BigDecimal.valueOf(missing).setScale(3, RoundingMode.HALF_UP), item.unit(), null, false,
                        false, needsReview);
                repository.insertItemRecipes(con, lineId, item.sourceRecipeIds());
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

    /** What can be counted on: the stock if there is some and it has not expired by the shopping day, else 0. */
    private static double usableStock(Item stock, LocalDate shoppingDate) {
        if (stock == null || stock.getQuantity() <= 0) {
            return 0;
        }
        LocalDate expires = stock.getEarliestExpDate();
        return expires != null && expires.isBefore(shoppingDate) ? 0 : stock.getQuantity();
    }

    private static boolean sameUnit(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
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

    /** Throws {@link ListCompletedException} when the list is completed; a list that does not exist passes (callers check). */
    public void requireOpen(int shoppingListId) {
        repository.findShoppingListById(shoppingListId).ifPresent(row -> {
            if (row.status()) {
                throw new ListCompletedException(shoppingListId);
            }
        });
    }

    /** Ticks a line as bought, or unticks it. Nothing is added to stock: that comes from the receipt / barcode flow. */
    public void setPurchased(int shoppingListId, int shoppingListItemId, boolean purchased) {
        requireOpen(shoppingListId);
        repository.setPurchased(shoppingListItemId, purchased);
    }

    /** Changes the amount of a line; its unit stays and the amount must be above 0. */
    public void setQuantity(int shoppingListId, int shoppingListItemId, double quantity) {
        requireOpen(shoppingListId);
        repository.setQuantityNeeded(shoppingListItemId, validAmount(quantity));
    }

    public void removeItem(int shoppingListId, int shoppingListItemId) {
        requireOpen(shoppingListId);
        repository.deleteItem(shoppingListItemId);
    }

    /**
     * Adds a line the user wants on the list by hand, for a catalogue item (stock is not looked at: the user asked for
     * it). {@code unit} defaults to the item's own unit. Returns the new line's id.
     */
    public int addManualItem(int shoppingListId, int inventoryItemId, double quantity, String unit) {
        requireOpen(shoppingListId);
        BigDecimal amount = validAmount(quantity);
        if (inventoryRepository.findById(inventoryItemId).isEmpty()) {
            throw new IllegalArgumentException("No inventory item with id " + inventoryItemId);
        }
        Connection con = repository.openTransaction();
        try {
            int id = repository.insertShoppingListItem(con, shoppingListId, null, inventoryItemId, null, amount,
                    unit == null || unit.isBlank() ? null : unit.trim(), null, false, true, false);
            con.commit();
            return id;
        } catch (SQLException e) {
            try {
                con.rollback();
            } catch (SQLException ignored) {
            }
            throw new RuntimeException("Could not add a line to shopping list " + shoppingListId, e);
        } finally {
            try {
                con.close();
            } catch (SQLException ignored) {
            }
        }
    }

    /** Completes a list (it becomes read-only) or reopens it. */
    public void setCompleted(int shoppingListId, boolean completed) {
        repository.setListStatus(shoppingListId, completed);
    }

    private static BigDecimal validAmount(double quantity) {
        if (!(quantity > 0) || quantity >= 10_000_000) {
            throw new IllegalArgumentException("quantity must be above 0 and below 10000000");
        }
        return BigDecimal.valueOf(quantity).setScale(3, RoundingMode.HALF_UP);
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
            // offers are judged on the list's own shopping day (a list made before it existed: on 'today')
            LocalDate offersOn = row.shoppingDate() != null ? row.shoppingDate() : today;
            Map<Integer, List<SourceRecipe>> recipes = repository.recipesByItem(shoppingListId);
            List<PersistedShoppingListItem> items = repository.findItemsForShoppingList(shoppingListId).stream()
                    .map(itemRow -> toPersistedItem(itemRow, offersOn, recipes.getOrDefault(itemRow.itemId(), List.of())))
                    .toList();
            return new PersistedShoppingList(row.id(), row.mealPlanId(), row.createdDate(), row.shoppingDate(),
                    row.fromDate(), row.status(), items);
        });
    }

    /** The saved lists of a meal plan, newest first. A new generation never changes an older list. */
    public List<ShoppingListSummary> listsForPlan(int mealPlanId) {
        return repository.findListsForPlan(mealPlanId);
    }

    private PersistedShoppingListItem toPersistedItem(ShoppingListItemRow row, LocalDate offersOn,
                                                      List<SourceRecipe> recipes) {
        List<StoreOption> options = row.inventoryItemId() == null
                ? List.of()
                : buildStoreOptions(repository.loadStoreCandidates(row.inventoryItemId(), offersOn),
                        row.quantityNeeded(), row.unit());
        // a manual line has no catalogue item to look up; every other line is available when some store sells it
        boolean available = row.inventoryItemId() == null || !options.isEmpty();
        return new PersistedShoppingListItem(row.itemId(), row.inventoryItemId(), row.nameDeutsch(),
                row.nameEnglish(), row.quantityNeeded(), row.unit(), available, row.needsReview(),
                row.purchased(), row.stockQuantity(), row.stockUnit(), row.isManual(), recipes, options, row.selectedStoreItemId());
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
                candidate.discountValidFrom(),
                candidate.discountValidUntil(),
                deposit,
                tillPrice,
                unitCompatible,
                packsNeeded,
                packsTotalPrice);
    }
}
