package de.dahaem.tags;

import de.dahaem.testsupport.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static de.dahaem.tags.TagFixtures.count;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The item → animal / alcohol tool on a made-up catalogue in a throwaway MariaDB (Testcontainers), never the live one. */
class ItemAnimalServiceTest {

    private ItemAnimalRepository repository;
    private ItemAnimalService service;
    private int hackfleisch;
    private int salami;
    private int lachs;
    private int karotte;
    private int entenbrust;
    private int gelatine;
    private int rotwein;

    @BeforeEach
    void setUp() {
        TestDatabase.reset();
        repository = new ItemAnimalRepository();
        service = new ItemAnimalService(repository);
        hackfleisch = TagFixtures.item("gemischtes hackfleisch", "meat", "fresh meat");
        salami = TagFixtures.item("salami", "meat", "wurst & aufschnitt");
        lachs = TagFixtures.item("lachsfilet", "fish & seafood", "fresh fish");
        karotte = TagFixtures.item("karotten", "vegetable", null);
        entenbrust = TagFixtures.item("entenbrust", "meat", "fresh meat");
        gelatine = TagFixtures.item("gelatine", "baking supplies", null);
        rotwein = TagFixtures.item("rotwein", "beverages", "wine");
        TagFixtures.item("chili sin carne dose", "canned & jarred goods", "canned meat");
        TagFixtures.item("sojamilch", "dairy", null);
        TagFixtures.item("apfelsaft", "beverages", "juices");
    }

    @AfterEach
    void tearDown() {
        TestDatabase.close();
    }

    @Test
    void proposalCoversMeatFishGenericAnimalProductsAndAlcoholAndSplitsConfidentFromReview() {
        List<ItemAnimalService.Row> rows = service.propose();

        assertEquals(7, rows.size(), "vegetables, dairy and juice are not part of it: " + rows);
        ItemAnimalService.Row mince = row(rows, hackfleisch);
        assertEquals(List.of("Rind", "Schwein"), mince.animals());
        assertEquals(List.of(), mince.possibleAnimals());
        assertEquals("CONFIDENT", mince.status());
        assertFalse(mince.alcohol());

        ItemAnimalService.Row sausage = row(rows, salami);
        assertEquals(List.of(), sausage.animals());
        assertEquals(List.of("Geflügel", "Rind", "Schwein"), sausage.possibleAnimals(), "Salami: possible, not certain");
        assertEquals("REVIEW", sausage.status());

        ItemAnimalService.Row jelly = row(rows, gelatine);
        assertEquals(List.of("Rind", "Schwein"), jelly.possibleAnimals());
        assertEquals("REVIEW", jelly.status());

        assertEquals(List.of("Ente"), row(rows, entenbrust).animals());
        assertEquals("CONFIDENT", row(rows, entenbrust).status());
        assertEquals("CONFIDENT", row(rows, lachs).status());

        ItemAnimalService.Row wine = row(rows, rotwein);
        assertTrue(wine.alcohol());
        assertEquals(List.of(), wine.animals());
        assertEquals("CONFIDENT", wine.status());

        assertTrue(rows.stream().anyMatch(r -> r.name().equals("chili sin carne dose")
                && r.animals().isEmpty() && r.possibleAnimals().isEmpty() && r.status().equals("REVIEW")));
        assertEquals(0, count("SELECT COUNT(*) FROM inventory_item_animal"), "proposing writes nothing");
    }

    @Test
    void tsvHasCertainAndPossibleAnimalsAndTheAlcoholColumnAndSurvivesARoundTrip() {
        List<ItemAnimalService.Row> rows = service.propose();
        String tsv = ItemAnimalService.toTsv(rows);

        String[] lines = tsv.split("\n");
        assertEquals("item_id\tname\tcategory\tsubcategory\tcertain_animals\tpossible_animals\talcohol\tstatus", lines[0]);
        assertTrue(tsv.contains(hackfleisch + "\tgemischtes hackfleisch\tmeat\tfresh meat\tRind+Schwein\t\tno\tCONFIDENT"));
        assertTrue(tsv.contains(salami + "\tsalami\tmeat\twurst & aufschnitt\t\tGeflügel+Rind+Schwein\tno\tREVIEW"));
        assertTrue(tsv.contains(rotwein + "\trotwein\tbeverages\twine\t\t\tyes\tCONFIDENT"));
        assertEquals(rows, ItemAnimalService.parseTsv(tsv));
    }

