package de.dahaem.stores;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Command-line entry point for the store imports. Meant for cron / a systemd
 * timer on the server, and for running from IntelliJ during development.
 * Run it from the app directory (the one containing data/ and config/).
 *
 *   range aldi     ALDI Nord full range; first run = initial load, then monthly comparison
 *   offers aldi    ALDI Nord offers, this week + next week's preview
 *   offers penny   Penny offers for the store in config/stores.properties
 *   offers all     both offer imports (one failing doesn't stop the other)
 *   match aldi     link ALDI store_product rows to inventory_item; unmatched -> data/stores/unmatched_aldi.tsv
 *
 * Exit code: 0 = all OK, 1 = something failed or was only partial, 2 = bad arguments.
 */
public class StoreImportRunner {

    private static final int MAX_LISTED_CHANGES = 40;
    private static final Path DATA_DIR = Path.of("data", "stores");

    public static void main(String[] args) {
        String job = String.join(" ", args).trim().toLowerCase();
        boolean fixedJob = List.of("range aldi", "offers aldi", "offers penny", "offers all", "match aldi", "promote", "discounts").contains(job);
        boolean aggregatorJob = job.startsWith("offers aggregator ");
        if (!fixedJob && !aggregatorJob) {
            printUsage();
            System.exit(2);
        }

        StoreConfig config = new StoreConfig();
        HttpFetcher http = new HttpFetcher(config.requestDelay());
        StoreRepository repository = new StoreRepository();
        repository.ensureSchema();
        AggregatorClient aggregator = new AggregatorClient(http);
        StoreImportService service = new StoreImportService(
                repository, new AldiNordClient(http), new PennyClient(http), aggregator, config);

        List<Runnable> jobs = new ArrayList<>();
        List<ImportSummary> results = new ArrayList<>();
        boolean failed = false;
        if (aggregatorJob) {
            String which = job.substring("offers aggregator ".length()).trim();
            List<String> stores = which.equals("all")
                    ? new ArrayList<>(AggregatorClient.STORES.keySet())
                    : List.of(which);
            for (String s : stores) {
                if (!aggregator.knows(s)) {
                    System.err.println("unknown aggregator store: " + s + " (known: " + AggregatorClient.STORES.keySet() + ")");
                    System.exit(2);
                }
                jobs.add(() -> results.add(service.importAggregatorOffers(s)));
            }
        } else switch (job) {
            case "range aldi" -> jobs.add(() -> results.add(service.importAldiRange()));
            case "offers aldi" -> jobs.add(() -> results.add(service.importAldiOffers()));
            case "offers penny" -> jobs.add(() -> results.add(service.importPennyOffers()));
            case "match aldi" -> jobs.add(() -> {
                StoreMatchService matcher = new StoreMatchService(repository, loadOverrides());
                results.add(matcher.matchStore(StoreImportService.ALDI, DATA_DIR.resolve("unmatched_aldi.tsv")));
            });
            case "promote" -> jobs.add(() -> results.add(new StorePromotionService(repository).promote()));
            case "discounts" -> jobs.add(() -> results.add(
                    new StoreDiscountService(repository, loadOverrides())
                            .promoteOffers(null, DATA_DIR.resolve("unmatched_offers.tsv"))));
            default -> {                                   // "offers all"
                jobs.add(() -> results.add(service.importAldiOffers()));
                jobs.add(() -> results.add(service.importPennyOffers()));
            }
        }
        for (Runnable j : jobs) {
            try {
                j.run();
            } catch (RuntimeException e) {
                failed = true;
                System.err.println("IMPORT FAILED: " + e.getMessage());
            }
        }

        results.forEach(StoreImportRunner::print);
        boolean allOk = !failed && results.stream().allMatch(r -> "OK".equals(r.status()));
        System.exit(allOk ? 0 : 1);
    }

    private static void print(ImportSummary r) {
        System.out.printf("%n== %s %s: %s, %d items%s%n", r.store(), r.kind(), r.status(), r.items(),
                r.pages() > 0 ? ", " + r.pages() + " pages" : "");
        if (!r.notes().isEmpty()) {
            System.out.println("   " + r.notes());
        }
        if (r.changes().isEmpty()) {
            return;
        }
        Map<CatalogChange.Type, Integer> counts = new EnumMap<>(CatalogChange.Type.class);
        r.changes().forEach(c -> counts.merge(c.type(), 1, Integer::sum));
        System.out.println("   changes: " + counts);
        r.changes().stream().limit(MAX_LISTED_CHANGES).forEach(c -> System.out.printf(
                "   %-8s %-50s %s -> %s%n", c.type(), c.name(), c.oldPrice(), c.newPrice()));
        if (r.changes().size() > MAX_LISTED_CHANGES) {
            System.out.println("   ... all changes are in table store_catalog_change");
        }
    }

    /** config/store_overrides.json: {"exact": {"<normalized store name>": <inventory_item_id>}}. Optional. */
    private static Map<String, Integer> loadOverrides() {
        Map<String, Integer> map = new HashMap<>();
        Path file = Path.of("config", "store_overrides.json");
        if (!Files.exists(file)) {
            return map;
        }
        try {
            JsonNode exact = new ObjectMapper().readTree(file.toFile()).path("exact");
            exact.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue().asInt()));
        } catch (Exception e) {
            System.err.println("could not read store_overrides.json: " + e.getMessage());
        }
        return map;
    }

    private static void printUsage() {
        System.err.println("""
                usage: StoreImportRunner <job>
                  range aldi     ALDI Nord full range (first run = initial load, then monthly comparison)
                  offers aldi    ALDI Nord weekly offers (+ next week's preview)
                  offers penny   Penny weekly offers (store from config/stores.properties)
                  offers all     both offer imports
                  offers aggregator <store>   aktionspreis.de offers (SECONDARY); store: netto|edeka|rewe|lidl|famila|aldi, or 'all'
                  match aldi     link ALDI store products to catalog items; unmatched -> data/stores/unmatched_aldi.tsv
                  promote        turn matched+complete store_products into verified store_item rows
                  discounts      turn matched flyer offers into store_item + store_discount; unmatched -> data/stores/unmatched_offers.tsv""");
    }
}
