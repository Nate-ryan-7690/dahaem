package de.dahaem.stores;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Turns pulled flyer offers into the verified layer: each matched offer becomes (or refreshes)
 * a store_item shell for its chain, plus a store_discount holding the flyer price and dates.
 *
 * A flyer price is a DISCOUNT, not a normal price, so the store_item's normal price is set only
 * from a "was" price when the flyer shows one (else left null until a receipt fills it in). The
 * flyer price always lands on the store_discount. This is also how the offer-only chains (Penny +
 * all aggregator chains, which have no range/store_product) get their store_items bootstrapped,
 * without hand-entering every product.
 *
 * An offer is promoted only when its name matches a generic inventory_item and its pack size parses;
 * everything else is written to a review file, never guessed into the verified layer.
 */
public class StoreDiscountService {

    /**
     * brand_id is NOT NULL, so an offer without a recognisable brand (own-label / fresh produce such as
     * "Fischstäbchen", "Zwiebeln") is filed under this neutral brand instead of an invented one.
     */
    static final String NO_BRAND = "Ohne Marke";

    private final StoreRepository repository;
    private final Map<String, Integer> overrides;

    public StoreDiscountService(StoreRepository repository, Map<String, Integer> overrides) {
        this.repository = repository;
        this.overrides = overrides;
    }

    public ImportSummary promoteOffers(Integer storeId, Path reviewFile) {
        List<CatalogItem> catalog = repository.loadCatalog();
        StoreMatchService matcher = new StoreMatchService(repository, overrides);
        List<OfferRow> offers = repository.loadOffersForPromotion(storeId);
        Map<String, String> knownBrands = knownBrandsByKey(repository.loadKnownOfferBrands());

        int items = 0, discounts = 0, unmatched = 0, noSize = 0;
        List<String> unmatchedRows = new ArrayList<>();

        Connection con = repository.openTransaction();
        try {
            for (OfferRow o : offers) {
                BrandSplit split = resolveBrand(o.brand(), o.name(), knownBrands);
                String brand = split.brand();
                String matchName = split.matchName();
                if (matchName == null || matchName.isBlank()) {
                    unmatched++;
                    continue;
                }
                Integer itemId = matcher.match(matchName, o.category(), catalog);
                if (itemId == null) {
                    unmatched++;
                    unmatchedRows.add(o.storeId() + "\t" + o.brand() + "\t" + o.name());
                    continue;
                }
                StorePromotionService.Pack pack = StorePromotionService.parseSize(o.salesUnit());
                if (pack == null) {
                    noSize++;
                    continue;
                }
                int brandId = repository.findOrCreateBrand(con, brand == null ? NO_BRAND : brand);
                int storeItemId = repository.upsertStoreItemFromOffer(con, o.storeId(), o.externalId(), brandId,
                        itemId, pack.unit(), pack.size(), o.normalPrice(), true, o.deposit());
                items++;
                boolean newDiscount = repository.upsertDiscount(con, storeItemId, o.validFrom(), o.validUntil(),
                        o.salePrice(), o.dealQuantity(), o.dealPrice(), o.promoText());
                if (newDiscount) {
                    discounts++;
                }
            }
            con.commit();
        } catch (SQLException e) {
            rollback(con);
            throw new RuntimeException("Discount promotion failed: " + e.getMessage(), e);
        } finally {
            close(con);
        }

        writeReview(reviewFile, unmatchedRows);
        String notes = String.format("%d offers: %d store_items touched, %d new discounts; "
                + "%d unmatched (-> %s), %d unparseable-size", offers.size(), items, discounts,
                unmatched, reviewFile, noSize);
        return new ImportSummary("(offers)", "DISCOUNTS", "OK", 0, discounts, List.of(), notes);
    }

    /** Brand to file the offer under (null = no brand) and the text to match against the catalog. */
    record BrandSplit(String brand, String matchName) {
    }

    /**
     * A flyer brand wins. A brandless aggregator name only yields a brand when its first word is a brand
     * we already know from the stores' own data (offer / range brands); otherwise it has no brand and is
     * matched on its full name. The first word is never taken as a brand just because it comes first.
     */
    static BrandSplit resolveBrand(String brand, String name, Map<String, String> knownBrands) {
        String cleanName = name == null ? null : name.trim();
        if (brand != null && !brand.isBlank()) {
            return new BrandSplit(brand.trim(), cleanName);
        }
        if (cleanName == null || cleanName.isEmpty()) {
            return new BrandSplit(null, cleanName);
        }
        String[] parts = cleanName.split("\\s+", 2);
        String known = parts.length > 1 ? knownBrands.get(parts[0].toLowerCase(Locale.GERMAN)) : null;
        if (known != null) {
            return new BrandSplit(known, parts[1]);
        }
        return new BrandSplit(null, cleanName);
    }

    private static Map<String, String> knownBrandsByKey(Set<String> brands) {
        Map<String, String> byKey = new HashMap<>();
        for (String b : brands) {
            if (b != null && !b.isBlank() && !b.contains(" ")) {      // single-word brands only; first word is compared
                byKey.putIfAbsent(b.trim().toLowerCase(Locale.GERMAN), b.trim());
            }
        }
        return byKey;
    }

    private void writeReview(Path file, List<String> rows) {
        StringBuilder sb = new StringBuilder("# unmatched offers (store_id\tbrand\tname): " + rows.size() + "\n");
        rows.forEach(r -> sb.append(r).append('\n'));
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write review file " + file, e);
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
