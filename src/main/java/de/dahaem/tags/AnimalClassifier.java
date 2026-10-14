package de.dahaem.tags;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Proposes the animal(s) and the alcohol flag of a catalogue item from its name. Pure: no SQL, no I/O.
 *
 * <p>Three kinds of evidence, kept apart on purpose (surface, don't decide):
 * <ul>
 *   <li>the name SAYS the animal ("Hähnchenbrustfilet", "Rinderhackfleisch", "Entenbrust", "Lachsfilet") →
 *       certain animals, {@link Confidence#CONFIDENT};</li>
 *   <li>the animal is only implied and the product is made from one animal ("Kasseler", "Speck",
 *       "Schinken") → a suggestion of certain animals, always {@link Confidence#REVIEW};</li>
 *   <li>the product is generic and exists from different animals, the recipe does not say which
 *       ("Salami", "Fleischwurst", "Wiener", "Lyoner", "Leberwurst", "Chorizo", "Gelatine") → POSSIBLE
 *       animals, always REVIEW: they give no Tier tag, but they block vegetarisch and make a recipe
 *       "anpassbar" for a household that excludes one of them;</li>
 *   <li>no evidence at all → REVIEW with no proposal (and, for the fish categories, the animal
 *       the subcategory points at as the suggestion).</li>
 * </ul>
 * Nothing is stored without Nate. Alcohol is proposed the same way: a drinks subcategory that is alcoholic
 * is CONFIDENT, an alcohol word in the name of something else is REVIEW.
 */
public final class AnimalClassifier {

    public enum Confidence { CONFIDENT, REVIEW }

    /**
     * {@code animals} are the certain ones, {@code possibleAnimals} the ones the generic item may come from;
     * no animal is in both lists.
     */
    public record Proposal(List<String> animals, List<String> possibleAnimals, Confidence confidence) {
    }

    private static final String L = "(?<![a-zäöüß])";   // start of a word (compounds like "Bio-Rinder…" start after a hyphen)

    private static Pattern p(String regex) {
        return Pattern.compile(regex);
    }

    // Names that SAY the animal. Order = display order of Animals.ALL.
    private static final Pattern HAEHNCHEN = p("hähnchen|hänchen|hühn|huhn|chicken|poularde|hendl");
    private static final Pattern PUTE = p(L + "pute|truthahn|turkey");
    private static final Pattern GEFLUEGEL = p("geflügel|gefluegel");
    private static final Pattern ENTE = p(L + "ente(?:n|(?![a-zäöüß]))|(?:flug|barbarie|maulard|peking)ente");
    private static final Pattern GANS = p(L + "gans|gänse|gaense");
    private static final Pattern RIND = p(L + "rind(?!en)|beef|" + L + "ochsen");
    private static final Pattern SCHWEIN = p("schwein|pork|ferkel");
    private static final Pattern LAMM = p(L + "lamm|hammel");
    private static final Pattern KALB = p(L + "kalb");
    private static final Pattern WILD = p(L + "(?:wild(?!lachs|fang|reis|kräuter|knoblauch|beeren|heidel|honig)"
            + "|hirsch(?!horn)|fasan|reh(?:keule|braten|rücken|fleisch|ragout|gulasch|filet|medaillon"
            + "|geschnetzelt|steak)?(?![a-zäöüß]))");
    private static final Pattern KANINCHEN = p("kaninchen|karnickel");
    private static final Pattern PFERD = p("pferd|rossfleisch|rosswurst");
    private static final Pattern FISCH = p("fisch|lachs|forelle|kabeljau|dorsch|hecht|scholle|seezunge|heilbutt|barsch"
            + "|pangasius|tilapia|dorade|zander|karpfen|makrele|hering|sardine|sardelle|anchov|seeteufel|steinbeißer"
            + "|sprotte|matjes|rollmops|bückling|saibling|surimi|kaviar|" + L + "wels|" + L + "aal|aal(?![a-zäöüß])");
    private static final Pattern MEERESFRUECHTE = p("garnele|shrimp|scampi|krabbe|krebs|hummer|muschel|tintenfisch"
            + "|kalmar|calamari|oktopus|krake|auster|languste|sepia|meeresfrüchte|frutti di mare|kaisergranat");

    // Names that only IMPLY an animal: suggestions for review, never stored automatically.
    private record Hint(Pattern pattern, List<String> animals, String certainty) {
    }

    /** A product made from one animal: the suggestion is certain (once it has been reviewed). */
    private static Hint hint(String regex, String... animals) {
        return new Hint(p(regex), List.of(animals), Animals.CERTAIN);
    }

    /** A generic product that exists from several animals and the recipe does not say which. */
    private static Hint ambiguous(String regex, String... animals) {
        return new Hint(p(regex), List.of(animals), Animals.POSSIBLE);
    }

    private static final List<Hint> HINTS = List.of(
            hint("nduja|presskopf|schmalzfleisch|frühstücksfleisch|bacon|speck|pancetta|guanciale|kasseler|salsiccia"
                    + "|spareribs|rippchen|haxe|eisbein|krustenbraten|prosciutto|serrano|parmaschinken|schinken",
                    Animals.SCHWEIN),
            hint("weißwurst", Animals.KALB, Animals.SCHWEIN),
            hint("ribeye|sirloin|striploin|entrec[oô]te|rumpsteak|tafelspitz|tomahawk|con carne", Animals.RIND),
            ambiguous("salami|pepperoni|mortadella|lyoner|fleischwurst|jagdwurst|gelbwurst|leberwurst|leberkäse"
                    + "|leberkase|mettwurst|teewurst|cervelat|landjäger|chorizo|bratwurst|currywurst|grillwurst"
                    + "|knackwurst|blutwurst|rotwurst|bierwurst|käsekrainer|wiener(?! ?schnitzel)|frankfurter|bockwurst|bifi",
                    Animals.SCHWEIN, Animals.RIND, Animals.GEFLUEGEL),
            ambiguous("merguez", Animals.RIND, Animals.LAMM),
            ambiguous("sülze", Animals.SCHWEIN, Animals.RIND, Animals.GEFLUEGEL),
            ambiguous("gelatine", Animals.SCHWEIN, Animals.RIND),
            ambiguous("(?<!butter)schmalz", Animals.SCHWEIN, Animals.GANS),
            ambiguous("talg", Animals.RIND, Animals.LAMM),
            ambiguous("worcester", Animals.FISCH));

    private static final Pattern GEMISCHTES_HACK = p(L + "gemischtes?\\s+hack");

    private AnimalClassifier() {
    }

    /** Proposal for an item; call only for items that {@link #isAnimalCandidate} accepts. */
    public static Proposal propose(CatalogItem item) {
        String name = normalise(item.name());
        Set<String> found = explicit(name);
        if (!found.isEmpty() && isMeatOrFish(item)) {
            return new Proposal(Animals.sorted(found), List.of(), Confidence.CONFIDENT);
        }
        // An item outside the meat / fish categories (a stock, a sauce) that names an animal is not proposed:
        // only the generic products below are.
        Set<String> implied = new LinkedHashSet<>();
        Set<String> possible = new LinkedHashSet<>();
        for (Hint hint : HINTS) {
            if (hint.pattern().matcher(name).find()) {
                (hint.certainty().equals(Animals.POSSIBLE) ? possible : implied).addAll(hint.animals());
            }
        }
        if (implied.isEmpty() && possible.isEmpty() && isMeatOrFish(item)) {
            implied.addAll(fromCategory(item));
        }
        possible.removeAll(implied);
        return new Proposal(Animals.sorted(implied), Animals.sorted(possible), Confidence.REVIEW);
    }

    /**
     * True for the items the animal tool proposes something for: every meat / fish item, and any other item
     * whose name is a generic animal product (Gelatine, Schmalz, Worcestersauce ...). A plant-based product or
     * a seasoning blend named after the dish is never a candidate outside the meat categories.
     */
    public static boolean isAnimalCandidate(CatalogItem item) {
        if (isMeatOrFish(item)) {
            return true;
        }
        String name = normalise(item.name());
        if (item.category().equals("plant-based") || PLANT_BASED.matcher(name).find() || name.contains("gewürz")) {
            return false;
        }
        for (Hint hint : HINTS) {
            if (hint.pattern().matcher(name).find()) {
                return true;
            }
        }
        return false;
    }

    /** Where meat / fish live in the catalogue: whole categories, plus single subcategories of other categories. */
    public static boolean isMeatOrFish(CatalogItem item) {
        return MEAT_CATEGORIES.contains(item.category())
                || MEAT_SUBCATEGORIES.contains(item.category() + "/" + item.subcategory());
    }

    private static final Set<String> MEAT_CATEGORIES = Set.of("meat", "meat & poultry", "fish & seafood");
    private static final Set<String> MEAT_SUBCATEGORIES = Set.of("canned & jarred goods/canned meat",
            "canned & jarred goods/canned fish", "snacks/jerky & meat snacks", "frozen/fish", "frozen/poultry products");
    private static final Pattern PLANT_BASED = p("vegan|veggie|vegetar|pflanzlich|tofu|seitan|tempeh");

    /** Animals the name itself says, in canonical order; empty when the name does not say one. */
    public static Set<String> explicit(String normalisedName) {
        // "Lachsschinken" is pork, "Tintenfisch" is not a fish, "Muschelnudeln" are pasta, "Austernpilz" a mushroom: settle those first.
        String name = normalisedName.replace("lachsschinken", "schinken").replace("tintenfisch", "meeresfrüchte")
                .replace("muschelnudeln", "nudeln").replace("austernpilz", "pilz");
        Set<String> found = new LinkedHashSet<>();
        if (GEMISCHTES_HACK.matcher(name).find()) {
            found.add(Animals.RIND);
            found.add(Animals.SCHWEIN);
        }
        add(found, HAEHNCHEN, name, Animals.HAEHNCHEN);
        add(found, PUTE, name, Animals.PUTE);
        add(found, GEFLUEGEL, name, Animals.GEFLUEGEL);
        add(found, ENTE, name, Animals.ENTE);
        add(found, GANS, name, Animals.GANS);
        add(found, RIND, name, Animals.RIND);
        add(found, SCHWEIN, name, Animals.SCHWEIN);
        add(found, LAMM, name, Animals.LAMM);
        add(found, KALB, name, Animals.KALB);
        add(found, WILD, name, Animals.WILD);
        add(found, KANINCHEN, name, Animals.KANINCHEN);
        add(found, PFERD, name, Animals.PFERD);
        add(found, FISCH, name, Animals.FISCH);
        add(found, MEERESFRUECHTE, name, Animals.MEERESFRUECHTE);
        if (name.contains("wildschwein")) {
            found.remove(Animals.SCHWEIN);   // wild boar is Wild
        }
        return found;
    }

    // ---------- alcohol ----------

    /** Drinks subcategories that are alcoholic; the name can still say it is not ("Alkoholfreies Bier"). */
    private static final Set<String> ALCOHOLIC_SUBCATEGORIES = Set.of("beer", "wine", "sparkling wine", "spirits",
            "liqueur", "cider", "mixed drinks");
    private static final Pattern ALCOHOL_FREE = p("alkoholfrei|alkoholarm|kinder|0[,.]0\\s?%|ohne alkohol|malzbier|ginger ale");
    // Alcohol words in the name of something that is not a drink. Vinegars, spice blends, aromas, grapes and
    // wine stone are no alcohol; a name that matches here is only ever REVIEW, except cooking wine.
    private static final Pattern ALCOHOL_NAME = p("kochwein|(?<![a-zäöüß])(?:rot|weiß|weiss|rosé|rose|reis|schaum|süß|dessert)?wein"
            + "(?!essig|stein|traube|blatt|gewürz|berg|gummi)|(?<![a-zäöüß])bier(?!schinken|wurst|essig|\\s?erdn)"
            + "|likör|liqueur|schnaps|sherry|portwein|marsala|cognac|weinbrand|brandy|wodka|vodka|whisk(?:e)?y"
            + "|(?<![a-zäöüß])gin(?![a-zäöüß])|(?<![a-zäöüß])rum(?![a-zäöüß]|\\s?-?\\s?(?:aroma|extrakt))|amaretto"
            + "|(?<![a-zäöüß])sekt|prosecco|champagner|(?<![a-zäöüß])sake(?![a-zäöüß])|mirin|kirschwasser|grappa|ouzo"
            + "|tequila|calvados|wermut|aperol|campari|(?<![a-zäöüß])cid(?:er|re)|glühwein|(?<![a-zäöüß])korn(?:brand)?(?![a-zäöüß])");
    private static final Pattern COOKING_WINE = p("kochwein");

    /**
     * Alcohol proposal for an item, empty when none: an alcoholic drinks subcategory is CONFIDENT (unless the name
     * says alcohol-free), an alcohol word in the name of anything else is REVIEW, cooking wine is CONFIDENT.
     */
    public static Optional<Confidence> proposeAlcohol(CatalogItem item) {
        String name = normalise(item.name());
        if (ALCOHOL_FREE.matcher(name).find()) {
            return Optional.empty();
        }
        if (item.category().equals("beverages") && ALCOHOLIC_SUBCATEGORIES.contains(item.subcategory())) {
            return Optional.of(Confidence.CONFIDENT);
        }
        if (name.contains("gewürz") || name.contains("aroma")) {
            return Optional.empty();
        }
        if (ALCOHOL_NAME.matcher(name).find()) {
            return Optional.of(COOKING_WINE.matcher(name).find() ? Confidence.CONFIDENT : Confidence.REVIEW);
        }
        return Optional.empty();
    }

    /** True if the name says or implies an animal (used to keep such items out of vegetarian recipes). */
    public static boolean mentionsAnimal(String name) {
        String n = normalise(name);
        if (!explicit(n).isEmpty()) {
            return true;
        }
        for (Hint hint : HINTS) {
            if (hint.pattern().matcher(n).find()) {
                return true;
            }
        }
        return false;
    }

    private static List<String> fromCategory(CatalogItem item) {
        List<String> out = new ArrayList<>();
        switch (item.subcategory()) {
            case "fresh fish", "frozen fish", "smoked fish", "canned fish", "prepared & marinated", "fish" ->
                    out.add(Animals.FISCH);
            case "shellfish & seafood" -> out.add(Animals.MEERESFRUECHTE);
            default -> {
            }
        }
        return out;
    }

    private static void add(Set<String> into, Pattern pattern, String name, String animal) {
        if (pattern.matcher(name).find()) {
            into.add(animal);
        }
    }

    static String normalise(String name) {
        return name == null ? "" : name.toLowerCase(Locale.GERMAN).trim();
    }
}
