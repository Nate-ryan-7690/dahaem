package de.dahaem.mealplanner;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Step amounts for a number of people. Every step text here is made up, written in HelloFresh's
 * notation ({@code 2-person value [3-person | 4-person]}); no recipe text is copied from the site.
 */
class StepAmountsTest {

    /** A normal 2-serving recipe, where HelloFresh's 2 / 3 / 4 values are trusted. */
    private static String two(String step, Integer people) {
        return StepAmounts.render(step, 2, null, people, false);
    }

    private static void assertSizes(String step, String p1, String p2, String p3, String p4, String p6) {
        assertEquals(p1, two(step, 1), "1 person");
        assertEquals(p2, two(step, 2), "2 people");
        assertEquals(p3, two(step, 3), "3 people");
        assertEquals(p4, two(step, 4), "4 people");
        assertEquals(p6, two(step, 6), "6 people");
    }

    // --- standard bracket ------------------------------------------------------------------------

    @Test
    void standardBracketUsesHelloFreshValuesFor2To4AndScalesOtherCounts() {
        assertSizes("Erhitze 1 EL [1,5 EL | 2 EL] Butter* in einer Pfanne.",
                "Erhitze ½ EL Butter* in einer Pfanne.",
                "Erhitze 1 EL Butter* in einer Pfanne.",
                "Erhitze 1½ EL Butter* in einer Pfanne.",
                "Erhitze 2 EL Butter* in einer Pfanne.",
                "Erhitze 3 EL Butter* in einer Pfanne.");
    }

    @Test
    void withoutPeopleTheBracketResolvesToThe2PersonValue() {
        assertEquals("Erhitze 1 EL Butter* in einer Pfanne.",
                two("Erhitze 1 EL [1,5 EL | 2 EL] Butter* in einer Pfanne.", null));
        assertEquals("Gib 150 g Reis und 300 ml Wasser in einen Topf.",
                two("Gib 150 g [225 g | 300 g] Reis und 300 ml [450 ml | 600 ml] Wasser in einen Topf.", null));
    }

    @Test
    void helloFreshValuesAreUsedEvenWhenNotLinear() {
        // 3 people get 250 g, not 1.5 × 150 g
        assertEquals("Koche 250 g Nudeln.", two("Koche 150 g [250 g | 300 g] Nudeln.", 3));
        assertEquals("Koche 300 g Nudeln.", two("Koche 150 g [250 g | 300 g] Nudeln.", 4));
        assertEquals("Koche 75 g Nudeln.", two("Koche 150 g [250 g | 300 g] Nudeln.", 1));
    }

    @Test
    void severalBracketsAndPlainAmountsInOneStep() {
        String step = "Verrühre 1 EL [1,5 EL | 2 EL] Senf, 2 TL [3 TL | 4 TL] Honig* und 50 ml Wasser*.";
        assertEquals("Verrühre 2 EL Senf, 4 TL Honig* und 100 ml Wasser*.", two(step, 4));
        assertEquals("Verrühre 1½ EL Senf, 3 TL Honig* und 75 ml Wasser*.", two(step, 3));
        assertEquals("Verrühre ½ EL Senf, 1 TL Honig* und 25 ml Wasser*.", two(step, 1));
    }

    // --- variants --------------------------------------------------------------------------------

    @Test
    void singleValueBracketIsThe4PersonValueAnd3PeopleScale() {
        assertSizes("Gib 10 g [20 g] Öl* in die Pfanne.",
                "Gib 5 g Öl* in die Pfanne.",
                "Gib 10 g Öl* in die Pfanne.",
                "Gib 15 g Öl* in die Pfanne.",
                "Gib 20 g Öl* in die Pfanne.",
                "Gib 30 g Öl* in die Pfanne.");
    }

    @Test
    void dotDecimalsInside() {
        assertEquals("Gib 1½ EL Essig dazu.", two("Gib 1 EL [1.5 EL | 2 EL] Essig dazu.", 3));
    }

    @Test
    void slashFractionsInside() {
        // "11/2" is HelloFresh's "1 1/2"
        assertEquals("Gib 1½ EL Zucker* dazu.", two("Gib 1 EL [11/2 EL | 2 EL] Zucker* dazu.", 3));
        assertEquals("Gib 1¾ TL Paprika dazu.", two("Gib 1 TL [13/4 TL | 2 TL] Paprika dazu.", 3));
        assertEquals("Gib ¾ TL Salz* dazu.", two("Gib ½ TL [3/4 TL | 1 TL] Salz* dazu.", 3));
        assertEquals("Gib 1½ EL Öl* dazu.", two("Gib 1 EL [1 1/2 EL | 2 EL] Öl* dazu.", 3));
    }

