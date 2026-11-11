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

    /** Gives purchases without a household (the demo data predates households) to this household. */
    public static void adoptOrphanPurchases(int householdId) {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE purchase_history SET purchase_history_household_id = ? WHERE purchase_history_household_id IS NULL")) {
            ps.setInt(1, householdId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The oldest household of the scratch database; one named "Test" is made when there is none yet. */
    public static int defaultHousehold() {
        try (Connection con = Database.getConnection(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT MIN(household_id) FROM household")) {
            rs.next();
            int id = rs.getInt(1);
            return rs.wasNull() ? createHousehold("Test", null) : id;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Copies the stock that a test wrote straight into the legacy columns of inventory_item (quantity and earliest expiry
     * date) to this household's stock, the way migration 0024 does. Call it after seeding, before the code under test.
     */
    public static void seedStockOf(int householdId) {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO household_stock (household_stock_household_id, household_stock_item_id, "
                             + "household_stock_quantity, household_stock_earliest_exp_date) "
                             + "SELECT ?, inventory_item_id, inventory_item_quantity, inventory_item_earliest_exp_date "
                             + "FROM inventory_item WHERE inventory_item_quantity <> 0 OR inventory_item_earliest_exp_date IS NOT NULL "
                             + "ON DUPLICATE KEY UPDATE household_stock_quantity = VALUES(household_stock_quantity), "
                             + "household_stock_earliest_exp_date = VALUES(household_stock_earliest_exp_date)")) {
            ps.setInt(1, householdId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    /** One household's stock of an item, written directly: {quantity, earliest expiry (text) or null}. */
    public static void setStock(int householdId, int itemId, int quantity, String earliestExpDate) {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO household_stock (household_stock_household_id, household_stock_item_id, "
                             + "household_stock_quantity, household_stock_earliest_exp_date) VALUES (?, ?, ?, ?) "
                             + "ON DUPLICATE KEY UPDATE household_stock_quantity = VALUES(household_stock_quantity), "
                             + "household_stock_earliest_exp_date = VALUES(household_stock_earliest_exp_date)")) {
            ps.setInt(1, householdId);
            ps.setInt(2, itemId);
            ps.setInt(3, quantity);
            ps.setString(4, earliestExpDate);
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
