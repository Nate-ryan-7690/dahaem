package de.dahaem.receipts;

import de.dahaem.stores.Normalizer;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Receipt-specific name handling, used only by the receipt import (offer matching keeps
 * {@link Normalizer} as it is). PENNY prints most names in ALL CAPS and without a brand
 * ("ESL MILCH 3,5%"), which {@link Normalizer} would read as brand words and strip, so ALL-CAPS
 * words are written in normal case first. Known PENNY abbreviations are expanded ("Hä." ->
 * Hähnchen), and a brand is taken only from a known prefix ("LH" = Lindenhof), never guessed.
 */
public final class ReceiptNames {

    private static final Pattern LETTERS = Pattern.compile("\\p{L}+");
    private static final Pattern CAPS_SYMBOL_WORD = Pattern.compile("(?<!\\S)\\p{Lu}(?:[&+/]\\p{Lu})+(?!\\S)");

    /** Applied in order, case-insensitive, after the CAPS words are in normal case. */
    private static final Map<Pattern, String> ABBREVIATIONS = new LinkedHashMap<>();

    /** Known brand prefixes (lowercase, as printed) -> brand name. Longest prefix first. */
    private static final Map<String, String> BRANDS = new LinkedHashMap<>();

    static {
        abbreviation("gem\\.", "gemischt");                    // Hackfleisch gem.
        abbreviation("hä\\.", "Hähnchen");                     // Hä.-Brustfilet
        abbreviation("konfit(?!\\p{L})\\.?", "Konfitüre");     // Landliebe Konfit
        abbreviation("tom(?!\\p{L})\\.?", "Tomaten");          // SF Passierte Tom

        brand("ben & jerry's", "Ben & Jerry's");
        brand("lindenhof", "Lindenhof");
        brand("lh", "Lindenhof");
        brand("milprima", "Milprima");
        brand("kerrygold", "Kerrygold");
        brand("landliebe", "Landliebe");
        brand("lay's", "Lay's");
        brand("lays", "Lay's");
        brand("naturgut", "Naturgut");
        brand("heinz", "Heinz");
        brand("corny", "Corny");
        brand("w&c", "W&C");
        brand("krombacher", "Krombacher");
        brand("krom", "Krombacher");
        brand("ferrero", "Ferrero");
    }

    private ReceiptNames() {
    }

    private static void abbreviation(String regex, String expansion) {
        ABBREVIATIONS.put(Pattern.compile("(?iu)(?<!\\p{L})" + regex), expansion);
    }

    private static void brand(String prefix, String name) {
        BRANDS.put(prefix, name);
    }

    /** "HACKFLEISCH GEM." -> "Hackfleisch gemischt", "Hä.-Brustfilet" -> "Hähnchen-Brustfilet". */
    public static String readable(String raw) {
        if (raw == null) {
            return "";
        }
        String s = normalCaseCapsWords(ReceiptText.apostrophes(raw).strip());
        for (Map.Entry<Pattern, String> a : ABBREVIATIONS.entrySet()) {
            s = a.getKey().matcher(s).replaceAll(Matcher.quoteReplacement(a.getValue()));
        }
        return s;
    }

    /** The comparable key of a receipt name, as used in config/receipt_overrides.json. */
    public static String key(String raw) {
        return Normalizer.key(readable(raw));
    }

    /** The brand of a known prefix ("LH FRISCHK.NATUR" -> "Lindenhof"), else null. */
    public static String brand(String raw) {
        String prefix = brandPrefix(raw);
        return prefix == null ? null : BRANDS.get(prefix);
    }

    /** The name without its known brand prefix ("LH FRISCHK.NATUR" -> "FRISCHK.NATUR"); unchanged if none. */
    public static String withoutBrand(String raw) {
        String prefix = brandPrefix(raw);
        if (prefix == null) {
            return raw;
        }
        String rest = ReceiptText.apostrophes(raw).strip().substring(prefix.length()).replaceFirst("^[\\s.]+", "");
        return rest.isEmpty() ? raw : rest;
    }

    private static String brandPrefix(String raw) {
        if (raw == null) {
            return null;
        }
        String lower = ReceiptText.apostrophes(raw).strip().toLowerCase(java.util.Locale.GERMAN);
        for (String prefix : BRANDS.keySet()) {
            if (lower.startsWith(prefix)
                    && (lower.length() == prefix.length() || !Character.isLetterOrDigit(lower.charAt(prefix.length())))) {
                return prefix;
            }
        }
        return null;
    }

    /**
     * Every run of two or more letters that is ALL CAPS becomes "Xxxx"; mixed-case words stay as printed.
     * A word of single capitals joined by symbols ("W&C") keeps its first letter only, so it is not read
     * as a brand word either.
     */
    static String normalCaseCapsWords(String s) {
        Matcher symbols = CAPS_SYMBOL_WORD.matcher(s);
        StringBuilder joined = new StringBuilder();
        while (symbols.find()) {
            String word = symbols.group();
            symbols.appendReplacement(joined, Matcher.quoteReplacement(
                    word.charAt(0) + word.substring(1).toLowerCase(java.util.Locale.GERMAN)));
        }
        symbols.appendTail(joined);
        s = joined.toString();
        Matcher m = LETTERS.matcher(s);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String w = m.group();
            boolean caps = w.length() >= 2 && w.equals(w.toUpperCase(java.util.Locale.GERMAN))
                    && !w.equals(w.toLowerCase(java.util.Locale.GERMAN));
            String replacement = caps ? w.charAt(0) + w.substring(1).toLowerCase(java.util.Locale.GERMAN) : w;
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }
}
