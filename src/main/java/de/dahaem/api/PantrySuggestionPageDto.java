package de.dahaem.api;

import java.util.List;

/**
 * One page of {@code GET /api/suggestions/pantry}. {@code items} are the suggested recipes of this page in the
 * requested order; {@code total} is the number of recipes that fit in ALL pages; {@code page} (from 1) and
 * {@code pageSize} echo the request with its defaults applied; {@code totalPages} is {@code ceil(total / pageSize)},
 * 0 when nothing fits. A page past the end has empty {@code items}.
 */
public record PantrySuggestionPageDto(List<PantrySuggestionDto> items, long total, int page, int pageSize,
                                      int totalPages) {
}
