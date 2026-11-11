package de.dahaem.testsupport;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Households and users for tests, made with plain SQL (no login is needed, so the password hash is a dummy). Also holds
 * the user the MockMvc tests act as ({@link ActingUserTestConfig} sends it as X-Acting-User).
 */
public final class TestHousehold {

    private static volatile int actingUserId;

    private TestHousehold() {
    }

    public static int createHousehold(String name, Integer people) {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO household (household_name, household_people, created_date) VALUES (?, ?, '2026-11-01')",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setObject(2, people);
            ps.executeUpdate();
            return key(ps);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A user (role ADMIN or MEMBER) in the household; returns the user id. */
    public static int createUser(int householdId, String email, String role) {
        try (Connection con = Database.getConnection()) {
            int userId;
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO app_user (user_email, user_display_name, user_password_hash, user_created_date) "
                            + "VALUES (?, ?, 'not-a-real-hash', '2026-11-01')", Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, email);
                ps.setString(2, email.substring(0, email.indexOf('@')));
                ps.executeUpdate();
                userId = key(ps);
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO household_member (household_id, user_id, role) VALUES (?, ?, ?)")) {
                ps.setInt(1, householdId);
                ps.setInt(2, userId);
                ps.setString(3, role);
                ps.executeUpdate();
            }
            return userId;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A user that belongs to no household; returns the user id. */
    public static int createUserWithoutHousehold(String email) {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO app_user (user_email, user_display_name, user_password_hash, user_created_date) "
                             + "VALUES (?, 'Nobody', 'not-a-real-hash', '2026-11-01')", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.executeUpdate();
            return key(ps);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A household with one ADMIN user; the user becomes the one the MockMvc tests act as. Returns the household id. */
    public static int createAndActAs(String name, Integer people) {
        int household = createHousehold(name, people);
        actAs(createUser(household, name.toLowerCase().replace(' ', '.') + "@example.test", "ADMIN"));
        return household;
    }

    /** Gives plans without a household (the demo data predates households) to this household. */
    public static void adoptOrphanPlans(int householdId) {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE meal_plan SET meal_plan_household_id = ? WHERE meal_plan_household_id IS NULL")) {
            ps.setInt(1, householdId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    public static void actAs(int userId) {
        actingUserId = userId;
    }

    public static int actingUserId() {
        return actingUserId;
    }

    private static int key(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            keys.next();
            return keys.getInt(1);
        }
    }
}
