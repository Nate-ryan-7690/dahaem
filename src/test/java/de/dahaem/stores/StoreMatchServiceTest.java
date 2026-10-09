package de.dahaem.stores;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class StoreMatchServiceTest {

    private static CatalogItem item(int id, String english, String deutsch) {
        return new CatalogItem(id, english, deutsch, Normalizer.key(english), Normalizer.key(deutsch));
    }

    @Test
    void overrideWins() {
        List<CatalogItem> catalog = List.of(item(1, "butter", "butter"));
        // The override maps a normalized store name to a *different* catalog id than the
        // exact/contains rules would pick, so we can tell the override actually short-circuited.
        Map<String, Integer> overrides = Map.of(Normalizer.key("MILSANI Streichfein"), 42);
        StoreMatchService service = new StoreMatchService(null, overrides);

        assertEquals(42, service.match("MILSANI Streichfein", catalog));
    }

    @Test
    void exactMatchOnNormalizedName() {
        List<CatalogItem> catalog = List.of(item(1, "butter", "butter"), item(2, "milk", "milch"));
        StoreMatchService service = new StoreMatchService(null, Map.of());

        assertEquals(1, service.match("Butter", catalog));
    }

    @Test
    void longestWholeWordContainsWins() {
        List<CatalogItem> catalog = List.of(item(1, "oil", "oel"), item(2, "rapeseed oil", "rapsoel"));
        StoreMatchService service = new StoreMatchService(null, Map.of());

        // "Bio Rapsöl" normalizes to "bio rapsoel"; both "oel" and "rapsoel" appear as whole
        // words, but "rapsoel" is longer and should win.
        assertEquals(2, service.match("Bio Rapsöl", catalog));
    }

    @Test
    void tieBetweenEquallyLongCandidatesLeavesUnmatched() {
        // Two catalog names of the same length that both match as whole words in the store name.
        List<CatalogItem> tiedCatalog = List.of(
                item(10, "aaa", "abc"),
                item(11, "bbb", "xyz"));
        StoreMatchService service = new StoreMatchService(null, Map.of());

        assertNull(service.match("abc xyz product", tiedCatalog));
    }

    @Test
    void noMatchReturnsNull() {
        List<CatalogItem> catalog = List.of(item(1, "butter", "butter"));
        StoreMatchService service = new StoreMatchService(null, Map.of());

        assertNull(service.match("Kaffee komplett anders", catalog));
    }

    @Test
    void emptyNormalizedNameReturnsNull() {
        List<CatalogItem> catalog = List.of(item(1, "butter", "butter"));
        StoreMatchService service = new StoreMatchService(null, Map.of());

        assertNull(service.match("", catalog));
    }

    // ---------------------------------------------------------------------------------
    // German-aware matching: compounds, plurals and descriptors, against a small catalog
    // modeled on real flyer names that the old exact/contains-only matcher dropped.
    // ---------------------------------------------------------------------------------

    private static final int SUESSKARTOFFEL = 1;
    private static final int ZWIEBEL = 2;
    private static final int CHAMPIGNON = 3;
    private static final int PFLAUME = 4;
    private static final int BIRNE = 5;
    private static final int ARTISCHOCKE = 6;
    private static final int OLIVE = 7;
    private static final int BELL_PEPPER = 8;   // only here to prove "bell" never fires on unrelated words

    private static final List<CatalogItem> GERMAN_CATALOG = List.of(
            item(SUESSKARTOFFEL, "sweet potato", "Süßkartoffel"),
            item(ZWIEBEL, "onion", "Zwiebel"),
            item(CHAMPIGNON, "champignon", "Champignon"),
            item(PFLAUME, "plum", "Pflaume"),
            item(BIRNE, "pear", "Birne"),
            item(ARTISCHOCKE, "artichoke", "Artischocke"),
            item(OLIVE, "olive", "Olive"),
            item(BELL_PEPPER, "bell", "Glocke"));

    private static Integer matchGerman(String storeName) {
        return new StoreMatchService(null, Map.of()).match(storeName, GERMAN_CATALOG);
    }

    @Test
    void foldsPluralOntoSingularCatalogTerm() {
        assertEquals(SUESSKARTOFFEL, matchGerman("Süßkartoffeln"));
        assertEquals(PFLAUME, matchGerman("Pflaumen"));
    }

    @Test
    void matchesCompoundHeadZwiebel() {
        assertEquals(ZWIEBEL, matchGerman("Gemüsezwiebeln"));
    }

    @Test
    void matchesCompoundHeadBirne() {
        assertEquals(BIRNE, matchGerman("Tafelbirnen"));
    }

    @Test
    void dropsDescriptorThenMatchesCompoundHeadChampignon() {
        assertEquals(CHAMPIGNON, matchGerman("Kulturchampignons braun"));
    }

    @Test
    void dropsSizeDescriptorThenMatchesCompoundHeadOlive() {
        assertEquals(OLIVE, matchGerman("Riesenoliven XXL"));
    }

    @Test
    void stripsFusedPartSuffixThenMatchesArtischocke() {
        assertEquals(ARTISCHOCKE, matchGerman("Artischockenherzen"));
    }

    @Test
    void doesNotMatchSubstringInTheMiddleOfAWord() {
        // "kabellose" contains "bell" mid-word; a real substring scan would wrongly hit the
        // catalog's "bell" (bell pepper) term. The compound rule only looks at word endings.
        assertNull(matchGerman("Kabellose Türklingel"));
    }

    @Test
    void unrelatedNonFoodNamesStayUnmatched() {
        assertNull(matchGerman("Klapptrittleiter"));
        assertNull(matchGerman("LED-Strahler"));
        assertNull(matchGerman("Servietten"));
        assertNull(matchGerman("Malerkrepp"));
        assertNull(matchGerman("Pyjama"));
    }

    // ---------------------------------------------------------------------------------
    // Precision rules: head-noun matching + non-food guard, modeled on real bad matches
    // from production data (see the PR description for the full list).
    // ---------------------------------------------------------------------------------

    private static final int KARTOFFEL = 30;
    private static final int ZITRONE = 31;
    private static final int MAYONNAISE = 32;
    private static final int AIOLI = 33;

    private static final List<CatalogItem> PRECISION_CATALOG = List.of(
            item(KARTOFFEL, "potato", "Kartoffel"),
            item(ZITRONE, "lemon", "Zitrone"),
            item(MAYONNAISE, "mayonnaise", "Mayonnaise"),
            item(AIOLI, "aioli", "Aioli"));

    private static Integer matchPrecision(String name, String category) {
        return new StoreMatchService(null, Map.of()).match(name, category, PRECISION_CATALOG);
    }

    // --- MUST REJECT: catalog word appears in the name, but is not the head noun ---

    @Test
    void rejectsKartoffelRingeSinceHeadIsRinge() {
        // "SUN SNACKS Kartoffel-Ringe / Dolphy's" (chips-salzgebaeck) -> head "ringe", not "kartoffel"
        assertNull(matchPrecision("Kartoffel-Ringe", "chips-salzgebaeck"));
    }

    @Test
    void rejectsKartoffelPureeSinceHeadIsPueree() {
        assertNull(matchPrecision("Kartoffel Püree", "beilagen"));
    }

    @Test
    void rejectsKartoffelKloesseSinceHeadIsKloesse() {
        assertNull(matchPrecision("Semmel-Knödel oder Kartoffel-Klöße", "beilagen"));
    }

    @Test
    void rejectsKartoffelchipsCompoundEndingInDifferentNoun() {
        // Single fused word ending in "chips", not "kartoffel" -> already rejected by the
        // existing suffix rule; asserted here so the head-noun change doesn't regress it.
        assertNull(matchPrecision("Kartoffelchips", "chips-salzgebaeck"));
    }

    @Test
    void rejectsZitronePlusSinceTrailingModifierIsNotTheCatalogTerm() {
        // Head is "plus" (BIO, fett-oele-essig, an actual juice) -> "plus" is not stripped as
        // a descriptor, so it blocks the match rather than falling back to "zitrone".
        assertNull(matchPrecision("Zitrone Plus", "fett-oele-essig"));
    }

    // --- MUST REJECT: non-food guard (category or name carries a cleaning/hygiene marker) ---

    @Test
    void rejectsWcReinigerZitroneByNameMarker() {
        // "WC-Reiniger Zitrone" (POWER FORCE) -> "zitrone" is even the head noun here, so only
        // the non-food guard stops this from resolving to the catalog's lemon.
        assertNull(matchPrecision("WC-Reiniger Zitrone", "haushaltsreiniger"));
    }

    @Test
    void rejectsByCategoryMarkerEvenWhenNameLooksLikeFood() {
        assertNull(matchPrecision("Zitrone", "wc reiniger"));
    }

    // --- MUST KEEP: real catalog terms still match ---

    @Test
    void keepsPlainKartoffelnMatch() {
        assertEquals(KARTOFFEL, matchPrecision("Kartoffeln", "gemuese"));
        assertEquals(KARTOFFEL, matchPrecision("Kartoffel", "gemuese"));
    }

    @Test
    void keepsPlainMayonnaiseMatch() {
        assertEquals(MAYONNAISE, matchPrecision("Mayonnaise", "sossen-dressings"));
    }

    @Test
    void keepsMojoSauceAioliByHeadNoun() {
        assertEquals(AIOLI, matchPrecision("Mojo-Sauce/Aioli", "sossen-dressings"));
    }

    @Test
    void keepsPlainZitroneAndZitronenMatch() {
        assertEquals(ZITRONE, matchPrecision("Zitrone", "obst"));
        assertEquals(ZITRONE, matchPrecision("Zitronen", "obst"));
    }

    // ---- matcher gaps: trailing qualifier, hyphenated compound, soft hyphen, umlaut plural ----

    @Test
    void trailingQualifierAfterTheProductWordIsIgnored() {
        List<CatalogItem> catalog = List.of(item(1, "gouda", "gouda"), item(2, "prosciutto", "prosciutto"),
                item(3, "coffee", "kaffee"), item(4, "mozzarella", "mozzarella"));
        StoreMatchService service = new StoreMatchService(null, Map.of());

        assertEquals(1, service.match("LINDENHOF Gouda jung", catalog));
        assertEquals(2, service.match("CUCINA Prosciutto Crudo Dolce", catalog));
        assertNull(service.match("JACOBS Kaffee-Sticks", catalog), "coffee sticks are their own items, not Kaffee");
        assertEquals(4, service.match("CUCINA Mozzarella-Stange", catalog));
    }

    @Test
    void unlistedTrailingWordStillBlocksTheMatch() {
        List<CatalogItem> catalog = List.of(item(1, "prosciutto", "prosciutto"), item(2, "potato", "kartoffel"));
        StoreMatchService service = new StoreMatchService(null, Map.of());

        assertNull(service.match("CUCINA Prosciutto Cotto XXL", catalog));   // cooked ham is not prosciutto
        assertNull(service.match("Kartoffel Püree", catalog));
    }

    @Test
    void hyphenatedCompoundMatchesTheJoinedCatalogWord() {
        List<CatalogItem> catalog = List.of(item(1, "chicken breast fillet", "hähnchenbrustfilet"),
                item(2, "risotto rice", "risottoreis"));
        StoreMatchService service = new StoreMatchService(null, Map.of());

        assertEquals(1, service.match("Frische Hähnchen-Brustfilets", catalog));
        assertEquals(2, service.match("CUCINA Risotto-Reis", catalog));
    }

    @Test
    void hyphenJoinNeverReachesAShorterCatalogWordThroughACompoundHead() {
        List<CatalogItem> catalog = List.of(item(1, "potato", "kartoffel"), item(2, "butter", "butter"));
        StoreMatchService service = new StoreMatchService(null, Map.of());

        // Joined: "kartoffelpueree" / "kraeutercreme" only count when they equal a catalog word.
        assertNull(service.match("Kartoffel-Püree", catalog));
        assertNull(service.match("Kartoffel-Taschen", catalog));
        assertNull(service.match("Butter-Gebäck", catalog));
    }

    @Test
    void softHyphenInsideAWordDoesNotSplitIt() {
        List<CatalogItem> catalog = List.of(item(1, "walnut kernels", "walnusskern"));
        StoreMatchService service = new StoreMatchService(null, Map.of());

        assertEquals(1, service.match("Bio Walnuss\u00adkerne", catalog));
        assertEquals("walnusskerne", Normalizer.key("Walnuss\u00adkerne"));
    }

    @Test
    void pluralUmlautFoldsBackOntoTheSingularCatalogTerm() {
        List<CatalogItem> catalog = List.of(item(1, "peanut", "erdnuss"), item(2, "apple", "apfel"));
        StoreMatchService service = new StoreMatchService(null, Map.of());

        assertEquals(1, service.match("Ültje Erdnüsse", catalog));
        assertEquals(2, service.match("Deutsche rote Äpfel", catalog));
        assertEquals(2, service.match("Tafeläpfel", catalog));
        assertNull(service.match("Ärzte", catalog));
    }

    @Test
    void anOverrideOfZeroMeansExcludedAndSkipsTheRules() {
        List<CatalogItem> catalog = List.of(item(1, "butter", "butter"));
        StoreMatchService service = new StoreMatchService(null, Map.of(Normalizer.key("Butter Gebäck"), 0));

        assertEquals(0, service.match("Butter Gebäck", catalog));
        assertEquals(true, StoreMatchService.isExcluded(service.match("Butter Gebäck", catalog)));
        assertEquals(false, StoreMatchService.isExcluded(service.match("Butter", catalog)));
        assertEquals(false, StoreMatchService.isExcluded(null));
    }
}
