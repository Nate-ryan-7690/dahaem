"""Amounts from the cooking steps, the rules for amountless ingredient lines, servings ranges.

Run from the repo root:  python3 -m unittest discover -s scripts/importer/tests
"""
import os
import sys
import unittest
from decimal import Decimal

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
sys.path.insert(0, HERE)

import recipe_parse  # noqa: E402
import step_amounts  # noqa: E402
from test_recipe_parse import catalog  # noqa: E402


def amounts(step):
    return [(m["amount"], m["unit"], m["noun"].split()[0] if m["noun"] else "") for m in step_amounts.mentions(step)]


class StepNotationTest(unittest.TestCase):
    """'1 EL [1,5 EL | 2 EL] Butter*': 1 EL is for 2 people, the bracket holds 3 | 4 people."""

    def test_only_the_two_person_amount_is_read(self):
        self.assertEqual([(Decimal(1), "EL", "Butter*")], amounts("In einer Pfanne 1 EL [1,5 EL | 2 EL] Butter* erhitzen."))

    def test_variants_of_the_bracket_do_not_matter(self):
        for step in ("1 EL [1.5 EL | 2 EL] Öl*", "1 EL [1,5 EL I 2 EL] Öl*", "1 EL [1,5 EL|2 EL] Öl*",
                     "1 EL [1, 5 EL | 2 EL] Öl*", "1 EL [2 EL] Öl*", "1 EL [11/2 EL | 2 EL] Öl*",
                     "1 EL [2 EL – 4 EL] Öl*", "1 EL [3 EL l | 4 EL] Öl*"):
            with self.subTest(step=step):
                self.assertEqual([(Decimal(1), "EL", "Öl*")], amounts(step))

    def test_outer_fractions_decimals_and_mixed_numbers(self):
        self.assertEqual(Decimal("0.5"), amounts("½ EL [1 EL] Olivenöl")[0][0])
        self.assertEqual(Decimal("1.5"), amounts("1½ EL [3 EL] Olivenöl")[0][0])
        self.assertEqual(Decimal("0.25"), amounts("0,25 TL [0,25 TL | 0,5 TL] Salz*")[0][0])
        self.assertEqual(Decimal("1.5"), amounts("11/2 EL Olivenöl")[0][0])     # 1 and 1/2, not 11/2

    def test_amount_without_brackets_and_a_unit_after_the_bracket(self):
        self.assertEqual([(Decimal(500), "g", "Wasser*")], amounts("500 g Wasser* in den Mixtopf geben"))
        self.assertEqual([(Decimal(15), "g", "Butter")], amounts("15 [30] g Butter in den Mixtopf"))
        self.assertEqual([(Decimal(1), "Prise", "Salz")], amounts("1 Prise Salz zugeben"))

    def test_numbers_that_are_not_amounts_are_never_read(self):
        step = ("Backofen auf 220 °C vorheizen, 4 – 5 Min. anbraten, 3 Sek./Stufe 8 zerkleinern, 2 cm dicke Scheiben, "
                "bei 750 Watt, [19 Min. | 20 Min.] garen, 60 % Fett.")
        self.assertEqual([], [a for a in amounts(step) if a[1]])

    def test_ranges_are_not_guessed(self):
        self.assertEqual([], amounts("1 – 2 EL [3 EL] Zitronensaft"))

    def test_time_in_a_bracket_is_not_an_amount(self):
        self.assertEqual([], [a for a in amounts("8 Min. [10 Min. | 12 Min.] backen") if a[1]])


