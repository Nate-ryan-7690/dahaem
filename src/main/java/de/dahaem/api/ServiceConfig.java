package de.dahaem.api;

import de.dahaem.auth.AccountRepository;
import de.dahaem.auth.AccountService;
import de.dahaem.diet.DietFilterRepository;
import de.dahaem.diet.DietFilterService;
import de.dahaem.diet.DietPresets;
import de.dahaem.finance.PurchaseRepository;
import de.dahaem.finance.PurchaseService;
import de.dahaem.inventory.InventoryRepository;
import de.dahaem.inventory.InventoryService;
import de.dahaem.mealplanner.MealPlanRepository;
import de.dahaem.mealplanner.MealPlanService;
import de.dahaem.mealplanner.RecipeRepository;
import de.dahaem.mealplanner.RecipeSearchService;
import de.dahaem.mealplanner.RecipeService;
import de.dahaem.shopping.ShoppingListRepository;
import de.dahaem.shopping.ShoppingListService;
import de.dahaem.stores.StoreRepository;
import de.dahaem.suggestions.SuggestionRepository;
import de.dahaem.suggestions.SuggestionService;
import de.dahaem.tags.TagRepository;
import de.dahaem.tags.TagService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Wires the existing plain-Java repositories and services as Spring beans.
 * The classes themselves stay free of Spring annotations; they are built here exactly
 * the way the console runners build them, just once per application.
 */
@Configuration
public class ServiceConfig {

    @Bean
    public InventoryRepository inventoryRepository() {
        return new InventoryRepository();
    }

    @Bean
    public InventoryService inventoryService(InventoryRepository inventoryRepository) {
        return new InventoryService(inventoryRepository);
    }

    @Bean
    public RecipeRepository recipeRepository() {
        return new RecipeRepository();
    }

    @Bean
    public RecipeService recipeService(RecipeRepository recipeRepository) {
        return new RecipeService(recipeRepository);
    }

    @Bean
    public MealPlanRepository mealPlanRepository() {
        return new MealPlanRepository();
    }

    @Bean
    public MealPlanService mealPlanService(MealPlanRepository mealPlanRepository) {
        return new MealPlanService(mealPlanRepository);
    }

    @Bean
    public TagRepository tagRepository() {
        return new TagRepository();
    }

    @Bean
    public TagService tagService(TagRepository tagRepository) {
        return new TagService(tagRepository);
    }

    @Bean
    public ShoppingListRepository shoppingListRepository() {
        return new ShoppingListRepository();
    }

    @Bean
    public ShoppingListService shoppingListService(ShoppingListRepository shoppingListRepository,
                                                     MealPlanRepository mealPlanRepository,
                                                     InventoryRepository inventoryRepository) {
        return new ShoppingListService(shoppingListRepository, mealPlanRepository, inventoryRepository);
    }

    @Bean
    public StoreRepository storeRepository() {
        return new StoreRepository();
    }

    @Bean
    public PurchaseRepository purchaseRepository() {
        return new PurchaseRepository();
    }

    @Bean
    public PurchaseService purchaseService(PurchaseRepository purchaseRepository, StoreRepository storeRepository,
                                           InventoryRepository inventoryRepository) {
        return new PurchaseService(purchaseRepository, storeRepository, inventoryRepository);
    }

    @Bean
    public AccountRepository accountRepository() {
        return new AccountRepository();
    }

    @Bean
    public AccountService accountService(AccountRepository accountRepository, PasswordEncoder passwordEncoder) {
        return new AccountService(accountRepository, passwordEncoder);
    }

    @Bean
    public SuggestionRepository suggestionRepository() {
        return new SuggestionRepository();
    }

    @Bean
    public SuggestionService suggestionService(SuggestionRepository suggestionRepository) {
        return new SuggestionService(suggestionRepository);
    }

    @Bean
    public DietFilterRepository dietFilterRepository() {
        return new DietFilterRepository();
    }

    /** Presets come from config/diet_presets.json (relative to the working directory, /app in the container). */
    @Bean
    public DietPresets dietPresets() {
        return DietPresets.load(DietPresets.DEFAULT_FILE);
    }

    @Bean
    public DietFilterService dietFilterService(DietFilterRepository dietFilterRepository, DietPresets dietPresets,
                                               @org.springframework.beans.factory.annotation.Value("${dahaem.diet.cache-seconds:60}") long cacheSeconds) {
        return new DietFilterService(dietFilterRepository, dietPresets, cacheSeconds, System::nanoTime);
    }

    @Bean
    public RecipeSearchService recipeSearchService(RecipeRepository recipeRepository, TagService tagService,
                                                   DietFilterService dietFilterService) {
        return new RecipeSearchService(recipeRepository, tagService, dietFilterService);
    }
}
