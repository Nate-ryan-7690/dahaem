package de.dahaem.stores;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Promotes RAW store_product rows into the VERIFIED store_item layer that the
 * shopping list reads. A product is promoted only when it is "complete": matched
 * to an inventory_item, available, with a brand, a price, and a parseable pack size.
 * Everything else stays raw and is reported, never guessed into store_item.
 *
 * The size string ("500-g-Packung", "1,5-l-Flasche") is normalised to an integer in
 * a base unit (g / ml / Stück), so 1,5 l becomes 1500 ml and pack sizes are comparable.
 * Promotion is idempotent: re-running refreshes price/availability in place.
 *
 * Offer -> store_discount linking (and offer-only chains like Penny/aggregator, which
 * have no store_product) are a separate step; this only handles the range products.
 */
public class StorePromotionService {

    private final StoreRepository repository;

    public StorePromotionService(StoreRepository repository) {
        this.repository = repository;
    }

    /** A pack size reduced to an integer count of a base unit. */
    record Pack(int size, String unit) {}

    // "<n> <unit>" possibly as "500-g", "1,5-l", "6 x 0,33 l"; the container word ("Packung") is ignored.
    private static final Pattern SIZE = Pattern.compile(
            "(?:(\\d+(?:[.,]\\d+)?)\\s*-?\\s*[xX]\\s*-?\\s*)?"      // optional "6 x"
            + "(\\d+(?:[.,]\\d+)?)\\s*-?\\s*"                        // the amount
            + "(kg|g|ml|cl|l|stück|st)\\b",                          // the unit
            Pattern.CASE_INSENSITIVE);

    public ImportSummary promote() {
        List<PromotableProduct> products = repository.loadPromotableProducts();
        int inserted = 0, updated = 0, skippedBrand = 0, skippedSize = 0, legacy = 0;

        Connection con = repository.openTransaction();
        try {
            legacy = repository.deleteLegacyStoreItems(con);   // one-time: drop pre-per-product rows
            for (PromotableProduct p : products) {
                if (p.brand() == null || p.brand().isBlank()) {
                    skippedBrand++;
                    continue;
                }
                Pack pack = parseSize(p.salesUnit());
                if (pack == null) {
                    skippedSize++;
                    continue;
                }
                int brandId = repository.findOrCreateBrand(con, p.brand().trim());
                boolean isNew = repository.upsertStoreItem(con, p.storeProductId(), p.storeId(), brandId,
                        p.inventoryItemId(), pack.unit(), pack.size(), p.regularPrice(), p.available(), p.deposit());
                if (isNew) {
                    inserted++;
                } else {
                    updated++;
                }
            }
            con.commit();
        } catch (SQLException e) {
            rollback(con);
            throw new RuntimeException("Promotion failed: " + e.getMessage(), e);
        } finally {
            close(con);
        }

        String notes = String.format("%d candidates: %d new, %d refreshed; skipped %d no-brand, %d unparseable-size%s",
                products.size(), inserted, updated, skippedBrand, skippedSize,
                legacy > 0 ? "; cleared " + legacy + " legacy rows" : "");
        return new ImportSummary("(all stores)", "PROMOTE", "OK", 0, inserted + updated, List.of(), notes);
    }

    /** "500-g-Packung" -> 500 g; "1,5-l-Flasche" -> 1500 ml; "6 x 0,33 l" -> 1980 ml; null if unparseable. */
    static Pack parseSize(String salesUnit) {
        if (salesUnit == null) {
            return null;
        }
        Matcher m = SIZE.matcher(salesUnit);
        if (!m.find()) {
            return null;
        }
        double multiplier = m.group(1) != null ? num(m.group(1)) : 1.0;
        double amount = num(m.group(2));
        String unit = m.group(3).toLowerCase();
        double total = multiplier * amount;
        switch (unit) {
            case "kg" -> { total *= 1000; unit = "g"; }
            case "l"  -> { total *= 1000; unit = "ml"; }
            case "cl" -> { total *= 10;   unit = "ml"; }
            case "st" -> unit = "stück";
            default -> { /* g, ml, stück stay as-is */ }
        }
        int size = (int) Math.round(total);
        return size > 0 ? new Pack(size, unit) : null;
    }

    private static double num(String s) {
        return Double.parseDouble(s.replace(",", "."));
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