class StepAmountTest(unittest.TestCase):

    def test_exact_mentions_are_added_up(self):
        steps = ["2 EL Olivenöl und etwas Salz verrühren.", "In einem Topf 1 EL Öl erhitzen."]
        self.assertEqual(("found", Decimal(3), "EL"), step_amounts.step_amount("Olivenöl", steps)[:3])
        self.assertEqual(("found", Decimal(3), "EL"), step_amounts.step_amount("(Oliven-)Öl*", steps)[:3])

    def test_mixed_units_are_never_added_up(self):
        steps = ["1 EL Butter erhitzen.", "1 TL Butter zugeben."]
        self.assertEqual("mixed", step_amounts.step_amount("Butter", steps)[0])

    def test_another_wording_is_only_a_hint(self):
        self.assertEqual("uncertain", step_amounts.step_amount("Weißweinessig", ["1 EL Essig zugeben."])[0])
        self.assertEqual("uncertain", step_amounts.step_amount("mittelscharfer Senf", ["1 TL Senf verrühren."])[0])
        self.assertEqual("uncertain", step_amounts.step_amount("Zitrone, gewachst", ["1 EL Zitronensaft zugeben."])[0])

    def test_state_adjectives_do_not_make_a_mention_unsure(self):
        self.assertEqual(("found", Decimal(300), "ml"),
                         step_amounts.step_amount("Gemüsebrühe", ["300 ml heiße Gemüsebrühe zubereiten."])[:3])

    def test_two_amounts_in_one_sentence_are_both_read(self):
        steps = ["1 EL Honig und 1 EL Honig verrühren."]
        self.assertEqual(("found", Decimal(2), "EL"), step_amounts.step_amount("Honig", steps)[:3])

    def test_the_amount_belongs_to_the_first_ingredient_of_a_list(self):
        steps = ["1 Prise Salz und Pfeffer zugeben."]
        self.assertEqual("found", step_amounts.step_amount("Salz", steps)[0])
        self.assertEqual("none", step_amounts.step_amount("Pfeffer", steps)[0])

    def test_nothing_in_the_steps(self):
        self.assertEqual("none", step_amounts.step_amount("Salz", ["Mit Salz* und Pfeffer* abschmecken."])[0])


def recipe(ingredients, steps, name="Test", recipe_yield=2):
    return {"name": name, "recipeYield": recipe_yield, "recipeIngredient": ingredients,
            "recipeInstructions": [{"@type": "HowToStep", "text": f"<p>{s}</p>"} for s in steps]}


def lines_of(parsed):
    return {(l["item_id"]): (l["quantity"], l["unit"], l["to_taste"], l.get("origin")) for l in parsed["lines"]}


class AmountlessLineRulesTest(unittest.TestCase):
    """Nate's rules of 2026-10-11."""

    def parse(self, ingredients, steps=()):
        return recipe_parse.parse_recipe(recipe(ingredients, list(steps)), catalog())

    def test_amount_from_the_steps_wins_for_stueck_and_bare_lines(self):
        p = self.parse(["Stück Honig", " Olivenöl"], ["1 TL Honig unterrühren.", "2 EL Olivenöl, 1 EL Öl erhitzen."])
        self.assertEqual((Decimal(1), "TL", False, "steps"), lines_of(p)[1070])
        self.assertEqual((Decimal(3), "EL", False, "steps"), lines_of(p)[1022])

    def test_stueck_without_an_amount_is_one_stueck_not_to_taste(self):
        p = self.parse(["Stück Pita-Brote"])
        self.assertEqual((Decimal(1), "Stück", False, "one"), lines_of(p)[565])

    def test_nach_geschmack_is_to_taste_whatever_the_steps_say(self):
        p = self.parse(["nach Geschmack Zucker", "nach Geschmack Salz"], ["1 Prise Zucker zugeben.", "0,25 TL Salz zugeben."])
        self.assertEqual((None, "", True, None), lines_of(p)[1061])
        self.assertEqual((None, "", True, None), lines_of(p)[1148])

    def test_salt_pepper_oil_vinegar_without_any_amount_are_to_taste(self):
        p = self.parse(["Stück Salz", "Stück Pfeffer", " Olivenöl", "Stück Weißweinessig"], ["Alles verrühren."])
        self.assertEqual({1148, 1156, 1022, 1050}, {i for i, v in lines_of(p).items() if v[2]})
        self.assertEqual([], p["reviews"])

    def test_anything_else_without_an_amount_goes_to_review(self):
        p = self.parse([" Zucker", "g Honig"], ["Alles verrühren."])
        self.assertEqual({}, lines_of(p))
        self.assertEqual({1061, 1070}, {r["item_id"] for r in p["reviews"]})
        self.assertTrue(all(r["reason"].startswith("REVIEW") for r in p["reviews"]))

    def test_stueck_of_something_uncountable_is_reviewed_not_one_stueck(self):
        p = self.parse(["Stück Zucker"], ["Alles verrühren."])
        self.assertEqual({}, lines_of(p))
        self.assertEqual(1, len(p["reviews"]))

    def test_unsure_step_mentions_are_reviewed_never_used(self):
        p = self.parse(["Stück mittelscharfer Senf"], ["1 TL Senf verrühren."])
        self.assertEqual({}, lines_of(p))
        self.assertIn("different wording", p["reviews"][0]["reason"])

    def test_a_numbered_line_of_the_same_kind_means_no_row_of_its_own(self):
        p = self.parse(["1 Esslöffel Olivenöl", "Stück Öl"], ["Öl erhitzen."])
        self.assertEqual({1022: (Decimal(1), "EL", False, None)}, lines_of(p))
        self.assertIn("no row of its own", p["reviews"][0]["reason"])

    def test_numbered_lines_are_untouched(self):
        p = self.parse(["3 Esslöffel Olivenöl", "½ Stück Zitrone, gewachst"], ["Alles verrühren."])
        self.assertEqual({1022: (Decimal(3), "EL", False, None), 186: (Decimal("0.5"), "Stück", False, None)}, lines_of(p))

    def test_a_page_without_steps_still_parses(self):
        p = recipe_parse.parse_recipe({"name": "X", "recipeYield": 2, "recipeIngredient": ["Stück Salz"],
                                       "recipeInstructions": 0}, catalog())
        self.assertEqual((None, "", True, None), lines_of(p)[1148])


