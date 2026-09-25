package de.dahaem.api;

/** Body of POST /api/recipes/{id}/tags. {@code category} is only used when the tag does not exist yet. */
public record AddTagRequest(String name, String category) {
}
