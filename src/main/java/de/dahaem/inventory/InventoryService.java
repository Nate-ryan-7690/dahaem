package de.dahaem.inventory;

import java.time.LocalDate;
import java.util.List;

/**
 * Business logic for inventory. No SQL here, no UI here.
 * It asks the repository for data and answers questions about it.
 * This is the class your future web controllers will call.
 */
public class InventoryService {

    private final InventoryRepository repository;

    public InventoryService(InventoryRepository repository) {
        this.repository = repository;
    }

    public long totalItemCount() {
        return repository.countAll();
    }

    /** The catalogue with the stock of this household. */
    public List<Item> allItems(int householdId) {
        return repository.findAll(householdId);
    }

    /** One page of the catalogue filtered by name text and/or category (both optional); see {@link InventoryRepository#search}. */
    public ItemPage search(int householdId, String query, String category, long offset, int limit) {
        return repository.search(householdId, blankToNull(query), blankToNull(category), offset, limit);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    /** Items at or below a stock threshold, e.g. "what is running low?". */
    public List<Item> lowStock(int householdId, int threshold) {
        return repository.findAll(householdId).stream()
                .filter(item -> item.getQuantity() <= threshold)
                .toList();
    }

    /** Items with a known expiry date within the next N days. Null expiry dates are ignored. */
    public List<Item> expiringWithin(int householdId, int days) {
        LocalDate limit = LocalDate.now().plusDays(days);
        return repository.findAll(householdId).stream()
                .filter(item -> item.getEarliestExpDate() != null)
                .filter(item -> !item.getEarliestExpDate().isAfter(limit))
                .toList();
    }
}
