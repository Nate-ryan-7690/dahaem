package de.dahaem.barcode;

import de.dahaem.finance.PurchaseService;
import de.dahaem.finance.StockAmounts;
import de.dahaem.stores.StoreRef;
import de.dahaem.stores.StoreRepository;
import de.dahaem.stores.UnknownStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Puts groceries away by barcode: for each line (barcode, packs, expiry date, price) the store item for (store, brand,
 * catalogue item, pack) is found or created, linked to the barcode, and the purchase recorded, which adds packs x pack size
 * to the item's stock in the item's own unit and keeps the expiry date as a batch. Every line stands on its own: one bad
 * line is reported and the rest are still put away. No SQL here, no UI here.
 */
public class IntakeService {

    private static final Logger log = LoggerFactory.getLogger(IntakeService.class);
    static final String NO_BRAND = "Ohne Marke";
    static final int MAX_PACKS = 1000;

    private final ProductBarcodeRepository barcodes;
    private final PurchaseService purchases;
    private final StoreRepository stores;

    public IntakeService(ProductBarcodeRepository barcodes, PurchaseService purchases, StoreRepository stores) {
        this.barcodes = barcodes;
        this.purchases = purchases;
        this.stores = stores;
    }

    /**
     * One line to put away. {@code packs} defaults to 1; {@code expirationDate} is required; {@code pricePaid} is the total
     * for all the packs of the line and, when missing, the known price of the store item times the packs (a line for a
     * product with no known price at this store needs it); {@code packSize} / {@code packUnit} (both or neither) replace
     * the barcode's pack for this purchase, for a special package.
     */
    public record Line(String barcode, Integer packs, LocalDate expirationDate, Double pricePaid, Integer packSize,
                       String packUnit) {
    }

    /** What happened to one line: recorded (purchase, store item, how much stock was added in which unit) or why not. */
    public record LineResult(int index, String barcode, boolean ok, Integer purchaseId, Integer storeItemId,
                             Integer stockAdded, String stockUnit, String code, String message) {
    }

    /**
     * Puts the lines away at the store. The store must already exist (case does not matter) unless {@code createStore}
     * is set, so a typo cannot add a bogus store: otherwise an UnknownStoreException (400 UNKNOWN_STORE) and nothing is
     * recorded.
     */
    public List<LineResult> intake(String storeName, boolean createStore, LocalDate purchaseDate, List<Line> lines) {
        String wanted = storeName.trim();
        Optional<StoreRef> existing = stores.findStoreByName(wanted);
        if (existing.isEmpty() && !createStore) {
            throw new UnknownStoreException(wanted);
        }
        String store = existing.map(StoreRef::name).orElse(wanted);
        List<LineResult> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            out.add(one(i, store, purchaseDate, lines.get(i)));
        }
        return out;
    }

    private LineResult one(int index, String storeName, LocalDate purchaseDate, Line line) {
        String shown = line.barcode();
        try {
            String code = Gtin.normalize(line.barcode());
            shown = code;
            BarcodeProduct product = barcodes.findByCode(code).orElseThrow(() -> new LineException("BARCODE_UNKNOWN",
                    "This barcode is not known yet: assign it to a catalogue item first"));
            int packs = line.packs() == null ? 1 : line.packs();
            if (packs < 1 || packs > MAX_PACKS) {
                throw new LineException("BAD_REQUEST", "'packs' must be between 1 and " + MAX_PACKS);
            }
            if (line.expirationDate() == null) {
                throw new LineException("EXPIRY_REQUIRED", "'expirationDate' is required");
            }
            if ((line.packSize() == null) != (line.packUnit() == null)) {
                throw new LineException("BAD_REQUEST", "give 'packSize' and 'packUnit' together or not at all");
            }
            int packSize = product.packSize();
            String packUnit = product.packUnit();
            if (line.packSize() != null) {
                BarcodeService.Pack pack = BarcodeService.canonicalPack(line.packSize(), line.packUnit().trim(),
                        product.itemUnit());
                packSize = pack.size();
                packUnit = pack.unit();
            }
            String brand = product.brand() == null ? NO_BRAND : product.brand();

            double price;
            if (line.pricePaid() != null) {
                if (line.pricePaid() < 0) {
                    throw new LineException("BAD_REQUEST", "'pricePaid' must not be negative");
                }
                price = line.pricePaid();
            } else {
                Optional<Double> known = stores.findStoreItemPrice(storeName, brand, product.inventoryItemId(),
                        packUnit, packSize);
                if (known.isEmpty()) {
                    throw new LineException("PRICE_REQUIRED", "No price is known for this product at " + storeName
                            + ": give 'pricePaid' for the line");
                }
                price = known.get() * packs;
            }
            price = Math.round(price * 100) / 100.0;

            int storeItemId = purchases.findOrCreateStoreItem(storeName, brand, product.inventoryItemId(), null, null,
                    null, packUnit, packSize, null);
            barcodes.link(code, storeItemId);
            int purchaseId = purchases.recordPurchase(storeItemId, null, false, purchaseDate, packs, price,
                    line.expirationDate());
            int added = packs * StockAmounts.perPack(packSize, packUnit, product.itemUnit());
            return new LineResult(index, code, true, purchaseId, storeItemId, added, product.itemUnit(), null, null);
        } catch (LineException e) {
            return failed(index, shown, e.code, e.getMessage());
        } catch (InvalidBarcodeException e) {
            return failed(index, shown, e.code(), e.getMessage());
        } catch (PackUnitException e) {
            return failed(index, shown, "PACK_UNIT_MISMATCH", e.getMessage());
        } catch (IllegalArgumentException e) {
            return failed(index, shown, "BAD_REQUEST", e.getMessage());
        } catch (RuntimeException e) {
            log.error("Could not put away barcode line {}", index, e);
            return failed(index, shown, "INTERNAL_ERROR", "This line could not be recorded");
        }
    }

    private static LineResult failed(int index, String barcode, String code, String message) {
        return new LineResult(index, barcode, false, null, null, null, null, code, message);
    }

    private static final class LineException extends RuntimeException {
        private final String code;

        LineException(String code, String message) {
            super(message);
            this.code = code;
        }
    }
}
