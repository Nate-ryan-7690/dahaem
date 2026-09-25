package de.dahaem.stores;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

        int items = 0, discounts = 0, unmatched = 0, noSize = 0;
        List<String> unmatchedRows = new ArrayList<>();

        Connection con = repository.openTransaction();
        try {
            for (OfferRow o : offers) {
                String brand = o.brand();
                String matchName = o.name();
                if ((brand == null || brand.isBlank()) && o.name() != null) {
                    String[] parts = o.name().trim().split("\\s+", 2);   // aggregator: "Brand Rest"
                    brand = parts[0];
                    matchName = parts.length > 1 ? parts[1] : o.name();
                }
                if (brand == null || brand.isBlank() || matchName == null || matchName.isBlank()) {
                    unmatched++;
                    continue;
                }
                Integer itemId = matcher.match(matchName, catalog);
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
                int brandId = repository.findOrCreateBrand(con, brand.trim());
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
