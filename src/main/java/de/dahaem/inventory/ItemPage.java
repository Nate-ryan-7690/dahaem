package de.dahaem.inventory;

import java.util.List;

/** One page of inventory items plus how many items match in all pages. */
public record ItemPage(List<Item> items, long total) {
}
