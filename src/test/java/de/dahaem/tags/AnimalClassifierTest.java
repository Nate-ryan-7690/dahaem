package de.dahaem.tags;

import de.dahaem.tags.AnimalClassifier.Confidence;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The name rules of the animal and alcohol proposal, on names from the real catalogue. */
class AnimalClassifierTest {

    private static AnimalClassifier.Proposal propose(String name, String category, String subcategory) {
        return AnimalClassifier.propose(new CatalogItem(1, name, category, subcategory));
    }

    private static void confident(String name, String... animals) {
        AnimalClassifier.Proposal p = propose(name, "meat", "fresh meat");
        assertEquals(List.of(animals), p.animals(), name);
        assertEquals(List.of(), p.possibleAnimals(), name);
        assertEquals(Confidence.CONFIDENT, p.confidence(), name);
    }

    /** A product made from one animal, named generically: a suggestion of CERTAIN animals, never confident. */
    private static void review(String name, String... suggested) {
        AnimalClassifier.Proposal p = propose(name, "meat", "wurst & aufschnitt");
        assertEquals(List.of(suggested), p.animals(), name);
        assertEquals(List.of(), p.possibleAnimals(), name);
        assertEquals(Confidence.REVIEW, p.confidence(), name);
    }

    /** A generic product that exists from several animals: POSSIBLE animals, never confident. */
    private static void ambiguous(String name, String category, String... possible) {
        AnimalClassifier.Proposal p = propose(name, category, "wurst & aufschnitt");
        assertEquals(List.of(), p.animals(), name);
        assertEquals(List.of(possible), p.possibleAnimals(), name);
        assertEquals(Confidence.REVIEW, p.confidence(), name);
    }

    @Test
    void theNameSaysTheAnimalSoItIsConfident() {
        confident("hähnchenbrustfilet", "Hähnchen");
        confident("ganzes hähnchen", "Hähnchen");
        confident("Chicken Nuggets", "Hähnchen");
        confident("hühner nudeltopf dose", "Hähnchen");
        confident("putenbrustfilet", "Pute");
        confident("Pulled Turkey", "Pute");
        confident("rinderhackfleisch", "Rind");
        confident("Bio Roastbeef", "Rind");
        confident("corned beef", "Rind");
        confident("beef jerky original", "Rind");
        confident("schweinenackensteak", "Schwein");
        confident("Pulled Pork", "Schwein");
        confident("spanferkel", "Schwein");
        confident("Bio-Schweinefleischstreifen mariniert", "Schwein");
        confident("lammkeule", "Lamm");
        confident("kalbsschnitzel", "Kalb");
        confident("rehkeule", "Wild");
        confident("wildschwein", "Wild");
        confident("hirschgulasch", "Wild");
    }

    @Test
    void duckGooseRabbitHorseAndMixedPoultryAreTheirOwnConfidentValues() {
        confident("entenbrust", "Ente");
        confident("Barbarie-Entenkeule", "Ente");
        confident("flugente ganz", "Ente");
        confident("gänsekeule", "Gans");
        confident("Ganskeule", "Gans");
        confident("kaninchenkeule", "Kaninchen");
        confident("Kaninchen-Rücken", "Kaninchen");
        confident("pferdefleisch", "Pferd");
        confident("Pferdewurst", "Pferd");
        confident("geflügelbrust", "Geflügel");
        confident("Geflügelwurst", "Geflügel");
        confident("geflügelsalami", "Geflügel");
        confident("ente-gans-mix", "Ente", "Gans");
    }

    @Test
    void wordsThatContainDuckOrGooseAreNotOne() {
        for (String name : List.of("parmesan", "zentimeter", "patentreis", "organsalz", "ganze tomaten")) {
            assertEquals(java.util.Set.of(), AnimalClassifier.explicit(name), name);
        }
        assertEquals(java.util.Set.of("Ente"), AnimalClassifier.explicit("ente"));
    }

    @Test
    void mixedMinceIsBeefAndPork() {
        confident("gemischtes hackfleisch", "Rind", "Schwein");
        confident("gemischtes Hack", "Rind", "Schwein");
    }

