"""Parser tests on saved JSON-LD fixtures. No network, no database.

Run from the repo root:  python3 -m unittest discover -s scripts/importer/tests
"""
import json
import os
import sys
import unittest
from decimal import Decimal

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

import recipe_parse  # noqa: E402
from extract import Catalog, build_sql, is_pantry, load_overrides  # noqa: E402
from load_recipes import parse_draft  # noqa: E402
from recipe_parse import Line, num, parse_ingredient, servings_for  # noqa: E402

FIXTURES = os.path.join(HERE, "fixtures")


def fixture(name):
    with open(os.path.join(FIXTURES, "jsonld", name), encoding="utf-8") as f:
        return json.load(f)


def catalog():
    with open(os.path.join(FIXTURES, "repair", "state.json"), encoding="utf-8") as f:
        items = [tuple(i) for i in json.load(f)["items"]]
    return Catalog(items, load_overrides())


class AmountTest(unittest.TestCase):

    def test_every_fraction_form_is_exact(self):
        cases = {"½": "0.5", "1½": "1.5", "1 ½": "1.5", "¼": "0.25", "¾": "0.75", "⅓": "0.333",
                 "⅔": "0.667", "1/2": "0.5", "1 1/2": "1.5", "0,5": "0.5", "2.5": "2.5", "2": "2"}
        for text, expected in cases.items():
            with self.subTest(text=text):
                line = parse_ingredient(text + " Stück Zitrone")
                self.assertEqual(Decimal(expected), line.quantity)
                self.assertEqual("Stück", line.unit)
                self.assertEqual("Zitrone", line.name)
                self.assertFalse(line.to_taste)

    def test_old_half_bug_is_gone(self):
        # the old num() turned "½" into ".5" and matched only "5"
        self.assertEqual(Decimal("0.5"), num("½"))
        self.assertEqual(Decimal("0.333"), num("⅓"))

    def test_no_rounding_and_nothing_forced_to_one(self):
        self.assertEqual(Decimal("0.25"), parse_ingredient("¼ TL Salz").quantity)
        self.assertEqual(Decimal("1.5"), parse_ingredient("1½ Stück Zwiebel").quantity)

    def test_german_thousands_separator_and_glued_unit(self):
        self.assertEqual(Line(None, Decimal(1500), "g", "Mehl", False), parse_ingredient("1.500 g Mehl"))
        self.assertEqual(Line(None, Decimal(100), "g", "Mehl", False), parse_ingredient("100g Mehl"))

    def test_range_keeps_first_value(self):
        self.assertEqual(Decimal(1), parse_ingredient("1-2 Stück Chili").quantity)

    def test_old_parser_replay_reproduces_the_bugs(self):
        self.assertEqual((Decimal(5), "Stück"), recipe_parse.old_parse("½ Stück Zitrone"))
        self.assertEqual((Decimal(2), "Stück"), recipe_parse.old_parse("1½ Stück Zwiebel"))
        self.assertEqual((Decimal(3), "Stück"), recipe_parse.old_parse("3 Esslöffel Olivenöl"))
        self.assertEqual((Decimal(1), "TL"), recipe_parse.old_parse("1 TL Honig"))
        self.assertEqual((Decimal(1), "Stück"), recipe_parse.old_parse("nach Geschmack Salz"))

    def test_nutrition_strings_still_parse(self):
        self.assertEqual(Decimal(620), num("620 kcal"))
        self.assertEqual(Decimal("70.5"), num("70,5 g"))


class UnitTest(unittest.TestCase):

    def test_spoons_stay_spoons(self):
        self.assertEqual(Line(None, Decimal(3), "EL", "Olivenöl", False), parse_ingredient("3 Esslöffel Olivenöl"))
        self.assertEqual(Line(None, Decimal(2), "TL", "Zucker", False), parse_ingredient("2 Teelöffel Zucker"))
        self.assertEqual(Line(None, Decimal(2), "EL", "Olivenöl", False), parse_ingredient("2 EL Olivenöl"))
        self.assertEqual("TL", parse_ingredient("1 TL Paprikapulver").unit)

    def test_known_units_are_kept(self):
        for unit in ["Prise", "Messerspitze", "Zehe", "Bund", "Scheiben", "Dose", "Packung", "Stück",
                     "g", "ml", "kg", "l"]:
            with self.subTest(unit=unit):
                self.assertEqual(unit, parse_ingredient(f"2 {unit} Etwas").unit)
        self.assertEqual("Prise", parse_ingredient("2 Prisen Zucker").unit)

    def test_kg_and_l_are_not_converted(self):
        self.assertEqual((Decimal("2.5"), "kg"), (parse_ingredient("2.5 kg Kartoffeln").quantity, "kg"))
        self.assertEqual("l", parse_ingredient("0,5 l Milch").unit)

    def test_unknown_word_after_amount_is_a_piece(self):
        self.assertEqual(Line(None, Decimal(2), "Stück", "Knoblauchzehe", False), parse_ingredient("2 Knoblauchzehe"))


