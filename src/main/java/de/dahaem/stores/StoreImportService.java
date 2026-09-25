package de.dahaem.stores;

import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Runs the store imports and decides what changed. No SQL (StoreRepository),
 * no web parsing (AldiNordClient / PennyClient): only the rules.
 *
 * Range import = monthly comparator:
 *  - first run is the initial load: everything is stored, no change rows logged
 *  - later runs log NEW / PRICE / RETURNED / REMOVED in store_catalog_change
 *  - products are only marked REMOVED if the crawl was complete and not
 *    suspiciously small, so a network hiccup can't "remove" half the range
 *  - a sale price never overwrites the stored normal price
 */
public class StoreImportService {

    public static final String ALDI = "Aldi";       // existing row in `store` (this is ALDI Nord)
    public static final String PENNY = "Penny";     // existing row in `store`

    /** A pull smaller than this share of the known range is treated as incomplete. */
    private static final double MIN_COMPLETE_SHARE = 0.8;
    private static final double PRICE_EPSILON = 0.004;

    /** Provenance tags stored on store_offer.store_offer_source. */
    public static final String SRC_STORE = "STORE";
    public static final String SRC_AGGREGATOR = "AGGREGATOR";

    private final StoreRepository repository;
    private final AldiNordClient aldi;
    private final PennyClient penny;
    private final AggregatorClient aggregator;
    private final StoreConfig config;

    public StoreImportService(StoreRepository repository, AldiNordClient aldi, PennyClient penny,
                              AggregatorClient aggregator, StoreConfig config) {
        this.repository = repository;
        this.aldi = aldi;
        this.penny = penny;
        this.aggregator = aggregator;
        this.config = config;
    }

    // ---------------------------------------------------------------- ALDI range (initial load + monthly)

    public ImportSummary importAldiRange() {
        int storeId = repository.findOrCreateStore(ALDI);
        int runId = repository.startRun(storeId, "RANGE");
        try {
            CrawlResult crawl = aldi.fetchRange();
            Map<String, KnownProduct> known = repository.loadKnownProducts(storeId);
            boolean initialLoad = known.isEmpty();
            long knownAvailable = known.values().stream().filter(KnownProduct::available).count();
            boolean complete = crawl.isComplete()
                    && crawl.products().size() >= MIN_COMPLETE_SHARE * knownAvailable;

            List<StoreProduct> toSave = new ArrayList<>();
            List<CatalogChange> changes = new ArrayList<>();
            Set<String> recordPriceFor = new HashSet<>();
            Set<String> pulledIds = new HashSet<>();

            for (StoreProduct pulled : crawl.products()) {
                pulledIds.add(pulled.externalId());
                KnownProduct k = known.get(pulled.externalId());

                // Only a sale price was shown: keep the normal price we already have.
                StoreProduct product = (pulled.promoPriceOnly() && k != null)
                        ? pulled.withRegularPrice(k.regularPrice())
                        : pulled;
                toSave.add(product);
                Double price = product.regularPrice();

                if (k == null) {
                    if (!pulled.promoPriceOnly()) {
                        recordPriceFor.add(product.externalId());          // first price = baseline
                    }
                    if (!initialLoad) {
                        changes.add(new CatalogChange(product.externalId(), product.name(),
                                CatalogChange.Type.NEW, null, price));
                    }
                    continue;
                }
                if (!k.available()) {
                    changes.add(new CatalogChange(product.externalId(), product.name(),
                            CatalogChange.Type.RETURNED, k.regularPrice(), price));
                }
                if (price != null && priceChanged(k.regularPrice(), price)) {
                    recordPriceFor.add(product.externalId());
                    changes.add(new CatalogChange(product.externalId(), product.name(),
                            CatalogChange.Type.PRICE, k.regularPrice(), price));
                }
            }

            List<Integer> removedIds = new ArrayList<>();
            if (complete) {
                for (KnownProduct k : known.values()) {
                    if (k.available() && !pulledIds.contains(k.externalId())) {
                        removedIds.add(k.id());
                        changes.add(new CatalogChange(k.externalId(), k.name(),
                                CatalogChange.Type.REMOVED, k.regularPrice(), null));
                    }
                }
            }

            repository.saveRange(storeId, runId, toSave, known, removedIds, changes, recordPriceFor, today());

            String status = complete ? "OK" : "PARTIAL";
            StringBuilder notes = new StringBuilder(initialLoad ? "initial load" : "compared with previous pull");
            if (!crawl.failedPages().isEmpty()) {
                notes.append("; failed pages: ").append(crawl.failedPages());
            }
            if (!crawl.truncatedPages().isEmpty()) {
                notes.append("; truncated pages: ").append(crawl.truncatedPages());
            }
            if (!complete) {
                notes.append("; pull incomplete, removals NOT applied");
            }
            repository.finishRun(runId, status, crawl.pagesCrawled(), toSave.size(), notes.toString());
            return new ImportSummary(ALDI, "RANGE", status, crawl.pagesCrawled(), toSave.size(), changes, notes.toString());
        } catch (RuntimeException e) {
            repository.finishRun(runId, "FAILED", 0, 0, e.toString());
            throw e;
        }
    }

