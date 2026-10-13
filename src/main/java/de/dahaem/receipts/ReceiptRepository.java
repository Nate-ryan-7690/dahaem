package de.dahaem.receipts;

import de.dahaem.shared.Database;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * All SQL of the receipt import: purchase_receipt, purchase_receipt_line, purchase_receipt_adjustment
 * (migration 0008) and the store lookup. Every statement is parameterized. {@link #saveReceipts} writes
 * a whole import in ONE transaction: any failure rolls all of it back. Nothing here touches
 * purchase_history or inventory_item.
 */
public class ReceiptRepository {

    /** The store's id, or null if there is no such row (a dry run never creates one). */
    public Integer findStoreId(String name) {
        try (Connection con = Database.getConnection()) {
            return findStoreId(con, name);
        } catch (SQLException e) {
            throw new RuntimeException("Could not look up store " + name, e);
        }
    }

    /** True if this receipt (store, date-time, receipt number) is already stored. */
    public boolean exists(int storeId, LocalDateTime purchasedAt, String receiptNumber) {
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT 1 FROM purchase_receipt WHERE purchase_receipt_store_id = ? "
                     + "AND purchase_receipt_purchased_at = ? AND purchase_receipt_receipt_number = ?")) {
            ps.setInt(1, storeId);
            ps.setTimestamp(2, Timestamp.valueOf(purchasedAt));
            ps.setString(3, receiptNumber);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not check for receipt " + receiptNumber, e);
        }
    }

    /**
     * Writes the receipts -- header, every line, every adjustment, and the store row if it is missing --
     * in one transaction. The UNIQUE key (store, date-time, number) is the last guard against a duplicate:
     * it fails the whole import rather than storing a receipt twice.
     */
    public void saveReceipts(String storeName, List<ReceiptImportService.ReceiptResult> receipts) {
        try (Connection con = Database.getConnection()) {
            con.setAutoCommit(false);
            try {
                int storeId = findOrCreateStore(con, storeName);
                for (ReceiptImportService.ReceiptResult r : receipts) {
                    int receiptId = insertReceipt(con, storeId, r.receipt());
                    List<Integer> lineIds = new ArrayList<>();
                    for (ReceiptImportService.PlannedLine l : r.lines()) {
                        lineIds.add(insertLine(con, receiptId, l));
                    }
                    for (ReceiptAdjustment a : r.receipt().adjustments()) {
                        insertAdjustment(con, receiptId, a.lineIndex() == null ? null : lineIds.get(a.lineIndex()),
                                storedType(a.type()), a.amount(), a.description());
                    }
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Receipt import failed, nothing was written: " + e.getMessage(), e);
        }
    }

    /** Parser adjustment type -> purchase_receipt_adjustment_type. */
    static String storedType(AdjustmentType t) {
        return switch (t) {
            case APP_RABATT -> "APP_PRICE_DISCOUNT";
            case APP_COUPON -> "APP_COUPON";
            case LOYALTY_STICKER -> "LOYALTY_ITEM";
            case FRESHNESS_DISCOUNT -> "FRESHNESS_DISCOUNT";
            case PFAND_DEPOSIT -> "DEPOSIT";
            case LEERGUT_RETURN -> "DEPOSIT_RETURN";
            case POSITIONSRABATT, OTHER -> "OTHER";
        };
    }

    Integer findStoreId(Connection con, String name) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT store_id FROM store WHERE store_name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : null;
            }
        }
    }

    int findOrCreateStore(Connection con, String name) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("INSERT IGNORE INTO store (store_name) VALUES (?)")) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
        return findStoreId(con, name);
    }

    public int insertReceipt(Connection con, int storeId, ParsedReceipt r) throws SQLException {
        String sql = "INSERT INTO purchase_receipt (purchase_receipt_store_id, purchase_receipt_purchased_at, "
                + "purchase_receipt_receipt_number, purchase_receipt_total, purchase_receipt_app_savings, "
                + "purchase_receipt_other_savings, purchase_receipt_loyalty_points) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, storeId);
            ps.setTimestamp(2, Timestamp.valueOf(r.purchasedAt()));
            ps.setString(3, r.receiptNumber());
            ps.setBigDecimal(4, r.total());
            ps.setBigDecimal(5, r.appSavings());
            ps.setBigDecimal(6, r.otherSavings());
            if (r.loyaltyPointsEarned() == null) {
                ps.setNull(7, Types.INTEGER);
            } else {
                ps.setInt(7, r.loyaltyPointsEarned());
            }
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    public int insertLine(Connection con, int receiptId, ReceiptImportService.PlannedLine l) throws SQLException {
        String sql = "INSERT INTO purchase_receipt_line (purchase_receipt_line_receipt_id, purchase_receipt_line_line_no, "
                + "purchase_receipt_line_raw_name, purchase_receipt_line_brand, purchase_receipt_line_quantity, "
                + "purchase_receipt_line_unit, purchase_receipt_line_weight_kg, purchase_receipt_line_price_per_kg, "
                + "purchase_receipt_line_unit_price, purchase_receipt_line_line_total, purchase_receipt_line_paid_total, "
                + "purchase_receipt_line_tax_symbol, purchase_receipt_line_tax_rate, purchase_receipt_line_discount_excluded, "
                + "purchase_receipt_line_deposit, purchase_receipt_line_inventory_item_id, purchase_receipt_line_match_source) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        ReceiptLine line = l.line();
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, receiptId);
            ps.setInt(2, l.lineNo());
            ps.setString(3, line.rawName());
            ps.setString(4, l.brand());
            ps.setBigDecimal(5, line.quantity());
            ps.setString(6, line.unit());
            ps.setBigDecimal(7, line.weightKg());
            ps.setBigDecimal(8, line.pricePerKg());
            ps.setBigDecimal(9, line.unitPrice());
            ps.setBigDecimal(10, line.lineTotal());
            ps.setBigDecimal(11, l.paidTotal());
            ps.setString(12, line.taxSymbol());
            ps.setBigDecimal(13, line.taxRatePercent());
            ps.setBoolean(14, line.discountExcluded());
            ps.setBigDecimal(15, line.deposit());
            if (l.match().itemId() == null) {
                ps.setNull(16, Types.INTEGER);
            } else {
                ps.setInt(16, l.match().itemId());
            }
            ps.setString(17, l.match().source());
            ps.executeUpdate();
            return generatedId(ps);
        }
    }

    public void insertAdjustment(Connection con, int receiptId, Integer lineId, String type, BigDecimal amount,
                                 String rawText) throws SQLException {
        String sql = "INSERT INTO purchase_receipt_adjustment (purchase_receipt_adjustment_receipt_id, "
                + "purchase_receipt_adjustment_line_id, purchase_receipt_adjustment_type, "
                + "purchase_receipt_adjustment_amount, purchase_receipt_adjustment_raw_text) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, receiptId);
            if (lineId == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, lineId);
            }
            ps.setString(3, type);
            ps.setBigDecimal(4, amount);
            ps.setString(5, rawText);
            ps.executeUpdate();
        }
    }

    private static int generatedId(PreparedStatement ps) throws SQLException {
        try (ResultSet keys = ps.getGeneratedKeys()) {
            keys.next();
            return keys.getInt(1);
        }
    }
}
