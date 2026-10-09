package de.dahaem.shared;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Single place that knows how to open a database connection.
 * Every repository asks Database for a connection; nobody else builds the URL.
 *
 * <p>The database is MariaDB, reached through a HikariCP pool. Settings come from the
 * environment so no secret lives in the repo:
 * <ul>
 *   <li>{@code DAHAEM_DB_URL} e.g. {@code jdbc:mariadb://db:3306/dahaem}</li>
 *   <li>{@code DAHAEM_DB_USER}</li>
 *   <li>{@code DAHAEM_DB_PASSWORD}</li>
 * </ul>
 * Callers keep using {@link #getConnection()} and close the connection as before; closing
 * returns it to the pool.
 */
public final class Database {

    static final String URL_ENV = "DAHAEM_DB_URL";
    static final String USER_ENV = "DAHAEM_DB_USER";
    static final String PASSWORD_ENV = "DAHAEM_DB_PASSWORD";

    private static HikariDataSource dataSource;

    private Database() {
        // Utility class, no instances.
    }

    public static Connection getConnection() throws SQLException {
        return pool().getConnection();
    }

    /**
     * Points the pool at a different database, closing the previous pool. Used by tests to
     * target a throwaway MariaDB; production code reads the environment instead.
     */
    public static synchronized void configure(String url, String user, String password) {
        close();
        dataSource = new HikariDataSource(hikariConfig(url, user, password));
    }

    /** Closes the pool. The next {@link #getConnection()} reconnects using the environment. */
    public static synchronized void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    private static synchronized HikariDataSource pool() {
        if (dataSource == null) {
            dataSource = new HikariDataSource(hikariConfig(
                    required(URL_ENV), required(USER_ENV), required(PASSWORD_ENV)));
        }
        return dataSource;
    }

    private static HikariConfig hikariConfig(String url, String user, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setPoolName("dahaem");
        config.setMaximumPoolSize(10);
        return config;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Environment variable " + name + " is not set");
        }
        return value;
    }
}
