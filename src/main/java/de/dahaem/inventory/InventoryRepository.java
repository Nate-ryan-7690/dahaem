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

    /**
     * The columns of an {@link Item} with the stock of ONE household: the household's row in household_stock (no row = no
     * stock: quantity 0, no expiry). The query must join {@code household_stock hs} on that household, see {@link #STOCK_JOIN}.
     */
    private static final String ITEM_COLUMNS = "ii.inventory_item_id, ii.inventory_item_name_english, "
            + "ii.inventory_item_name_deutsch, COALESCE(hs.household_stock_quantity, 0) AS inventory_item_quantity, "
            + "ii.inventory_item_unit, ii.inventory_item_category, ii.inventory_item_subcategory, "
            + "hs.household_stock_earliest_exp_date AS inventory_item_earliest_exp_date";

    /** One placeholder: the household id. */
    private static final String STOCK_JOIN = "FROM inventory_item ii LEFT JOIN household_stock hs "
            + "ON hs.household_stock_item_id = ii.inventory_item_id AND hs.household_stock_household_id = ?";

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

    /** Every catalogue item with the stock of this household. */
    public List<Item> findAll(int householdId) {
        String sql = "SELECT " + ITEM_COLUMNS + " " + STOCK_JOIN + " ORDER BY ii.inventory_item_name_english";
        List<Item> items = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load inventory items", e);
        }
        return items;
    }

    /**
     * One page of the catalogue, ordered by English name. {@code query} (nullable) matches the English or German name
     * as a case-insensitive substring; {@code category} (nullable) must equal the item's category. Both are bound as
     * parameters; the wildcards of the query text are escaped so a search for "50%" means "50%".
     */
    public ItemPage search(int householdId, String query, String category, long offset, int limit) {
        String where = " WHERE (? IS NULL OR ii.inventory_item_name_english LIKE ? OR ii.inventory_item_name_deutsch LIKE ?) "
                + "AND (? IS NULL OR ii.inventory_item_category = ?)";
        String pattern = query == null ? null : "%" + query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        String listSql = "SELECT " + ITEM_COLUMNS + " " + STOCK_JOIN + where +
                " ORDER BY ii.inventory_item_name_english, ii.inventory_item_id LIMIT ? OFFSET ?";
        String countSql = "SELECT COUNT(*) FROM inventory_item ii" + where;
        try (Connection con = Database.getConnection()) {
            long total;
            try (PreparedStatement ps = con.prepareStatement(countSql)) {
                bindFilter(ps, 0, pattern, category);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    total = rs.getLong(1);
                }
            }
            List<Item> items = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(listSql)) {
                ps.setInt(1, householdId);
                bindFilter(ps, 1, pattern, category);
                ps.setInt(7, limit);
                ps.setLong(8, offset);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        items.add(mapRow(rs));
                    }
                }
            }
            return new ItemPage(items, total);
        } catch (SQLException e) {
            throw new RuntimeException("Could not search inventory items", e);
        }
    }

    /** Binds the five filter placeholders, starting after {@code skip} placeholders that are already bound. */
    private static void bindFilter(PreparedStatement ps, int skip, String pattern, String category) throws SQLException {
        ps.setString(skip + 1, pattern);
        ps.setString(skip + 2, pattern);
        ps.setString(skip + 3, pattern);
        ps.setString(skip + 4, category);
        ps.setString(skip + 5, category);
    }

    /** The catalogue item with the stock of this household, or empty if there is no such item. */
    public Optional<Item> findById(int householdId, int id) {
        String sql = "SELECT " + ITEM_COLUMNS + " " + STOCK_JOIN + " WHERE ii.inventory_item_id = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, householdId); // Parameterized: never build SQL by string concatenation.
            ps.setInt(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load inventory item " + id, e);
        }
    }

    /**
     * The catalogue item WITHOUT any household's stock: quantity 0 and no expiry date. For callers that only need what
     * the catalogue says (name, unit, category) or whether the item exists. Use {@link #findById(int, int)} for stock.
     */
    public Optional<Item> findCatalogueItem(int id) {
        String sql = "SELECT inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch, "
                + "0 AS inventory_item_quantity, inventory_item_unit, inventory_item_category, "
                + "inventory_item_subcategory, NULL AS inventory_item_earliest_exp_date "
                + "FROM inventory_item WHERE inventory_item_id = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
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

    /** Sets the quantity of this household's stock of an item (the expiry date stays as it is). */
    public void updateQuantity(int householdId, int id, int newQuantity) {
        String sql = "INSERT INTO household_stock (household_stock_household_id, household_stock_item_id, "
                + "household_stock_quantity) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE household_stock_quantity = VALUES(household_stock_quantity)";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            ps.setInt(2, id);
            ps.setInt(3, newQuantity);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("Could not update quantity for item " + id, e);
        }
    }

    /** The item with this household's stock, locked for the caller's transaction (see PurchaseRepository.openTransaction()). */
    public Optional<Item> findByIdForUpdate(Connection con, int householdId, int id) throws SQLException {
        String sql = "SELECT " + ITEM_COLUMNS + " " + STOCK_JOIN + " WHERE ii.inventory_item_id = ? FOR UPDATE";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            ps.setInt(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        }
    }

    /** Sets this household's stock of an item and its earliest expiry date together (null date = none known). */
    public void setStock(Connection con, int householdId, int id, int quantity, LocalDate earliestExpDate) throws SQLException {
        String sql = "INSERT INTO household_stock (household_stock_household_id, household_stock_item_id, "
                + "household_stock_quantity, household_stock_earliest_exp_date) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE household_stock_quantity = VALUES(household_stock_quantity), "
                + "household_stock_earliest_exp_date = VALUES(household_stock_earliest_exp_date)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            ps.setInt(2, id);
            ps.setInt(3, quantity);
            ps.setObject(4, earliestExpDate);
            ps.executeUpdate();
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
