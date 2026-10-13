package de.dahaem.tags;

import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Which catalogue items stop a recipe from being vegetarisch / vegan. Pure: no SQL.
 *
 * <p>The rules are a heuristic over category, subcategory and name, written so that doubt blocks the
 * tag: a missing "vegan" costs nothing, a wrong one is a claim about food. The runner can print every
 * catalogue item together with the rule that blocks it ({@code RecipeTagRunner --animal-products}) so
 * the list can be reviewed and extended here.
 */
public final class AnimalProducts {

    /** Categories that are meat or fish whatever the item is called. */
    private static final Set<String> MEAT_CATEGORIES = Set.of("meat", "meat & poultry", "fish & seafood");
    /** Meat / fish living in other categories. */
    private static final Set<String> MEAT_SUBCATEGORIES = Set.of("canned meat", "canned fish", "jerky & meat snacks",
            "poultry products");

    // Vegan only: dairy / egg / honey and friends.
    private static final Set<String> DAIRY_CATEGORIES = Set.of("dairy");
    private static final Set<String> VEGAN_BLOCKING_SUBCATEGORIES = Set.of("egg pasta", "ice cream",
            "milk & yogurt drinks", "pastry & sweet bakery", "cakes", "instant mixes & puddings",
            "fix products & seasoning sachets");

    /** Says the item is plant-based; such an item is never blocked by a meat / dairy word in its name. */
    private static final Pattern PLANT_BASED = Pattern.compile("vegan|veggie|vegetar|pflanzlich|tofu|seitan|tempeh");
    /** Plant drinks and spreads that sit in the dairy category. */
    private static final Pattern DAIRY_ALTERNATIVE = Pattern.compile(
            "hafer|soja|mandel|reismilch|kokos|cashew|margarine|pflanzlich|vegan");
    /** Seasoning blends are named after the dish but contain no meat. */
    private static final Pattern SEASONING = Pattern.compile("gewürz");

    private static final Pattern HIDDEN_ANIMAL = Pattern.compile(
            "gelatine|worcester|" + "(?<!butter)schmalz|talg|knochen|bolognese|carbonara|fischsoße|fischsauce");

    private static final Pattern EGG = Pattern.compile(
            "(?<![a-zäöüß])eier|(?<![a-zäöüß])ei(?![a-zäöüß])|eigelb|eiweiß|vollei|eiernudel|eierspätzle|eierlikör");
    private static final Pattern HONEY = Pattern.compile("honig(?!melone)");
    private static final Pattern DAIRY_WORD = Pattern.compile(
            "milch|sahne(?!steif)|rahm|butter(?!nut|brot)|joghurt|jogurt|quark|käse|kase|kefir|molke|ghee|eiscreme"
                    + "|pudding|schmand|crème fraîche|creme fraiche|mascarpone|ricotta|mozzarella|parmesan|waffel"
                    + "|croissant|mayonnaise|aioli|remoulade|marzipan");
    private static final Pattern PLANT_DAIRY_WORD = Pattern.compile(
            "erdnussbutter|nussbutter|kakaobutter|kokosmilch|hafermilch|sojamilch|mandelmilch|reismilch|vegane? "
                    + "|reiswaffel|maiswaffel|reisflockenwaffel");

    private AnimalProducts() {
    }

    /**
     * Why this item keeps a recipe from being vegetarian, or empty if it does not.
     * {@code hasRecordedAnimal}: the item has an {@code inventory_item_animal} row.
     */
    public static Optional<String> vegetarianBlocker(CatalogItem item, boolean hasRecordedAnimal) {
        if (hasRecordedAnimal) {
            return Optional.of("animal recorded for the item");
        }
        if (MEAT_CATEGORIES.contains(item.category())) {
            return Optional.of("category " + item.category());
        }
        if (MEAT_SUBCATEGORIES.contains(item.subcategory())) {
            return Optional.of("subcategory " + item.subcategory());
        }
        if (item.category().equals("frozen") && item.subcategory().equals("fish")) {
            return Optional.of("subcategory frozen / fish");
        }
        String name = AnimalClassifier.normalise(item.name());
        if (isPlantBased(item, name) || SEASONING.matcher(name).find()) {
            return Optional.empty();
        }
        if (AnimalClassifier.mentionsAnimal(name)) {
            return Optional.of("animal in the name");
        }
        if (HIDDEN_ANIMAL.matcher(name).find()) {
            return Optional.of("animal ingredient in the name (gelatine, lard, Worcester, fish sauce, ...)");
        }
        return Optional.empty();
    }

    /**
     * Why this item (which is not meat / fish) keeps a recipe from being vegan: dairy, egg, honey or
     * another animal product; empty if it does not.
     */
    public static Optional<String> veganBlocker(CatalogItem item) {
        String name = AnimalClassifier.normalise(item.name());
        if (isPlantBased(item, name)) {
            return Optional.empty();
        }
        if (DAIRY_CATEGORIES.contains(item.category())) {
            return DAIRY_ALTERNATIVE.matcher(name).find() ? Optional.empty() : Optional.of("category dairy");
        }
        if (VEGAN_BLOCKING_SUBCATEGORIES.contains(item.subcategory())) {
            return Optional.of("subcategory " + item.subcategory());
        }
        if (EGG.matcher(name).find()) {
            return Optional.of("egg in the name");
        }
        if (HONEY.matcher(name).find()) {
            return Optional.of("honey in the name");
        }
        String plainName = PLANT_DAIRY_WORD.matcher(name).replaceAll(" ");
        if (DAIRY_WORD.matcher(plainName).find()) {
            return Optional.of("dairy / egg product in the name");
        }
        return Optional.empty();
    }

    /** Milk products only (not egg, not mayonnaise): what "Fleisch mit Milch" is about. */
    private static final Pattern MILK_WORD = Pattern.compile(
            "milch|sahne(?!steif)|rahm|butter(?!nut|brot)|joghurt|jogurt|quark|käse|kase|kefir|molke|ghee|schmand"
                    + "|crème fraîche|creme fraiche|mascarpone|ricotta|mozzarella|parmesan");

    /**
     * True if the item is a milk product (milk, cream, butter, cheese, yoghurt ...). An item that is itself meat
     * or has an animal in its name (Leberkäse) is not; plant drinks and spreads are not.
     */
    public static boolean isMilkProduct(CatalogItem item) {
        String name = AnimalClassifier.normalise(item.name());
        if (isPlantBased(item, name) || MEAT_CATEGORIES.contains(item.category())
                || AnimalClassifier.mentionsAnimal(name)) {
            return false;
        }
        String plainName = PLANT_DAIRY_WORD.matcher(name).replaceAll(" ");
        if (DAIRY_CATEGORIES.contains(item.category())) {
            return !DAIRY_ALTERNATIVE.matcher(name).find() && !EGG.matcher(name).find();
        }
        return MILK_WORD.matcher(plainName).find();
    }

    private static boolean isPlantBased(CatalogItem item, String name) {
        return item.category().equals("plant-based") || PLANT_BASED.matcher(name).find();
    }
}
