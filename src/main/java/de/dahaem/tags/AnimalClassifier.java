package de.dahaem.tags;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Proposes the animal(s) of a meat / fish catalogue item from its name. Pure: no SQL, no I/O.
 *
 * <p>Two kinds of evidence, kept apart on purpose (surface, don't decide):
 * <ul>
 *   <li>the name SAYS the animal ("Hähnchenbrustfilet", "Rinderhackfleisch", "Lachsfilet") →
 *       {@link Confidence#CONFIDENT};</li>
 *   <li>the animal is only implied ("Salami", "Fleischwurst", "Kasseler", "Speck") → a suggestion,
 *       always {@link Confidence#REVIEW}; nothing is stored without Nate;</li>
 *   <li>no evidence at all → REVIEW with no proposal (and, for the fish categories, the animal
 *       the subcategory points at as the suggestion).</li>
 * </ul>
 */
public final class AnimalClassifier {

    public enum Confidence { CONFIDENT, REVIEW }

    public record Proposal(List<String> animals, Confidence confidence) {
    }

    private static final String L = "(?<![a-zäöüß])";   // start of a word (compounds like "Bio-Rinder…" start after a hyphen)

    private static Pattern p(String regex) {
        return Pattern.compile(regex);
    }

    // Names that SAY the animal. Order = display order of Animals.ALL.
    private static final Pattern HAEHNCHEN = p("hähnchen|hänchen|hühn|huhn|chicken|poularde|hendl");
    private static final Pattern PUTE = p(L + "pute|truthahn|turkey");
    private static final Pattern RIND = p(L + "rind(?!en)|beef|" + L + "ochsen");
    private static final Pattern SCHWEIN = p("schwein|pork|ferkel");
    private static final Pattern LAMM = p(L + "lamm|hammel");
    private static final Pattern KALB = p(L + "kalb");
    private static final Pattern WILD = p(L + "(?:wild(?!lachs|fang|reis|kräuter|knoblauch|beeren|heidel|honig)"
            + "|hirsch(?!horn)|fasan|reh(?:keule|braten|rücken|fleisch|ragout|gulasch|filet|medaillon"
            + "|geschnetzelt|steak)?(?![a-zäöüß]))");
    private static final Pattern FISCH = p("fisch|lachs|forelle|kabeljau|dorsch|hecht|scholle|seezunge|heilbutt|barsch"
            + "|pangasius|tilapia|dorade|zander|karpfen|makrele|hering|sardine|sardelle|anchov|seeteufel|steinbeißer"
            + "|sprotte|matjes|rollmops|bückling|saibling|surimi|kaviar|" + L + "wels|" + L + "aal|aal(?![a-zäöüß])");
    private static final Pattern MEERESFRUECHTE = p("garnele|shrimp|scampi|krabbe|krebs|hummer|muschel|tintenfisch"
            + "|kalmar|calamari|oktopus|krake|auster|languste|sepia|meeresfrüchte|frutti di mare|kaisergranat");

    // Names that only IMPLY an animal: suggestion for review, never stored automatically.
    private record Hint(Pattern pattern, List<String> animals) {
    }

    private static Hint hint(String regex, String... animals) {
        return new Hint(p(regex), List.of(animals));
    }

    private static final List<Hint> HINTS = List.of(
            hint("salami|pepperoni|mortadella|lyoner|fleischwurst|jagdwurst|gelbwurst|leberwurst|leberkäse|leberkase"
                    + "|mettwurst|teewurst|cervelat|landjäger|chorizo|bratwurst|currywurst|grillwurst|knackwurst"
                    + "|blutwurst|rotwurst|käsekrainer|schinken|speck|bacon|pancetta|guanciale|kasseler|salsiccia"
                    + "|nduja|spareribs|rippchen|presskopf|sülze|frühstücksfleisch|schmalzfleisch|haxe|eisbein"
                    + "|krustenbraten|bierwurst|prosciutto|serrano|parmaschinken", Animals.SCHWEIN),
            hint("wiener|frankfurter|bockwurst|bifi", Animals.RIND, Animals.SCHWEIN),
            hint("weißwurst", Animals.KALB, Animals.SCHWEIN),
            hint("merguez", Animals.RIND, Animals.LAMM),
            hint("ribeye|sirloin|striploin|entrec[oô]te|rumpsteak|tafelspitz|tomahawk|con carne", Animals.RIND),
            hint("geflügel", Animals.HAEHNCHEN, Animals.PUTE));

    private static final Pattern GEMISCHTES_HACK = p(L + "gemischtes?\\s+hack");

    private AnimalClassifier() {
    }

    /** Proposal for an item; call only for meat / fish catalogue items. */
    public static Proposal propose(CatalogItem item) {
        String name = normalise(item.name());
        Set<String> found = explicit(name);
        if (!found.isEmpty()) {
            return new Proposal(Animals.sorted(found), Confidence.CONFIDENT);
        }
        Set<String> implied = new LinkedHashSet<>();
        for (Hint hint : HINTS) {
            if (hint.pattern().matcher(name).find()) {
                implied.addAll(hint.animals());
            }
        }
        if (implied.isEmpty()) {
            implied.addAll(fromCategory(item));
        }
        return new Proposal(Animals.sorted(implied), Confidence.REVIEW);
    }

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
        add(found, RIND, name, Animals.RIND);
        add(found, SCHWEIN, name, Animals.SCHWEIN);
        add(found, LAMM, name, Animals.LAMM);
        add(found, KALB, name, Animals.KALB);
        add(found, WILD, name, Animals.WILD);
        add(found, FISCH, name, Animals.FISCH);
        add(found, MEERESFRUECHTE, name, Animals.MEERESFRUECHTE);
        if (name.contains("wildschwein")) {
            found.remove(Animals.SCHWEIN);   // wild boar is Wild
        }
        return found;
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