    @Test
    void capitalIAsSeparator() {
        assertEquals("Gib 1½ EL Öl* dazu.", two("Gib 1 EL [1,5 EL I 2 EL] Öl* dazu.", 3));
        assertEquals("Gib 2 EL Öl* dazu.", two("Gib 1 EL [1,5 EL I 2 EL] Öl* dazu.", 4));
    }

    @Test
    void noSpacesAroundTheSeparator() {
        assertEquals("Gieße 450 ml Wasser* an.", two("Gieße 300 ml [450 ml|600 ml] Wasser* an.", 3));
        assertEquals("Gieße 600 ml Wasser* an.", two("Gieße 300 ml [450 ml|600 ml] Wasser* an.", 4));
    }

    @Test
    void unicodeFractionsOutsideAndInsideWithTheUnitAfterTheBracket() {
        assertSizes("Erhitze ½ [¾ I 1] EL Öl* in einer Pfanne.",
                "Erhitze ¼ EL Öl* in einer Pfanne.",
                "Erhitze ½ EL Öl* in einer Pfanne.",
                "Erhitze ¾ EL Öl* in einer Pfanne.",
                "Erhitze 1 EL Öl* in einer Pfanne.",
                "Erhitze 1½ EL Öl* in einer Pfanne.");
    }

    @Test
    void differentUnitInside() {
        assertSizes("Schmelze 1 TL [1 EL] Butter.",
                "Schmelze ½ TL Butter.",
                "Schmelze 1 TL Butter.",
                "Schmelze 1½ TL Butter.",   // no 3-person value: scaled from 2 people
                "Schmelze 1 EL Butter.",
                "Schmelze 3 TL Butter.");
    }

    @Test
    void rangeInside() {
        assertSizes("Gib 2 EL [4 EL – 8 EL] Wasser* hinzu.",
                "Gib 1 EL Wasser* hinzu.",
                "Gib 2 EL Wasser* hinzu.",
                "Gib 3 EL Wasser* hinzu.",
                "Gib 4 – 8 EL Wasser* hinzu.",
                "Gib 6 EL Wasser* hinzu.");
    }

    @Test
    void straySpacesInside() {
        assertEquals("Gib ¾ EL Honig dazu.", two("Gib ½ EL [0.75 EL | 1 EL ] Honig dazu.", 3));
        assertEquals("Gib 1 EL Honig dazu.", two("Gib ½ EL [ 0.75 EL | 1 EL ] Honig dazu.", 4));
    }

    @Test
    void splitDecimalsInside() {
        assertEquals("Gib 1½ TL Curry dazu.", two("Gib 1 TL [1, 5 TL | 2 TL] Curry dazu.", 3));
    }

    @Test
    void countsWithoutUnits() {
        assertSizes("Forme 4 [6 | 8] ovale Frikadellen.",
                "Forme 2 ovale Frikadellen.",
                "Forme 4 ovale Frikadellen.",
                "Forme 6 ovale Frikadellen.",
                "Forme 8 ovale Frikadellen.",
                "Forme 12 ovale Frikadellen.");
        assertSizes("Gib den Saft von 1 [2 | 2] Zitronenspalten dazu.",
                "Gib den Saft von ½ Zitronenspalten dazu.",
                "Gib den Saft von 1 Zitronenspalten dazu.",
                "Gib den Saft von 2 Zitronenspalten dazu.",
                "Gib den Saft von 2 Zitronenspalten dazu.",
                "Gib den Saft von 3 Zitronenspalten dazu.");
    }

    @Test
    void unitAfterTheBracket() {
        assertSizes("Schmelze 15 [30] g Butter.",
                "Schmelze 7,5 g Butter.",
                "Schmelze 15 g Butter.",
                "Schmelze 22,5 g Butter.",
                "Schmelze 30 g Butter.",
                "Schmelze 45 g Butter.");
    }

