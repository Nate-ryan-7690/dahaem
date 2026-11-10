package de.dahaem.api;

import java.util.List;

/**
 * One page of {@code GET /api/inventory/search}. {@code total} is the number of items that match in ALL pages;
 * {@code page} (from 1) and {@code pageSize} echo the request with its defaults applied; {@code totalPages} is
 * {@code ceil(total / pageSize)}, 0 when nothing matches. A page past the end has empty {@code items}.
 */
public record ItemPageDto(List<ItemDto> items, long total, int page, int pageSize, int totalPages) {
}
