package de.dahaem.api;

import de.dahaem.auth.AccountRepository;
import de.dahaem.auth.AccountSelfService;
import de.dahaem.auth.AccountService;
import de.dahaem.auth.HouseholdEquipmentRepository;
import de.dahaem.auth.HouseholdEquipmentService;
import de.dahaem.auth.InvitationRepository;
import de.dahaem.auth.InvitationService;
import de.dahaem.auth.LoginService;
import de.dahaem.auth.LoginThrottle;
import de.dahaem.diet.DietFilterRepository;
import de.dahaem.diet.DietFilterService;
import de.dahaem.diet.DietPresets;
import de.dahaem.barcode.BarcodeService;
import de.dahaem.barcode.IntakeRequestRepository;
import de.dahaem.barcode.IntakeService;
import de.dahaem.barcode.OpenFoodFactsSource;
import de.dahaem.barcode.ProductBarcodeRepository;
import de.dahaem.barcode.ProductDataSource;
import de.dahaem.barcode.RateLimitedSource;
import de.dahaem.finance.PurchaseRepository;
import de.dahaem.finance.PurchaseService;
import de.dahaem.finance.StockService;
import de.dahaem.inventory.InventoryRepository;
import de.dahaem.inventory.InventoryService;
import de.dahaem.mealplanner.MealPlanRepository;
import de.dahaem.mealplanner.MealPlanService;
import de.dahaem.mealplanner.RecipeRepository;
import de.dahaem.mealplanner.RecipeSearchService;
import de.dahaem.mealplanner.RecipeService;
import de.dahaem.shopping.ShoppingListRepository;
import de.dahaem.shopping.ShoppingListService;
import de.dahaem.stores.StoreCatalogService;
import de.dahaem.stores.StoreRepository;
import de.dahaem.suggestions.PantrySuggestionService;
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
    public MealPlanService mealPlanService(MealPlanRepository mealPlanRepository,
                                           @org.springframework.beans.factory.annotation.Value("${dahaem.mealplan.zone:Europe/Berlin}") String zone) {
        // "today" (which days are past) is the date in the household's time zone, not the server's
        return new MealPlanService(mealPlanRepository, java.time.Clock.system(java.time.ZoneId.of(zone)));
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
    public ProductBarcodeRepository productBarcodeRepository() {
        return new ProductBarcodeRepository();
    }

    /**
     * Where unknown barcodes are looked up: {@code dahaem.barcode.lookup=openfoodfacts} (env DAHAEM_BARCODE_LOOKUP) asks
     * Open Food Facts, with only the number sent; the default is off, so nothing leaves the server unless it is switched on.
     */
    @Bean
    public ProductDataSource productDataSource(
            @org.springframework.beans.factory.annotation.Value("${dahaem.barcode.lookup:off}") String mode,
            @org.springframework.beans.factory.annotation.Value("${dahaem.barcode.openfoodfacts-url:https://world.openfoodfacts.org}") String url,
            @org.springframework.beans.factory.annotation.Value("${dahaem.barcode.lookups-per-minute:30}") int perMinute) {
        return "openfoodfacts".equalsIgnoreCase(mode.trim())
                ? new RateLimitedSource(new OpenFoodFactsSource(url), perMinute, System::nanoTime)
                : ProductDataSource.disabled();
    }

    @Bean
    public BarcodeService barcodeService(ProductBarcodeRepository productBarcodeRepository,
                                         InventoryRepository inventoryRepository, StoreRepository storeRepository,
                                         ProductDataSource productDataSource) {
        return new BarcodeService(productBarcodeRepository, inventoryRepository, storeRepository, productDataSource);
    }

    @Bean
    public LoginThrottle loginThrottle() {
        return new LoginThrottle(System::nanoTime);
    }

    @Bean
    public LoginService loginService(AccountService accountService, LoginThrottle loginThrottle) {
        return new LoginService(accountService, loginThrottle);
    }

    @Bean
    public AccountSelfService accountSelfService(AccountService accountService, LoginThrottle loginThrottle) {
        return new AccountSelfService(accountService, loginThrottle);
    }

    @Bean
    public HouseholdEquipmentRepository householdEquipmentRepository() {
        return new HouseholdEquipmentRepository();
    }

    @Bean
    public HouseholdEquipmentService householdEquipmentService(HouseholdEquipmentRepository householdEquipmentRepository) {
        return new HouseholdEquipmentService(householdEquipmentRepository);
    }

    @Bean
    public InvitationRepository invitationRepository() {
        return new InvitationRepository();
    }

    @Bean
    public InvitationService invitationService(InvitationRepository invitationRepository,
                                               AccountRepository accountRepository, AccountService accountService,
                                               LoginThrottle loginThrottle) {
        return new InvitationService(invitationRepository, accountRepository, accountService, loginThrottle,
                java.time.Clock.systemUTC());
    }

    @Bean
    public StoreCatalogService storeCatalogService(StoreRepository storeRepository) {
        return new StoreCatalogService(storeRepository);
    }

    @Bean
    public IntakeRequestRepository intakeRequestRepository() {
        return new IntakeRequestRepository();
    }

    @Bean
    public IntakeService intakeService(ProductBarcodeRepository productBarcodeRepository,
                                       PurchaseService purchaseService, StoreRepository storeRepository,
                                       IntakeRequestRepository intakeRequestRepository) {
        return new IntakeService(productBarcodeRepository, purchaseService, storeRepository, intakeRequestRepository);
    }

    @Bean
    public StockService stockService(PurchaseRepository purchaseRepository, InventoryRepository inventoryRepository) {
        return new StockService(purchaseRepository, inventoryRepository);
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
    public PantrySuggestionService pantrySuggestionService(SuggestionRepository suggestionRepository,
                                                           RecipeRepository recipeRepository, TagService tagService,
                                                           DietFilterService dietFilterService,
                                                           @org.springframework.beans.factory.annotation.Value("${dahaem.mealplan.zone:Europe/Berlin}") String zone) {
        // "today" (what is on sale, what expires) is the date in the household's time zone, not the server's
        return new PantrySuggestionService(suggestionRepository, recipeRepository, tagService, dietFilterService,
                java.time.Clock.system(java.time.ZoneId.of(zone)));
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
