package de.dahaem.api;

import de.dahaem.tags.Tag;

public record TagDto(int id, String name, String category) {

    static TagDto from(Tag tag) {
        return new TagDto(tag.getId(), tag.getName(), tag.getCategory());
    }
}
