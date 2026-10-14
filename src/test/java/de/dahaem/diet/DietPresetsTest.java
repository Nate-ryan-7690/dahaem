package de.dahaem.diet;

import de.dahaem.tags.Animals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** config/diet_presets.json and how request parameters become rules. No database. */
class DietPresetsTest {

    private static final DietPresets PRESETS = DietPresets.load(DietPresets.DEFAULT_FILE);

    @Test
    void theShippedFileHasTheFourPresetsAsDecided() {
        assertEquals(List.of("halal", "koscher", "vegetarisch", "vegan"), List.copyOf(PRESETS.names()));
        assertEquals(List.of("Schwein", "Alkohol"), PRESETS.find("halal").orElseThrow().excludeTags());
        assertEquals(List.of("Schwein", "Meeresfrüchte", "Kaninchen", "Pferd", "Fleisch mit Milch"),
                PRESETS.find("koscher").orElseThrow().excludeTags());
        assertEquals(List.of("vegetarisch"), PRESETS.find("vegetarisch").orElseThrow().requireTags());
        assertEquals(List.of("vegan"), PRESETS.find("vegan").orElseThrow().requireTags());
    }

    @Test
    void everyTagInThePresetsIsOneTheTaggerCanDerive() {
        Set<String> derivable = new java.util.HashSet<>(Animals.ALL);
        derivable.addAll(Set.of("Alkohol", "Fleisch mit Milch", "vegetarisch", "vegan"));
        for (DietPreset preset : PRESETS.all()) {
            for (String tag : preset.excludeTags()) {
                assertTrue(derivable.contains(tag), preset.name() + " excludes '" + tag + "', which no run derives");
            }
            for (String tag : preset.requireTags()) {
                assertTrue(derivable.contains(tag), preset.name() + " requires '" + tag + "'");
            }
        }
    }

    @Test
    void presetsAreNamedHonestlyAndSayThatMeatCertificationIsTheShoppersJob() {
        for (String name : List.of("halal", "koscher")) {
            DietPreset p = PRESETS.find(name).orElseThrow();
            assertTrue(p.label().contains("Zutaten") && p.label().contains("keine Zertifizierung"), p.label());
            assertTrue(p.note().contains("responsibility") || p.note().contains("Slaughter"), p.note());
        }
        assertTrue(PRESETS.find("koscher").orElseThrow().note().contains("fins and scales"),
                "the file says fish without fins and scales cannot be detected yet");
    }

    @Test
    void rulesResolvePresetsAndAddTheExplicitTagsAndItems() {
        DietRules rules = DietRules.of(PRESETS, List.of("halal", "vegetarisch"), List.of("Pferd, Kaninchen"),
                List.of(5, 6), List.of("Rind"), List.of(7), true);
        assertEquals(Set.of("Schwein", "Alkohol", "Pferd", "Kaninchen"), rules.excludeTags());
        assertEquals(Set.of("vegetarisch"), rules.requireTags());
        assertEquals(Set.of(5, 6), rules.excludeItems());
        assertEquals(Set.of("Rind"), rules.avoidTags());
        assertEquals(Set.of(7), rules.avoidItems());
        assertTrue(rules.includeUnclear() && rules.hasHardRules() && rules.hasRules());
    }

    @Test
    void noParametersMeansNoRules() {
        DietRules none = DietRules.of(PRESETS, null, null, null, null, null, false);
        assertFalse(none.hasRules());
        assertFalse(DietRules.of(PRESETS, List.of(), List.of(" "), List.of(), List.of(), List.of(), true).hasRules(),
                "includeUnclear alone is no rule");
        assertTrue(DietRules.of(PRESETS, null, null, null, List.of("Schwein"), null, false).hasRules());
        assertFalse(DietRules.of(PRESETS, null, null, null, List.of("Schwein"), null, false).hasHardRules());
    }

    @Test
    void anUnknownPresetIsAnErrorThatListsTheKnownOnes() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> DietRules.of(PRESETS, List.of("paleo"), null, null, null, null, false));
        assertTrue(e.getMessage().contains("paleo") && e.getMessage().contains("halal, koscher, vegetarisch, vegan"),
                e.getMessage());
    }

    @Test
    void aMissingOrMalformedFileStopsWithAClearMessage(@TempDir Path dir) throws IOException {
        assertTrue(assertThrows(IllegalStateException.class, () -> DietPresets.load(dir.resolve("none.json")))
                .getMessage().contains("not found"));
        Path bad = dir.resolve("bad.json");
        Files.writeString(bad, "{\"presets\": {\"x\": {\"excludeTags\": \"Schwein\"}}}");
        assertTrue(assertThrows(IllegalStateException.class, () -> DietPresets.load(bad))
                .getMessage().contains("must be a list"));
        Files.writeString(bad, "{\"nope\": 1}");
        assertTrue(assertThrows(IllegalStateException.class, () -> DietPresets.load(bad))
                .getMessage().contains("presets"));
    }

    @Test
    void aPresetCanBeChangedInTheFileWithoutTouchingCode(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("p.json");
        Files.writeString(file, "{\"_note\": \"x\", \"presets\": {\"_c\": {}, \"ohne-ente\": "
                + "{\"label\": \"ohne Ente\", \"excludeTags\": [\"Ente\"]}}}");
        DietPresets presets = DietPresets.load(file);
        assertEquals(Set.of("ohne-ente"), presets.names());
        assertEquals(Set.of("Ente"), DietRules.of(presets, List.of("ohne-ente"), null, null, null, null, false).excludeTags());
    }
}
