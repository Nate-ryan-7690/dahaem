package de.dahaem.api;

import java.util.List;

/**
 * One page of {@code GET /api/recipes}. {@code items} are the cards of this page, in the requested sort order;
 * {@code total} is the number of recipes that match in ALL pages (after search, filters and diet rules);
 * {@code page} (from 1) and {@code pageSize} echo the request with its defaults applied; {@code totalPages} is
 * {@code ceil(total / pageSize)}, 0 when nothing matches. A page past the end has empty {@code items}.
 */
public record RecipePageDto(List<RecipeCardDto> items, long total, int page, int pageSize, int totalPages) {
}
