package de.dahaem.tags;

import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.dahaem.tags.TagFixtures.count;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The item → animal tool on a made-up catalogue in a throwaway MariaDB (Testcontainers), never the live one. */
class ItemAnimalServiceTest {

    private ItemAnimalRepository repository;
    private ItemAnimalService service;
    private int hackfleisch;
    private int salami;
    private int lachs;
    private int karotte;

    @BeforeEach
    void setUp() {
        TestDatabase.reset();
        repository = new ItemAnimalRepository();
        service = new ItemAnimalService(repository);
        hackfleisch = TagFixtures.item("gemischtes hackfleisch", "meat", "fresh meat");
        salami = TagFixtures.item("salami", "meat", "wurst & aufschnitt");
        lachs = TagFixtures.item("lachsfilet", "fish & seafood", "fresh fish");
        karotte = TagFixtures.item("karotten", "vegetable", null);
        TagFixtures.item("chili sin carne dose", "canned & jarred goods", "canned meat");
        TagFixtures.item("sojamilch", "dairy", null);
    }

    @AfterEach
    void tearDown() {
        TestDatabase.close();
    }

    @Test
    void proposalCoversOnlyMeatAndFishItemsAndSplitsConfidentFromReview() {
        List<ItemAnimalService.Row> rows = service.propose();

        assertEquals(4, rows.size(), "vegetables and dairy are not part of it: " + rows);
        ItemAnimalService.Row mince = row(rows, hackfleisch);
        assertEquals(List.of("Rind", "Schwein"), mince.animals());
        assertEquals("CONFIDENT", mince.status());
        assertEquals("REVIEW", row(rows, salami).status());
        assertEquals(List.of("Schwein"), row(rows, salami).animals());
        assertEquals("CONFIDENT", row(rows, lachs).status());
        assertTrue(rows.stream().anyMatch(r -> r.name().equals("chili sin carne dose")
                && r.animals().isEmpty() && r.status().equals("REVIEW")));
        assertEquals(0, count("SELECT COUNT(*) FROM inventory_item_animal"), "proposing writes nothing");
    }

    @Test
    void tsvHasTheSixColumnsAndSurvivesARoundTrip() {
        List<ItemAnimalService.Row> rows = service.propose();
        String tsv = ItemAnimalService.toTsv(rows);

        String[] lines = tsv.split("\n");
        assertEquals("item_id\tname\tcategory\tsubcategory\tproposed_animals\tstatus", lines[0]);
        assertTrue(tsv.contains(hackfleisch + "\tgemischtes hackfleisch\tmeat\tfresh meat\tRind+Schwein\tCONFIDENT"));
        assertEquals(rows, ItemAnimalService.parseTsv(tsv));
    }

    @Test
    void applyIsADryRunByDefaultAndStoresOnlyConfidentRows() {
        List<ItemAnimalService.Row> rows = service.propose();

        ItemAnimalService.ApplyReport dry = service.apply(rows, false);
        assertEquals(0, count("SELECT COUNT(*) FROM inventory_item_animal"), "dry run writes nothing");
        assertEquals(3, dry.animalsToAdd(), "Rind + Schwein for the mince, Fisch for the salmon");

        ItemAnimalService.ApplyReport done = service.apply(rows, true);
        assertTrue(done.committed());
        assertEquals(3, count("SELECT COUNT(*) FROM inventory_item_animal"));
        assertEquals(Set.of("Rind", "Schwein"), repository.findAnimals().get(hackfleisch));
        assertEquals(Set.of("Fisch"), repository.findAnimals().get(lachs));
        assertTrue(!repository.findAnimals().containsKey(salami), "REVIEW rows are never stored");
        assertEquals(2, done.rowsSkippedReview());
    }

    @Test
    void aReviewedRowIsStoredWithTheAnimalsNateWrote() {
        List<ItemAnimalService.Row> edited = new java.util.ArrayList<>();
        for (ItemAnimalService.Row r : service.propose()) {
            edited.add(r.itemId() == salami
                    ? new ItemAnimalService.Row(r.itemId(), r.name(), r.category(), r.subcategory(),
                            List.of("Schwein", "Rind"), "REVIEWED")
                    : r);
        }
        service.apply(edited, true);
        assertEquals(Set.of("Schwein", "Rind"), repository.findAnimals().get(salami));
    }

