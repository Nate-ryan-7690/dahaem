package de.dahaem.tags;

import de.dahaem.tags.AnimalClassifier.Confidence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The name rules of the animal proposal, on names from the real catalogue. */
class AnimalClassifierTest {

    private static AnimalClassifier.Proposal propose(String name, String category, String subcategory) {
        return AnimalClassifier.propose(new CatalogItem(1, name, category, subcategory));
    }

    private static void confident(String name, String... animals) {
        AnimalClassifier.Proposal p = propose(name, "meat", "fresh meat");
        assertEquals(List.of(animals), p.animals(), name);
        assertEquals(Confidence.CONFIDENT, p.confidence(), name);
    }

    private static void review(String name, String... suggested) {
        AnimalClassifier.Proposal p = propose(name, "meat", "wurst & aufschnitt");
        assertEquals(List.of(suggested), p.animals(), name);
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
    void anAnimalOnlyImpliedIsASuggestionForReviewNeverConfident() {
        review("salami", "Schwein");
        review("fleischwurst", "Schwein");
        review("geräucherter speck", "Schwein");
        review("kasseler", "Schwein");
        review("Speckstreifen", "Schwein");
        review("lyoner", "Schwein");
        review("leberwurst", "Schwein");
        review("chorizo", "Schwein");
        review("wiener würstchen", "Rind", "Schwein");
        review("weißwurst", "Schwein", "Kalb");
        review("merguez", "Rind", "Lamm");
        review("geflügelsalami", "Hähnchen", "Pute", "Schwein");
        review("ribeye steak", "Rind");
        review("lachsschinken", "Schwein");
    }

    @Test
    void noEvidenceIsReviewWithoutAProposalExceptTheFishSubcategories() {
        AnimalClassifier.Proposal soup = propose("gemüsetopf dose", "canned & jarred goods", "canned meat");
        assertEquals(List.of(), soup.animals());
        assertEquals(Confidence.REVIEW, soup.confidence());

        AnimalClassifier.Proposal duck = propose("entenbrust", "meat", "fresh meat");
        assertEquals(List.of(), duck.animals(), "Ente is not in the Tier list");
        assertEquals(Confidence.REVIEW, duck.confidence());

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
}
