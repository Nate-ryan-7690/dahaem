package de.dahaem.finance;

import de.dahaem.inventory.InventoryRepository;
import de.dahaem.stores.StoreRepository;

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
    private final StoreRepository storeRepository;
    private final InventoryRepository inventoryRepository;

    public PurchaseService(PurchaseRepository repository) {
        this(repository, new StoreRepository(), new InventoryRepository());
    }

    public PurchaseService(PurchaseRepository repository, StoreRepository storeRepository,
                            InventoryRepository inventoryRepository) {
        this.repository = repository;
        this.storeRepository = storeRepository;
        this.inventoryRepository = inventoryRepository;
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

    /**
     * Records a purchase of a product that isn't in the catalog yet — an off-list buy, e.g. from a
     * receipt, with no shopping_list_item behind it. Finds or creates the store and brand, resolves
     * the inventory_item (an existing one, or a new generic one with quantity 0 and no expiry), finds
     * or creates the matching store_item for (store, brand, item, unit, size), and then delegates to
     * {@link #recordPurchase} so the purchase row and inventory update stay atomic. Returns the new
     * purchase's id.
     *
     * <p>Exactly one of {@code existingInventoryItemId} or the {@code newItem*} fields must be used:
     * pass an id to reuse a known item, or leave it null and pass name/category to create a generic one.
     */
    public int recordNewProductPurchase(String storeName, String brandName, Integer existingInventoryItemId,
                                         String newItemNameEnglish, String newItemNameDeutsch, String newItemCategory,
                                         String packageUnit, int packageSize, Double normalPrice,
                                         int quantityPurchased, double pricePaid, LocalDate purchaseDate,
                                         LocalDate expirationDate) {
        int storeId = storeRepository.findOrCreateStore(storeName);

        int storeItemId;
        Connection con = repository.openTransaction();
        try {
            int brandId = storeRepository.findOrCreateBrand(con, brandName);
            int inventoryItemId = existingInventoryItemId != null
                    ? existingInventoryItemId
                    : inventoryRepository.findOrCreateGenericItem(con, newItemNameEnglish, newItemNameDeutsch,
                            newItemCategory, packageUnit);
            storeItemId = storeRepository.findOrCreateStoreItem(con, storeId, brandId, inventoryItemId,
                    packageUnit, packageSize, normalPrice);

            con.commit();
        } catch (SQLException e) {
            rollback(con);
            throw new RuntimeException("Could not set up catalog entries for a new product purchase at " + storeName, e);
        } catch (RuntimeException e) {
            rollback(con);
            throw e;
        } finally {
            close(con);
        }

        return recordPurchase(storeItemId, null, false, purchaseDate, quantityPurchased, pricePaid, expirationDate);
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
