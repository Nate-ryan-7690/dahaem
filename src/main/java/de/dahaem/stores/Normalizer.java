package de.dahaem.stores;

import java.util.regex.Pattern;

/**
 * Turns a German product or ingredient name into a comparable key: lowercase,
 * umlauts to ae/oe/ue/ss, allergen markers and leading ALL-CAPS brand words
 * removed, punctuation to spaces. Same idea as the recipe importer's normalize,
 * kept here so store matching and (later) recipe matching can converge in Java.
 */
public final class Normalizer {

    private static final Pattern ALLERGEN = Pattern.compile("\\d+\\)");
    private static final Pattern ORIGIN = Pattern.compile("\\b[A-Z]{2}\\b(\\s*\\|\\s*[A-Z]{2}\\b)*");
    private static final Pattern NON_WORD = Pattern.compile("[^a-z0-9 ]");
    private static final Pattern HYPHEN_BETWEEN_LETTERS = Pattern.compile("(?<=\\p{L})-(?=\\p{L})");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private Normalizer() {
    }

    /** Full normalize, brand words (leading ALL-CAPS tokens) stripped. */
    public static String key(String raw) {
        if (raw == null) {
            return "";
        }
        String s = stripBrand(dropInvisible(raw));
        s = ALLERGEN.matcher(s).replaceAll(" ");
        s = ORIGIN.matcher(s).replaceAll(" ");
        s = s.replace("**", " ");
        s = ascii(s.toLowerCase());
        s = NON_WORD.matcher(s).replaceAll(" ");
        return SPACES.matcher(s).replaceAll(" ").strip();
    }

    /**
     * Like {@link #key}, but a hyphen between two letters is removed instead of becoming a space, so
     * "Hähnchen-Brustfilets" gives "haehnchenbrustfilets" (one word) rather than "haehnchen brustfilets".
     */
    public static String joinedKey(String raw) {
        if (raw == null) {
            return "";
        }
        return key(HYPHEN_BETWEEN_LETTERS.matcher(dropInvisible(raw)).replaceAll(""));
    }

    /** Soft hyphens (U+00AD) and zero-width spaces are layout marks that some sites put inside words ("Walnuss­kerne"). */
    private static String dropInvisible(String raw) {
        return raw.replace("\u00ad", "").replace("\u200b", "");
    }

    /** Drops leading ALL-CAPS brand words ("MILSANI Streichfein" -> "Streichfein"); keeps all if every word is caps. */
    static String stripBrand(String title) {
        String[] words = title.split("\\s+");
        int i = 0;
        while (i < words.length - 1 && isBrandWord(words[i])) {
            i++;
        }
        return String.join(" ", java.util.Arrays.copyOfRange(words, i, words.length));
    }

    private static boolean isBrandWord(String word) {
        boolean hasLetter = word.chars().anyMatch(Character::isLetter);
        return word.length() >= 2 && hasLetter && word.equals(word.toUpperCase(java.util.Locale.GERMAN));
    }

    private static String ascii(String s) {
        return s.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss");
    }
}
