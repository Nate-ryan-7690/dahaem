package de.dahaem.auth;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * All SQL for the household_invite table lives here and nowhere else. The code of an invitation is never stored, only its
 * hash. Times are UTC.
 */
public class InvitationRepository {

    /**
     * One invitation. {@code usedByEmail} is null while it is unused.
     */
    public record Invite(int id, int householdId, String hint, String note, int createdBy, LocalDateTime createdAt,
                         LocalDateTime expiresAt, LocalDateTime usedAt, String usedByEmail, LocalDateTime revokedAt) {

        /** Neither used nor revoked nor expired at {@code now}. */
        public boolean isOpen(LocalDateTime now) {
            return usedAt == null && revokedAt == null && expiresAt.isAfter(now);
        }
    }

    private static final String SELECT = "SELECT i.household_invite_id, i.household_invite_household_id, "
            + "i.household_invite_code_hint, i.household_invite_note, i.household_invite_created_by, "
            + "i.household_invite_created_at, i.household_invite_expires_at, i.household_invite_used_at, "
            + "u.user_email, i.household_invite_revoked_at "
            + "FROM household_invite i LEFT JOIN app_user u ON u.user_id = i.household_invite_used_by ";

    public int insert(int householdId, int createdBy, String codeHash, String hint, String note,
                      LocalDateTime createdAt, LocalDateTime expiresAt) {
        String sql = "INSERT INTO household_invite (household_invite_household_id, household_invite_code_hash, "
                + "household_invite_code_hint, household_invite_note, household_invite_created_by, "
                + "household_invite_created_at, household_invite_expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, householdId);
            ps.setString(2, codeHash);
            ps.setString(3, hint);
            ps.setString(4, note);
            ps.setInt(5, createdBy);
            ps.setObject(6, createdAt);
            ps.setObject(7, expiresAt);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not save the invitation", e);
        }
    }

    /** How many invitations of the household can still be used at {@code now}. */
    public int countOpen(int householdId, LocalDateTime now) {
        String sql = "SELECT COUNT(*) FROM household_invite WHERE household_invite_household_id = ? "
                + "AND household_invite_used_at IS NULL AND household_invite_revoked_at IS NULL "
                + "AND household_invite_expires_at > ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            ps.setObject(2, now);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not count the open invitations", e);
        }
    }

    public Optional<Invite> findByHash(String codeHash) {
        try (Connection con = Database.getConnection()) {
            return findByHash(con, codeHash, false);
        } catch (SQLException e) {
            throw new RuntimeException("Could not look up an invitation", e);
        }
    }

    /** The invitation with this code hash, locked for the caller's transaction. */
    public Optional<Invite> lockByHash(Connection con, String codeHash) throws SQLException {
        return findByHash(con, codeHash, true);
    }

    private Optional<Invite> findByHash(Connection con, String codeHash, boolean lock) throws SQLException {
        String sql = SELECT + "WHERE i.household_invite_code_hash = ?" + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, codeHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /** The newest invitations of a household first, at most {@code limit}. */
    public List<Invite> list(int householdId, int limit) {
        String sql = SELECT + "WHERE i.household_invite_household_id = ? "
                + "ORDER BY i.household_invite_id DESC LIMIT ?";
        List<Invite> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(map(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not list the invitations", e);
        }
        return out;
    }

    /** Revokes an invitation of this household that can still be used. False if there is none such. */
    public boolean revoke(int householdId, int invitationId, LocalDateTime now) {
        String sql = "UPDATE household_invite SET household_invite_revoked_at = ? WHERE household_invite_id = ? "
                + "AND household_invite_household_id = ? AND household_invite_used_at IS NULL "
                + "AND household_invite_revoked_at IS NULL AND household_invite_expires_at > ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, now);
            ps.setInt(2, invitationId);
            ps.setInt(3, householdId);
            ps.setObject(4, now);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new RuntimeException("Could not revoke invitation " + invitationId, e);
        }
    }

    /** Marks the invitation as used by this user, on the caller's transaction. */
    public void markUsed(Connection con, int invitationId, int userId, LocalDateTime now) throws SQLException {
        String sql = "UPDATE household_invite SET household_invite_used_at = ?, household_invite_used_by = ? "
                + "WHERE household_invite_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, now);
            ps.setInt(2, userId);
            ps.setInt(3, invitationId);
            ps.executeUpdate();
        }
    }

    private static Invite map(ResultSet rs) throws SQLException {
        return new Invite(
                rs.getInt(1),
                rs.getInt(2),
                rs.getString(3),
                rs.getString(4),
                rs.getInt(5),
                rs.getObject(6, LocalDateTime.class),
                rs.getObject(7, LocalDateTime.class),
                rs.getObject(8, LocalDateTime.class),
                rs.getString(9),
                rs.getObject(10, LocalDateTime.class));
    }
}
