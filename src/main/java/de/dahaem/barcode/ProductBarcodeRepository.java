package de.dahaem.barcode;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** All SQL for product_barcode and product_barcode_store_item lives here and nowhere else. */
public class ProductBarcodeRepository {

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

    /** The product a normalised barcode identifies, with the store items that carry it; empty if DAHÄM does not know it. */
    public Optional<BarcodeProduct> findByCode(String code) {
        String sql = "SELECT pb.product_barcode_item_id, ii.inventory_item_name_deutsch, ii.inventory_item_name_english, "
                + "ii.inventory_item_unit, b.brand_name, pb.product_barcode_package_size, pb.product_barcode_package_unit, pb.product_barcode_name "
                + "FROM product_barcode pb "
                + "JOIN inventory_item ii ON ii.inventory_item_id = pb.product_barcode_item_id "
                + "LEFT JOIN brand b ON b.brand_id = pb.product_barcode_brand_id "
                + "WHERE pb.product_barcode_code = ?";
        String linksSql = "SELECT si.store_item_id, s.store_name, si.store_item_price "
                + "FROM product_barcode_store_item l "
                + "JOIN store_item si ON si.store_item_id = l.product_barcode_store_item_store_item_id "
                + "JOIN store s ON s.store_id = si.store_item_store_id "
                + "WHERE l.product_barcode_store_item_code = ? ORDER BY s.store_name, si.store_item_id";
        try (Connection con = Database.getConnection()) {
            BarcodeProduct partial;
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    partial = new BarcodeProduct(code, rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getInt(6), rs.getString(7), rs.getString(8), List.of());
                }
            }
            List<BarcodeProduct.LinkedStoreItem> links = new ArrayList<>();
            try (PreparedStatement ps = con.prepareStatement(linksSql)) {
                ps.setString(1, code);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        double price = rs.getDouble(3);
                        boolean unknown = rs.wasNull();   // right after reading the column: NULL = no price known
                        links.add(new BarcodeProduct.LinkedStoreItem(rs.getInt(1), rs.getString(2),
                                unknown ? null : price));
                    }
                }
            }
            return Optional.of(new BarcodeProduct(code, partial.inventoryItemId(), partial.nameDeutsch(),
                    partial.nameEnglish(), partial.itemUnit(), partial.brand(), partial.packSize(), partial.packUnit(),
                    partial.name(), links));
        } catch (SQLException e) {
            throw new RuntimeException("Could not load barcode " + code, e);
        }
    }

    /** Creates the barcode, or replaces what it identifies (the day it was first entered stays). On the caller's transaction. */
    public void upsert(Connection con, String code, int inventoryItemId, Integer brandId, String packUnit, int packSize,
                       String name, LocalDate today) throws SQLException {
        String sql = "INSERT INTO product_barcode (product_barcode_code, product_barcode_item_id, product_barcode_brand_id, "
                + "product_barcode_package_unit, product_barcode_package_size, product_barcode_name, "
                + "product_barcode_created_date) VALUES (?, ?, ?, ?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE product_barcode_item_id = VALUES(product_barcode_item_id), "
                + "product_barcode_brand_id = VALUES(product_barcode_brand_id), "
                + "product_barcode_package_unit = VALUES(product_barcode_package_unit), "
                + "product_barcode_package_size = VALUES(product_barcode_package_size), "
                + "product_barcode_name = VALUES(product_barcode_name)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setInt(2, inventoryItemId);
            ps.setObject(3, brandId);
            ps.setString(4, packUnit);
            ps.setInt(5, packSize);
            ps.setString(6, name);
            ps.setObject(7, today);
            ps.executeUpdate();
        }
    }

    /** Links a store item to the barcode (already linked is fine). On the caller's transaction. */
    public void link(Connection con, String code, int storeItemId) throws SQLException {
        String sql = "INSERT INTO product_barcode_store_item (product_barcode_store_item_code, "
                + "product_barcode_store_item_store_item_id) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE product_barcode_store_item_code = product_barcode_store_item_code";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, code);
            ps.setInt(2, storeItemId);
            ps.executeUpdate();
        }
    }

    /** Links a store item to the barcode on its own connection (already linked is fine). */
    public void link(String code, int storeItemId) {
        try (Connection con = Database.getConnection()) {
            link(con, code, storeItemId);
        } catch (SQLException e) {
            throw new RuntimeException("Could not link store item " + storeItemId + " to barcode " + code, e);
        }
    }

    /** The catalogue item a store item is for, or empty if there is no such store item. */
    public Optional<Integer> findInventoryItemIdOfStoreItem(Connection con, int storeItemId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT store_item_item_id FROM store_item WHERE store_item_id = ?")) {
            ps.setInt(1, storeItemId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(rs.getInt(1)) : Optional.empty();
            }
        }
    }
}
