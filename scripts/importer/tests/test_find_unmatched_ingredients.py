"""Unmatched-lines tool on made-up JSON-LD: a JSON snapshot instead of the database, a pre-filled page cache
instead of the real one. Checks the guarded SQL against the committed expected file (which FindUnmatchedSqlTest
on the Java side applies to a throwaway MariaDB) and the report.

Run from the repo root:  python3 -m unittest discover -s scripts/importer/tests
"""
import io
import json
import os
import shutil
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

import find_unmatched_ingredients as tool  # noqa: E402
import recipe_parse  # noqa: E402
from extract import Catalog, is_pantry, load_overrides  # noqa: E402

FIXTURES = os.path.join(HERE, "fixtures", "unmatched")
EXPECTED_SQL = os.path.join(FIXTURES, "expected_unmatched.sql")


def fill_cache(cache_dir):
    os.makedirs(cache_dir)
    with open(os.path.join(FIXTURES, "pages.json"), encoding="utf-8") as f:
        pages = json.load(f)
    for n, (url, recipe) in enumerate(pages.items()):
        with open(os.path.join(cache_dir, f"page{n}.json"), "w", encoding="utf-8") as f:
            json.dump({"url": url, "status": "ok", "recipe": recipe}, f)
    with open(os.path.join(cache_dir, "gone.json"), "w", encoding="utf-8") as f:     # recipe 5: the page is gone
        json.dump({"url": "https://example.test/recipes/weg-0005", "status": "404", "recipe": None}, f)
    with open(os.path.join(cache_dir, "broken.json"), "w", encoding="utf-8") as f:
        f.write("{not json")


class UnmatchedToolTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.cache = os.path.join(self.tmp, "cache")
        self.out = os.path.join(self.tmp, "out")
        fill_cache(self.cache)

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def run_tool(self, *extra):
        stdout = io.StringIO()
        with redirect_stdout(stdout), redirect_stderr(io.StringIO()):
            tool.main(["--state", os.path.join(FIXTURES, "state.json"), "--cache", self.cache,
                       "--out", self.out, "--stamp", "TEST", *extra])
        return stdout.getvalue()

    def read(self, name):
        with open(os.path.join(self.out, name), encoding="utf-8") as f:
            return f.read()

    def statements(self):
        return [l for l in self.read("unmatched_TEST.sql").splitlines() if not l.startswith("--") and l.strip()]

    def test_sql_matches_the_expected_file(self):
        self.run_tool()
        with open(EXPECTED_SQL, encoding="utf-8") as f:
            self.assertEqual(f.read(), "\n".join(self.statements()) + "\n")

    def test_every_insert_is_guarded_and_there_is_no_transaction_control_or_delete(self):
        self.run_tool()
        stmts = self.statements()
        self.assertEqual(9, len(stmts))
        for s in stmts:
            self.assertTrue(s.startswith("INSERT INTO recipe_unmatched_ingredient "))
            self.assertIn("WHERE EXISTS (SELECT 1 FROM recipe WHERE recipe_id = ", s)
            self.assertIn("AND NOT EXISTS (SELECT 1 FROM recipe_unmatched_ingredient WHERE ", s)
        joined = "\n".join(stmts).upper()
        for word in ("BEGIN", "COMMIT", "START TRANSACTION", "DELETE", "UPDATE", "DROP", "TRUNCATE"):
            self.assertNotIn(word, joined)

    def test_unmatched_lines_are_the_ones_whose_item_is_not_stored(self):
        self.run_tool()
        rows = [l.split("\t") for l in self.read("unmatched_TEST.tsv").splitlines()[1:]]
        by_recipe = {}
        for rid, _name, line_no, reason, name, raw in rows:
            by_recipe.setdefault(int(rid), []).append((line_no, reason, raw))
        self.assertEqual([("3", "unmatched", "50 g Speck")], by_recipe[1])           # the dropped Speck
        self.assertNotIn(2, by_recipe)                                                # complete recipe: nothing
        self.assertEqual([("", "unchecked", "(keine Quell-URL)")], by_recipe[3])
        self.assertEqual([("", "unchecked", "(Seite nicht im Cache)")], by_recipe[4])
        self.assertEqual([("", "unchecked", "(Seite ohne Rezept: 404)")], by_recipe[5])
        # a catalogue match the recipe never stored counts too
        self.assertEqual([("2", "unmatched", "200 g Spaghetti")], by_recipe[6])
        # water is skipped; a trailing slash on the stored URL still finds the page
        self.assertEqual([("3", "unmatched", "100 g Mamas 'Spezial' Soße"), ("4", "unmatched", "Stück Speck")],
                         by_recipe[7])
        # the same raw text twice keeps both line numbers
        self.assertEqual([("1", "unmatched", "100 g Speck"), ("3", "unmatched", "100 g Speck")], by_recipe[8])

    def test_a_quote_in_a_line_is_escaped_in_the_sql(self):
        self.run_tool()
        self.assertTrue([s for s in self.statements() if "'100 g Mamas ''Spezial'' Soße'" in s])

    def test_report(self):
        out = self.run_tool()
        self.assertEqual(out.split("\n\nwrote")[0], self.read("unmatched_TEST.txt").rstrip("\n"))
        self.assertIn("warning: 1 cache files could not be read", out)
        self.assertIn("recipes checked against their cached page: 5", out)
        self.assertIn("with unmatched lines: 4 (6 lines in total)", out)
        self.assertIn("recipes unchecked (one 'unchecked' row each): 3"
                      " (no URL: 1, URL without a cached page: 1, page without a recipe: 1)", out)
        self.assertIn("recipes that will be 'unklar' for diet purposes: 7", out)
        self.assertRegex(out, r"\n\s+4\s+3\s+Speck\n")          # 4 lines named Speck, in recipes 1, 7 and 8

    def test_top_lists_the_most_common_names_first_and_respects_the_limit(self):
        out = self.run_tool("--top", "1")
        self.assertIn("the 1 most common unmatched names", out)
        lines = out.split("most common unmatched names (lines, recipes):\n")[1].split("\n\nwrote")[0].splitlines()
        self.assertEqual(1, len(lines))
        self.assertTrue(lines[0].strip().endswith("Speck"))

    def test_unmatched_lines_helper_ignores_pantry_and_stored_items(self):
        catalog = Catalog([(40, "onions", "zwiebel"), (2, "spaghetti", "spaghetti")], load_overrides())
        rec = {"name": "x", "recipeYield": "2", "recipeIngredient": ["1 Liter Wasser", "2 Stück Zwiebel", "50 g Speck"]}
        parsed = recipe_parse.parse_recipe(rec, catalog, is_pantry=lambda n: is_pantry(n, n))
        self.assertEqual([(3, "50 g Speck", "Speck")], recipe_parse.unmatched_lines(rec, parsed, {40}))
        self.assertEqual([], recipe_parse.unmatched_lines(
            {"recipeIngredient": ["2 Stück Zwiebel"]}, {"lines": [{"raw": "2 Stück Zwiebel", "item_id": 40,
                                                                  "name": "Zwiebel"}]}, {40}))


if __name__ == "__main__":
    unittest.main()
