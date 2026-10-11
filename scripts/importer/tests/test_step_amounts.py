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

    def test_spoons_of_different_size_are_converted_exactly_into_el(self):
        steps = ["1 EL Butter erhitzen.", "1 TL Butter zugeben."]
        status, amount, unit, notes = step_amounts.step_amount("Butter", steps)
        self.assertEqual(("found", Decimal("1.333"), "EL"), (status, amount, unit))
        self.assertIn("1 EL = 3 TL", notes[0])
        self.assertEqual(Decimal(2), step_amounts.step_amount("Butter", ["1 EL Butter", "3 TL Butter"])[1])
        self.assertEqual(("found", Decimal(2), "TL"), step_amounts.step_amount("Butter", ["1 TL Butter", "1 TL Butter"])[:3])

    def test_spoons_mixed_with_weights_are_never_added_up(self):
        for steps in (["1 EL Butter erhitzen.", "10 g Butter zugeben."], ["1 TL Öl", "50 ml Öl"],
                      ["1 Prise Zucker", "1 TL Zucker"]):
            with self.subTest(steps=steps):
                name = "Butter" if "Butter" in steps[0] else "Öl" if "Öl" in steps[0] else "Zucker"
                self.assertEqual("mixed", step_amounts.step_amount(name, steps)[0])

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
    """Nate's rules of 2026-10-11, in order."""

    def parse(self, ingredients, steps=()):
        return recipe_parse.parse_recipe(recipe(ingredients, list(steps)), catalog())

    def reasons(self, p):
        return {r["item_id"]: r["reason"] for r in p["reviews"]}

    # 1. nach Geschmack
    def test_nach_geschmack_is_to_taste_whatever_the_steps_say(self):
        p = self.parse(["nach Geschmack Zucker", "nach Geschmack Salz"], ["1 Prise Zucker zugeben.", "0,25 TL Salz zugeben."])
        self.assertEqual((None, "", True, None), lines_of(p)[1061])
        self.assertEqual((None, "", True, None), lines_of(p)[1148])

    # 2. amount from the steps
    def test_amount_from_the_steps_for_stueck_and_bare_lines(self):
        p = self.parse(["Stück Honig", " Olivenöl"], ["1 TL Honig unterrühren.", "2 EL Olivenöl, 1 EL Öl erhitzen."])
        self.assertEqual((Decimal(1), "TL", False, "steps"), lines_of(p)[1070])
        self.assertEqual((Decimal(3), "EL", False, "steps"), lines_of(p)[1022])

    def test_oil_wordings_are_one_ingredient(self):
        for line in ("Stück Öl", "Stück Olivenöl", " Öl"):
            with self.subTest(line=line):
                p = self.parse([line], ["1 EL Öl erhitzen.", "2 EL Olivenöl zugeben."])
                self.assertEqual((Decimal(3), "EL", False, "steps"), lines_of(p)[1022])

    def test_a_generic_vinegar_belongs_to_the_only_vinegar_of_the_recipe(self):
        p = self.parse([" Weißweinessig"], ["1 EL Essig zugeben."])
        self.assertEqual((Decimal(1), "EL", False, "steps"), lines_of(p)[1050])
        p = self.parse([" Balsamicoessig", "Stück Zucker"], ["2 EL Essig und 1 EL Zucker verrühren."])
        self.assertEqual((Decimal(2), "EL", False, "steps"), lines_of(p)[1053])

    def test_a_generic_vinegar_with_several_vinegars_in_the_recipe_goes_to_review(self):
        p = self.parse([" Weißweinessig", " Rotweinessig"], ["1 EL Essig zugeben."])
        self.assertEqual({}, lines_of(p))
        self.assertEqual({1050, 1051}, set(self.reasons(p)))
        self.assertTrue(all("several items of this kind" in r for r in self.reasons(p).values()))

    def test_a_specific_vinegar_wording_is_exact_even_with_several_vinegars(self):
        p = self.parse([" Weißweinessig", " Rotweinessig"], ["1 EL Weißweinessig zugeben."])
        self.assertEqual((Decimal(1), "EL", False, "steps"), lines_of(p)[1050])
        self.assertEqual((None, "", True, None), lines_of(p)[1051])    # nothing for it anywhere: vinegar is to taste

    def test_a_generic_mustard_belongs_to_the_only_mustard(self):
        p = self.parse(["Stück mittelscharfer Senf"], ["1 TL Senf verrühren."])
        self.assertEqual((Decimal(1), "TL", False, "steps"), lines_of(p)[937])

    def test_lemon_juice_is_not_the_lemon(self):
        p = self.parse(["Stück Zitrone, gewachst"], ["1 EL Zitronensaft zugeben.", "Saft von 1 [2 | 2] Zitronenspalten."])
        self.assertEqual({}, lines_of(p))
        self.assertIn("different wording", self.reasons(p)[186])

    # 3. spoons
    def test_el_and_tl_are_converted_and_stored_in_el(self):
        p = self.parse([" Honig"], ["1 EL Honig erhitzen.", "1 TL Honig unterrühren."])
        self.assertEqual((Decimal("1.333"), "EL", False, "steps"), lines_of(p)[1070])

    def test_spoons_with_g_or_ml_are_reviewed(self):
        p = self.parse([" Honig"], ["1 EL Honig erhitzen.", "20 g Honig unterrühren."])
        self.assertEqual({}, lines_of(p))
        self.assertIn("cannot be added", self.reasons(p)[1070])

    # 4. no amount anywhere
    def test_salt_pepper_oil_vinegar_without_any_amount_are_to_taste(self):
        p = self.parse(["Stück Salz", "Stück Pfeffer", " Olivenöl", "Stück Weißweinessig"], ["Alles verrühren."])
        self.assertEqual({1148, 1156, 1022, 1050}, {i for i, v in lines_of(p).items() if v[2]})
        self.assertEqual([], p["reviews"])

    def test_uncountable_staples_without_an_amount_are_to_taste(self):
        p = self.parse(["Stück Zucker", " Honig", "g Mehl", "Stück Milch", "Stück mittelscharfer Senf", "Stück Butter",
                        "Stück Gemüsebrühe", " Rinderfond"], ["Alles verrühren."])
        to_taste_names = {l["name"] for l in p["lines"] if l["to_taste"]}
        self.assertEqual({"Zucker", "Honig", "Mehl", "Milch", "mittelscharfer Senf", "Butter", "Gemüsebrühe", "Rinderfond"},
                         to_taste_names)
        self.assertEqual([], p["reviews"])

    def test_stueck_of_a_countable_thing_is_one_stueck(self):
        p = self.parse(["Stück Pita-Brote", "Stück Knoblauchzehe", "Stück Zitrone, gewachst"], ["Alles verrühren."])
        self.assertEqual({565: (Decimal(1), "Stück", False, "one"), 45: (Decimal(1), "Stück", False, "one"),
                          186: (Decimal(1), "Stück", False, "one")}, lines_of(p))
        p = self.parse(["Stück Granatapfel", "Stück Ei", "Stück Schmand", "Stück Frühlingszwiebel"], ["Alles verrühren."])
        self.assertEqual({"Granatapfel": Decimal(1), "Ei": Decimal(1), "Schmand": Decimal(1), "Frühlingszwiebel": Decimal(1)},
                         {l["name"]: l["quantity"] for l in p["lines"] if l["unit"] == "Stück"})
        self.assertEqual([], p["reviews"])

    def test_anything_else_without_an_amount_goes_to_review(self):
        p = self.parse(["Stück Petersilie", "g Rinderhackfleischzubereitung", " Basmatireis", "Stück Weißwein"],
                       ["Alles verrühren."])
        self.assertEqual({}, lines_of(p))
        self.assertEqual(4, len(p["reviews"]))
        self.assertTrue(all(r["reason"].startswith("REVIEW") for r in p["reviews"]))

    # 5. covered
    def test_a_numbered_line_of_the_same_ingredient_means_no_row_of_its_own(self):
        p = self.parse(["1 Esslöffel Olivenöl", "Stück Öl"], ["Öl erhitzen."])
        self.assertEqual({1022: (Decimal(1), "EL", False, None)}, lines_of(p))
        self.assertIn("no row of its own", p["reviews"][0]["reason"])

    def test_two_amountless_lines_of_one_item_take_the_steps_amount_once(self):
        p = self.parse(["Stück Öl", " Olivenöl"], ["1 EL Öl erhitzen."])
        self.assertEqual({1022: (Decimal(1), "EL", False, "steps")}, lines_of(p))

    def test_numbered_lines_are_untouched(self):
        p = self.parse(["3 Esslöffel Olivenöl", "½ Stück Zitrone, gewachst"], ["Alles verrühren."])
        self.assertEqual({1022: (Decimal(3), "EL", False, None), 186: (Decimal("0.5"), "Stück", False, None)}, lines_of(p))

    def test_a_page_without_steps_still_parses(self):
        p = recipe_parse.parse_recipe({"name": "X", "recipeYield": 2, "recipeIngredient": ["Stück Salz"],
                                       "recipeInstructions": 0}, catalog())
        self.assertEqual((None, "", True, None), lines_of(p)[1148])


