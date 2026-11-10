package de.dahaem.api;

/** Body of POST /api/inventory/{id}/use: how much leaves stock (used or thrown away, it is the same). */
public record UseStockRequest(Integer quantity) {
}
