package de.dahaem.tags;

import java.util.List;

/**
 * Business logic for tags. No SQL here, no UI here.
 * It asks the repository for data and answers questions about it.
 */

public class TagService {
    private final TagRepository repository;

    public TagService(TagRepository repository){ this.repository = repository; }

    // Finds or creates the tag, then links it to the recipe.
    public Tag addTagToRecipe(int recipeId, String name, String category){
        Tag tag = repository.findOrCreateTag(name, category);
        repository.tagRecipe(recipeId, tag.getId());
        return tag;
    }

    public List<Tag> tagsForRecipe(int recipeId){ return repository.tagsForRecipe(recipeId); }

    public List<TagRepository.RecipeTag> recipeTagsForRecipe(int recipeId){ return repository.recipeTagsForRecipe(recipeId); }

    // Tag names per recipe id, for every tagged recipe, in one query.
    public java.util.Map<Integer, List<String>> tagNamesByRecipe(){ return repository.tagNamesByRecipe(); }

    // Finds the ids of every recipe carrying a tag with this name.
    public List<Integer> recipesWithTag(String name){
        return repository.findByName(name)
                .map(tag -> repository.recipesForTag(tag.getId()))
                .orElseGet(List::of);
    }
}