class ToTasteTest(unittest.TestCase):

    def test_nach_geschmack_and_amountless_lines(self):
        for raw in ["nach Geschmack Salz", "Salz nach Geschmack", "etwas Salz", "Salz", "Prise Salz"]:
            with self.subTest(raw=raw):
                self.assertEqual(Line(raw, None, "", "Salz", True), parse_ingredient(raw))

    def test_amount_wins_over_the_words(self):
        self.assertEqual(Line(None, Decimal(1), "Prise", "Salz", False), parse_ingredient("1 Prise Salz nach Geschmack"))

    def test_zero_amount_is_to_taste(self):
        self.assertTrue(parse_ingredient("0 g Salz").to_taste)


class ServingsTest(unittest.TestCase):

    def test_plain_recipe_uses_recipe_yield(self):
        self.assertEqual((2, [], recipe_parse.FROM_YIELD), servings_for(fixture("zitronen_haehnchen.json")))

    def test_bundle_for_four_wins_over_recipe_yield(self):
        servings, notes, source = servings_for(fixture("familien_bolognese_bundle.json"))
        self.assertEqual((4, recipe_parse.FROM_BUNDLE_TITLE), (servings, source))
        self.assertIn("bundle for 4", notes[0])
        self.assertEqual(4, servings_for({"name": "Pasta für 4 Personen", "recipeYield": 2})[0])

    def test_other_conflicts_are_reported_not_applied(self):
        servings, notes, source = servings_for(fixture("curry_fuer_3.json"))   # title 3, recipeYield "2 Portionen"
        self.assertEqual((2, recipe_parse.FROM_YIELD), (servings, source))
        self.assertTrue(notes[0].startswith("CONFLICT"))

    def test_missing_or_unreadable_yield_is_only_a_default(self):
        for rec in [fixture("kuerbissuppe_ohne_yield.json"), {"name": "X", "recipeYield": "viele"},
                    {"name": "X", "recipeYield": 0}]:
            with self.subTest(rec=rec.get("recipeYield")):
                servings, notes, source = servings_for(rec)
                self.assertEqual((2, recipe_parse.DEFAULT), (servings, source))
                self.assertIn("recipeYield missing", notes[0])

    def test_bundle_title_is_reliable_even_without_yield(self):
        servings, _notes, source = servings_for({"name": "Pasta  Bundle 4P"})
        self.assertEqual((4, recipe_parse.FROM_BUNDLE_TITLE), (servings, source))

    def test_yield_shapes(self):
        self.assertEqual(2, recipe_parse.yield_people(["2"]))
        self.assertEqual(2, recipe_parse.yield_people("2 Portionen"))
        self.assertIsNone(recipe_parse.yield_people(None))


