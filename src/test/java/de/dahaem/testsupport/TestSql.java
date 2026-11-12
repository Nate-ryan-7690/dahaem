package de.dahaem.testsupport;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Three one-liners every database test needs, against the throwaway database of {@link TestDatabase}: run a statement, read
 * a number, read a text. Import them statically: {@code import static de.dahaem.testsupport.TestSql.exec;}.
 */
public final class TestSql {

    private TestSql() {
    }

    /** Runs one statement (an INSERT, UPDATE, DELETE or a script line). */
    public static void exec(String sql) throws SQLException {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement()) {
            st.execute(sql);
        }
    }

    /** The first column of the first row of a query, as a number. */
    public static int count(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** The first column of the first row of a query, as text (null for SQL NULL). */
    public static String text(String sql) throws SQLException {
        try (Connection con = Database.getConnection();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
