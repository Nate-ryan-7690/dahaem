package de.dahaem.mealplanner;

import java.time.LocalDate;

/**
 * What to change on a planned meal. A field that was not sent keeps the meal's value. The three that can
 * be cleared (servings: back to the default, label, note) carry a flag, because "not sent" and "sent as
 * null" mean different things. Build it with the {@code with...} methods.
 */
public final class MealChange {

    private Integer recipeId;
    private LocalDate date;
    private Integer position;
    private boolean setServings;
    private Integer servings;
    private boolean setLabel;
    private String label;
    private boolean setNote;
    private String note;

    public MealChange withRecipeId(int recipeId) { this.recipeId = recipeId; return this; }

    public MealChange withDate(LocalDate date) { this.date = date; return this; }

    public MealChange withPosition(int position) { this.position = position; return this; }

    /** null clears the meal's own number, so it follows the default again. */
    public MealChange withServings(Integer servings) { this.setServings = true; this.servings = servings; return this; }

    public MealChange withLabel(String label) { this.setLabel = true; this.label = label; return this; }

    public MealChange withNote(String note) { this.setNote = true; this.note = note; return this; }

    public Integer recipeId() { return recipeId; }

    public LocalDate date() { return date; }

    public Integer position() { return position; }

    public boolean setServings() { return setServings; }

    public Integer servings() { return servings; }

    public boolean setLabel() { return setLabel; }

    public String label() { return label; }

    public boolean setNote() { return setNote; }

    public String note() { return note; }

    public boolean isEmpty() {
        return recipeId == null && date == null && position == null && !setServings && !setLabel && !setNote;
    }
}
