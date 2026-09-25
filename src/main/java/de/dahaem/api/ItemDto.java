package de.dahaem.api;

import de.dahaem.inventory.Item;

import java.time.LocalDate;

/** One inventory item as returned by /api/inventory. */
public record ItemDto(int id, String nameEnglish, String nameDeutsch, int quantity, String unit,
                      String category, String subcategory, LocalDate earliestExpDate) {

    static ItemDto from(Item item) {
        return new ItemDto(item.getId(), item.getNameEnglish(), item.getNameDeutsch(), item.getQuantity(),
                item.getUnit(), item.getCategory(), item.getSubcategory(), item.getEarliestExpDate());
    }
}
