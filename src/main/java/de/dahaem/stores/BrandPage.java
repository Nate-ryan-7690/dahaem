package de.dahaem.stores;

import java.util.List;

/** One page of brands plus how many match in all pages. */
public record BrandPage(List<BrandRef> items, long total) {
}
