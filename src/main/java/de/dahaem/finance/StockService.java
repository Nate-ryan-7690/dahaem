package de.dahaem.finance;

import de.dahaem.inventory.InventoryRepository;
import de.dahaem.inventory.Item;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Takes food out of stock, in the item's own unit (g, ml, Stück). Using it and throwing it away are the same action: the
 * amount leaves the batch that expires first, the item's quantity goes down, and its earliest expiry date is worked out again from the batches that are left.
 * No SQL here. Everything runs in one transaction on the item's locked row.
 */
public class StockService {

    private final PurchaseRepository purchaseRepository;
    private final InventoryRepository inventoryRepository;

    public StockService(PurchaseRepository purchaseRepository, InventoryRepository inventoryRepository) {
        this.purchaseRepository = purchaseRepository;
        this.inventoryRepository = inventoryRepository;
    }

    /**
     * Removes {@code quantity} of an inventory item from stock and returns the item as it is now, or empty if there is no
     * such item. More than is in stock is refused (IllegalArgumentException). Stock that no purchase explains (seeded
     * or typed in by hand) is only counted down; when no purchase batch is left the stored date is kept unless the
     * item is now empty, in which case it is cleared.
     */
    public Optional<Item> use(int inventoryItemId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than 0");
        }
        Connection con = purchaseRepository.openTransaction();
        try {
            Optional<Item> found = inventoryRepository.findByIdForUpdate(con, inventoryItemId);
            if (found.isEmpty()) {
                con.rollback();
                return Optional.empty();
            }
            Item item = found.get();
            if (quantity > item.getQuantity()) {
                throw new IllegalArgumentException("Only " + item.getQuantity() + " in stock, cannot take " + quantity);
            }

            int toTake = quantity;
            for (PurchaseRepository.Batch batch : purchaseRepository.lockBatchesWithStock(con, inventoryItemId)) {
                if (toTake == 0) {
                    break;
                }
                int left = batch.remaining() != null ? batch.remaining()
                        : batch.quantityPurchased() * StockAmounts.perPack(batch.packSize(), batch.packUnit(), item.getUnit());
                int take = Math.min(left, toTake);
                purchaseRepository.setRemaining(con, batch.purchaseId(), left - take);
                toTake -= take;
            }

            int newQuantity = item.getQuantity() - quantity;
            LocalDate newDate = purchaseRepository.earliestExpiryWithStock(con, inventoryItemId);
            if (newDate == null) {
                newDate = newQuantity == 0 ? null : item.getEarliestExpDate();
            }
            inventoryRepository.setStock(con, inventoryItemId, newQuantity, newDate);
            con.commit();
            return Optional.of(new Item(item.getId(), item.getNameEnglish(), item.getNameDeutsch(), newQuantity,
                    item.getUnit(), item.getCategory(), item.getSubcategory(), newDate));
        } catch (SQLException e) {
            rollback(con);
            throw new RuntimeException("Could not take " + quantity + " of inventory item " + inventoryItemId + " out of stock", e);
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
}
