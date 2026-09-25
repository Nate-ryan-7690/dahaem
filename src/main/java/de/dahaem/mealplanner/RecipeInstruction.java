package de.dahaem.mealplanner;

public class RecipeInstruction {

    private final int stepId;
    private final int recipeId;
    private final int stepNumber;
    private final String instruction;
    private final String notes;
    private final String source;

    public RecipeInstruction(int stepId, int recipeId, int stepNumber, String instruction, String notes, String source){
        this.stepId = stepId;
        this.recipeId = recipeId;
        this.stepNumber = stepNumber;
        this.instruction = instruction;
        this.notes = notes;
        this.source = source;
    }

    public int getStepId(){return stepId;}
    public int getRecipeId(){return recipeId;}
    public int getStepNumber(){return stepNumber;}
    public String getInstruction(){return instruction;}
    public String getNotes(){return notes;}
    public String getSource(){return source;}

    @Override
    public String toString() {
        return "RecipeInstruction{" +
                "stepId=" + stepId +
                ", recipeId=" + recipeId +
                ", stepNumber=" + stepNumber +
                ", instruction='" + instruction + '\'' +
                ", notes='" + notes + '\'' +
                ", source='" + source + '\'' +
                '}';
    }
}
