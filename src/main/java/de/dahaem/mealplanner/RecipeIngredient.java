package de.dahaem.mealplanner;

public class RecipeIngredient {

    private final int itemId;
    private final int recipeId;
    private final String unit;
    private final int quantityNeeded;
    private final String nameEnglish;
    private final String nameDeutsch;

    public RecipeIngredient(int itemId, int recipeId, String unit,
                            int quantityNeeded, String nameEnglish,
                            String nameDeutsch) {

        this.itemId = itemId;
        this.recipeId = recipeId;
        this.unit = unit;
        this.quantityNeeded = quantityNeeded;
        this.nameEnglish = nameEnglish;
        this.nameDeutsch = nameDeutsch;
    }

    public int getItemId() {return itemId;}
    public int getRecipeId(){return recipeId;}
    public String getUnit(){return unit;}
    public int getQuantityNeeded(){return quantityNeeded;}
    public String getNameEnglish(){return nameEnglish;}
    public String getNameDeutsch(){return nameDeutsch;}

    @Override
    public String toString() {
        return "RecipeIngredient{" +
                "ingredientId=" + itemId +
                ", recipeId=" + recipeId +
                ", unit='" + unit + '\'' +
                ", quantityNeeded=" + quantityNeeded +
                ", nameEnglish='" + nameEnglish + '\'' +
                ", nameDeutsch='" + nameDeutsch + '\'' +
                "}";
    }
}
