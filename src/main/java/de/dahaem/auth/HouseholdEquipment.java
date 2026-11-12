package de.dahaem.auth;

import de.dahaem.tags.DerivedTag;

import java.util.List;
import java.util.Optional;

/**
 * The kitchen equipment a household can say it does not have. A recipe that needs equipment carries a derived tag of the
 * group "Ausstattung" (see {@link DerivedTag}); a household without it does not get those recipes in the lists, the search and
 * the suggestions.
 */
public enum HouseholdEquipment {

    /** HelloFresh's Thermomix recipes ("Mixtopf", "Varoma", "Thermomix" in a step). */
    THERMOMIX("thermomix", "Thermomix", DerivedTag.THERMOMIX);

    private final String code;
    private final String displayName;
    private final String recipeTag;

    HouseholdEquipment(String code, String displayName, String recipeTag) {
        this.code = code;
        this.displayName = displayName;
        this.recipeTag = recipeTag;
    }

    /** The short name used in the API and the database. */
    public String code() {
        return code;
    }

    /** The name shown to people. */
    public String displayName() {
        return displayName;
    }

    /** The name of the recipe tag that marks a recipe as needing this equipment. */
    public String recipeTag() {
        return recipeTag;
    }

    public static Optional<HouseholdEquipment> byCode(String code) {
        return List.of(values()).stream().filter(e -> e.code.equals(code)).findFirst();
    }
}
