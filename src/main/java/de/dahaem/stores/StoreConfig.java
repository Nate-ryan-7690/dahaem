package de.dahaem.stores;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;

/**
 * Settings for the store importers, read from config/stores.properties
 * (relative to the working directory, like the database path).
 * The file is optional; missing keys fall back to defaults.
 *
 * Later the Penny store should come from a "current location" setting in the
 * app (home vs. vacation), not from this file. See HANDOFF.md.
 */
public final class StoreConfig {

    private static final String CONFIG_PATH = "config/stores.properties";

    private final Properties properties = new Properties();

    public StoreConfig() {
        Path path = Path.of(CONFIG_PATH);
        if (Files.exists(path)) {
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                properties.load(reader);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not read " + CONFIG_PATH, e);
            }
        }
    }

    /** Penny store id ("wwIdent"); its price region decides which offers you see. */
    public String pennyMarketId() {
        return properties.getProperty("penny.market", "").trim();
    }

    /** Postal code for regional aggregator chains (famila): aktionspreis.de gates their offers behind a PLZ. */
    public String aggregatorPlz() {
        return properties.getProperty("aggregator.plz", "").trim();
    }

    /** Pause after every web request, so we never hammer a store's server. */
    public Duration requestDelay() {
        return Duration.ofMillis(Long.parseLong(properties.getProperty("http.delay.ms", "1000").trim()));
    }
}
