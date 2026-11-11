package de.dahaem.api;

import de.dahaem.stores.BrandRef;

import java.util.List;

/**
 * One page of {@code GET /api/brands}. {@code total} is the number of brands that match in ALL pages; {@code page} (from
 * 1) and {@code pageSize} echo the request with its defaults applied; {@code totalPages} is {@code ceil(total / pageSize)},
 * 0 when nothing matches.
 */
public record BrandPageDto(List<BrandRef> items, long total, int page, int pageSize, int totalPages) {
}
