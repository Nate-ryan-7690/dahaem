package de.dahaem.tags;

/** A tag the tagger derives: a name in a tag group (tag_category). */
public record DerivedTag(String name, String category) {

    public static final String TIER = "Tier";
    public static final String ERNAEHRUNG = "Ernährung";
    public static final String ZEIT = "Zeit";
    public static final String NAEHRWERTE = "Nährwerte";
    public static final String KUECHE = "Küche";

    public static final String VEGETARISCH = "vegetarisch";
    public static final String VEGAN = "vegan";
    public static final String SCHNELL = "schnell";
    public static final String KALORIENARM = "kalorienarm";
    public static final String PROTEINREICH = "proteinreich";
}
