package de.dahaem.tags;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Thresholds for the derived recipe tags, from config/recipe_tags.properties (all per serving). */
public record TagSettings(int quickMaxMinutes, int lowCalorieMaxKcal, int highProteinMinGrams) {

    public static TagSettings defaults() {
        return new TagSettings(30, 600, 30);
    }

    /** Reads the properties file; a missing file or key falls back to the default. */
    public static TagSettings load(Path file) {
        TagSettings d = defaults();
        if (file == null || !Files.isRegularFile(file)) {
            return d;
        }
        Properties props = new Properties();
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            props.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Could not read " + file + ": " + e.getMessage(), e);
        }
        return new TagSettings(
                number(props, "quick.max.minutes", d.quickMaxMinutes()),
                number(props, "low-calorie.max.kcal", d.lowCalorieMaxKcal()),
                number(props, "high-protein.min.grams", d.highProteinMinGrams()));
    }

    private static int number(Properties props, String key, int fallback) {
        String value = props.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("recipe_tags.properties: " + key + " must be a whole number, got '"
                    + value + "'");
        }
    }
}