    @Test
    void fishAndSeafoodNamesAreConfident() {
        confident("lachsfilet", "Fisch");
        confident("wildlachsfilet", "Fisch");
        confident("thunfisch in öl dose", "Fisch");
        confident("räucheraal", "Fisch");
        confident("Schellfischfilet", "Fisch");
        confident("fischstäbchen", "Fisch");
        confident("garnelen tiefgefroren", "Meeresfrüchte");
        confident("königskrabbe", "Meeresfrüchte");
        confident("miesmuscheln", "Meeresfrüchte");
        confident("tintenfischringe", "Meeresfrüchte");
        confident("meeresfrüchte mix", "Meeresfrüchte");
    }

    @Test
    void aProductMadeFromOneAnimalIsASuggestionOfCertainAnimalsForReviewNeverConfident() {
        review("geräucherter speck", "Schwein");
        review("kasseler", "Schwein");
        review("Speckstreifen", "Schwein");
        review("Kochschinken", "Schwein");
        review("weißwurst", "Schwein", "Kalb");
        review("ribeye steak", "Rind");
        review("lachsschinken", "Schwein");
    }

    @Test
    void aGenericProductThatExistsFromSeveralAnimalsGetsPossibleAnimals() {
        ambiguous("salami", "meat", "Geflügel", "Rind", "Schwein");
        ambiguous("fleischwurst", "meat", "Geflügel", "Rind", "Schwein");
        ambiguous("lyoner", "meat", "Geflügel", "Rind", "Schwein");
        ambiguous("leberwurst", "meat", "Geflügel", "Rind", "Schwein");
        ambiguous("chorizo", "meat", "Geflügel", "Rind", "Schwein");
        ambiguous("wiener würstchen", "meat", "Geflügel", "Rind", "Schwein");
        ambiguous("Bifi Original", "snacks", "Geflügel", "Rind", "Schwein");
        ambiguous("merguez", "meat", "Rind", "Lamm");
        ambiguous("gelatine", "baking supplies", "Rind", "Schwein");
        ambiguous("schmalz", "oils & vinegars", "Gans", "Schwein");
        ambiguous("worcestersauce", "condiments & sauces", "Fisch");
    }

    @Test
    void whenTheNameSaysTheAnimalASausageIsNotAmbiguous() {
        confident("Putensalami", "Pute");
        confident("Rinderleberwurst", "Rind");
        confident("Schweineschmalz", "Schwein");
        confident("Gänseschmalz", "Gans");
        assertEquals(List.of("Rind"), propose("Rinderbratwurst", "meat", "wurst & aufschnitt").animals());
    }

    @Test
    void anItemOutsideMeatAndFishIsOnlyACandidateWhenItsNameIsAGenericAnimalProduct() {
        assertTrue(AnimalClassifier.isAnimalCandidate(new CatalogItem(1, "gelatine", "baking supplies", null)));
        assertTrue(AnimalClassifier.isAnimalCandidate(new CatalogItem(1, "Bio Schmalz", "oils & vinegars", null)));
        assertTrue(AnimalClassifier.isAnimalCandidate(new CatalogItem(1, "lachsfilet", "fish & seafood", "fresh fish")));
        assertTrue(AnimalClassifier.isAnimalCandidate(new CatalogItem(1, "x", "frozen", "fish")));
        assertFalse(AnimalClassifier.isAnimalCandidate(new CatalogItem(1, "karotten", "vegetable", null)));
        assertFalse(AnimalClassifier.isAnimalCandidate(new CatalogItem(1, "hühnerbrühe", "condiments & sauces", null)),
                "a stock names an animal but is not a generic product: not proposed");
        assertFalse(AnimalClassifier.isAnimalCandidate(new CatalogItem(1, "vegane salami", "plant-based", null)));
        assertFalse(AnimalClassifier.isAnimalCandidate(new CatalogItem(1, "Bratwurst Gewürz", "spices & seasonings", null)));
    }

