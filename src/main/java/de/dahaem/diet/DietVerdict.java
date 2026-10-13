package de.dahaem.diet;

import java.util.List;

/**
 * The filter's answer for one recipe. {@code hints} (ANPASSBAR, and UNCLEAR when it applies too): per item that
 * may come from an excluded animal, the animals it may still be. {@code soft}: what a soft rule matched, null when
 * nothing (or no soft rule).
 */
public record DietVerdict(DietStatus status, List<Hint> hints, SoftHits soft) {

    /** An item with possible animals of which at least one is excluded; {@code allowedAnimals} is what remains. */
    public record Hint(int itemId, String name, List<String> allowedAnimals, List<String> possibleAnimals) {
    }

    /** A matched item (id and name). */
    public record Item(int itemId, String name) {
    }

    /**
     * Soft-rule hits: tags the recipe has, ingredient items it has, and items that MAY be an avoided animal
     * (for an avoided animal tag).
     */
    public record SoftHits(List<String> tags, List<Item> items, List<Hint> possibleItems) {

        public boolean isEmpty() {
            return tags.isEmpty() && items.isEmpty() && possibleItems.isEmpty();
        }
    }

    public boolean hasSoftHits() {
        return soft != null && !soft.isEmpty();
    }
}