    @Test
    void relativeWordsPickTheVariantFor2To4AndKeepThe2PersonTextOtherwise() {
        assertSizes("Gib die Hälfte [drei Viertel | gesamte] Sojasoße dazu.",
                "Gib die Hälfte Sojasoße dazu.",
                "Gib die Hälfte Sojasoße dazu.",
                "Gib die drei Viertel Sojasoße dazu.",
                "Gib die gesamte Sojasoße dazu.",
                "Gib die Hälfte Sojasoße dazu.");
        assertEquals("Gib die Hälfte Sojasoße dazu.",
                two("Gib die Hälfte [drei Viertel | gesamte] Sojasoße dazu.", null));
    }

    @Test
    void timesInBracketsArePickedButNeverScaled() {
        assertSizes("Backe alles ca. 18 Min. [19 Min. | 20 Min.] im Ofen.",
                "Backe alles ca. 18 Min. im Ofen.",
                "Backe alles ca. 18 Min. im Ofen.",
                "Backe alles ca. 19 Min. im Ofen.",
                "Backe alles ca. 20 Min. im Ofen.",
                "Backe alles ca. 18 Min. im Ofen.");
        // a single time value is the 4-person one; 3 people keep the 2-person time
        assertEquals("Gare es 10 Min.", two("Gare es 10 Min. [12 Min.]", 3));
        assertEquals("Gare es 12 Min.", two("Gare es 10 Min. [12 Min.]", 4));
    }

    // --- times, temperatures and settings: picked per size, never scaled -------------------------

    /** Checks people null / 1 / 2 / 3 / 4 / 6 on a 2-serving recipe. */
    private static void assertAllSizes(String step, String none, String p1, String p2, String p3, String p4,
                                       String p6) {
        assertEquals(none, two(step, null), "no people");
        assertSizes(step, p1, p2, p3, p4, p6);
    }

    @Test
    void timeUnitAfterASingleValueBracket() {
        assertAllSizes("Backe 10 [12] Min. im Ofen.",
                "Backe 10 Min. im Ofen.",
                "Backe 10 Min. im Ofen.",
                "Backe 10 Min. im Ofen.",
                "Backe 10 Min. im Ofen.",   // no 3-person value, and times are not scaled
                "Backe 12 Min. im Ofen.",
                "Backe 10 Min. im Ofen.");
    }

    @Test
    void timeUnitAfterATwoValueBracket() {
        assertAllSizes("Backe 10 [12 | 14] Minuten im Ofen.",
                "Backe 10 Minuten im Ofen.",
                "Backe 10 Minuten im Ofen.",
                "Backe 10 Minuten im Ofen.",
                "Backe 12 Minuten im Ofen.",
                "Backe 14 Minuten im Ofen.",
                "Backe 10 Minuten im Ofen.");
    }

    @Test
    void stufeBeforeASingleValueBracket() {
        assertAllSizes("Rühre auf Stufe 2 [3] um.",
                "Rühre auf Stufe 2 um.",
                "Rühre auf Stufe 2 um.",
                "Rühre auf Stufe 2 um.",
                "Rühre auf Stufe 2 um.",
                "Rühre auf Stufe 3 um.",
                "Rühre auf Stufe 2 um.");
    }

    @Test
    void stufeBeforeATwoValueBracket() {
        assertAllSizes("Rühre auf Stufe 2 [3 | 4] um.",
                "Rühre auf Stufe 2 um.",
                "Rühre auf Stufe 2 um.",
                "Rühre auf Stufe 2 um.",
                "Rühre auf Stufe 3 um.",
                "Rühre auf Stufe 4 um.",
                "Rühre auf Stufe 2 um.");
    }

    @Test
    void temperatureAfterTheBracket() {
        assertAllSizes("Backe alles bei 200 [220] °C.",
                "Backe alles bei 200 °C.",
                "Backe alles bei 200 °C.",
                "Backe alles bei 200 °C.",
                "Backe alles bei 200 °C.",
                "Backe alles bei 220 °C.",
                "Backe alles bei 200 °C.");
    }

