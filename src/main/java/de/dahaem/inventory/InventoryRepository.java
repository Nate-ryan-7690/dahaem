package de.dahaem.inventory;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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

    /**
     * Find-or-create a generic inventory_item by (name_english, category), on the caller's transaction
     * (see PurchaseRepository.openTransaction()/StoreRepository.openTransaction()). A newly created item
     * starts with zero quantity and no known expiry; the caller (e.g. a purchase being recorded) is
     * responsible for adding the purchased quantity and expiry afterwards. Returns the item's id.
     */
    public int findOrCreateGenericItem(Connection con, String nameEnglish, String nameDeutsch, String category,
                                        String unit) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT inventory_item_id FROM inventory_item "
                + "WHERE inventory_item_name_english = ? AND inventory_item_category = ?")) {
            ps.setString(1, nameEnglish);
            ps.setString(2, category);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        }
        String sql = "INSERT INTO inventory_item (inventory_item_name_english, inventory_item_name_deutsch, " +
                "inventory_item_category, inventory_item_unit, inventory_item_quantity) VALUES (?, ?, ?, ?, 0)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, nameEnglish);
            ps.setString(2, nameDeutsch);
            ps.setString(3, category);
            ps.setString(4, unit);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
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
        LocalDate exp = rs.getObject("inventory_item_earliest_exp_date", LocalDate.class);
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
