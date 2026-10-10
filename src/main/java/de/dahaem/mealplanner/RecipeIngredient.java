package de.dahaem.mealplanner;

import java.math.BigDecimal;

public class RecipeIngredient {

    private final int itemId;
    private final int recipeId;
    private final String unit;
    private final BigDecimal quantityNeeded;
    private final boolean toTaste;
    private final String nameEnglish;
    private final String nameDeutsch;

    /**
     * {@code quantityNeeded} is the exact stored amount (e.g. 0.5 Stück), or null for a "to taste"
     * ingredient ({@code toTaste} true): the recipe needs the item but names no amount.
     */
    public RecipeIngredient(int itemId, int recipeId, String unit,
                            BigDecimal quantityNeeded, boolean toTaste, String nameEnglish,
                            String nameDeutsch) {

        this.itemId = itemId;
        this.recipeId = recipeId;
        this.unit = unit;
        this.quantityNeeded = quantityNeeded;
        this.toTaste = toTaste;
        this.nameEnglish = nameEnglish;
        this.nameDeutsch = nameDeutsch;
    }

    public int getItemId() {return itemId;}
    public int getRecipeId(){return recipeId;}
    public String getUnit(){return unit;}
    public BigDecimal getQuantityNeeded(){return quantityNeeded;}
    public boolean isToTaste(){return toTaste;}
    public String getNameEnglish(){return nameEnglish;}
    public String getNameDeutsch(){return nameDeutsch;}

    @Override
    public String toString() {
        return "RecipeIngredient{" +
                "ingredientId=" + itemId +
                ", recipeId=" + recipeId +
                ", unit='" + unit + '\'' +
                ", quantityNeeded=" + quantityNeeded +
                ", toTaste=" + toTaste +
                ", nameEnglish='" + nameEnglish + '\'' +
                ", nameDeutsch='" + nameDeutsch + '\'' +
                "}";
    }
}