    @Test
    void nonAmountBracketsInARangeRecipeKeepThe2PersonValue() {
        assertEquals("Backe 10 Min.", StepAmounts.render("Backe 10 [12] Min.", 3, 4, 6, false));
        assertEquals("Rühre auf Stufe 2 um.", StepAmounts.render("Rühre auf Stufe 2 [3] um.", 3, 4, 6, true));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 6})
    void settingWithAnAmountInsideIsUnreadable(int people) {
        String step = "Rühre auf Stufe 2 [3 EL] um.";
        assertEquals(step, two(step, people));
    }

    // --- pack sizes in parentheses ------------------------------------------------------------------

    @Test
    void packSizeInParenthesesIsNeverScaled() {
        String feta = "Zerbrösele 1 Packung Feta (200 g) über den Salat.";
        assertAllSizes(feta, feta, feta, feta, feta, feta, feta);
        String tomatoes = "Gib 1 Dose (400 g) Tomaten in den Topf.";
        assertAllSizes(tomatoes, tomatoes, tomatoes, tomatoes, tomatoes, tomatoes, tomatoes);
    }

    @Test
    void amountsBesideAPackSizeStillScale() {
        assertEquals("Gib 1 Dose (400 g) Tomaten und 200 ml Wasser* dazu.",
                two("Gib 1 Dose (400 g) Tomaten und 100 ml Wasser* dazu.", 4));
    }

    // --- left unchanged --------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 6})
    void freeTextInBracketsStaysAsWritten(int people) {
        String step = "Gib 500 ml Wasser* in den Mixtopf [und im Varoma-Behälter] und koche 20 Min.";
        String expectedWater = switch (people) {
            case 1 -> "250 ml";
            case 3 -> "750 ml";
            case 4 -> "1000 ml";
            case 6 -> "1500 ml";
            default -> "500 ml";
        };
        assertEquals("Gib " + expectedWater + " Wasser* in den Mixtopf [und im Varoma-Behälter] und koche 20 Min.",
                two(step, people));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 6})
    void brokenNumbersInsideStayExactlyAsWritten(int people) {
        String step = "Gib 0,5 TL [0,7´25 TL | 1 TL] Zucker* dazu.";
        assertEquals(step, two(step, people));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 6})
    void aMissingClosingBracketLeavesTheWholeStepAsWritten(int people) {
        String step = "Gib 1 EL [1,5 EL | 2 EL Butter* und 200 g Mehl in die Schüssel.";
        assertEquals(step, two(step, people));
        String reversed = "Gib 1 EL 1,5 EL | 2 EL] Butter* dazu.";
        assertEquals(reversed, two(reversed, people));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 6})
    void unreadableBracketsStayAsWritten(int people) {
        for (String step : new String[]{
                "Gib 1 EL [1,5 EL | 2 EL | 3 EL] Öl* dazu.",       // three values: no known size mapping
                "Gib 1 EL [ | 2 EL] Öl* dazu.",                     // empty value
                "Gib 15 [30 g] g Butter dazu.",                     // unit inside and after
                "Backe 10 Min. [2 EL] lang.",                       // time outside, amount inside
                "Gib die Hälfte [etwas mehr] Soße dazu.",           // words that aren't size words
                "Gib drei Viertel [gesamte] Soße dazu.",            // outer of two words
                "Gib 1 EL Öl* [1,5 EL | 2 EL] dazu.",               // the name sits between amount and bracket
                "Gib 1 EL [] Öl* dazu."}) {
            assertEquals(step, two(step, people), step);
        }
    }

    @Test
    void aFailedBracketKeepsItsOwnAmountButOtherAmountsStillScale() {
        assertEquals("Gib 1 EL [1,5 EL | 2 EL | 3 EL] Öl* und 2 EL Essig dazu.",
                two("Gib 1 EL [1,5 EL | 2 EL | 3 EL] Öl* und 1 EL Essig dazu.", 4));
        assertEquals("Gib 200 g Mehl dazu, dann 1 EL Öl* [1,5 EL | 2 EL] und 2 EL Essig.",
                two("Gib 100 g Mehl dazu, dann 1 EL Öl* [1,5 EL | 2 EL] und 1 EL Essig.", 4));
    }

    // --- amounts without brackets ----------------------------------------------------------------

    @Test
    void plainAmountsWithAMeasuringUnitScale() {
        assertEquals("Erhitze 1½ EL Öl.", two("Erhitze 1 EL Öl.", 3));
        assertEquals("Gib 750 g Wasser* dazu.", two("Gib 500 g Wasser* dazu.", 3));
        assertEquals("Gib 250 g Wasser* dazu.", two("Gib 500 g Wasser* dazu.", 1));
        assertEquals("Gib 1,5 kg Kartoffeln dazu.", two("Gib 1 kg Kartoffeln dazu.", 3));
        assertEquals("Gib 0,75 l Brühe dazu.", two("Gib 0,5 l Brühe dazu.", 3));
        assertEquals("Würze mit 2 Prise Zucker* und 1 Msp. Chili.", two("Würze mit 1 Prise Zucker* und ½ Msp. Chili.", 4));
        assertEquals("Gib 2 TL Senf dazu.", two("Gib 1 TL Senf dazu.", 4));
        assertEquals("Gib 3 TL Senf dazu.", two("Gib 1 TL Senf dazu.", 6));
        assertEquals("Gib 2 – 4 EL Wasser* dazu.", two("Gib 1 – 2 EL Wasser* dazu.", 4));
        assertEquals("Gib 2–4 EL Wasser* dazu.", two("Gib 1–2 EL Wasser* dazu.", 4));
        assertEquals("Gib 2 bis 4 EL Wasser* dazu.", two("Gib 1 bis 2 EL Wasser* dazu.", 4));
        assertEquals("Gib 300ml Milch dazu.", two("Gib 150ml Milch dazu.", 4));
        assertEquals("Gib 2¼ EL Öl* dazu.", two("Gib 1 1/2 EL Öl* dazu.", 3));
    }

    @Test
    void mixedFractionsAreReadAsOneNumber() {
        assertEquals("Gib 3 EL Öl* dazu.", two("Gib 1 1/2 EL Öl* dazu.", 4));
        assertEquals("Gib 3 EL Öl* dazu.", two("Gib 1½ EL Öl* dazu.", 4));
        assertEquals("Gib 3 EL Öl* dazu.", two("Gib 1,5 EL Öl* dazu.", 4));
        assertEquals("Gib 3 EL Öl* dazu.", two("Gib 1.5 EL Öl* dazu.", 4));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 6})
    void numbersThatAreNotAmountsAreNeverChanged(int people) {
        for (String step : new String[]{
                "Heize den Backofen auf 220 °C (Umluft: 200 °C) vor.",
                "Backe alles 20 – 25 Min. im Ofen und lass es 5 Min. ruhen.",
                "Brate es 30 Sek. pro Seite an; im Slow Cooker 4 Std. garen.",
                "Püriere 10 Sek./Stufe 8 und koche 5 Min./100 °C/Stufe 1.",
                "Schneide die Zucchini in 1 cm dicke Scheiben, den Lauch in 5 mm Ringe.",
                "Gare alles in der Mikrowelle bei 600 Watt.",
                "Verwende Sahne mit 30 % Fett.",
                "Das Rezept ist für 2 Personen; teile es auf 4 Teller auf.",
                "Erhitze 2 Pfannen und halbiere 3 Zwiebeln.",
                "Gib die 2 x 100 g Packungen in den Topf.",
                "Schritt 3: Wende die Schnitzel 2-mal.",
                "Viertele die Tomaten und gib ½ Zitrone dazu."}) {
            assertEquals(step, two(step, people), step);
        }
    }

    // --- recipes with servings other than 2 ------------------------------------------------------

    @Test
    void rangeRecipeScalesTheOuterValueFromServingsOrServingsMaxAndIgnoresTheSizeMapping() {
        String step = "Brate 300 g [450 g | 600 g] Hackfleisch mit 150 ml Wasser* an.";
        // servings 3 – 4, basis default: people / 3
        assertEquals("Brate 300 g Hackfleisch mit 150 ml Wasser* an.", StepAmounts.render(step, 3, 4, null, false));
        assertEquals("Brate 200 g Hackfleisch mit 100 ml Wasser* an.", StepAmounts.render(step, 3, 4, 2, false));
        assertEquals("Brate 300 g Hackfleisch mit 150 ml Wasser* an.", StepAmounts.render(step, 3, 4, 3, false));
        assertEquals("Brate 400 g Hackfleisch mit 200 ml Wasser* an.", StepAmounts.render(step, 3, 4, 4, false));
        assertEquals("Brate 600 g Hackfleisch mit 300 ml Wasser* an.", StepAmounts.render(step, 3, 4, 6, false));
        // basis max: people / 4
        assertEquals("Brate 300 g Hackfleisch mit 150 ml Wasser* an.", StepAmounts.render(step, 3, 4, null, true));
        assertEquals("Brate 150 g Hackfleisch mit 75 ml Wasser* an.", StepAmounts.render(step, 3, 4, 2, true));
        assertEquals("Brate 225 g Hackfleisch mit 113 ml Wasser* an.", StepAmounts.render(step, 3, 4, 3, true));
        assertEquals("Brate 300 g Hackfleisch mit 150 ml Wasser* an.", StepAmounts.render(step, 3, 4, 4, true));
        assertEquals("Brate 450 g Hackfleisch mit 225 ml Wasser* an.", StepAmounts.render(step, 3, 4, 6, true));
    }

    @Test
    void rangeRecipeSpoonsAndCountsUseFractions() {
        assertEquals("Gib ⅔ EL Öl* dazu.", StepAmounts.render("Gib 1 EL [1,5 EL | 2 EL] Öl* dazu.", 3, 4, 2, false));
        assertEquals("Forme 2⅔ Frikadellen.", StepAmounts.render("Forme 4 [6 | 8] Frikadellen.", 3, 4, 2, false));
        assertEquals("Gib 3,33 l Brühe dazu.", StepAmounts.render("Gib 5 l Brühe dazu.", 3, 4, 2, false));
    }

    @Test
    void rangeRecipeKeepsThe2PersonWordsAndTimes() {
        assertEquals("Gib die Hälfte Soße dazu.",
                StepAmounts.render("Gib die Hälfte [drei Viertel | gesamte] Soße dazu.", 3, 4, 4, false));
        assertEquals("Backe 18 Min.", StepAmounts.render("Backe 18 Min. [19 Min. | 20 Min.]", 3, 4, 4, true));
    }

    @Test
    void readyMealWithOneServingScalesInsteadOfTrustingTheBracket() {
        assertEquals("Gib 2 EL Öl* dazu.", StepAmounts.render("Gib 1 EL [2 EL] Öl* dazu.", 1, null, 2, false));
        assertEquals("Gib 4 EL Öl* dazu.", StepAmounts.render("Gib 1 EL [2 EL] Öl* dazu.", 1, null, 4, false));
        // basis max on a recipe that is not a range is ignored
        assertEquals("Gib 4 EL Öl* dazu.", StepAmounts.render("Gib 1 EL [2 EL] Öl* dazu.", 1, null, 4, true));
    }

    @Test
    void recipeWithZeroServingsResolvesBracketsButScalesNothing() {
        assertEquals("Gib 1 EL Öl* und 100 g Mehl dazu.",
                StepAmounts.render("Gib 1 EL [1,5 EL | 2 EL] Öl* und 100 g Mehl dazu.", 0, null, 4, false));
    }

    // --- edges ------------------------------------------------------------------------------------

    @Test
    void emptyAndNullStepsComeBackAsTheyAre() {
        assertEquals("", two("", 3));
        assertEquals(null, two(null, 3));
    }

    @Test
    void peopleBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> two("1 EL Öl", 0));
    }

    @Test
    void numbersAreParsedAsHelloFreshWritesThem() {
        assertEquals(0, new BigDecimal("1.5").compareTo(StepAmounts.parseNumber("11/2")));
        assertEquals(0, new BigDecimal("0.5").compareTo(StepAmounts.parseNumber("1/2")));
        assertEquals(0, new BigDecimal("1.5").compareTo(StepAmounts.parseNumber("1 1/2")));
        assertEquals(0, new BigDecimal("1.5").compareTo(StepAmounts.parseNumber("1, 5")));
        assertEquals(0, new BigDecimal("2.25").compareTo(StepAmounts.parseNumber("2¼")));
        assertEquals(null, StepAmounts.parseNumber("12/2"), "last digit not below the denominator: not a mixed number");
    }

    @Test
    void numbersAreFormattedTheGermanWay() {
        assertEquals("1,5", StepAmounts.formatNumber(new BigDecimal("1.5"), "kg"));
        assertEquals("22,5", StepAmounts.formatNumber(new BigDecimal("22.5"), "g"));
        assertEquals("333", StepAmounts.formatNumber(new BigDecimal("333.33"), "g"));
        assertEquals("33,3", StepAmounts.formatNumber(new BigDecimal("33.33"), "ml"));
        assertEquals("1½", StepAmounts.formatNumber(new BigDecimal("1.50"), "EL"));
        assertEquals("¼", StepAmounts.formatNumber(new BigDecimal("0.25"), "TL"));
        assertEquals("⅔", StepAmounts.formatNumber(new BigDecimal("0.67"), null));
        assertEquals("1,2", StepAmounts.formatNumber(new BigDecimal("1.20"), "EL"));
        assertEquals("3", StepAmounts.formatNumber(new BigDecimal("3.00"), "Prisen"));
    }
}
