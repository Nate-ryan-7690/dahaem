package de.dahaem.receipts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dahaem.stores.CatalogItem;
import de.dahaem.stores.StoreMatchService;
import de.dahaem.stores.StoreRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Links one receipt line to an inventory item, or leaves it unmatched. No SQL. Order:
 * <ol>
 *   <li>config/receipt_overrides.json {@code exact}: receipt key (see {@link ReceiptNames#key}) -> item id,
 *       tried for the full name, then for the name without its brand prefix. 0 = excluded on purpose.</li>
 *   <li>{@code review}: keys waiting for Nate's decision. They stay unmatched; the rules below are not
 *       asked, so a known wrong guess ("Krom FB Kirsche" -> cherries) can't slip in.</li>
 *   <li>the normal name rules of {@link StoreMatchService}, on the readable name without the brand.
 *       config/store_overrides.json is NOT used: its keys are offer names, and some differ in meaning on
 *       a receipt ("fruchtzwerge" -> plain yogurt there).</li>
 * </ol>
 */
public final class ReceiptMatcher {

    /** How a line got its item; the values are what purchase_receipt_line_match_source stores. */
    public static final String OVERRIDE = "override";
    public static final String RULE = "rule";

    /** itemId null = no item; excluded = an override of 0 (left out on purpose). */
    public record Match(Integer itemId, String source, String key, boolean excluded) {
        public boolean matched() {
            return itemId != null;
        }
    }

    /** Parsed config/receipt_overrides.json. */
    public record Overrides(Map<String, Integer> exact, Set<String> review) {
        public Overrides {
            exact = Map.copyOf(exact);
            review = Set.copyOf(review);
        }

        public static Overrides none() {
            return new Overrides(Map.of(), Set.of());
        }

        /** Reads {"exact": {"key": id}, "review": ["key", ...]}; a malformed file fails loudly. */
        public static Overrides load(Path file) {
            if (!Files.exists(file)) {
                throw new IllegalArgumentException("No receipt overrides file at " + file);
            }
            try {
                JsonNode root = new ObjectMapper().readTree(file.toFile());
                Map<String, Integer> exact = new HashMap<>();
                root.path("exact").fields().forEachRemaining(e -> {
                    if (!e.getValue().isInt()) {
                        throw new IllegalArgumentException("not an item id: " + e.getKey() + " -> " + e.getValue());
                    }
                    exact.put(e.getKey(), e.getValue().asInt());
                });
                Set<String> review = new HashSet<>();
                root.path("review").forEach(n -> review.add(n.asText()));
                for (String k : exact.keySet()) {
                    if (review.contains(k)) {
                        throw new IllegalArgumentException("key both mapped and in review: " + k);
                    }
                }
                return new Overrides(exact, review);
            } catch (Exception e) {
                throw new IllegalArgumentException("Malformed " + file + " (fix it): " + e.getMessage(), e);
            }
        }
    }

    private final Overrides overrides;
    private final List<CatalogItem> catalog;
    private final StoreMatchService rules = new StoreMatchService(new StoreRepository(), Map.of());

    public ReceiptMatcher(Overrides overrides, List<CatalogItem> catalog) {
        this.overrides = overrides;
        this.catalog = List.copyOf(catalog);
    }

    public Match match(String rawName) {
        String key = ReceiptNames.key(rawName);
        String bare = ReceiptNames.withoutBrand(rawName);
        String bareKey = ReceiptNames.key(bare);
        for (String k : List.of(key, bareKey)) {
            Integer id = overrides.exact().get(k);
            if (id != null) {
                return id <= 0 ? new Match(null, null, key, true) : new Match(id, OVERRIDE, key, false);
            }
        }
        if (key.isEmpty() || overrides.review().contains(key) || overrides.review().contains(bareKey)) {
            return new Match(null, null, key, false);
        }
        Integer id = rules.match(ReceiptNames.readable(bare), catalog);
        return id == null || id <= 0 ? new Match(null, null, key, false) : new Match(id, RULE, key, false);
    }
}
