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
    private static final Pattern THOUSANDS = Pattern.compile("\\d{1,3}(?:\\.\\d{3})+");
    /**
     * Some shop pages write the litre sign as a capital I ("0,7 I", "1,5 I"). Only an upper-case I that directly
     * follows a number and stands alone counts, so "Typ I", "2 IN 1" or "500 g I" are left alone.
     */
    private static final Pattern CAPITAL_I_LITRE = Pattern.compile("(\\d(?:[.,]\\d+)?)\\s*-?\\s*I(?![\\p{L}\\d])");
    /** "46-50 WL", "4 - 6 Stück", "140 - 150g", "0,5 - 0,7l": a range of the same unit; the lower number counts. */
    private static final Pattern RANGE = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*-\\s*(?=\\d+(?:[.,]\\d+)?\\s*-?\\s*(?:kg|g|ml|cl|l|stück|st|wl)\\b)(?:\\d+(?:[.,]\\d+)?)\\s*-?\\s*(kg|g|ml|cl|l|stück|st|wl)\\b",
            Pattern.CASE_INSENSITIVE);
    /** "8er-Packung", "10er", "6er-Pack": that many pieces. */
    private static final Pattern MULTIPACK = Pattern.compile("(?<![\\d.,])(\\d{1,3})er\\b", Pattern.CASE_INSENSITIVE);
    /** Products whose litre figure is a yield. */
    private static final Pattern YIELD_PRODUCT = Pattern.compile("(?i)br(ü|ue)he|bouillon|\\bfond\\b|\\bfonds\\b");
    private static final Pattern SIZE = Pattern.compile(
            "(?:(\\d+(?:[.,]\\d+)?)\\s*-?\\s*[xX]\\s*-?\\s*)?"      // optional "6 x"
            + "(\\d+(?:[.,]\\d+)?)\\s*-?\\s*"                        // the amount
            + "(kg|g|ml|cl|l|stück|st|wl)\\b",                       // the unit ("WL" = Waschladungen)
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
                Pack pack = parseSize(p.salesUnit(), p.name());
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
        return parseSize(salesUnit, null);
    }

    /**
     * Like {@link #parseSize(String)}, with the product name for the cases where the name changes the reading.
     * For broths and stocks ("Gemüse Brühe 18 I") a litre figure is the yield (what the pack makes), not the content,
     * so only a stated weight counts; without one there is no usable size.
     */
    static Pack parseSize(String salesUnit, String productName) {
        if (salesUnit == null) {
            return null;
        }
        boolean yieldProduct = productName != null && YIELD_PRODUCT.matcher(productName).find();
        String text = CAPITAL_I_LITRE.matcher(salesUnit).replaceAll("$1 l");
        text = RANGE.matcher(text).replaceAll("$1 $2");            // "46-50 WL", "4 - 6 Stück": the LOWER number
        Matcher m = SIZE.matcher(text);
        boolean found = false;
        while (m.find()) {
            String u = m.group(3).toLowerCase();
            if (yieldProduct && (u.equals("l") || u.equals("ml") || u.equals("cl"))) {
                continue;                                           // a yield, look for a weight instead
            }
            found = true;
            break;
        }
        Matcher multi = MULTIPACK.matcher(text);
        int packs = 0;
        int multiCount = 0;
        while (multi.find()) {
            multiCount++;
            packs = Integer.parseInt(multi.group(1));
        }
        if (!found) {
            if (multiCount == 1 && !yieldProduct && packs > 0) {
                return new Pack(packs, "stück");                    // "8er-Packung" = 8 pieces
            }
            return null;
        }
        String unit = m.group(3).toLowerCase();
        double multiplier = m.group(1) != null ? num(m.group(1), unit) : 1.0;
        double amount = num(m.group(2), unit);
        double total = multiplier * amount;
        switch (unit) {
            case "kg" -> { total *= 1000; unit = "g"; }
            case "l"  -> { total *= 1000; unit = "ml"; }
            case "cl" -> { total *= 10;   unit = "ml"; }
            case "st" -> unit = "stück";
            case "wl" -> unit = "WL";
            default -> { /* g, ml, stück stay as-is */ }
        }
        int size = (int) Math.round(total);
        return size > 0 ? new Pack(size, unit) : null;
    }

    private static double num(String s, String unit) {
        boolean smallUnit = unit.equals("g") || unit.equals("ml") || unit.equals("st") || unit.equals("stück");
        if (smallUnit && THOUSANDS.matcher(s).matches()) {
            return Double.parseDouble(s.replace(".", ""));
        }
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
