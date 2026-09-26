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
import java.util.regex.Pattern;

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
 *   1. store_overrides.json exact map  (normalized store name -> catalog id) — always
 *      wins, regardless of every rule below (a human override overrules the guard too).
 *   2. non-food guard: reject outright when the product's name or category carries a
 *      cleaning/hygiene/household marker (see NON_FOOD_MARKERS) — a "WC-Reiniger Zitrone"
 *      must never resolve to the catalog's lemon just because the word appears.
 *   3. exact: normalized store name equals a catalog item's normalized de/en name
 *   4. German-aware fuzzy match, HEAD NOUN ONLY: a size/number/descriptor stoplist is
 *      dropped from the (space-split) name, and only the last remaining word — the head
 *      noun — is compared against catalog terms, never any earlier word. This is what
 *      keeps "Kartoffel-Ringe" (head "ringe") and "Kartoffel Püree" (head "püree") from
 *      matching the catalog's "kartoffel" just because that word appears earlier in the
 *      name; "Kartoffeln" (head "kartoffeln") still matches, since the head noun itself
 *      folds onto the catalog term. Dropped before the head is picked:
 *      - a small DESCRIPTOR stoplist (colors, sizes, "frisch"/"Aktion", and a handful of
 *        fused cut/part suffixes like "-herzen"/"-scheiben");
 *      - size/quantity tokens ("100", "750ml", "3x32g", a bare unit like "g"/"stück").
 *      A word NOT on either stoplist (e.g. "Plus" in "Zitrone Plus") is kept and, if it
 *      ends up as the head, blocks the match — it is not silently treated as a descriptor.
 *      The head noun then matches a catalog term the same way the old word-by-word scan
 *      did:
 *      - EXACTLY when the head equals the term, or equals the term plus a common German
 *        plural/inflection ending (süßkartoffeln <-> süßkartoffel);
 *      - as a COMPOUND HEAD when the head word *ends with* the term (optionally
 *        inflected) and has a non-empty prefix (gemüsezwiebeln ends with zwiebeln ->
 *        zwiebel). Terms under 4 chars are never used for compound matching, and the
 *        match must land on the word's end, never the middle or start, so "bell" never
 *        fires inside "kabellose". A multi-word catalog term (e.g. an English phrase)
 *        still falls back to the old whole-name "contains" check, independent of the
 *        head-noun restriction.
 *      - the longest matching catalog term wins; ties of equal length prefer an
 *        EXACT hit over a COMPOUND one; a remaining tie (same length, same kind,
 *        different catalog items) is left unmatched rather than guessed.
 * Unmatched or rejected products are written to a review file; nothing is guessed into
 * the DB — an uncertain or non-food-flagged match is left for a human, per the review
 * workflow below, rather than linked speculatively.
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

    /** Bare unit/multiplier words that can trail a number as their own token ("100 g", "6 x"). */
    private static final Set<String> BARE_SIZE_UNITS =
            Set.of("g", "kg", "mg", "ml", "cl", "l", "stk", "stueck", "st", "x");

    /** A token that is purely a quantity: "100", "750ml", "3x32g" — dropped before picking the head noun. */
    private static final Pattern SIZE_TOKEN =
            Pattern.compile("^[0-9]+(x[0-9]+)?(g|kg|mg|ml|cl|l|stk|stueck|st)?$");

    /**
     * Non-food markers: when the product's normalized name OR category contains one of
     * these, no catalog (food/recipe) match is made, however well a word would otherwise
     * line up — this is what stops "WC-Reiniger Zitrone" from resolving to the catalog's
     * lemon. Kept deliberately small and grouped by kind; each entry was chosen from real
     * production mismatches or their obvious siblings, not a general non-food dictionary.
     */
    private static final Set<String> NON_FOOD_MARKERS = Set.of(
            // cleaning / laundry
            "reiniger", "wc", "wasch", "putz", "spuel", "spul", "weichspueler", "klar",
            // personal care / cosmetics
            "kosmetik", "shampoo", "seife", "dusch", "duft",
            // household / misc non-food
            "kerze", "teelicht", "batterie", "papier", "windel");

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
            Integer itemId = match(p.name(), p.category(), catalog);
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

    /** Convenience overload for callers with no category available (guard is skipped). */
    Integer match(String storeName, List<CatalogItem> catalog) {
        return match(storeName, null, catalog);
    }

    Integer match(String storeName, String category, List<CatalogItem> catalog) {
        String key = Normalizer.key(storeName);
        if (key.isEmpty()) {
            return null;
        }
        if (overrides.containsKey(key)) {
            return overrides.get(key);                            // override wins regardless of the guard/rules below
        }
        if (isNonFood(key, category)) {
            return null;                                          // cleaning/hygiene/household marker -> never a food match
        }
        for (CatalogItem c : catalog) {
            if (key.equals(c.normDeutsch()) || key.equals(c.normEnglish())) {
                return c.id();                                  // exact wins immediately
            }
        }

        List<String> words = new ArrayList<>();
        for (String w : key.split(" ")) {
            if (!w.isEmpty() && !isNoiseWord(w)) {
                words.add(w);
            }
        }
        if (words.isEmpty()) {
            return null;                                          // descriptor/size-only leftover -> not a match
        }
        String head = words.get(words.size() - 1);                // head noun: last significant token
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
                Tier tier = tierFor(norm, head, padded);
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

    /** True when the product's name or category carries a non-food marker (see NON_FOOD_MARKERS). */
    private static boolean isNonFood(String nameKey, String category) {
        String catKey = category == null ? "" : Normalizer.key(category);
        for (String marker : NON_FOOD_MARKERS) {
            if (nameKey.contains(marker) || catKey.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    /** Descriptor, or a size/quantity token ("100", "750ml", "3x32g", a bare unit) — never the head noun. */
    private static boolean isNoiseWord(String word) {
        return DESCRIPTORS.contains(word) || BARE_SIZE_UNITS.contains(word) || SIZE_TOKEN.matcher(word).matches();
    }

    /** Match tier a single catalog term reaches against the head noun (last significant word of the name). */
    private static Tier tierFor(String norm, String head, String padded) {
        if (norm.indexOf(' ') >= 0) {                            // multi-word catalog term: whole-word-run only
            return padded.contains(" " + norm + " ") ? Tier.COMPOUND : Tier.NONE;
        }
        return tierForWord(norm, head);
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