class ServingsRangeTest(unittest.TestCase):

    def info(self, name, recipe_yield=2):
        return recipe_parse.servings_info({"name": name, "recipeYield": recipe_yield})

    def test_range_keeps_the_lower_bound_and_the_maximum(self):
        for name, low, high in (("Crumble für 3 – 4 Personen | 40 Minuten", 3, 4),
                                ("Käseplatte für 2 – 4 Personen | 14,99 €", 2, 4),
                                ("Miniburger für 2 – 3 Personen | 30 Minuten", 2, 3)):
            with self.subTest(name=name):
                i = self.info(name)
                self.assertEqual((low, high, recipe_parse.FROM_RANGE_TITLE), (i["servings"], i["servings_max"], i["source"]))

    def test_explicit_four_people_is_four_without_a_maximum(self):
        for name in ("Schinken-Tasche für 4 Personen", "Meat Bundle 4P", "OTP 4P Rote Beete Carpaccio"):
            with self.subTest(name=name):
                i = self.info(name)
                self.assertEqual((4, None, recipe_parse.FROM_BUNDLE_TITLE), (i["servings"], i["servings_max"], i["source"]))

    def test_four_people_with_other_portions_goes_to_review(self):
        i = self.info("OTP 4P Birnen Parfait 2 Portionen | zubereiten & genießen")
        self.assertEqual(recipe_parse.REVIEW, i["source"])
        self.assertTrue(i["notes"][0].startswith("REVIEW"))
        i = self.info("Vorspeisenplatte für 4 Personen 4 Portionen")      # consistent: no review
        self.assertEqual((4, recipe_parse.FROM_BUNDLE_TITLE), (i["servings"], i["source"]))

    def test_every_other_recipe_stays_two(self):
        for name in ("Chicken Parm cheesy Schnitzel", "Salat für 2 Personen", "Quiche 4 Portionen | erhitzen & genießen",
                     "Brownies 3-4 Portionen", "Brunch für 6 Personen"):
            with self.subTest(name=name):
                i = self.info(name)
                self.assertEqual((2, None), (i["servings"], i["servings_max"]))

    def test_a_range_is_not_read_as_its_last_number(self):
        self.assertIsNone(recipe_parse.title_people("Crumble für 3 – 4 Personen"))
        self.assertEqual((3, 4), recipe_parse.title_range("Crumble für 3 – 4 Personen"))
        self.assertIsNone(recipe_parse.title_range("Crumble für 4 Personen"))

    def test_servings_for_keeps_its_old_shape(self):
        self.assertEqual((3, recipe_parse.FROM_RANGE_TITLE), (lambda r: (r[0], r[2]))(
            recipe_parse.servings_for({"name": "Crumble für 3 – 4 Personen", "recipeYield": 2})))


if __name__ == "__main__":
    unittest.main()