    // ---------------------------------------------------------------- offers

    public ImportSummary importAldiOffers() {
        int storeId = repository.findOrCreateStore(ALDI);
        int runId = repository.startRun(storeId, "OFFERS");
        try {
            return saveOffers(ALDI, storeId, runId, aldi.fetchOffers(), SRC_STORE);
        } catch (RuntimeException e) {
            repository.finishRun(runId, "FAILED", 0, 0, e.toString());
            throw e;
        }
    }

    /**
     * Aggregator offers for one chain (SECONDARY). storeKey is a key in
     * AggregatorClient.STORES (netto, edeka, rewe, lidl, famila, aldi). Tagged
     * AGGREGATOR so it never overwrites a direct pull and ranks below it.
     */
    public ImportSummary importAggregatorOffers(String storeKey) {
        String store = aggregator.displayName(storeKey);
        int storeId = repository.findOrCreateStore(store);
        int runId = repository.startRun(storeId, "OFFERS");
        try {
            return saveOffers(store, storeId, runId, aggregator.fetchOffers(storeKey, config.aggregatorPlz()), SRC_AGGREGATOR);
        } catch (IOException e) {
            repository.finishRun(runId, "FAILED", 0, 0, e.toString());
            throw new RuntimeException("Aggregator offer import failed for " + store + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            repository.finishRun(runId, "FAILED", 0, 0, e.toString());
            throw e;
        }
    }

    public ImportSummary importPennyOffers() {
        String marketId = config.pennyMarketId();
        if (marketId.isEmpty()) {
            throw new IllegalStateException("Set penny.market in config/stores.properties");
        }
        int storeId = repository.findOrCreateStore(PENNY);
        int runId = repository.startRun(storeId, "OFFERS");
        try {
            return saveOffers(PENNY, storeId, runId, penny.fetchOffers(marketId), SRC_STORE);
        } catch (IOException e) {
            repository.finishRun(runId, "FAILED", 0, 0, e.toString());
            throw new RuntimeException("Penny offer import failed: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            repository.finishRun(runId, "FAILED", 0, 0, e.toString());
            throw e;
        }
    }

    private ImportSummary saveOffers(String store, int storeId, int runId, OfferPull pull, String source) {
        if (pull.offers().isEmpty() && !pull.failedRequests().isEmpty()) {
            throw new RuntimeException(store + ": no offers pulled, all requests failed: " + pull.failedRequests());
        }
        int saved = repository.saveOffers(storeId, pull.offers(), today(), source);
        String status = pull.failedRequests().isEmpty() ? "OK" : "PARTIAL";
        String notes = pull.failedRequests().isEmpty() ? "" : "failed requests: " + pull.failedRequests();
        repository.finishRun(runId, status, 0, saved, notes);
        return new ImportSummary(store, "OFFERS", status, 0, saved, List.of(), notes);
    }

    // ---------------------------------------------------------------- helpers

    private static boolean priceChanged(Double oldPrice, double newPrice) {
        return oldPrice == null || Math.abs(oldPrice - newPrice) > PRICE_EPSILON;
    }

    private static LocalDate today() {
        return LocalDate.now(JsonUtil.GERMANY);
    }
}
