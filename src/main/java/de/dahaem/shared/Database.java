package de.dahaem.shared;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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
        // SQLite does not enforce foreign keys unless you switch them on per connection.
        connection.createStatement().execute("PRAGMA foreign_keys = ON");
        return connection;
    }
}
