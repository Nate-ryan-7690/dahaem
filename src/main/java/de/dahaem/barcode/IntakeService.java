package de.dahaem.barcode;

import de.dahaem.finance.PurchaseService;
import de.dahaem.finance.StockAmounts;
import de.dahaem.stores.StoreRef;
import de.dahaem.stores.StoreRepository;
import de.dahaem.stores.UnknownStoreException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
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

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ProductBarcodeRepository barcodes;
    private final PurchaseService purchases;
    private final StoreRepository stores;
    private final IntakeRequestRepository requests;

    public IntakeService(ProductBarcodeRepository barcodes, PurchaseService purchases, StoreRepository stores,
                         IntakeRequestRepository requests) {
        this.barcodes = barcodes;
        this.purchases = purchases;
        this.stores = stores;
        this.requests = requests;
    }

    /** The lines' results, and whether they are the stored answer to a request id seen before (nothing was recorded again). */
    public record Outcome(List<LineResult> results, boolean replayed) {
    }

    /**
     * Like {@link #intake} but safe to send twice: a client that is not sure its request arrived repeats it with the same
     * {@code requestId} and gets the stored answer back ({@code replayed}), and nothing is recorded a second time.
     * {@code fingerprint} identifies the request body: the same id with another body is an IntakeConflictException
     * (409 REQUEST_ID_REUSED); an id whose first request is still being processed is 409 REQUEST_IN_PROGRESS. A request
     * that fails before anything was recorded (an unknown store) gives its id back, so the corrected request can be sent
     * with the same id. Without a request id this is plain {@link #intake}.
     */
    public Outcome intakeOnce(int householdId, String requestId, String fingerprint, String storeName,
                              boolean createStore, LocalDate purchaseDate, List<Line> lines) {
        if (requestId == null) {
            return new Outcome(intake(householdId, storeName, createStore, purchaseDate, lines), false);
        }
        // the same request id of two households are two requests: the stored key is made from both
        String key = sha256(householdId + ":" + requestId);
        String hash = sha256(fingerprint);
        if (!requests.claim(key, hash)) {
            IntakeRequestRepository.Stored stored = requests.find(key)
                    .orElseThrow(() -> new IntakeConflictException("REQUEST_IN_PROGRESS",
                            "This request id was just given back: send the request again"));
            if (!stored.hash().equals(hash)) {
                throw new IntakeConflictException("REQUEST_ID_REUSED",
                        "This request id was already used for a different request: use a new one");
            }
            if (stored.resultJson() == null) {
                throw new IntakeConflictException("REQUEST_IN_PROGRESS",
                        "This request is still being processed: wait, and check the stock before sending it again");
            }
            return new Outcome(fromJson(stored.resultJson()), true);
        }
        try {
            List<LineResult> results = intake(householdId, storeName, createStore, purchaseDate, lines);
            requests.complete(key, toJson(results));
            return new Outcome(results, false);
        } catch (RuntimeException e) {
            requests.release(key);   // every line is handled on its own; only the store check can fail before any is recorded
            throw e;
        }
    }

    private static String toJson(List<LineResult> results) {
        try {
            return JSON.writeValueAsString(results);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not store the intake answer", e);
        }
    }

    private static List<LineResult> fromJson(String json) {
        try {
            return JSON.readValue(json, new TypeReference<List<LineResult>>() {
            });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read the stored intake answer", e);
        }
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
    public List<LineResult> intake(int householdId, String storeName, boolean createStore, LocalDate purchaseDate,
                                   List<Line> lines) {
        String wanted = storeName.trim();
        Optional<StoreRef> existing = stores.findStoreByName(wanted);
        if (existing.isEmpty() && !createStore) {
            throw new UnknownStoreException(wanted);
        }
        String store = existing.map(StoreRef::name).orElse(wanted);
        List<LineResult> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            out.add(one(householdId, i, store, purchaseDate, lines.get(i)));
        }
        return out;
    }

    private LineResult one(int householdId, int index, String storeName, LocalDate purchaseDate, Line line) {
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
            int purchaseId = purchases.recordPurchase(householdId, storeItemId, null, false, purchaseDate, packs, price,
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
