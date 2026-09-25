package de.dahaem.shared;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Single place that knows how to open a database connection.
 * Every repository asks Database for a connection; nobody else builds the URL.
 * When you move to a web app, only this class (and the repositories) change.
 */
public final class Database {

    // Path is relative to the project root (the working directory when run from IntelliJ).
    private static final String DEFAULT_DB_PATH = "data/kitchen_organizer.db";
    // System property so tests can point at a temporary file instead of the real database.
    private static final String DB_PATH_PROPERTY = "dahaem.db.path";

    private Database() {
        // Utility class, no instances.
    }

    public static Connection getConnection() throws SQLException {
        String path = System.getProperty(DB_PATH_PROPERTY, DEFAULT_DB_PATH);
        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        // Per-connection PRAGMAs. Close the Statement (try-with-resources) so we don't leak one
        // handle per connection across the app. foreign_keys: SQLite enforces FKs only when switched
        // on. busy_timeout: wait up to 5s for a lock instead of failing immediately with SQLITE_BUSY
        // when writers overlap (e.g. a web request racing a cron-triggered import).
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }
}
