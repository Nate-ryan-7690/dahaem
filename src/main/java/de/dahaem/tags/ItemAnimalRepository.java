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

/** All SQL for inventory_item_animal and the alcohol flag, and the read-only catalogue queries the animal tool needs. */
public class ItemAnimalRepository {

    static final String NAME_SQL = "COALESCE(NULLIF(inventory_item_name_deutsch, ''), inventory_item_name_english)";

    /** Every catalogue item, by id (read-only); the animal tool and the diet filter work from this list. */
    public List<CatalogItem> findAllItems() {
        String sql = "SELECT inventory_item_id, " + NAME_SQL + " AS item_name, inventory_item_category, "
                + "inventory_item_subcategory, inventory_item_alcohol FROM inventory_item ORDER BY inventory_item_id";
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            return readItems(ps);
        } catch (SQLException e) {
            throw new RuntimeException("Could not load the catalogue", e);
        }
    }

    /** item id → animals already stored (certain and possible). */
    public Map<Integer, Set<String>> findAnimals() {
        Map<Integer, Set<String>> out = new LinkedHashMap<>();
        findAnimalCertainty().forEach((id, byAnimal) -> out.put(id, new TreeSet<>(byAnimal.keySet())));
        return out;
    }

    /** item id → (animal → 'certain' | 'possible') already stored. */
    public Map<Integer, Map<String, String>> findAnimalCertainty() {
        String sql = "SELECT inventory_item_animal_item_id, inventory_item_animal_animal, "
                + "inventory_item_animal_certainty FROM inventory_item_animal";
        Map<Integer, Map<String, String>> out = new LinkedHashMap<>();
        try (Connection con = Database.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                out.computeIfAbsent(rs.getInt(1), k -> new java.util.TreeMap<>()).put(rs.getString(2), rs.getString(3));
            }
        } catch (SQLException e) {
            throw new RuntimeException("Could not load the item animals (is migration 0012 applied?)", e);
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
     * Stores the reviewed animals (item id → animal → certainty) and sets the alcohol flag of the given items,
     * all in ONE transaction. A pair that is already stored keeps its row; only its certainty is brought in line
     * with the reviewed one. Animals are never removed and the alcohol flag is only ever set, not cleared.
     * Any failure rolls the whole batch back.
     */
    public void store(Map<Integer, Map<String, String>> animalsByItem, Set<Integer> alcoholItems) {
        String animalSql = "INSERT INTO inventory_item_animal (inventory_item_animal_item_id, inventory_item_animal_animal, "
                + "inventory_item_animal_certainty) VALUES (?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE inventory_item_animal_certainty = VALUES(inventory_item_animal_certainty)";
        String alcoholSql = "UPDATE inventory_item SET inventory_item_alcohol = TRUE WHERE inventory_item_id = ?";
        try (Connection con = Database.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement animals = con.prepareStatement(animalSql);
                 PreparedStatement alcohol = con.prepareStatement(alcoholSql)) {
                for (Map.Entry<Integer, Map<String, String>> e : animalsByItem.entrySet()) {
                    for (Map.Entry<String, String> animal : e.getValue().entrySet()) {
                        animals.setInt(1, e.getKey());
                        animals.setString(2, animal.getKey());
                        animals.setString(3, animal.getValue());
                        animals.executeUpdate();
                    }
                }
                for (int id : alcoholItems) {
                    alcohol.setInt(1, id);
                    alcohol.executeUpdate();
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
                items.add(new CatalogItem(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getBoolean(5)));
            }
        }
        return items;
    }

    static String placeholders(int count) {
        return String.join(", ", java.util.Collections.nCopies(count, "?"));
    }
}
