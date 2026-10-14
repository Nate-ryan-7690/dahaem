package de.dahaem.tags;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What keeps a recipe from being vegetarian / vegan, on items from the real catalogue. */
class AnimalProductsTest {

    private static CatalogItem item(String name, String category, String subcategory) {
        return new CatalogItem(1, name, category, subcategory);
    }

    private static boolean notVegetarian(String name, String category, String subcategory) {
        return AnimalProducts.vegetarianBlocker(item(name, category, subcategory), false).isPresent();
    }

    private static boolean notVegan(String name, String category, String subcategory) {
        return AnimalProducts.veganBlocker(item(name, category, subcategory)).isPresent();
    }

    @Test
    void meatAndFishCategoriesBlockVegetarian() {
        assertTrue(notVegetarian("salami", "meat", "wurst & aufschnitt"));
        assertTrue(notVegetarian("Guanciale", "meat & poultry", ""));
        assertTrue(notVegetarian("lachsfilet", "fish & seafood", "fresh fish"));
        assertTrue(notVegetarian("sardinen in öl dose", "canned & jarred goods", "canned fish"));
        assertTrue(notVegetarian("bifi original", "snacks", "jerky & meat snacks"));
        assertTrue(notVegetarian("Backfisch", "frozen", "fish"));
    }

    @Test
    void anItemWithARecordedAnimalBlocksVegetarianWhateverItIsCalled() {
        assertTrue(AnimalProducts.vegetarianBlocker(item("Dino-Nuggets", "frozen", "poultry products"), true)
                .isPresent());
        assertTrue(AnimalProducts.vegetarianBlocker(item("Mystery", "pantry", ""), true).isPresent());
    }

    @Test
    void hiddenAnimalIngredientsOutsideMeatCategoriesBlockVegetarian() {
        assertTrue(notVegetarian("hühnerbrühe würfel", "dry goods", "dried soup & stock"));
        assertTrue(notVegetarian("rinderfond", "condiments & sauces", ""));
        assertTrue(notVegetarian("gelatineblätter", "dry goods", "thickeners & starches"));
        assertTrue(notVegetarian("fischsoße", "condiments & sauces", "asian sauces"));
        assertTrue(notVegetarian("tortellini schinken", "pasta", "filled pasta"));
        assertTrue(notVegetarian("worcestershire sauce", "condiments & sauces", "other condiments"));
    }

    @Test
    void aCurrywurstSauceIsASauceWithoutMeat() {
        assertFalse(notVegetarian("currywurst sauce", "condiments & sauces", "ketchup & tomato sauces"));
        assertFalse(notVegetarian("maggi fix currywurst sauce", "spices & seasonings",
                "fix products & seasoning sachets"));
        assertFalse(notVegetarian("Currywurst Soße", "condiments & sauces", ""));
        assertFalse(AnimalClassifier.isAnimalCandidate(new CatalogItem(1, "currywurst sauce", "condiments & sauces",
                "ketchup & tomato sauces")));
        // the sausage itself still blocks
        assertTrue(notVegetarian("currywurst", "meat", "würstchen"));
        assertTrue(AnimalClassifier.isAnimalCandidate(new CatalogItem(1, "currywurst", "meat", "würstchen")));
        assertTrue(notVegetarian("currywurst im glas", "canned & jarred goods", ""));
    }

    @Test
    void plantBasedAndLookAlikesDoNotBlockVegetarian() {
        assertFalse(notVegetarian("vegane Filetstücke Hähnchen-Art", "plant-based", ""));
        assertFalse(notVegetarian("veganes Hack", "plant-based", ""));
        assertFalse(notVegetarian("geflügelgewürz", "spices & seasonings", "spice blends & mixes"));
        assertFalse(notVegetarian("hirschhornsalz", "baking supplies", "raising agents & yeasts"));
        assertFalse(notVegetarian("wildreis", "grains & rice", "rice"));
        assertFalse(notVegetarian("butterschmalz", "dairy", ""));
        assertFalse(notVegetarian("gemüsebrühe pulver", "dry goods", "dried soup & stock"));
        assertFalse(notVegetarian("karroten", "vegetable", ""));
        assertFalse(notVegetarian("fleischtomate", "vegetable", ""));
        assertFalse(notVegetarian("austernpilz", "vegetable", ""));
    }

    @Test
    void dairyEggAndHoneyBlockVegan() {
        assertTrue(notVegan("vollmilch", "dairy", ""));
        assertTrue(notVegan("butter", "dairy", ""));
        assertTrue(notVegan("gouda", "dairy", "cheese"));
        assertTrue(notVegan("Eier", "dairy", ""));
        assertTrue(notVegan("eiernudeln", "pasta", "egg pasta"));
        assertTrue(notVegan("honig", "baking supplies", "sugars & sweeteners"));
        assertTrue(notVegan("mayonnaise", "condiments & sauces", "mustard & mayonnaise"));
        assertTrue(notVegan("croissant", "bread & bakery", "pastry & sweet bakery"));
        assertTrue(notVegan("tortellini käse", "pasta", "filled pasta"));
        assertTrue(notVegan("vanille puddingpulver", "baking supplies", "instant mixes & puddings"));
    }

    @Test
    void plantAlternativesAndLookAlikesDoNotBlockVegan() {
        assertFalse(notVegan("hafermilch", "dairy", ""));
        assertFalse(notVegan("sojamilch", "dairy", ""));
        assertFalse(notVegan("pflanzliche butter", "dairy", ""));
        assertFalse(notVegan("margarine", "dairy", ""));
        assertFalse(notVegan("kokosjoghurt", "dairy", "yogurt"));
        assertFalse(notVegan("vegane mayonnaise", "condiments & sauces", "mustard & mayonnaise"));
        assertFalse(notVegan("Tofu Natur", "plant-based", ""));
        assertFalse(notVegan("butternut kürbis", "vegetable", ""));
        assertFalse(notVegan("honigmelone", "fruit", ""));
        assertFalse(notVegan("sahnesteif", "baking supplies", "raising agents & yeasts"));
        assertFalse(notVegan("eisbergsalat", "vegetable", ""));
        assertFalse(notVegan("erdnussbutter", "snacks", "dips & spreads"));
        assertFalse(notVegan("pasta (spaghetti)", "pasta", "dried pasta"));
        assertFalse(notVegan("reiswaffeln natur", "snacks", "crackers & rice cakes"));
        assertTrue(notVegan("reiswaffeln vollmilch", "snacks", "crackers & rice cakes"));
    }
}
