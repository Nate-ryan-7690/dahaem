package de.dahaem.finance;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Business logic for purchases. No SQL here, no UI here.
 * Recording a purchase touches both purchase_history and the linked inventory_item,
 * so it runs both writes in one transaction: either both land or neither does.
 */
public class PurchaseService {

    private final PurchaseRepository repository;

    public PurchaseService(PurchaseRepository repository) {
        this.repository = repository;
    }

    public List<Purchase> allPurchases() {
        return repository.findAll();
    }

    public double spendingTotal() {
        return repository.spendingTotal();
    }

    public double spendingBetween(LocalDate from, LocalDate to) {
        return repository.spendingBetween(from, to);
    }

    /**
     * Records a purchase and applies it to inventory in one transaction: inserts the
     * purchase_history row, then (via store_item.store_item_item_id) increases the linked
     * inventory_item's quantity by {@code quantityPurchased} and pulls its earliest expiration
     * date down to {@code expirationDate} if that is sooner. Returns the new purchase's id.
     */
    public int recordPurchase(int storeItemId, Integer discountId, boolean wasDiscounted, LocalDate purchaseDate,
                               int quantityPurchased, double pricePaid, LocalDate expirationDate) {
        Connection con = repository.openTransaction();
        try {
            int purchaseId = repository.insert(con, storeItemId, discountId, wasDiscounted, purchaseDate,
                    quantityPurchased, pricePaid, expirationDate);

            Integer inventoryItemId = repository.findInventoryItemIdForStoreItem(con, storeItemId);
            if (inventoryItemId == null) {
                throw new IllegalArgumentException("No store_item found with id " + storeItemId);
            }
            repository.applyPurchaseToInventory(con, inventoryItemId, quantityPurchased, expirationDate);

            con.commit();
            return purchaseId;
        } catch (SQLException e) {
            rollback(con);
            throw new RuntimeException("Could not record purchase for store item " + storeItemId, e);
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
