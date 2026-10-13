package de.dahaem.tags;

/** A tag the tagger derives: a name in a tag group (tag_category). */
public record DerivedTag(String name, String category) {

    public static final String TIER = "Tier";
    public static final String ERNAEHRUNG = "Ernährung";
    public static final String ZEIT = "Zeit";
    public static final String NAEHRWERTE = "Nährwerte";
    public static final String KUECHE = "Küche";
    public static final String INHALT = "Inhalt";

    public static final String VEGETARISCH = "vegetarisch";
    public static final String VEGAN = "vegan";
    /** The diet cannot be judged: a line of the recipe has no catalogue item (or the page was not checked). */
    public static final String UNKLAR = "unklar";
    /** Contains alcohol as an ingredient (an item with the alcohol flag). */
    public static final String ALKOHOL = "Alkohol";
    /** A land animal (certain or possible) together with a dairy item. */
    public static final String FLEISCH_MIT_MILCH = "Fleisch mit Milch";
    public static final String SCHNELL = "schnell";
    public static final String KALORIENARM = "kalorienarm";
    public static final String PROTEINREICH = "proteinreich";
}
