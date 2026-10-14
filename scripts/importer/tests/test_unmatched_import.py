"""Going forward, a recipe line the importer cannot match is stored, not dropped: extract.py writes it into the
draft as `-- UNMATCHED <no> <line>`, load_recipes.py puts it into recipe_unmatched_ingredient in the same
transaction as the recipe. Made-up pages and a fake connection: no network, no database.

Run from the repo root:  python3 -m unittest discover -s scripts/importer/tests
"""
import os
import shutil
import sys
import tempfile
import unittest
from decimal import Decimal

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

import extract  # noqa: E402
import load_recipes  # noqa: E402
from extract import Catalog, build_sql, load_overrides  # noqa: E402

NUTRITION = {k: None for k in ("cal", "prot", "carb", "fat", "fibre", "sugar")}
MATCHED = [("Zwiebel", "Stück", Decimal(2), False, 40, "override", ""),
           ("Speck", "g", Decimal(50), False, None, "none", "NO MATCH -> add to catalog or overrides.json")]


class FakeCursor:
    def __init__(self):
        self.statements = []
        self.lastrowid = 7

    def execute(self, sql, params=None):
        self.statements.append(("execute", sql, params))

    def executemany(self, sql, rows):
        self.statements.append(("executemany", sql, list(rows)))

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


class FakeConnection:
    def __init__(self):
        self.cur = FakeCursor()

    def cursor(self):
        return self.cur


class DraftTest(unittest.TestCase):

    def test_unmatched_lines_round_trip_through_the_draft(self):
        sql = build_sql("Made-up Pasta", 2, 30, NUTRITION, MATCHED, ["Kochen."], "https://example.test/r-0123456789abcdef01234567",
                        [(3, "50 g Speck"), (5, "100 g Mamas 'Spezial' Soße")])
        recipe, ings, steps = load_recipes.parse_draft(sql)
        self.assertEqual([(3, "50 g Speck"), (5, "100 g Mamas 'Spezial' Soße")], recipe["unmatched"])
        self.assertEqual([(40, "Stück", Decimal(2), False), (0, "g", Decimal(50), False)], ings)
        self.assertEqual([(1, "Kochen.")], steps)
        kept, n0, _ = load_recipes.clean_ingredients(ings)
        self.assertEqual(1, n0)
        self.assertEqual([(3, "50 g Speck"), (5, "100 g Mamas 'Spezial' Soße")], load_recipes.unmatched_rows(recipe, n0))

    def test_the_unmatched_comments_sit_before_the_ingredient_block(self):
        # rematch.py rewrites the lines between the two INSERTs and puts the terminators on the last row of that
        # block; a comment line inside it would break the SQL
        sql = build_sql("Made-up Pasta", 2, 30, NUTRITION, MATCHED, ["Kochen."], None, [(3, "50 g Speck")])
        lines = sql.splitlines()
        marks = [i for i, l in enumerate(lines) if l.startswith("-- UNMATCHED")]
        start = next(i for i, l in enumerate(lines) if l.startswith("INSERT INTO recipe_ingredient"))
        end = next(i for i, l in enumerate(lines) if l.startswith("INSERT INTO recipe_instructions"))
        self.assertTrue(marks and all(m < start for m in marks))
        self.assertFalse([l for l in lines[start + 1:end] if l.strip().startswith("--")])

    def test_an_older_draft_gives_its_dropped_lines_by_name_without_a_line_number(self):
        old = ("BEGIN;\nINSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, "
               "recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar, recipe_source_url)\n"
               "VALUES ('Alt', 2, 30, 0, NULL, NULL, NULL, NULL, NULL, NULL, NULL);\n"
               "INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, "
               "recipe_ingredient_unit, recipe_ingredient_quantity_needed, recipe_ingredient_to_taste) VALUES\n"
               "  ((SELECT recipe_id FROM recipe WHERE recipe_name='Alt'), 40, 'Stück', 2, 0),\n"
               "  ((SELECT recipe_id FROM recipe WHERE recipe_name='Alt'), 0, 'g', 50, 0);   "
               "-- REVIEW (none): Speck | NO MATCH -> add to catalog or overrides.json\nCOMMIT;\n")
        recipe, ings, _ = load_recipes.parse_draft(old)
        kept, n0, _ = load_recipes.clean_ingredients(ings)
        self.assertEqual([(40, "Stück", Decimal(2), False)], kept)
        self.assertEqual([(None, "Speck")], load_recipes.unmatched_rows(recipe, n0))

    def test_a_draft_without_dropped_lines_has_no_unmatched_rows(self):
        sql = build_sql("Made-up Pasta", 2, 30, NUTRITION, MATCHED[:1], ["Kochen."])
        recipe, ings, _ = load_recipes.parse_draft(sql)
        self.assertEqual([], load_recipes.unmatched_rows(recipe, 0))


class LoadTest(unittest.TestCase):

    def test_load_stores_the_unmatched_lines_with_the_recipe(self):
        con = FakeConnection()
        recipe = dict(name="Made-up", servings=2, cook=30, prep=0, cal=None, prot=None, carb=None, fat=None,
                      fibre=None, sugar=None, url=None)
        rid = load_recipes.load(con, recipe, [(40, "Stück", Decimal(2), False)], [(1, "Kochen.")],
                                [(3, "50 g  Speck"), (None, "x" * 600)])
        self.assertEqual(7, rid)
        kinds = [(k, sql.split("(")[0].strip()) for k, sql, _p in con.cur.statements]
        self.assertEqual("INSERT INTO recipe_unmatched_ingredient", kinds[-1][1])
        rows = con.cur.statements[-1][2]
        self.assertEqual([(7, 3, "50 g Speck"), (7, None, "x" * 500)], rows)
        self.assertIn("'unmatched'", con.cur.statements[-1][1])
        self.assertNotIn("%s) VALUES", con.cur.statements[-1][1])      # values only through parameters

    def test_a_recipe_with_nothing_unmatched_writes_no_unmatched_statement(self):
        con = FakeConnection()
        recipe = dict(name="Made-up", servings=2, cook=30, prep=0, cal=None, prot=None, carb=None, fat=None,
                      fibre=None, sugar=None, url=None)
        load_recipes.load(con, recipe, [(40, "Stück", Decimal(2), False)], [])
        self.assertFalse([s for s in con.cur.statements if "recipe_unmatched_ingredient" in s[1]])


class ExtractTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.saved = (extract.DRAFTS, extract.fetch_recipe)
        extract.DRAFTS = self.tmp
        extract.fetch_recipe = lambda h: {
            "name": "Made-up Pasta mit Speck", "recipeYield": "2 Portionen", "totalTime": "PT30M",
            "recipeIngredient": ["2 Stück Zwiebel", "1 Liter Wasser", "50 g Speck"],
            "recipeInstructions": [{"text": "Alles kochen."}]}

    def tearDown(self):
        extract.DRAFTS, extract.fetch_recipe = self.saved
        shutil.rmtree(self.tmp)

    def test_process_writes_the_unmatched_line_into_the_draft(self):
        cat = Catalog([(40, "onions", "zwiebel")], load_overrides())
        result, flags, kind = extract.process("0123456789abcdef01234567", None, cat, False, 0)
        self.assertEqual("ok", kind)
        self.assertEqual([(3, "50 g Speck")], result["unmatched"])
        with open(os.path.join(self.tmp, result["file"]), encoding="utf-8") as f:
            recipe, ings, _ = load_recipes.parse_draft(f.read())
        self.assertEqual([(3, "50 g Speck")], recipe["unmatched"])


if __name__ == "__main__":
    unittest.main()
