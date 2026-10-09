package de.dahaem.auth;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Optional;

/**
 * All SQL for the household, app_user and household_member tables lives here.
 * The rest of the app never writes SQL against the database; it calls these methods.
 *
 * <p>The insert methods run on a caller-supplied connection (see {@link #openTransaction()}) so the
 * service can create a household, its first user and the membership linking them atomically.
 */
public class AccountRepository {

    /** Opens a connection with auto-commit off, for a caller that needs to combine several writes atomically. */
    public Connection openTransaction() {
        try {
            Connection con = Database.getConnection();
            con.setAutoCommit(false);
            return con;
        } catch (SQLException e) {
            throw new RuntimeException("Could not open transaction", e);
        }
    }

    /** Inserts a household row, on the caller's transaction. Returns it with its generated id. */
    public Household insertHousehold(Connection con, String name, LocalDate createdDate) throws SQLException {
        String sql = "INSERT INTO household (household_name, created_date) VALUES (?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setObject(2, createdDate);
            ps.executeUpdate();
            return new Household(generatedId(ps), name, createdDate);
        }
    }

    /**
     * Inserts an app_user row, on the caller's transaction. Returns it with its generated id.
     * user_email is UNIQUE, so a second user with the same email fails with a SQLIntegrityConstraintViolationException.
     */
    public AppUser insertUser(Connection con, String email, String displayName, String passwordHash,
                              LocalDate createdDate) throws SQLException {
        String sql = "INSERT INTO app_user (user_email, user_display_name, user_password_hash, user_created_date) "
                + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, email);
            ps.setString(2, displayName);
            ps.setString(3, passwordHash);
            ps.setObject(4, createdDate);
            ps.executeUpdate();
            return new AppUser(generatedId(ps), email, displayName, passwordHash, createdDate);
        }
    }

    /**
     * Inserts a household_member row, on the caller's transaction. Returns its generated id.
     * (household_id, user_id) is UNIQUE, so adding the same user to the same household twice fails.
     */
    public int insertMember(Connection con, int householdId, int userId, HouseholdRole role) throws SQLException {
        String sql = "INSERT INTO household_member (household_id, user_id, role) VALUES (?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, householdId);
            ps.setInt(2, userId);
            ps.setString(3, role.name());
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    /** Finds the user with exactly this (already normalized) email. */
    public Optional<AppUser> findUserByEmail(String email) {
        String sql = "SELECT user_id, user_email, user_display_name, user_password_hash, user_created_date "
                + "FROM app_user WHERE user_email = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapUser(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not look up user " + email, e);
        }
    }

    /** Replaces a user's password hash. Returns false when no user has this id. */
    public boolean updatePasswordHash(int userId, String passwordHash) {
        String sql = "UPDATE app_user SET user_password_hash = ? WHERE user_id = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setInt(2, userId);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("Could not update password for user " + userId, e);
        }
    }

    /** The oldest household, i.e. the one the first user created. Empty on a fresh database. */
    public Optional<Household> findFirstHousehold() {
        String sql = "SELECT household_id, household_name, created_date FROM household "
                + "ORDER BY household_id LIMIT 1";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(mapHousehold(rs)) : Optional.empty();
        } catch (SQLException e) {
            throw new RuntimeException("Could not look up households", e);
        }
    }

    /**
     * The household a user belongs to, with their role in it. A user is in at most one household
     * for now (no multi-household switching yet); if there were several, the oldest membership wins.
     */
    public Optional<HouseholdMembership> findMembershipForUser(int userId) {
        String sql = "SELECT m.household_member_id, m.user_id, m.role, "
                + "h.household_id, h.household_name, h.created_date "
                + "FROM household_member m "
                + "INNER JOIN household h ON h.household_id = m.household_id "
                + "WHERE m.user_id = ? "
                + "ORDER BY m.household_member_id LIMIT 1";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new HouseholdMembership(
                        rs.getInt("household_member_id"),
                        mapHousehold(rs),
                        rs.getInt("user_id"),
                        HouseholdRole.valueOf(rs.getString("role"))));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load household for user " + userId, e);
        }
    }

    private static int generatedId(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            keys.next();
            return keys.getInt(1);
        }
    }

    // Turns the current ResultSet row into an AppUser. Kept in one place so every query maps the same way.
    private static AppUser mapUser(ResultSet rs) throws SQLException {
        return new AppUser(
                rs.getInt("user_id"),
                rs.getString("user_email"),
                rs.getString("user_display_name"),
                rs.getString("user_password_hash"),
                rs.getObject("user_created_date", LocalDate.class));
    }

    private static Household mapHousehold(ResultSet rs) throws SQLException {
        return new Household(
                rs.getInt("household_id"),
                rs.getString("household_name"),
                rs.getObject("created_date", LocalDate.class));
    }
}
