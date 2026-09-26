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
import java.util.Objects;
import java.util.Set;

/**
 * Links store_product/store_offer rows to inventory_item catalog rows by name (no SQL, no web).
 *
 * A store sells specific branded items ("MILSANI Streichfein ungesalzen"); the
 * catalog holds generic ingredients ("butter"), so a perfect match rate is not
 * expected. We match conservatively and leave the rest for review, rather than
 * inventing catalog rows: bulk-adding 1700 store items (much of it non-food:
 * cosmetics, detergent, wine, gift cards) would wreck the curated catalog.
 *
 * Match order per store product/offer (after brand-stripping + normalizing):
 *   1. store_overrides.json exact map  (normalized store name -> catalog id)
 *   2. exact: normalized store name equals a catalog item's normalized de/en name
 *   3. German-aware fuzzy match, word by word:
 *      - a small DESCRIPTOR stoplist (colors, sizes, "frisch"/"Aktion", and a
 *        handful of fused cut/part suffixes like "-herzen"/"-scheiben") is
 *        dropped before comparing;
 *      - a catalog term matches an offer word EXACTLY when the word equals the
 *        term, or equals the term plus a common German plural/inflection ending
 *        (süßkartoffeln <-> süßkartoffel);
 *      - a catalog term matches as a COMPOUND HEAD when the offer word *ends
 *        with* the term (optionally inflected) and has a non-empty prefix
 *        (gemüsezwiebeln ends with zwiebeln -> zwiebel). Terms under 4 chars are
 *        never used for compound matching, and the match must land on the word's
 *        end, never the middle or start, so "bell" never fires inside
 *        "kabellose". A multi-word catalog term (e.g. an English phrase) still
 *        falls back to the old whole-word-run "contains" check.
 *      - the longest matching catalog term wins; ties of equal length prefer an
 *        EXACT hit over a COMPOUND one; a remaining tie (same length, same kind,
 *        different catalog items) is left unmatched rather than guessed.
 * Unmatched products are written to a review file; nothing is guessed into the DB.
 * See docs/store-matching.md for the grow-from-review workflow (review file ->
 * config/store_overrides.json).
 */
public class StoreMatchService {

    /** Colors, sizes, generic marketing words, and fused cut/part suffixes; dropped before matching. */
    private static final Set<String> DESCRIPTORS = Set.of(
            // colors
            "rot", "gruen", "gelb", "blau", "weiss", "schwarz", "orange", "rosa", "lila", "bunt", "braun",
            // sizes
            "xxl", "xl", "xs", "mini", "maxi", "gross", "klein", "riesen",
            // generic marketing words
            "frisch", "frische", "frischer", "aktion", "angebot", "neu",
            // fused cut/part suffixes ("Artischockenherzen", "Tomatenscheiben")
            "herzen", "herz", "scheiben", "scheibe", "wuerfel", "streifen", "viertel",
            "haelften", "haelfte", "spalten", "stuecke", "stueck", "halbiert", "geschnitten");

    /** Common German plural/inflection endings tried when folding a word onto a catalog term. */
    private static final List<String> INFLECTION_SUFFIXES = List.of("en", "er", "n", "e", "s");

    /** Catalog terms shorter than this are never used for compound-head matching (too many false hits). */
    private static final int MIN_COMPOUND_TERM_LENGTH = 4;

    private enum Tier { NONE, COMPOUND, EXACT }

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
        for (CatalogItem c : catalog) {
            if (key.equals(c.normDeutsch()) || key.equals(c.normEnglish())) {
                return c.id();                                  // exact wins immediately
            }
        }

        List<String> words = new ArrayList<>();
        for (String w : key.split(" ")) {
            if (!w.isEmpty() && !DESCRIPTORS.contains(w)) {
                words.add(w);
            }
        }
        if (words.isEmpty()) {
            return null;                                          // descriptor-only leftover -> not a match
        }
        String padded = " " + key + " ";

        Integer best = null;
        int bestLen = -1;
        boolean bestExact = false;
        boolean tie = false;

        for (CatalogItem c : catalog) {
            for (String norm : new String[]{c.normDeutsch(), c.normEnglish()}) {
                if (norm.isEmpty()) {
                    continue;
                }
                Tier tier = tierFor(norm, words, padded);
                if (tier == Tier.NONE) {
                    continue;
                }
                boolean exact = tier == Tier.EXACT;
                int len = norm.length();
                boolean better = len > bestLen || (len == bestLen && exact && !bestExact);
                if (best == null || better) {
                    best = c.id();
                    bestLen = len;
                    bestExact = exact;
                    tie = false;
                } else if (len == bestLen && exact == bestExact && !Objects.equals(best, c.id())) {
                    tie = true;
                }
            }
        }
        return tie ? null : best;
    }

    /** Best match tier a single catalog term reaches against the (descriptor-stripped) offer words. */
    private static Tier tierFor(String norm, List<String> words, String padded) {
        if (norm.indexOf(' ') >= 0) {                            // multi-word catalog term: whole-word-run only
            return padded.contains(" " + norm + " ") ? Tier.COMPOUND : Tier.NONE;
        }
        Tier best = Tier.NONE;
        for (String word : words) {
            Tier t = tierForWord(norm, word);
            if (t == Tier.EXACT) {
                return Tier.EXACT;
            }
            if (t == Tier.COMPOUND) {
                best = Tier.COMPOUND;
            }
        }
        return best;
    }

    private static Tier tierForWord(String norm, String word) {
        if (word.equals(norm)) {
            return Tier.EXACT;
        }
        for (String suffix : INFLECTION_SUFFIXES) {
            if (word.equals(norm + suffix)) {
                return Tier.EXACT;                                // plural/inflection of the exact word
            }
        }
        if (norm.length() >= MIN_COMPOUND_TERM_LENGTH) {
            if (word.endsWith(norm) && word.length() > norm.length()) {
                return Tier.COMPOUND;                              // compound head, e.g. gemuesezwiebel(n)
            }
            for (String suffix : INFLECTION_SUFFIXES) {
                String inflected = norm + suffix;
                if (word.endsWith(inflected) && word.length() > inflected.length()) {
                    return Tier.COMPOUND;                           // compound head, inflected: gemuesezwiebeln
                }
            }
        }
        return matchAfterStrippingFusedDescriptor(norm, word);
    }

    /** "Artischockenherzen" -> strip the fused descriptor suffix "herzen" -> "artischocken" folds onto "artischocke". */
    private static Tier matchAfterStrippingFusedDescriptor(String norm, String word) {
        if (norm.length() < MIN_COMPOUND_TERM_LENGTH) {
            return Tier.NONE;
        }
        for (String descriptor : DESCRIPTORS) {
            if (!word.endsWith(descriptor) || word.length() <= descriptor.length()) {
                continue;
            }
            String remainder = word.substring(0, word.length() - descriptor.length());
            if (remainder.equals(norm)) {
                return Tier.EXACT;
            }
            for (String suffix : INFLECTION_SUFFIXES) {
                if (remainder.equals(norm + suffix)) {
                    return Tier.EXACT;
                }
            }
        }
        return Tier.NONE;
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
