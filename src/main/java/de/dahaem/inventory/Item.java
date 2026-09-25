package de.dahaem.inventory;

import java.time.LocalDate;

/**
 * Plain data holder for one row of the inventory_item table.
 * No logic here on purpose: just the fields, a constructor, and getters.
 */
public class Item {

    private final int id;
    private final String nameEnglish;
    private final String nameDeutsch;   // may be null
    private final int quantity;
    private final String unit;
    private final String category;
    private final String subcategory;   // may be null
    private final LocalDate earliestExpDate; // may be null

    public Item(int id, String nameEnglish, String nameDeutsch, int quantity,
                String unit, String category, String subcategory, LocalDate earliestExpDate) {
        this.id = id;
        this.nameEnglish = nameEnglish;
        this.nameDeutsch = nameDeutsch;
        this.quantity = quantity;
        this.unit = unit;
        this.category = category;
        this.subcategory = subcategory;
        this.earliestExpDate = earliestExpDate;
    }

    public int getId() {
        return id;
    }

    public String getNameEnglish() {
        return nameEnglish;
    }

    public String getNameDeutsch() {
        return nameDeutsch;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getUnit() {
        return unit;
    }

    public String getCategory() {
        return category;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public LocalDate getEarliestExpDate() {
        return earliestExpDate;
    }

    @Override
    public String toString() {
        return "Item{" +
                "id=" + id +
                ", name='" + nameEnglish + '\'' +
                ", quantity=" + quantity + " " + unit +
                ", category='" + category + '\'' +
                ", expires=" + earliestExpDate +
                '}';
    }
}
