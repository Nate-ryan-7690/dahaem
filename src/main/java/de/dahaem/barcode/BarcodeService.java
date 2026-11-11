package de.dahaem.barcode;

import de.dahaem.inventory.InventoryRepository;
import de.dahaem.inventory.Item;
import de.dahaem.shared.Units;
import de.dahaem.stores.BrandRef;
import de.dahaem.stores.StoreRepository;
import de.dahaem.stores.UnknownBrandException;

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
     * Remembers what a barcode is (or replaces it): the catalogue item, the brand (blank = none; a new brand is created), the pack and the name as
     * printed. {@code storeItemId} (optional) also links the store item that carries it; it must be for the same catalogue
     * item. The pack is stored in the units the shopping list uses (g, ml, stück; see {@link #canonicalPack}) and must fit
     * the item's unit. Returns the barcode as it is now.
     */
    public BarcodeLookup assign(String rawCode, int inventoryItemId, String brand, int packSize, String packUnit,
                                String name, Integer storeItemId, LocalDate today) {
        return assign(rawCode, inventoryItemId, brand, packSize, packUnit, name, storeItemId, true, today);
    }

    /**
     * Like {@link #assign(String, int, String, int, String, String, Integer, LocalDate)}, but a brand that does not exist
     * is only created when {@code createBrand} is true; otherwise an UnknownBrandException (400 UNKNOWN_BRAND) and nothing
     * is saved, so a typo cannot add a bogus brand. The API uses this one.
     */
    public BarcodeLookup assign(String rawCode, int inventoryItemId, String brand, int packSize, String packUnit,
                                String name, Integer storeItemId, boolean createBrand, LocalDate today) {
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
        Item item = inventoryRepository.findCatalogueItem(inventoryItemId)
                .orElseThrow(() -> new IllegalArgumentException("No inventory item with id " + inventoryItemId));
        Pack pack = canonicalPack(packSize, unit, item.getUnit());
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
            Integer brandId = null;
            if (brandName != null) {
                brandId = createBrand
                        ? storeRepository.findOrCreateBrand(con, brandName)
                        : storeRepository.findBrandByName(brandName).map(BrandRef::id)
                                .orElseThrow(() -> new UnknownBrandException(brandName));
            }
            repository.upsert(con, code, inventoryItemId, brandId, pack.unit(), pack.size(), printedName, today);
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

    /** A pack as it is stored: the same units a store item and the shopping list use. */
    record Pack(int size, String unit) {
    }

    /**
     * The pack in the units the shopping list works in: mass in g, volume in ml, pieces in stück ("1 kg" is 1000 g, "1,5 l"
     * is 1500 ml), so a barcode's pack is the same as the matching store item's and the stock of the item. The pack must be
     * stockable in the item's own unit (same dimension): an item counted in g cannot take a pack in ml. A unit nobody knows
     * (WL, Waschladungen) is accepted only when it is the item's own unit. Otherwise a PackUnitException.
     */
    static Pack canonicalPack(int size, String unit, String itemUnit) {
        Units.Parsed parsed = Units.parse(unit);
        if (!parsed.isKnown()) {
            if (itemUnit != null && itemUnit.trim().equalsIgnoreCase(unit.trim())) {
                return new Pack(size, itemUnit.trim());
            }
            throw new PackUnitException("The pack unit '" + unit + "' is not a known unit and is not the item's unit '"
                    + itemUnit + "'");
        }
        String base = switch (parsed.dimension()) {
            case MASS -> "g";
            case VOLUME -> "ml";
            default -> "stück";
        };
        long total = Math.round(size * parsed.toBaseFactor());
        if (total <= 0 || total > 9_999_999) {
            throw new IllegalArgumentException("The pack is too large or too small: " + size + " " + unit);
        }
        if (!Units.compatible(itemUnit, base)) {
            throw new PackUnitException("A pack in " + base + " cannot be stocked for an item counted in '" + itemUnit + "'");
        }
        return new Pack((int) total, base);
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
