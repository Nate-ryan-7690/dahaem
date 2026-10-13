package de.dahaem.mealplanner;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Plain data holder for one row of the Recipe table.
 * No logic here on purpose just the fields, a constructor, and getters.
 */

public class Recipe {

    private final int id;
    private final String name;
    private final int servings;
    private final Integer servingsMax;
    private final int cookTime;
    private final int prepTime;
    private final Integer calories;
    private final Integer protein;
    private final Integer carbs;
    private final Integer fat;
    private final Integer fiber;
    private final Integer sugar;
    private final String description;
    private final String imageUrl;
    private final String thumbnailUrl;
    private final String imageSource;
    private final String cuisine;
    private final BigDecimal ratingAvg;
    private final Integer ratingCount;
    private final LocalDate publishedDate;

    public Recipe(int id, String name, int servings, Integer servingsMax, int cookTime, int prepTime,
                  Integer calories, Integer protein, Integer carbs, Integer fat, Integer fiber, Integer sugar,
                  String description, String imageUrl, String thumbnailUrl, String imageSource, String cuisine,
                  BigDecimal ratingAvg, Integer ratingCount, LocalDate publishedDate) {
        this.id = id;
        this.name = name;
        this.servings = servings;
        this.servingsMax = servingsMax;
        this.cookTime = cookTime;
        this.prepTime = prepTime;
        this.calories = calories;
        this.protein = protein;
        this.carbs = carbs;
        this.fat = fat;
        this.fiber = fiber;
        this.sugar = sugar;
        this.description = description;
        this.imageUrl = imageUrl;
        this.thumbnailUrl = thumbnailUrl;
        this.imageSource = imageSource;
        this.cuisine = cuisine;
        this.ratingAvg = ratingAvg;
        this.ratingCount = ratingCount;
        this.publishedDate = publishedDate;
    }

    public int getId(){ return id;}

    public String getName(){ return name;}

    public int getServings(){ return servings;}

    // Upper bound of a "3 – 4 Personen" range (servings is the lower bound); null when not a range.
    public Integer getServingsMax(){ return servingsMax;}

    public int getCookTime(){ return cookTime;}

    public int getPrepTime(){ return prepTime;}

    public Integer getCalories(){ return calories;}

    public Integer getProtein(){ return protein;}

    public Integer getCarbs(){ return carbs;}

    public Integer getFat(){ return fat;}

    public Integer getFiber(){ return fiber;}

    public Integer getSugar(){ return sugar;}

    // Enrichment from the recipe page (migration 0010); each is null when unknown.
    public String getDescription(){ return description;}

    // Linked, never copied: the browser loads the image from its source.
    public String getImageUrl(){ return imageUrl;}

    public String getThumbnailUrl(){ return thumbnailUrl;}

    // Where the image comes from, so it can be replaced in bulk: 'hellofresh' | 'own' | 'generated'; null without an image.
    public String getImageSource(){ return imageSource;}

    // One clean German cuisine name.
    public String getCuisine(){ return cuisine;}

    public BigDecimal getRatingAvg(){ return ratingAvg;}

    public Integer getRatingCount(){ return ratingCount;}

    public LocalDate getPublishedDate(){ return publishedDate;}

    @Override
    public String toString() {
        return "Recipe{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", servings=" + servings +
                ", servingsMax=" + servingsMax +
                ", cookTime=" + cookTime +
                ", prepTime=" + prepTime +
                ", calories=" + calories +
                ", protein=" + protein +
                ", carbs=" + carbs +
                ", fat=" + fat +
                ", fiber=" + fiber +
                ", sugar=" + sugar +
                ", cuisine=" + cuisine +
                "}";
    }

}
