package de.dahaem.diet;

/** What the diet rules say about one recipe. */
public enum DietStatus {
    /** Nothing in the recipe is excluded, and nothing is unknown. */
    OK,
    /** Excluded only through POSSIBLE animals of generic items (Salami, Gelatine ...): the hints say which animal each item may be. */
    ANPASSBAR,
    /** Excluded by a certain tag, animal or item (or a required tag it lacks): never returned. */
    HIDDEN,
    /** A hard rule applies and the recipe is "unklar" (a line was not matched): hidden unless includeUnclear. */
    UNCLEAR
}