    @Test
    void noEvidenceIsReviewWithoutAProposalExceptTheFishSubcategories() {
        AnimalClassifier.Proposal soup = propose("gemüsetopf dose", "canned & jarred goods", "canned meat");
        assertEquals(List.of(), soup.animals());
        assertEquals(List.of(), soup.possibleAnimals());
        assertEquals(Confidence.REVIEW, soup.confidence());

        AnimalClassifier.Proposal fish = propose("Schlemmerfilet", "frozen", "fish");
        assertEquals(List.of("Fisch"), fish.animals());
        assertEquals(Confidence.REVIEW, fish.confidence());

        AnimalClassifier.Proposal seafood = propose("frutti di mare", "fish & seafood", "shellfish & seafood");
        assertEquals(List.of("Meeresfrüchte"), seafood.animals());
        assertEquals(Confidence.CONFIDENT, seafood.confidence(), "the name says it");
    }

    @Test
    void wordsThatLookLikeAnimalsAreNotOne() {
        assertEquals(List.of(), propose("wildreis", "grains & rice", "rice").animals().stream()
                .filter(a -> a.equals("Wild")).toList());
        assertEquals(java.util.Set.of(), AnimalClassifier.explicit("wildkräuter"));
        assertEquals(java.util.Set.of(), AnimalClassifier.explicit("hirschhornsalz"));
        assertEquals(java.util.Set.of(), AnimalClassifier.explicit("muschelnudeln"));
        assertEquals(java.util.Set.of(), AnimalClassifier.explicit("flammkuchenteig"));
    }

    // ---------- alcohol ----------

    private static Optional<Confidence> alcohol(String name, String category, String subcategory) {
        return AnimalClassifier.proposeAlcohol(new CatalogItem(1, name, category, subcategory));
    }

    @Test
    void alcoholicDrinksAreConfidentByTheirSubcategory() {
        for (String[] drink : new String[][]{{"Rotwein", "wine"}, {"Pils", "beer"}, {"Whisky", "spirits"},
                {"Sahnelikör", "liqueur"}, {"Sekt", "sparkling wine"}, {"Apfelwein", "cider"}}) {
            assertEquals(Optional.of(Confidence.CONFIDENT), alcohol(drink[0], "beverages", drink[1]), drink[0]);
        }
    }

    @Test
    void alcoholFreeDrinksAreNotProposed() {
        assertEquals(Optional.empty(), alcohol("Alkoholfreies Bier", "beverages", "beer"));
        assertEquals(Optional.empty(), alcohol("Radler alkoholfrei", "beverages", "beer"));
        assertEquals(Optional.empty(), alcohol("Kindersekt", "beverages", "soft drinks"));
        assertEquals(Optional.empty(), alcohol("Malzbier", "beverages", "soft drinks"));
        assertEquals(Optional.empty(), alcohol("Apfelsaft", "beverages", "juices"));
    }

    @Test
    void cookingWineIsConfidentAndAnAlcoholWordInOtherFoodIsReview() {
        assertEquals(Optional.of(Confidence.CONFIDENT), alcohol("Kochwein weiß", "condiments & sauces", null));
        assertEquals(Optional.of(Confidence.REVIEW), alcohol("Reiswein", "condiments & sauces", "asian sauces"));
        assertEquals(Optional.of(Confidence.REVIEW), alcohol("Mirin", "condiments & sauces", "asian sauces"));
        assertEquals(Optional.of(Confidence.REVIEW), alcohol("Kräftige Rotweinpaste", "condiments & sauces", null));
        assertEquals(Optional.of(Confidence.REVIEW), alcohol("Amaretto", "baking supplies", null));
    }

    @Test
    void wordsThatLookLikeAlcoholAreNot() {
        for (String name : List.of("Weißweinessig", "Rotweinessig", "Weintrauben rot", "Weinstein Backpulver",
                "Rum Aroma", "Glühweingewürz", "Bierschinken", "Bier Erdnüsse", "Ginger Ale", "Popcorn",
                "Pfefferkorn", "Insektenmehl", "Kirschtomaten", "Schwarzwälder Kirschtorte")) {
            assertEquals(Optional.empty(), alcohol(name, "other", null), name);
        }
    }
}
