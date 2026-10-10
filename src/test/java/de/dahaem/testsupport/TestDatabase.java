package de.dahaem.testsupport;

import de.dahaem.shared.Database;
import org.testcontainers.containers.MariaDBContainer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * A throwaway MariaDB for the tests (Testcontainers), never the live database. One container is
 * started per test JVM; {@link #reset()} drops and recreates a scratch schema inside it, loads the
 * repo's {@code schema.sql} and points {@link Database} at it, so every test starts empty.
 */
public final class TestDatabase {

    private static final String SCRATCH_DB = "dahaem_test";
    private static final MariaDBContainer<?> CONTAINER = new MariaDBContainer<>("mariadb:11.4");

    static {
        CONTAINER.start();
    }

    private TestDatabase() {
    }

    /** Fresh, empty database containing the full schema; {@link Database} now points at it. */
    public static void reset() {
        reset(List.of(Path.of("schema.sql")));
    }

    /** Fresh database built from the given SQL scripts, in order. */
    public static void reset(List<Path> scripts) {
        try (Connection admin = DriverManager.getConnection(adminUrl(), "root", CONTAINER.getPassword());
             Statement st = admin.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + SCRATCH_DB);
            st.execute("CREATE DATABASE " + SCRATCH_DB + " CHARACTER SET utf8mb4");
            st.execute("USE " + SCRATCH_DB);
            for (Path script : scripts) {
                st.execute(Files.readString(script, StandardCharsets.UTF_8));
            }
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Could not build the test database", e);
        }
        Database.configure(CONTAINER.getJdbcUrl().replace("/test", "/" + SCRATCH_DB),
                "root", CONTAINER.getPassword());
    }

    /** Runs SQL scripts against the current scratch database as it is (no reset), e.g. a migration. */
    public static void apply(List<Path> scripts) {
        try (Connection admin = DriverManager.getConnection(adminUrl(), "root", CONTAINER.getPassword());
             Statement st = admin.createStatement()) {
            st.execute("USE " + SCRATCH_DB);
            for (Path script : scripts) {
                st.execute(Files.readString(script, StandardCharsets.UTF_8));
            }
        } catch (SQLException | IOException e) {
            throw new IllegalStateException("Could not apply " + scripts, e);
        }
    }

    /** Closes the pool; the container itself lives until the JVM exits. */
    public static void close() {
        Database.close();
    }

    private static String adminUrl() {
        return CONTAINER.getJdbcUrl() + "?allowMultiQueries=true";
    }
}
