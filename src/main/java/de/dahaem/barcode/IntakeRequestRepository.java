package de.dahaem.barcode;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;

/** All SQL for intake_request lives here and nowhere else. */
public class IntakeRequestRepository {

    /** A request id that was seen before: the fingerprint of its body and its answer (null while it is being processed). */
    public record Stored(String hash, String resultJson) {
    }

    /**
     * Claims a request id for this caller. True if it was free (the caller processes the request); false if the id was
     * already claimed (the caller looks at {@link #find}). Two callers with the same id cannot both get true.
     */
    public boolean claim(String requestId, String hash) {
        String sql = "INSERT INTO intake_request (intake_request_id, intake_request_hash, intake_request_created) "
                + "VALUES (?, ?, NOW())";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, requestId);
            ps.setString(2, hash);
            ps.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException e) {
            return false;   // the primary key: this id was claimed before
        } catch (SQLException e) {
            throw new RuntimeException("Could not claim intake request " + requestId, e);
        }
    }

    public Optional<Stored> find(String requestId) {
        String sql = "SELECT intake_request_hash, intake_request_result FROM intake_request WHERE intake_request_id = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, requestId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(new Stored(rs.getString(1), rs.getString(2))) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load intake request " + requestId, e);
        }
    }

    /** Remembers the answer of a claimed request. */
    public void complete(String requestId, String resultJson) {
        update("UPDATE intake_request SET intake_request_result = ? WHERE intake_request_id = ?", resultJson, requestId);
    }

    /** Gives a claim back (nothing was recorded for it), so the client can send the request again. */
    public void release(String requestId) {
        update("DELETE FROM intake_request WHERE intake_request_id = ? AND intake_request_result IS NULL", requestId);
    }

    private static void update(String sql, String... params) {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not update the intake request", e);
        }
    }
}