class ServingsTitleTest(unittest.TestCase):

    def info(self, name, recipe_yield=2):
        return recipe_parse.servings_info({"name": name, "recipeYield": recipe_yield})

    def check(self, name, servings, smax=None, source=None):
        i = self.info(name)
        self.assertEqual((servings, smax), (i["servings"], i["servings_max"]), name)
        if source:
            self.assertEqual(source, i["source"], name)

    def test_a_single_number_next_to_people_or_portions(self):
        self.check("Salat für 2 Personen | 20 Minuten", 2)
        self.check("American style BBQ 4 Portionen | erhitzen & genießen", 4, source=recipe_parse.FROM_TITLE)
        self.check("Bunte Gemüse-Gesichter 3 Portionen | verzehrfertig", 3)
        self.check("Black Burger Buns 5 Portionen (Inhalt: 331 g)", 5)
        self.check("Brunch für 6 Personen", 6)
        self.check("Herzhafter Brunch für 6 Personen 6 Portionen | im Ofen fertig backen", 6)
        self.check("Schinken-Tasche für 4 Personen", 4)
        self.check("Sonnige Vorspeisenplatte für 4 Personen 4 Portionen", 4)

    def test_one_person(self):
        self.check("1 Pers. Blitzschnelle Käsespätzepfanne für 1 Person | 15 Minuten", 1)
        self.check("1Pers. Risotto mit Garnelen für 1 Person", 1)
        self.check("Pasta für 1 Person", 1)

    def test_explicit_p_forms(self):
        for name in ("Meat Bundle 4P", "OTP 4P Rote Beete Carpaccio mit Meerrettichcreme", "Pasta  Bundle 4P"):
            self.check(name, 4)
        self.check("Fish Bundle 2P", 2)

    def test_ranges_with_personen_or_portionen(self):
        self.check("Crumble für 3 – 4 Personen | 40 Minuten", 3, 4, recipe_parse.FROM_RANGE_TITLE)
        self.check("Käseplatte für 2 – 4 Personen | 14,99 €", 2, 4)
        self.check("Miniburger für 2 – 3 Personen | 30 Minuten", 2, 3)
        self.check("Blaubeer-Brownies 3-4 Portionen | nach Rezeptanleitung", 3, 4)
        self.check("Gemüsequiche 1 – 2 Portionen (Inhalt: 350 g)", 1, 2)
        self.check("Ein süßer Start in den Tag 2 - 3 Portionen | Inhalt: siehe Zutatenliste", 2, 3)
        self.check("Gemüsesticks 4 - 5 Portionen | zubereiten & genießen", 4, 5)
        self.check("Focaccia Sandwiches zum Teilen 2 - 4 Portionen", 2, 4)

    def test_the_maximum_is_always_above_servings(self):
        for name in ("Kuchen 4 – 3 Portionen", "Kuchen 3 – 3 Portionen"):      # reversed or equal: not a range
            self.assertIsNone(self.info(name)["servings_max"], name)

    def test_contradictions_go_to_review(self):
        for name in ("OTP 4P Birnen Parfait 2 Portionen | zubereiten & genießen", "Pasta für 3 Personen 4 Portionen",
                     "Brownies 3-4 Portionen für 6 Personen"):
            with self.subTest(name=name):
                i = self.info(name)
                self.assertEqual(recipe_parse.REVIEW, i["source"])
                self.assertTrue(i["notes"][0].startswith("REVIEW"))

    def test_no_head_count_is_two(self):
        for name in ("Chicken Parm cheesy Schnitzel", "2-Gänge-Lunch: Rote Linsensuppe", "Burger mit 4 Sorten Käse",
                     "Salat 2 – 4 Wochen alt", "Käse 100 g – 200 g", "Suppe mit 3-4 Zutaten"):
            with self.subTest(name=name):
                self.check(name, 2, source=recipe_parse.FROM_YIELD)

    def test_title_helpers(self):
        self.assertIsNone(recipe_parse.title_people("Crumble für 3 – 4 Personen"))
        self.assertEqual((3, 4), recipe_parse.title_range("Crumble für 3 – 4 Personen"))
        self.assertEqual(1, recipe_parse.title_people("1 Pers. Käsespätzle für 1 Person"))
        self.assertIsNone(recipe_parse.title_range("Crumble für 4 Personen"))

    def test_servings_for_keeps_its_old_shape(self):
        self.assertEqual((3, recipe_parse.FROM_RANGE_TITLE), (lambda r: (r[0], r[2]))(
            recipe_parse.servings_for({"name": "Crumble für 3 – 4 Personen", "recipeYield": 2})))


if __name__ == "__main__":
    unittest.main()
