package de.dahaem.inventory;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * All SQL for the inventory_item table lives here and nowhere else.
 * The rest of the app never writes SQL against inventory; it calls these methods.
 */
public class InventoryRepository {

    public long countAll() {
        String sql = "SELECT COUNT(*) FROM inventory_item";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Could not count inventory items", e);
        }
    }

    public List<Item> findAll() {
        String sql = "SELECT inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, " +
                "inventory_item_quantity, inventory_item_unit, inventory_item_category, " +
                "inventory_item_subcategory, inventory_item_earliest_exp_date " +
                "FROM inventory_item ORDER BY inventory_item_name_english";
        List<Item> items = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                items.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load inventory items", e);
        }
        return items;
    }

    public Optional<Item> findById(int id) {
        String sql = "SELECT inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, " +
                "inventory_item_quantity, inventory_item_unit, inventory_item_category, " +
                "inventory_item_subcategory, inventory_item_earliest_exp_date " +
                "FROM inventory_item WHERE inventory_item_id = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id); // Parameterized: never build SQL by string concatenation.
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load inventory item " + id, e);
        }
    }

    public void updateQuantity(int id, int newQuantity) {
        String sql = "UPDATE inventory_item SET inventory_item_quantity = ? WHERE inventory_item_id = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, newQuantity);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not update quantity for item " + id, e);
        }
    }

    // Turns the current ResultSet row into an Item. Kept in one place so every query maps the same way.
    private Item mapRow(ResultSet rs) throws SQLException {
        String expText = rs.getString("inventory_item_earliest_exp_date");
        LocalDate exp = (expText == null || expText.isBlank()) ? null : LocalDate.parse(expText);
        return new Item(
                rs.getInt("inventory_item_id"),
                rs.getString("inventory_item_name_english"),
                rs.getString("inventory_item_name_deutsch"),
                rs.getInt("inventory_item_quantity"),
                rs.getString("inventory_item_unit"),
                rs.getString("inventory_item_category"),
                rs.getString("inventory_item_subcategory"),
                exp
        );
    }
}
