package de.dahaem.shared;

/** Builds LIKE patterns from user text, so a typed % or _ is searched for literally. */
public final class LikePattern {

    private LikePattern() {
    }

    /** {@code %text%} with the LIKE wildcards (and the escape character) in {@code text} escaped. */
    public static String contains(String text) {
        return "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }
}
