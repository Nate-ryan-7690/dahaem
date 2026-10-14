package de.dahaem.diet;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The diet presets, read from config/diet_presets.json (no hardcoding: the lists are Nate's to change).
 * Format: {"presets": {"<name>": {"label": "...", "excludeTags": [...], "requireTags": [...], "note": "..."}}};
 * keys starting with an underscore are comments. A malformed file stops the start with a clear message.
 */
public final class DietPresets {

    public static final Path DEFAULT_FILE = Path.of("config", "diet_presets.json");

    private final Map<String, DietPreset> presets;

    public DietPresets(Map<String, DietPreset> presets) {
        this.presets = new LinkedHashMap<>(presets);
    }

    public static DietPresets load(Path file) {
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("Diet presets not found: " + file + " (config/diet_presets.json)");
        }
        try {
            JsonNode root = new ObjectMapper().readTree(Files.readString(file, java.nio.charset.StandardCharsets.UTF_8));
            JsonNode node = root.get("presets");
            if (node == null || !node.isObject()) {
                throw new IllegalStateException(file + ": missing the \"presets\" object");
            }
            Map<String, DietPreset> out = new LinkedHashMap<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = node.fields(); it.hasNext(); ) {
                Map.Entry<String, JsonNode> e = it.next();
                if (e.getKey().startsWith("_")) {
                    continue;
                }
                JsonNode p = e.getValue();
                out.put(e.getKey(), new DietPreset(e.getKey(), text(p, "label", e.getKey()),
                        strings(p, "excludeTags", file), strings(p, "requireTags", file), text(p, "note", "")));
            }
            return new DietPresets(out);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + file + ": " + e.getMessage(), e);
        }
    }

    public Optional<DietPreset> find(String name) {
        return Optional.ofNullable(presets.get(name));
    }

    public Set<String> names() {
        return presets.keySet();
    }

    public List<DietPreset> all() {
        return List.copyOf(presets.values());
    }

    private static String text(JsonNode node, String field, String fallback) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? fallback : v.asText();
    }

    private static List<String> strings(JsonNode node, String field, Path file) {
        JsonNode v = node.get(field);
        List<String> out = new ArrayList<>();
        if (v == null || v.isNull()) {
            return out;
        }
        if (!v.isArray()) {
            throw new IllegalStateException(file + ": \"" + field + "\" must be a list of tag names");
        }
        v.forEach(x -> out.add(x.asText()));
        return out;
    }
}