    @Test
    void theSixColumnTsvOfTheFirstVersionIsStillRead() {
        List<ItemAnimalService.Row> rows = ItemAnimalService.parseTsv(
                "item_id\tname\tcategory\tsubcategory\tproposed_animals\tstatus\n"
                        + lachs + "\tlachsfilet\tfish & seafood\tfresh fish\tFisch\tCONFIDENT\n");
        assertEquals(List.of("Fisch"), rows.get(0).animals());
        assertEquals(List.of(), rows.get(0).possibleAnimals());
        assertFalse(rows.get(0).alcohol());
        assertEquals("CONFIDENT", rows.get(0).status());
    }

    @Test
    void applyIsADryRunByDefaultAndStoresOnlyConfidentRowsWithTheirCertaintyAndTheAlcoholFlag() {
        List<ItemAnimalService.Row> rows = service.propose();

        ItemAnimalService.ApplyReport dry = service.apply(rows, false);
        assertEquals(0, count("SELECT COUNT(*) FROM inventory_item_animal"), "dry run writes nothing");
        assertEquals(0, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_alcohol"));
        assertEquals(4, dry.animalsToAdd(), "Rind + Schwein for the mince, Ente, Fisch");
        assertEquals(1, dry.alcoholToSet());

        ItemAnimalService.ApplyReport done = service.apply(rows, true);
        assertTrue(done.committed());
        assertEquals(4, count("SELECT COUNT(*) FROM inventory_item_animal"));
        assertEquals(Set.of("Rind", "Schwein"), repository.findAnimals().get(hackfleisch));
        assertEquals(Set.of("Fisch"), repository.findAnimals().get(lachs));
        assertEquals(Map.of("Ente", "certain"), repository.findAnimalCertainty().get(entenbrust));
        assertFalse(repository.findAnimals().containsKey(salami), "REVIEW rows are never stored");
        assertFalse(repository.findAnimals().containsKey(gelatine));
        assertEquals(1, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_alcohol"));
        assertEquals(1, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_alcohol AND inventory_item_id = " + rotwein));
    }

    @Test
    void aReviewedRowIsStoredWithTheAnimalsAndCertaintyNateWrote() {
        List<ItemAnimalService.Row> edited = new java.util.ArrayList<>();
        for (ItemAnimalService.Row r : service.propose()) {
            edited.add(r.itemId() == salami
                    ? new ItemAnimalService.Row(r.itemId(), r.name(), r.category(), r.subcategory(),
                            List.of("Schwein"), List.of("Rind"), false, "REVIEWED")
                    : r.itemId() == gelatine
                    ? new ItemAnimalService.Row(r.itemId(), r.name(), r.category(), r.subcategory(),
                            List.of(), List.of("Rind", "Schwein"), false, "OK")
                    : r);
        }
        service.apply(edited, true);
        assertEquals(Map.of("Schwein", "certain", "Rind", "possible"), repository.findAnimalCertainty().get(salami));
        assertEquals(Map.of("Rind", "possible", "Schwein", "possible"), repository.findAnimalCertainty().get(gelatine));
    }

    @Test
    void anAnimalCannotBeBothCertainAndPossible() {
        ItemAnimalService.Row both = new ItemAnimalService.Row(salami, "salami", "meat", "x", List.of("Rind"),
                List.of("Rind", "Schwein"), false, "REVIEWED");
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> service.apply(List.of(both), true));
        assertTrue(e.getMessage().contains("both certain and possible"), e.getMessage());
        assertEquals(0, count("SELECT COUNT(*) FROM inventory_item_animal"));
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
    void anAnimalOutsideTheListOrAMissingItemAbortsBeforeAnythingIsWritten() {
        ItemAnimalService.Row good = new ItemAnimalService.Row(lachs, "lachsfilet", "fish & seafood", "fresh fish",
                List.of("Fisch"), "CONFIDENT");
        ItemAnimalService.Row dragon = new ItemAnimalService.Row(salami, "salami", "meat", "x", List.of("Drache"),
                "REVIEWED");
        ItemAnimalService.Row ghost = new ItemAnimalService.Row(999_999, "geist", "meat", "x", List.of("Rind"),
                "CONFIDENT");
        ItemAnimalService.Row ghostWine = new ItemAnimalService.Row(999_998, "geisterwein", "x", "x", List.of(),
                List.of(), true, "CONFIDENT");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> service.apply(List.of(good, dragon, ghost, ghostWine), true));
        assertTrue(e.getMessage().contains("Drache") && e.getMessage().contains("999999")
                && e.getMessage().contains("999998"), e.getMessage());
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
    void applyingTwiceAddsNothingAndClassifiedItemsLeaveTheProposalUnlessAll() {
        service.apply(service.propose(), true);
        ItemAnimalService.ApplyReport again = service.apply(List.of(new ItemAnimalService.Row(lachs, "lachsfilet",
                "fish & seafood", "fresh fish", List.of("Fisch"), "CONFIDENT")), true);

        assertEquals(0, again.animalsToAdd());
        assertEquals(0, again.certaintyChanges());
        assertEquals(4, count("SELECT COUNT(*) FROM inventory_item_animal"));
        List<ItemAnimalService.Row> open = service.propose();
        assertEquals(3, open.size(), "salami, gelatine and the canned chili are still open: " + open);
        assertTrue(open.stream().noneMatch(r -> r.itemId() == rotwein), "the wine has its alcohol flag");
        assertTrue(open.stream().noneMatch(r -> r.itemId() == lachs));

        List<ItemAnimalService.Row> everything = service.propose(true);
        assertEquals(7, everything.size(), "--all proposes the classified items again");
    }

    @Test
    void reviewingACertainAnimalToPossibleChangesItsCertaintyButNeverRemovesAnAnimal() {
        service.apply(List.of(new ItemAnimalService.Row(salami, "salami", "meat", "x", List.of("Schwein", "Rind"),
                "REVIEWED")), true);
        assertEquals(Map.of("Schwein", "certain", "Rind", "certain"), repository.findAnimalCertainty().get(salami));

        ItemAnimalService.ApplyReport report = service.apply(List.of(new ItemAnimalService.Row(salami, "salami",
                "meat", "x", List.of(), List.of("Schwein"), false, "REVIEWED")), true);
        assertEquals(1, report.certaintyChanges());
        assertEquals(Map.of("Schwein", "possible", "Rind", "certain"), repository.findAnimalCertainty().get(salami));
        assertEquals(1, report.warnings().size(), "Rind is stored but not in the row: left as it is, with a warning");
    }

    @Test
    void aFailureHalfWayRollsTheWholeBatchBack() {
        Map<Integer, Map<String, String>> batch = new LinkedHashMap<>();
        batch.put(lachs, Map.of("Fisch", "certain"));
        batch.put(999_999, Map.of("Rind", "certain"));   // no such item: the foreign key fails

        assertThrows(RuntimeException.class, () -> repository.store(batch, Set.of(rotwein)));
        assertEquals(0, count("SELECT COUNT(*) FROM inventory_item_animal"), "the salmon row was rolled back too");
        assertEquals(0, count("SELECT COUNT(*) FROM inventory_item WHERE inventory_item_alcohol"));
    }

    @Test
    void theDatabaseItselfOnlyAcceptsTheListedAnimalsAndCertainties() {
        assertThrows(RuntimeException.class, () -> TagFixtures.animal(karotte, "Drache"));
        TagFixtures.animal(karotte, "Meeresfrüchte");
        TagFixtures.animal(karotte, "Kaninchen");
        TagFixtures.animal(karotte, "Pferd");
        assertEquals(3, count("SELECT COUNT(*) FROM inventory_item_animal"));
        assertThrows(RuntimeException.class, () -> TagFixtures.animalWithCertainty(karotte, "Ente", "maybe"));
    }

    @Test
    void parseRejectsAShortLineAndABadAlcoholValue() {
        assertThrows(IllegalArgumentException.class, () -> ItemAnimalService.parseTsv("1\tname\tmeat"));
        assertThrows(IllegalArgumentException.class, () -> ItemAnimalService.parseTsv("x\ta\tb\tc\td\tCONFIDENT"));
        assertThrows(IllegalArgumentException.class,
                () -> ItemAnimalService.parseTsv("1\ta\tb\tc\t\t\tvielleicht\tCONFIDENT"));
    }

    private static ItemAnimalService.Row row(List<ItemAnimalService.Row> rows, int itemId) {
        return rows.stream().filter(r -> r.itemId() == itemId).findFirst().orElseThrow();
    }
}
