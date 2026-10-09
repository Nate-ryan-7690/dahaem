package de.dahaem.stores;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The store-data refresh pipelines, shared by the CLI ({@code StoreImportRunner refresh ...})
 * and the in-app scheduler ({@link ScheduledRefresh}). See docs/refresh.md.
 *
 * <pre>
 *   weekly:  offers aldi -> offers penny -> offers aggregator (each chain) -> discounts
 *   monthly: range aldi -> match aldi -> promote -> discounts
 * </pre>
 *
 * Each step is independent: one that throws is recorded and the next still runs. The match,
 * promote and discount services are created lazily per step, so a failure while building one
 * (e.g. a malformed config/store_overrides.json) only fails that step.
 */
public class RefreshService {

    private static final Logger log = LoggerFactory.getLogger(RefreshService.class);
    static final Path DATA_DIR = Path.of("data", "stores");

    private final StoreImportService importService;
    private final Supplier<StoreMatchService> matcher;
    private final Supplier<StorePromotionService> promoter;
    private final Supplier<StoreDiscountService> discounts;

    public RefreshService(StoreImportService importService, Supplier<StoreMatchService> matcher,
                          Supplier<StorePromotionService> promoter, Supplier<StoreDiscountService> discounts) {
        this.importService = importService;
        this.matcher = matcher;
        this.promoter = promoter;
        this.discounts = discounts;
    }

    /** Production wiring: real HTTP clients and repository, config read from the working directory. */
    public static RefreshService create() {
        StoreConfig config = new StoreConfig();
        HttpFetcher http = new HttpFetcher(config.requestDelay());
        StoreRepository repository = new StoreRepository();
        StoreImportService importService = new StoreImportService(
                repository, new AldiNordClient(http), new PennyClient(http), new AggregatorClient(http), config);
        return new RefreshService(importService,
                () -> new StoreMatchService(repository, loadOverrides()),
                () -> new StorePromotionService(repository),
                () -> new StoreDiscountService(repository, loadOverrides()));
    }

    public RefreshSummary refreshWeekly() {
        Run run = new Run("weekly");
        run.step("offers aldi", importService::importAldiOffers);
        run.step("offers penny", importService::importPennyOffers);
        for (String store : AggregatorClient.STORES.keySet()) {
            run.step("offers aggregator " + store, () -> importService.importAggregatorOffers(store));
        }
        run.step("discounts", this::discounts);
        return run.finish();
    }

    public RefreshSummary refreshMonthly() {
        Run run = new Run("monthly");
        run.step("range aldi", importService::importAldiRange);
        run.step("match aldi", this::matchAldi);
        run.step("promote", () -> promoter.get().promote());
        run.step("discounts", this::discounts);
        return run.finish();
    }

    /** The "match aldi" step; also used by the standalone CLI job. */
    ImportSummary matchAldi() {
        return matcher.get().matchStore(StoreImportService.ALDI, DATA_DIR.resolve("unmatched_aldi.tsv"));
    }

    /** The "discounts" step; also used by the standalone CLI job. */
    ImportSummary discounts() {
        return discounts.get().promoteOffers(null, DATA_DIR.resolve("unmatched_offers.tsv"));
    }

    /** config/store_overrides.json: {"exact": {"<normalized store name>": <inventory_item_id>}}. Optional. */
    static Map<String, Integer> loadOverrides() {
        Map<String, Integer> map = new HashMap<>();
        Path file = Path.of("config", "store_overrides.json");
        if (!Files.exists(file)) {
            return map;
        }
        try {
            new ObjectMapper().readTree(file.toFile()).path("exact").fields()
                    .forEachRemaining(e -> map.put(e.getKey(), e.getValue().asInt()));
        } catch (Exception e) {
            // A present-but-malformed file must fail loudly: silently continuing with no overrides
            // would quietly degrade match quality (everything lands in the unmatched review file).
            throw new RuntimeException("Malformed config/store_overrides.json (fix or remove it): " + e.getMessage(), e);
        }
        return map;
    }

    /** Collects step results for one flow, running every step regardless of earlier failures. */
    private static final class Run {
        private final String flow;
        private final List<ImportSummary> results = new ArrayList<>();
        private final List<String> failures = new ArrayList<>();

        Run(String flow) {
            this.flow = flow;
            log.info("refresh {}: start", flow);
        }

        void step(String name, Supplier<ImportSummary> action) {
            try {
                ImportSummary r = action.get();
                results.add(r);
                log.info("refresh {}: {} -> {} ({} items){}", flow, name, r.status(), r.items(),
                        r.notes().isEmpty() ? "" : " " + r.notes());
            } catch (RuntimeException e) {
                failures.add(name + ": " + e.getMessage());
                log.error("refresh {}: {} FAILED: {}", flow, name, e.getMessage(), e);
            }
        }

        RefreshSummary finish() {
            RefreshSummary summary = new RefreshSummary(flow, List.copyOf(results), List.copyOf(failures));
            log.info("refresh {}: finished, {} step(s) reported, {} failed, allOk={}",
                    flow, results.size(), failures.size(), summary.allOk());
            return summary;
        }
    }
}
