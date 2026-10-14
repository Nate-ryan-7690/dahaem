package de.dahaem.tags;

import java.util.List;
import java.util.Set;

/**
 * The animal values an item can have: the Tier tags, in display order. The same list is the CHECK in
 * {@code inventory_item_animal} (migrations 0011 / 0012), so a value outside it cannot be stored.
 *
 * <p>{@code Geflügel} is mixed or unspecified poultry; Ente, Gans, Kaninchen and Pferd are their own values
 * because rabbit and horse are not kosher, horse is disputed for halal and some people do not eat duck.
 */
public final class Animals {

    public static final String HAEHNCHEN = "Hähnchen";
    public static final String PUTE = "Pute";
    public static final String GEFLUEGEL = "Geflügel";
    public static final String ENTE = "Ente";
    public static final String GANS = "Gans";
    public static final String RIND = "Rind";
    public static final String SCHWEIN = "Schwein";
    public static final String LAMM = "Lamm";
    public static final String KALB = "Kalb";
    public static final String WILD = "Wild";
    public static final String KANINCHEN = "Kaninchen";
    public static final String PFERD = "Pferd";
    public static final String FISCH = "Fisch";
    public static final String MEERESFRUECHTE = "Meeresfrüchte";

    public static final List<String> ALL = List.of(HAEHNCHEN, PUTE, GEFLUEGEL, ENTE, GANS, RIND, SCHWEIN,
            LAMM, KALB, WILD, KANINCHEN, PFERD, FISCH, MEERESFRUECHTE);

    /** Certainty values of {@code inventory_item_animal_certainty}. */
    public static final String CERTAIN = "certain";
    public static final String POSSIBLE = "possible";

    private Animals() {
    }

    public static boolean isAnimal(String value) {
        return ALL.contains(value);
    }

    /** True for animals that live on land (everything except fish and seafood): the meat of "Fleisch mit Milch". */
    public static boolean isLandAnimal(String animal) {
        return isAnimal(animal) && !animal.equals(FISCH) && !animal.equals(MEERESFRUECHTE);
    }

    /** The given animals in the canonical order of {@link #ALL}. */
    public static List<String> sorted(Set<String> animals) {
        return ALL.stream().filter(animals::contains).toList();
    }
}
