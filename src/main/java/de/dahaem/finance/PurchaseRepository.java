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

    private static final String INSERT_SQL = "INSERT INTO purchase_history (purchase_history_store_item_id, " +
            "purchase_history_discount_id, purchase_history_was_discounted, purchase_history_purchase_date, " +
            "purchase_history_quantity_purchased, purchase_history_price_paid, purchase_history_expiration_date) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?)";

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

    public int insert(int storeItemId, Integer discountId, boolean wasDiscounted, LocalDate purchaseDate,
                       int quantityPurchased, double pricePaid, LocalDate expirationDate) {
        try (Connection con = Database.getConnection()) {
            return insert(con, storeItemId, discountId, wasDiscounted, purchaseDate,
                    quantityPurchased, pricePaid, expirationDate);
        } catch (SQLException e) {
            throw new RuntimeException("Could not insert purchase for store item " + storeItemId, e);
        }
    }

    /** Same insert, on a connection the caller manages (does not commit). */
    public int insert(Connection con, int storeItemId, Integer discountId, boolean wasDiscounted,
                       LocalDate purchaseDate, int quantityPurchased, double pricePaid, LocalDate expirationDate)
            throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, storeItemId);
            ps.setObject(2, discountId);
            ps.setBoolean(3, wasDiscounted);
            ps.setString(4, purchaseDate.toString());
            ps.setInt(5, quantityPurchased);
            ps.setDouble(6, pricePaid);
            ps.setString(7, expirationDate.toString());
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public List<Purchase> findAll() {
        String sql = SELECT_COLUMNS + " ORDER BY purchase_history_purchase_date DESC, purchase_history_purchase_id DESC";
        List<Purchase> purchases = new ArrayList<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                purchases.add(mapRow(rs));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load purchases", e);
        }
        return purchases;
    }

    /** Sum of price paid across every recorded purchase. 0 if there are none. */
    public double spendingTotal() {
        String sql = "SELECT COALESCE(SUM(purchase_history_price_paid), 0) FROM purchase_history";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getDouble(1) : 0;
        } catch (SQLException e) {
            throw new RuntimeException("Could not total spending", e);
        }
    }

    /** Sum of price paid for purchases dated between {@code from} and {@code to}, inclusive. 0 if none match. */
    public double spendingBetween(LocalDate from, LocalDate to) {
        String sql = "SELECT COALESCE(SUM(purchase_history_price_paid), 0) FROM purchase_history " +
                "WHERE purchase_history_purchase_date BETWEEN ? AND ?";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getDouble(1) : 0;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not total spending between " + from + " and " + to, e);
        }
    }

    /** The inventory_item this store_item is linked to (store_item.store_item_item_id), or null if the store_item is unknown. */
    public Integer findInventoryItemIdForStoreItem(Connection con, int storeItemId) throws SQLException {
        String sql = "SELECT store_item_item_id FROM store_item WHERE store_item_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, storeItemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    /**
     * Increases inventory_item_quantity by {@code quantityDelta} and pulls inventory_item_earliest_exp_date
     * down to {@code purchaseExpirationDate} if that is earlier than what is already stored (or nothing is
     * stored yet).
     */
    public void applyPurchaseToInventory(Connection con, int inventoryItemId, int quantityDelta,
                                          LocalDate purchaseExpirationDate) throws SQLException {
        String sql = "UPDATE inventory_item SET inventory_item_quantity = inventory_item_quantity + ?, " +
                "inventory_item_earliest_exp_date = CASE " +
                "WHEN inventory_item_earliest_exp_date IS NULL OR ? < inventory_item_earliest_exp_date THEN ? " +
                "ELSE inventory_item_earliest_exp_date END " +
                "WHERE inventory_item_id = ?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, quantityDelta);
            String expText = purchaseExpirationDate.toString();
            ps.setString(2, expText);
            ps.setString(3, expText);
            ps.setInt(4, inventoryItemId);
            ps.executeUpdate();
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
                LocalDate.parse(rs.getString("purchase_history_purchase_date")),
                rs.getInt("purchase_history_quantity_purchased"),
                rs.getDouble("purchase_history_price_paid"),
                LocalDate.parse(rs.getString("purchase_history_expiration_date"))
        );
    }
}
