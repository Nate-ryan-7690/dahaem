package de.dahaem.tags;

import java.util.List;
import java.util.Set;

/**
 * The animal values an item can have: the Tier tags, in display order. The same list is the CHECK in
 * {@code inventory_item_animal} (migration 0011), so a value outside it cannot be stored.
 */
public final class Animals {

    public static final String HAEHNCHEN = "Hähnchen";
    public static final String PUTE = "Pute";
    public static final String RIND = "Rind";
    public static final String SCHWEIN = "Schwein";
    public static final String LAMM = "Lamm";
    public static final String KALB = "Kalb";
    public static final String WILD = "Wild";
    public static final String FISCH = "Fisch";
    public static final String MEERESFRUECHTE = "Meeresfrüchte";

    public static final List<String> ALL = List.of(HAEHNCHEN, PUTE, RIND, SCHWEIN, LAMM, KALB, WILD, FISCH,
            MEERESFRUECHTE);

    private Animals() {
    }

    public static boolean isAnimal(String value) {
        return ALL.contains(value);
    }

    /** The given animals in the canonical order of {@link #ALL}. */
    public static List<String> sorted(Set<String> animals) {
        return ALL.stream().filter(animals::contains).toList();
    }
}