    @Test
    void aRowReviewedToNoAnimalStoresNothing() {
        ItemAnimalService.Row none = new ItemAnimalService.Row(salami, "salami", "meat", "wurst & aufschnitt",
                List.of(), "REVIEWED");
        ItemAnimalService.ApplyReport report = service.apply(List.of(none), true);
        assertEquals(1, report.rowsWithoutAnimals());
        assertEquals(0, count("SELECT COUNT(*) FROM inventory_item_animal"));
    }

    @Test
    void anAnimalOutsideTheTierListOrAMissingItemAbortsBeforeAnythingIsWritten() {
        ItemAnimalService.Row good = new ItemAnimalService.Row(lachs, "lachsfilet", "fish & seafood", "fresh fish",
                List.of("Fisch"), "CONFIDENT");
        ItemAnimalService.Row duck = new ItemAnimalService.Row(salami, "salami", "meat", "x", List.of("Ente"),
                "REVIEWED");
        ItemAnimalService.Row ghost = new ItemAnimalService.Row(999_999, "geist", "meat", "x", List.of("Rind"),
                "CONFIDENT");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.apply(List.of(good, duck, ghost), true));
        assertTrue(e.getMessage().contains("Ente") && e.getMessage().contains("999999"), e.getMessage());
        assertEquals(0, count("SELECT COUNT(*) FROM inventory_item_animal"));
    }

    @Test
    void anUnknownStatusIsSkippedWithAWarning() {
        ItemAnimalService.Row typo = new ItemAnimalService.Row(lachs, "lachsfilet", "fish & seafood", "fresh fish",
                List.of("Fisch"), "REVEIWED");
        ItemAnimalService.ApplyReport report = service.apply(List.of(typo), true);
        assertEquals(0, count("SELECT COUNT(*) FROM inventory_item_animal"));
        assertEquals(1, report.warnings().size());
    }

    @Test
    void applyingTwiceAddsNothingAndClassifiedItemsLeaveTheProposal() {
        service.apply(service.propose(), true);
        ItemAnimalService.ApplyReport again = service.apply(List.of(new ItemAnimalService.Row(lachs, "lachsfilet",
                "fish & seafood", "fresh fish", List.of("Fisch"), "CONFIDENT")), true);

        assertEquals(0, again.animalsToAdd());
        assertEquals(3, count("SELECT COUNT(*) FROM inventory_item_animal"));
        assertEquals(2, service.propose().size(), "only the two REVIEW items are still open: " + service.propose());
    }

    @Test
    void aFailureHalfWayRollsTheWholeBatchBack() {
        Map<Integer, Set<String>> batch = new LinkedHashMap<>();
        batch.put(lachs, new LinkedHashSet<>(List.of("Fisch")));
        batch.put(999_999, new LinkedHashSet<>(List.of("Rind")));   // no such item: the foreign key fails

        assertThrows(RuntimeException.class, () -> repository.insertAnimals(batch));
        assertEquals(0, count("SELECT COUNT(*) FROM inventory_item_animal"), "the salmon row was rolled back too");
    }

    @Test
    void theDatabaseItselfOnlyAcceptsTierAnimals() {
        assertThrows(RuntimeException.class, () -> TagFixtures.animal(karotte, "Ente"));
        TagFixtures.animal(karotte, "Meeresfrüchte");
        assertEquals(1, count("SELECT COUNT(*) FROM inventory_item_animal"));
    }

    @Test
    void parseRejectsAShortLine() {
        assertThrows(IllegalArgumentException.class, () -> ItemAnimalService.parseTsv("1\tname\tmeat"));
        assertThrows(IllegalArgumentException.class, () -> ItemAnimalService.parseTsv("x\ta\tb\tc\td\tCONFIDENT"));
    }

    private static ItemAnimalService.Row row(List<ItemAnimalService.Row> rows, int itemId) {
        return rows.stream().filter(r -> r.itemId() == itemId).findFirst().orElseThrow();
    }
}