class RecipeTest(unittest.TestCase):

    def test_fraction_fixture(self):
        got = [parse_ingredient(r).quantity for r in fixture("fractions.json")["recipeIngredient"]]
        self.assertEqual([Decimal(x) for x in ["0.5", "1.5", "1.5", "0.25", "0.75", "0.333", "0.667", "0.5", "0.5", "2.5"]], got)

    def test_parse_recipe_matches_merges_and_drops_water(self):
        parsed = recipe_parse.parse_recipe(fixture("zitronen_haehnchen.json"), catalog(),
                                           is_pantry=lambda n: is_pantry(n, n))
        self.assertEqual("Zitronen-Hähnchen", parsed["title"])
        by_item = {l["item_id"]: l for l in parsed["lines"]}
        self.assertEqual((Decimal("0.5"), "Stück"), (by_item[186]["quantity"], by_item[186]["unit"]))
        self.assertEqual((Decimal(4), "EL"), (by_item[1022]["quantity"], by_item[1022]["unit"]))  # 3 EL + 1 EL
        self.assertTrue(by_item[1148]["to_taste"])           # nach Geschmack Salz
        self.assertTrue(by_item[1156]["to_taste"])           # Pfeffer, no amount
        self.assertEqual((Decimal(1), "TL"), (by_item[1070]["quantity"], by_item[1070]["unit"]))
        self.assertNotIn("Wasser", [l["name"] for l in parsed["lines"]])
        self.assertTrue(any("summed into 4 EL" in n for n in parsed["notes"]))

    def test_same_item_different_units_keeps_first_and_reports(self):
        merged, notes = recipe_parse.merge_lines([
            {"item_id": 1022, "name": "Olivenöl", "quantity": Decimal(2), "unit": "EL", "to_taste": False, "conf": "exact"},
            {"item_id": 1022, "name": "Olivenöl", "quantity": Decimal(50), "unit": "ml", "to_taste": False, "conf": "exact"},
            {"item_id": 1148, "name": "Salz", "quantity": None, "unit": "", "to_taste": True, "conf": "override"},
            {"item_id": 1148, "name": "Salz", "quantity": Decimal(1), "unit": "Prise", "to_taste": False, "conf": "override"},
        ])
        self.assertEqual([(Decimal(2), "EL"), (Decimal(1), "Prise")], [(m["quantity"], m["unit"]) for m in merged])
        self.assertTrue(notes[0].startswith("REVIEW item #1022"))

    def test_uncertain_lines_are_never_summed(self):
        # "160 pikante Paprikasoße" (fuzzy match to Paprika) next to "1 rote Paprika" (exact)
        def line(raw, qty, conf):
            return {"item_id": 54, "name": raw, "raw": raw, "quantity": Decimal(qty), "unit": "Stück",
                    "to_taste": False, "conf": conf, "note": ""}
        for first, second in (("exact", "like"), ("like", "exact"), ("like", "like"), ("multi", "override")):
            merged, notes = recipe_parse.merge_lines([line("rote Paprika", 1, first), line("pikante Paprikasoße", 160, second)])
            self.assertEqual([Decimal(1), Decimal(160)], [m["quantity"] for m in merged], (first, second))
            self.assertFalse(merged[0].get("held_collision"))
            self.assertTrue(merged[1]["held_collision"])        # listed on its own, never added up
            self.assertFalse(any("summed" in n for n in notes))
        merged, _ = recipe_parse.merge_lines([line("rote Paprika", 1, "exact"), line("Paprika", 2, "override")])
        self.assertEqual([Decimal(3)], [m["quantity"] for m in merged])       # both confident: summed

    def test_recipe_from_saved_page(self):
        with open(os.path.join(FIXTURES, "jsonld", "page.html"), encoding="utf-8") as f:
            rec = recipe_parse.recipe_from_html(f.read())
        self.assertEqual("Familien-Bolognese", recipe_parse.cut_title(rec["name"]))

    def test_draft_round_trip_keeps_decimals_to_taste_and_url(self):
        matched = [("Zitrone", "Stück", Decimal("0.5"), False, 186, "override", ""),
                   ("Salz", "", None, True, 1148, "override", "")]
        sql = build_sql("Zitronen-Hähnchen", 2, 30, {k: None for k in ("cal", "prot", "carb", "fat", "fibre", "sugar")},
                        matched, ["Kochen."], "https://www.hellofresh.de/recipes/r-0123456789abcdef01234567")
        self.assertIn("'Stück', 0.5, 0)", sql)
        self.assertIn("'', NULL, 1)", sql)
        recipe, ings, steps = parse_draft(sql)
        self.assertEqual("https://www.hellofresh.de/recipes/r-0123456789abcdef01234567", recipe["url"])
        self.assertEqual(2, recipe["servings"])
        self.assertEqual([(186, "Stück", Decimal("0.5"), False), (1148, "", None, True)], ings)
        self.assertEqual([(1, "Kochen.")], steps)

    def test_old_integer_drafts_still_parse(self):
        old = ("BEGIN;\nINSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, "
               "recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar)\n"
               "VALUES ('Alt', 2, 30, 0, NULL, NULL, NULL, NULL, NULL, NULL);\n"
               "INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, "
               "recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES\n"
               "  ((SELECT recipe_id FROM recipe WHERE recipe_name='Alt'), 40, 'Stück', 2);\nCOMMIT;\n")
        recipe, ings, _steps = parse_draft(old)
        self.assertIsNone(recipe["url"])
        self.assertEqual([(40, "Stück", Decimal(2), False)], ings)


if __name__ == "__main__":
    unittest.main()
