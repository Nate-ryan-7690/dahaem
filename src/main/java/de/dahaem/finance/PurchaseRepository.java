package de.dahaem.finance;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL for the purchase_history table lives here and nowhere else.
 * The rest of the app never writes SQL against purchases; it calls these methods.
 *
 * insert/find/spending methods manage their own connection. The Connection-accepting
 * overloads exist so PurchaseService can combine a purchase insert with an inventory_item
 * update (via store_item.store_item_item_id) in a single transaction; see openTransaction().
 */
public class PurchaseRepository {

    private static final String INSERT_SQL = "INSERT INTO purchase_history (purchase_history_household_id, "
            + "purchase_history_store_item_id, purchase_history_discount_id, purchase_history_was_discounted, "
            + "purchase_history_purchase_date, purchase_history_quantity_purchased, purchase_history_price_paid, "
            + "purchase_history_expiration_date) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String SELECT_COLUMNS = "SELECT purchase_history_purchase_id, purchase_history_store_item_id, " +
            "purchase_history_discount_id, purchase_history_was_discounted, purchase_history_purchase_date, " +
            "purchase_history_quantity_purchased, purchase_history_price_paid, purchase_history_expiration_date " +
            "FROM purchase_history";

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

    public int insert(int householdId, int storeItemId, Integer discountId, boolean wasDiscounted, LocalDate purchaseDate,
                       int quantityPurchased, double pricePaid, LocalDate expirationDate) {
        try (Connection con = Database.getConnection()) {
            return insert(con, householdId, storeItemId, discountId, wasDiscounted, purchaseDate,
                    quantityPurchased, pricePaid, expirationDate);
        } catch (SQLException e) {
            throw new RuntimeException("Could not insert purchase for store item " + storeItemId, e);
        }
    }

