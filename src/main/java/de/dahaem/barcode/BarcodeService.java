package de.dahaem.barcode;

import de.dahaem.inventory.InventoryRepository;
import de.dahaem.stores.StoreRepository;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Barcodes: what a number is, and remembering it. A barcode DAHÄM knows is answered from its own table; an unknown one
 * may be described by an open data source (a suggestion only, never saved by itself). No SQL here, no UI here.
 */
public class BarcodeService {

    private final ProductBarcodeRepository repository;
    private final InventoryRepository inventoryRepository;
    private final StoreRepository storeRepository;
    private final ProductDataSource source;

    public BarcodeService(ProductBarcodeRepository repository, InventoryRepository inventoryRepository,
                          StoreRepository storeRepository, ProductDataSource source) {
        this.repository = repository;
        this.inventoryRepository = inventoryRepository;
        this.storeRepository = storeRepository;
        this.source = source;
    }

    /** What the barcode is: from DAHÄM if known, else a suggestion from the open data source if it is switched on. */
    public BarcodeLookup lookup(String rawCode) {
        String code = Gtin.normalize(rawCode);
        Optional<BarcodeProduct> known = repository.findByCode(code);
        if (known.isPresent()) {
            return new BarcodeLookup(code, "KNOWN", known.get(), null);
        }
        if (!source.enabled()) {
            return new BarcodeLookup(code, "DISABLED", null, null);
        }
        try {
            return source.find(code)
                    .map(suggestion -> new BarcodeLookup(code, "FOUND", null, suggestion))
                    .orElseGet(() -> new BarcodeLookup(code, "NOT_FOUND", null, null));
        } catch (IOException e) {
            return new BarcodeLookup(code, "UNAVAILABLE", null, null);
        }
    }

    /**
     * Remembers what a barcode is (or replaces it): the catalogue item, the brand (blank = none), the pack and the name as
     * printed. {@code storeItemId} (optional) also links the store item that carries it; it must be for the same catalogue
     * item. Returns the barcode as it is now.
     */
    public BarcodeLookup assign(String rawCode, int inventoryItemId, String brand, int packSize, String packUnit,
                                String name, Integer storeItemId, LocalDate today) {
        String code = Gtin.normalize(rawCode);
        String unit = packUnit == null ? "" : packUnit.trim();
        if (packSize <= 0 || packSize > 9_999_999) {
            throw new IllegalArgumentException("'packSize' must be above 0 and below 10000000");
        }
        if (unit.isEmpty() || unit.length() > 20) {
            throw new IllegalArgumentException("'packUnit' is required (at most 20 characters)");
        }
        String brandName = brand == null || brand.isBlank() ? null : brand.trim();
        String printedName = name == null || name.isBlank() ? null : name.trim();
        if (printedName != null && printedName.length() > 200) {
            throw new IllegalArgumentException("'name' must be at most 200 characters");
        }
        if (inventoryRepository.findById(inventoryItemId).isEmpty()) {
            throw new IllegalArgumentException("No inventory item with id " + inventoryItemId);
        }
        Connection con = repository.openTransaction();
        try {
            if (storeItemId != null) {
                Integer itemOfStoreItem = repository.findInventoryItemIdOfStoreItem(con, storeItemId).orElse(null);
                if (itemOfStoreItem == null) {
                    throw new IllegalArgumentException("No store item with id " + storeItemId);
                }
                if (itemOfStoreItem != inventoryItemId) {
                    throw new IllegalArgumentException("Store item " + storeItemId
                            + " is for another catalogue item than " + inventoryItemId);
                }
            }
            Integer brandId = brandName == null ? null : storeRepository.findOrCreateBrand(con, brandName);
            repository.upsert(con, code, inventoryItemId, brandId, unit, packSize, printedName, today);
            if (storeItemId != null) {
                repository.link(con, code, storeItemId);
            }
            con.commit();
        } catch (SQLException e) {
            rollback(con);
            throw new RuntimeException("Could not save barcode " + code, e);
        } catch (RuntimeException e) {
            rollback(con);
            throw e;
        } finally {
            close(con);
        }
        return lookup(code);
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
