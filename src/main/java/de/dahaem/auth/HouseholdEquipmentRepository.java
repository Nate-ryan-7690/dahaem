package de.dahaem.auth;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;

/** All SQL for household_unavailable_equipment lives here: one row = the household does NOT have that equipment. */
public class HouseholdEquipmentRepository {

    /** The equipment codes the household does not have. */
    public Set<String> findMissing(int householdId) {
        String sql = "SELECT household_unavailable_equipment_code FROM household_unavailable_equipment "
                + "WHERE household_unavailable_equipment_household_id = ? ORDER BY household_unavailable_equipment_code";
        Set<String> out = new LinkedHashSet<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(rs.getString(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not read the equipment of household " + householdId, e);
        }
        return out;
    }

    /** Says whether the household has the equipment: no row = has it. Setting the same value again is fine. */
    public void setHas(int householdId, String code, boolean has) {
        String sql = has
                ? "DELETE FROM household_unavailable_equipment WHERE household_unavailable_equipment_household_id = ? "
                        + "AND household_unavailable_equipment_code = ?"
                : "INSERT IGNORE INTO household_unavailable_equipment (household_unavailable_equipment_household_id, "
                        + "household_unavailable_equipment_code) VALUES (?, ?)";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            ps.setString(2, code);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not set the equipment of household " + householdId, e);
        }
    }
}
