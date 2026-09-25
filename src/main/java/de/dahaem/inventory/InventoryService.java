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

    public List<Item> allItems() {
        return repository.findAll();
    }

    /** Items at or below a stock threshold, e.g. "what is running low?". */
    public List<Item> lowStock(int threshold) {
        return repository.findAll().stream()
                .filter(item -> item.getQuantity() <= threshold)
                .toList();
    }

    /** Items with a known expiry date within the next N days. Null expiry dates are ignored. */
    public List<Item> expiringWithin(int days) {
        LocalDate limit = LocalDate.now().plusDays(days);
        return repository.findAll().stream()
                .filter(item -> item.getEarliestExpDate() != null)
                .filter(item -> !item.getEarliestExpDate().isAfter(limit))
                .toList();
    }
}
