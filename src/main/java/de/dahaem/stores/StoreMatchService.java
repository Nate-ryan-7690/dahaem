package de.dahaem.stores;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Links store_product rows to inventory_item catalog rows by name (no SQL, no web).
 *
 * A store sells specific branded items ("MILSANI Streichfein ungesalzen"); the
 * catalog holds generic ingredients ("butter"), so a perfect match rate is not
 * expected. We match conservatively and leave the rest for review, rather than
 * inventing catalog rows: bulk-adding 1700 store items (much of it non-food:
 * cosmetics, detergent, wine, gift cards) would wreck the curated catalog.
 *
 * Match order per store product (after brand-stripping + normalizing):
 *   1. store_overrides.json exact map  (normalized store name -> catalog id)
 *   2. exact: normalized store name equals a catalog item's normalized de/en name
 *   3. contains: a catalog item's whole normalized name appears as a word run in
 *      the store name ("Bio Rapsöl" -> catalog "rapsöl"); the LONGEST such catalog
 *      name wins, and ties (two catalog items equally applicable) are left unmatched.
 * Unmatched products are written to a review file; nothing is guessed into the DB.
 */
public class StoreMatchService {

    private final StoreRepository repository;
    private final Map<String, Integer> overrides;

    public StoreMatchService(StoreRepository repository, Map<String, Integer> overrides) {
        this.repository = repository;
        this.overrides = overrides;
    }

    public ImportSummary matchStore(String storeName, Path reviewFile) {
        int storeId = repository.findOrCreateStore(storeName);
        List<CatalogItem> catalog = repository.loadCatalog();
        List<StoreProductRef> products = repository.loadStoreProductsForMatch(storeId);

        Map<Integer, Integer> matches = new HashMap<>();       // storeProductId -> inventoryItemId
        List<StoreProductRef> unmatched = new ArrayList<>();
        for (StoreProductRef p : products) {
            Integer itemId = match(p.name(), catalog);
            if (itemId != null) {
                matches.put(p.id(), itemId);
            } else {
                unmatched.add(p);
            }
        }
        repository.saveMatches(matches);

        writeReview(reviewFile, storeName, unmatched);
        String notes = String.format("%d matched, %d unmatched -> %s", matches.size(), unmatched.size(), reviewFile);
        return new ImportSummary(storeName, "MATCH", "OK", 0, matches.size(), List.of(), notes);
    }

    Integer match(String storeName, List<CatalogItem> catalog) {
        String key = Normalizer.key(storeName);
        if (key.isEmpty()) {
            return null;
        }
        if (overrides.containsKey(key)) {
            return overrides.get(key);
        }
        String padded = " " + key + " ";
        Integer containsHit = null;
        int containsLen = 0;
        boolean containsTie = false;
        for (CatalogItem c : catalog) {
            for (String norm : new String[]{c.normDeutsch(), c.normEnglish()}) {
                if (norm.isEmpty()) {
                    continue;
                }
                if (norm.equals(key)) {
                    return c.id();                              // exact wins immediately
                }
                if (padded.contains(" " + norm + " ")) {        // whole-word run
                    if (norm.length() > containsLen) {
                        containsLen = norm.length();
                        containsHit = c.id();
                        containsTie = false;
                    } else if (norm.length() == containsLen && !java.util.Objects.equals(containsHit, c.id())) {
                        containsTie = true;
                    }
                }
            }
        }
        return containsTie ? null : containsHit;                // ambiguous -> leave for review
    }

    private void writeReview(Path file, String store, List<StoreProductRef> unmatched) {
        StringBuilder sb = new StringBuilder("# unmatched " + store + " products: " + unmatched.size() + "\n");
        sb.append("# category\tname (normalized)\n");
        unmatched.stream()
                .sorted((a, b) -> a.category() == null ? -1 : a.category().compareTo(b.category() == null ? "" : b.category()))
                .forEach(p -> sb.append(p.category()).append('\t').append(p.name())
                        .append("\t(").append(Normalizer.key(p.name())).append(")\n"));
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write review file " + file, e);
        }
    }
}