    /** Same insert, on a connection the caller manages (does not commit). */
    public int insert(Connection con, int householdId, int storeItemId, Integer discountId, boolean wasDiscounted,
                       LocalDate purchaseDate, int quantityPurchased, double pricePaid, LocalDate expirationDate)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, householdId);
            ps.setInt(2, storeItemId);
            ps.setObject(3, discountId);
            ps.setBoolean(4, wasDiscounted);
            ps.setObject(5, purchaseDate);
            ps.setInt(6, quantityPurchased);
            ps.setDouble(7, pricePaid);
            ps.setObject(8, expirationDate);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    /** The purchases of one household, newest first. */
    public List<Purchase> findAll(int householdId) {
        String sql = SELECT_COLUMNS + " WHERE purchase_history_household_id = ? "
                + "ORDER BY purchase_history_purchase_date DESC, purchase_history_purchase_id DESC";
        List<Purchase> purchases = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    purchases.add(mapRow(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load purchases", e);
        }
        return purchases;
    }

    /** Sum of price paid across every purchase of the household. 0 if there are none. */
    public double spendingTotal(int householdId) {
        String sql = "SELECT COALESCE(SUM(purchase_history_price_paid), 0) FROM purchase_history "
                + "WHERE purchase_history_household_id = ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not total spending", e);
        }
    }

    /** Sum of price paid for the household's purchases dated between {@code from} and {@code to}, inclusive. 0 if none match. */
    public double spendingBetween(int householdId, LocalDate from, LocalDate to) {
        String sql = "SELECT COALESCE(SUM(purchase_history_price_paid), 0) FROM purchase_history " +
                "WHERE purchase_history_household_id = ? AND purchase_history_purchase_date BETWEEN ? AND ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            ps.setString(2, from.toString());
            ps.setString(3, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not total spending between " + from + " and " + to, e);
        }
    }

    /** One purchase of an item with the facts a batch listing needs; {@code remaining} is null = nothing used yet. */
    public record BatchRow(int purchaseId, int storeItemId, String storeName, String brandName, LocalDate purchaseDate,
                           LocalDate expirationDate, int quantityPurchased, int packSize, String packUnit,
                           Integer remaining) {
    }

    /** Every purchase of an inventory item by this household, earliest expiry first (older purchase first on a tie). Looks only. */
    public List<BatchRow> findBatchesOfItem(int householdId, int inventoryItemId) {
        String sql = "SELECT ph.purchase_history_purchase_id, ph.purchase_history_store_item_id, s.store_name, b.brand_name, "
                + "ph.purchase_history_purchase_date, ph.purchase_history_expiration_date, "
                + "ph.purchase_history_quantity_purchased, si.store_item_package_size, si.store_item_package_unit, "
                + "ph.purchase_history_quantity_remaining "
                + "FROM purchase_history ph "
                + "JOIN store_item si ON si.store_item_id = ph.purchase_history_store_item_id "
                + "JOIN store s ON s.store_id = si.store_item_store_id "
                + "JOIN brand b ON b.brand_id = si.store_item_brand_id "
                + "WHERE ph.purchase_history_household_id = ? AND si.store_item_item_id = ? "
                + "ORDER BY ph.purchase_history_expiration_date, ph.purchase_history_purchase_id";
        List<BatchRow> out = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            ps.setInt(2, inventoryItemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int remaining = rs.getInt(10);
                    boolean untouched = rs.wasNull();   // right after reading the column
                    out.add(new BatchRow(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4),
                            rs.getObject(5, LocalDate.class), rs.getObject(6, LocalDate.class), rs.getInt(7),
                            rs.getInt(8), rs.getString(9), untouched ? null : remaining));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not list the batches of inventory item " + inventoryItemId, e);
        }
        return out;
    }

    /** What a purchase of a store item adds to stock: the catalogue item, the pack (size + unit) and the item's own unit. */
    public record StoreItemStock(int inventoryItemId, int packSize, String packUnit, String itemUnit) {
    }

    /** The stock facts of a store item, or null if there is no such store item. */
    public StoreItemStock findStoreItemStock(Connection con, int storeItemId) throws SQLException {
        String sql = "SELECT si.store_item_item_id, si.store_item_package_size, si.store_item_package_unit, "
                + "ii.inventory_item_unit FROM store_item si "
                + "JOIN inventory_item ii ON ii.inventory_item_id = si.store_item_item_id WHERE si.store_item_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, storeItemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new StoreItemStock(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getString(4)) : null;
            }
        }
    }

    /**
     * Adds {@code quantityDelta} to the household's stock of the item and pulls its earliest expiry date down to
     * {@code purchaseExpirationDate} if that is earlier than what is already stored (or nothing is stored yet). The
     * household's stock row is made on the first purchase.
     */
    public void applyPurchaseToInventory(Connection con, int householdId, int inventoryItemId, int quantityDelta,
                                          LocalDate purchaseExpirationDate) throws SQLException {
        String sql = "INSERT INTO household_stock (household_stock_household_id, household_stock_item_id, "
                + "household_stock_quantity, household_stock_earliest_exp_date) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + "household_stock_quantity = household_stock_quantity + VALUES(household_stock_quantity), "
                + "household_stock_earliest_exp_date = CASE "
                + "WHEN household_stock_earliest_exp_date IS NULL "
                + "OR VALUES(household_stock_earliest_exp_date) < household_stock_earliest_exp_date "
                + "THEN VALUES(household_stock_earliest_exp_date) ELSE household_stock_earliest_exp_date END";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            ps.setInt(2, inventoryItemId);
            ps.setInt(3, quantityDelta);
            ps.setObject(4, purchaseExpirationDate);
            ps.executeUpdate();
        }
    }

    /**
     * One purchase (a batch of one inventory item) with what it needs to tell how much is left: {@code remaining} in the
     * item's unit (null = nothing used yet, all of it is left), the packs bought and the pack (size + unit).
     */
    public record Batch(int purchaseId, Integer remaining, int quantityPurchased, int packSize, String packUnit) {
    }

    /**
     * The purchases of an inventory item that still have something left, earliest expiry first (older purchase first on
     * a tie), locked for the caller's transaction. A purchase whose remaining column is NULL has all of it left.
     * "Left" is counted in the item's own unit (see {@link StockAmounts}).
     */
    public List<Batch> lockBatchesWithStock(Connection con, int householdId, int inventoryItemId) throws SQLException {
        String sql = "SELECT ph.purchase_history_purchase_id, ph.purchase_history_quantity_remaining, " +
                "ph.purchase_history_quantity_purchased, si.store_item_package_size, si.store_item_package_unit " +
                "FROM purchase_history ph JOIN store_item si ON si.store_item_id = ph.purchase_history_store_item_id " +
                "WHERE ph.purchase_history_household_id = ? AND si.store_item_item_id = ? " +
                "AND COALESCE(ph.purchase_history_quantity_remaining, ph.purchase_history_quantity_purchased) > 0 " +
                "ORDER BY ph.purchase_history_expiration_date, ph.purchase_history_purchase_id FOR UPDATE";
        List<Batch> batches = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            ps.setInt(2, inventoryItemId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int remaining = rs.getInt(2);
                    boolean untouched = rs.wasNull();   // right after reading the column: NULL = nothing used yet
                    batches.add(new Batch(rs.getInt(1), untouched ? null : remaining, rs.getInt(3), rs.getInt(4),
                            rs.getString(5)));
                }
            }
        }
        return batches;
    }

    public void setRemaining(Connection con, int purchaseId, int remaining) throws SQLException {
        String sql = "UPDATE purchase_history SET purchase_history_quantity_remaining = ? " +
                "WHERE purchase_history_purchase_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, remaining);
            ps.setInt(2, purchaseId);
            ps.executeUpdate();
        }
    }

    /** The earliest expiry among the household's purchases of an inventory item that still have something left, or null if none. */
    public LocalDate earliestExpiryWithStock(Connection con, int householdId, int inventoryItemId) throws SQLException {
        String sql = "SELECT MIN(ph.purchase_history_expiration_date) FROM purchase_history ph " +
                "JOIN store_item si ON si.store_item_id = ph.purchase_history_store_item_id " +
                "WHERE ph.purchase_history_household_id = ? AND si.store_item_item_id = ? " +
                "AND COALESCE(ph.purchase_history_quantity_remaining, ph.purchase_history_quantity_purchased) > 0";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, householdId);
            ps.setInt(2, inventoryItemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getObject(1, LocalDate.class) : null;
            }
        }
    }

    private Purchase mapRow(ResultSet rs) throws SQLException {
        int discountId = rs.getInt("purchase_history_discount_id");
        Integer discount = rs.wasNull() ? null : discountId;
        return new Purchase(
                rs.getInt("purchase_history_purchase_id"),
                rs.getInt("purchase_history_store_item_id"),
                discount,
                rs.getBoolean("purchase_history_was_discounted"),
                rs.getObject("purchase_history_purchase_date", LocalDate.class),
                rs.getInt("purchase_history_quantity_purchased"),
                rs.getDouble("purchase_history_price_paid"),
                rs.getObject("purchase_history_expiration_date", LocalDate.class)
        );
    }
}
