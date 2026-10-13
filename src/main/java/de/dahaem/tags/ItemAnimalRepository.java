package de.dahaem.tags;

import de.dahaem.shared.Database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** All SQL for inventory_item_animal, and the read-only catalogue queries the animal tool needs. */
public class ItemAnimalRepository {

    static final String NAME_SQL = "COALESCE(NULLIF(inventory_item_name_deutsch, ''), inventory_item_name_english)";

    // Where meat / fish live in the catalogue: whole categories, plus single subcategories of other categories.
    private static final List<String> CANDIDATE_CATEGORIES = List.of("meat", "meat & poultry", "fish & seafood");
    private static final List<String[]> CANDIDATE_SUBCATEGORIES = List.of(
            new String[]{"canned & jarred goods", "canned meat"},
            new String[]{"canned & jarred goods", "canned fish"},
            new String[]{"snacks", "jerky & meat snacks"},
            new String[]{"frozen", "fish"},
            new String[]{"frozen", "poultry products"});

    /** The meat / fish items of the catalogue (read-only), by id. */
    public List<CatalogItem> findCandidates() {
        StringBuilder sql = new StringBuilder("SELECT inventory_item_id, " + NAME_SQL
                + " AS item_name, inventory_item_category, inventory_item_subcategory FROM inventory_item WHERE ");
        sql.append("inventory_item_category IN (").append(placeholders(CANDIDATE_CATEGORIES.size())).append(")");
        for (int i = 0; i < CANDIDATE_SUBCATEGORIES.size(); i++) {
            sql.append(" OR (inventory_item_category = ? AND inventory_item_subcategory = ?)");
        }
        sql.append(" ORDER BY inventory_item_id");
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            int n = 1;
            for (String category : CANDIDATE_CATEGORIES) {
                ps.setString(n++, category);
            }
            for (String[] pair : CANDIDATE_SUBCATEGORIES) {
                ps.setString(n++, pair[0]);
                ps.setString(n++, pair[1]);
            }
            return readItems(ps);
        } catch (SQLException e) {
            throw new RuntimeException("Could not load the meat / fish items", e);
        }
    }

    /** Every catalogue item, by id (read-only); used to list what blocks vegetarian / vegan. */
    public List<CatalogItem> findAllItems() {
        String sql = "SELECT inventory_item_id, " + NAME_SQL + " AS item_name, inventory_item_category, "
                + "inventory_item_subcategory FROM inventory_item ORDER BY inventory_item_id";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            return readItems(ps);
        } catch (SQLException e) {
            throw new RuntimeException("Could not load the catalogue", e);
        }
    }

    /** item id → animals already stored. */
    public Map<Integer, Set<String>> findAnimals() {
        String sql = "SELECT inventory_item_animal_item_id, inventory_item_animal_animal FROM inventory_item_animal";
        Map<Integer, Set<String>> out = new LinkedHashMap<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.computeIfAbsent(rs.getInt(1), k -> new TreeSet<>()).add(rs.getString(2));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load the item animals", e);
        }
        return out;
    }

    /** The ids of the given items that exist in the catalogue. */
    public Set<Integer> existingItemIds(Set<Integer> ids) {
        Set<Integer> existing = new HashSet<>();
        if (ids.isEmpty()) {
            return existing;
        }
        String sql = "SELECT inventory_item_id FROM inventory_item WHERE inventory_item_id IN ("
                + placeholders(ids.size()) + ")";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int n = 1;
            for (int id : ids) {
                ps.setInt(n++, id);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    existing.add(rs.getInt(1));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not check the item ids", e);
        }
        return existing;
    }

    /**
     * Inserts (item id, animal) pairs in ONE transaction; a pair that is already stored is left alone.
     * Any failure rolls the whole batch back.
     */
    public void insertAnimals(Map<Integer, Set<String>> animalsByItem) {
        String sql = "INSERT INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal) "
                + "VALUES (?, ?) ON DUPLICATE KEY UPDATE inventory_item_animal_item_id = inventory_item_animal_item_id";
        try (Connection con = Database.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(sql)) {
                for (Map.Entry<Integer, Set<String>> e : animalsByItem.entrySet()) {
                    for (String animal : e.getValue()) {
                        ps.setInt(1, e.getKey());
                        ps.setString(2, animal);
                        ps.executeUpdate();
                    }
                }
                con.commit();
            } catch (SQLException | RuntimeException ex) {
                con.rollback();
                throw ex;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not store the item animals, nothing was written: " + e.getMessage(), e);
        }
    }

    private static List<CatalogItem> readItems(PreparedStatement ps) throws SQLException {
        List<CatalogItem> items = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                items.add(new CatalogItem(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)));
            }
        }
        return items;
    }

    static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }
}
