package de.dahaem.diet;

import java.util.List;

/**
 * One diet preset from config/diet_presets.json: tags a recipe must NOT have ({@code excludeTags}) and tags it
 * MUST have ({@code requireTags}). A preset filters INGREDIENTS only; {@code note} says what it cannot know.
 */
public record DietPreset(String name, String label, List<String> excludeTags, List<String> requireTags, String note) {
}
