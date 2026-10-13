"""Enrichment tool on made-up JSON-LD: a JSON snapshot instead of the database, a pre-filled page
cache instead of the real one. Checks the guarded SQL against the committed expected file (which
EnrichSqlTest on the Java side applies to a throwaway MariaDB) and the report.

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
from decimal import Decimal

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

import enrich_recipes  # noqa: E402

FIXTURES = os.path.join(HERE, "fixtures", "enrich")
EXPECTED_SQL = os.path.join(FIXTURES, "expected_enrich.sql")
CUISINE_MAP = os.path.join(HERE, "..", "..", "..", "config", "cuisine_map.json")


def fill_cache(cache_dir):
    os.makedirs(cache_dir)
    with open(os.path.join(FIXTURES, "pages.json"), encoding="utf-8") as f:
        pages = json.load(f)
    for n, (url, recipe) in enumerate(pages.items()):
        with open(os.path.join(cache_dir, f"page{n}.json"), "w", encoding="utf-8") as f:
            json.dump({"url": url, "status": "ok", "recipe": recipe}, f)
    # a 404 and a broken file are not pages
    with open(os.path.join(cache_dir, "gone.json"), "w", encoding="utf-8") as f:
        json.dump({"url": "https://example.test/recipes/gone-0008", "status": "404", "recipe": None}, f)
    with open(os.path.join(cache_dir, "broken.json"), "w", encoding="utf-8") as f:
        f.write("{not json")


class EnrichToolTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.cache = os.path.join(self.tmp, "cache")
        self.out = os.path.join(self.tmp, "out")
        fill_cache(self.cache)

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def run_tool(self, state="state.json"):
        stdout = io.StringIO()
        with redirect_stdout(stdout), redirect_stderr(io.StringIO()):
            enrich_recipes.main(["--state", os.path.join(FIXTURES, state), "--cache", self.cache,
                                 "--out", self.out, "--cuisine-map", CUISINE_MAP, "--stamp", "TEST"])
        return stdout.getvalue()

    def read(self, name):
        with open(os.path.join(self.out, name), encoding="utf-8") as f:
            return f.read()

    def statements(self):
        return [l for l in self.read("enrich_TEST.sql").splitlines() if not l.startswith("--") and l.strip()]

    def test_sql_matches_the_expected_file(self):
        self.run_tool()
        with open(EXPECTED_SQL, encoding="utf-8") as f:
            self.assertEqual(f.read(), "\n".join(self.statements()) + "\n")

    def test_one_guarded_update_per_recipe_and_no_transaction_control(self):
        self.run_tool()
        sql = self.read("enrich_TEST.sql")
        stmts = self.statements()
        self.assertEqual(4, len(stmts))                 # recipes 1 - 4; 5 is complete, 6 / 7 unmatched
        for s in stmts:
            self.assertTrue(s.startswith("UPDATE recipe SET "))
            self.assertRegex(s, r"WHERE recipe_id = \d+ AND \(.* IS NULL.*\);$")
        for word in ("BEGIN", "COMMIT", "START TRANSACTION"):
            self.assertNotIn(word, "\n".join(stmts).upper())
        self.assertNotIn("DELETE", sql.upper().replace("-- ", ""))

    def test_existing_values_are_never_overwritten(self):
        self.run_tool()
        recipe4 = [s for s in self.statements() if "recipe_id = 4 " in s]
        self.assertEqual(1, len(recipe4))
        s = recipe4[0]
        self.assertNotIn("own-image-0004", s)           # a recipe with an own image gets no linked one,
        self.assertNotIn("recipe_image_url", s)         # not even the missing thumbnail
        self.assertNotIn("recipe_thumbnail_url", s)
        self.assertNotIn("recipe_image_source", s)
        self.assertNotIn("recipe_description", s)       # description was set by hand
        self.assertIn("recipe_cuisine = COALESCE(recipe_cuisine, 'Amerikanisch')", s)

    def test_complete_recipe_gets_no_statement(self):
        self.run_tool()
        self.assertFalse([s for s in self.statements() if "recipe_id = 5 " in s])

    def test_report(self):
        out = self.run_tool()
        self.assertEqual(out.split("\n\nwrote")[0], self.read("enrich_TEST.txt").rstrip("\n"))
        self.assertIn("recipes matched by recipe_source_url: 5", out)
        self.assertIn("recipes unmatched: 2 (no URL: 1, URL without a cached page: 1)", out)
        self.assertIn("recipes with an UPDATE: 4 (nothing new to fill: 1)", out)
        self.assertIn("unmapped cuisine values (left NULL, add them to config/cuisine_map.json): 1 recipes", out)
        self.assertIn("'Klingonisch'", out)
        self.assertIn("cached pages without a DB recipe (not imported; count only): 2", out)
        self.assertIn("cache files without a usable page: 2", out)
        self.assertIn("recipe_image_url         2", out)
        self.assertIn("recipe_description       2", out)    # recipes 1, 3 (2 is empty, 4 set by hand, 5 set)
        self.assertIn("recipe_image_source      2", out)    # recipes 1 and 2
        self.assertIn("recipe_cuisine           2", out)    # recipes 1 and 4

    def test_read_page_details(self):
        cmap = enrich_recipes.load_cuisine_map(CUISINE_MAP)
        values, unmapped = enrich_recipes.read_page(
            {"recipeCuisine": 0, "aggregateRating": {"ratingValue": 4.5, "ratingCount": 0},
             "image": {"url": "https://img.example.test/a.jpg"}, "thumbnailUrl": "javascript:alert(1)"}, cmap)
        self.assertIsNone(unmapped)
        self.assertNotIn("recipe_cuisine", values)       # 0 = none
        self.assertEqual(Decimal("4.50"), values["recipe_rating_avg"])
        self.assertEqual(0, values["recipe_rating_count"])
        self.assertEqual("https://img.example.test/a.jpg", values["recipe_image_url"])
        self.assertNotIn("recipe_thumbnail_url", values)  # not an http(s) URL
        self.assertEqual("hellofresh", values["recipe_image_source"])
        # a rating outside 0 - 5 or a half rating is dropped; no image -> no source
        values, _ = enrich_recipes.read_page({"aggregateRating": {"ratingValue": 6, "ratingCount": 2}}, cmap)
        self.assertEqual({}, values)
        values, _ = enrich_recipes.read_page({"aggregateRating": {"ratingValue": 4}}, cmap)
        self.assertEqual({}, values)

    def test_cuisine_map_covers_the_known_values_with_one_name_each(self):
        cmap = enrich_recipes.load_cuisine_map(CUISINE_MAP)
        for raw in ("American", "Amerikanisch", "North American"):
            self.assertEqual("Amerikanisch", cmap[raw])
        for raw in ("Middle Eastern", "Orientalisch"):
            self.assertEqual("Orientalisch", cmap[raw])
        self.assertEqual("Schwedisch", cmap["Svensk"])
        self.assertIsNone(cmap["0"])
        self.assertIsNone(cmap[""])
        for raw in ("Deutsch", "Italienisch", "Moroccan", "Austrian", "Argentinsk", "Nedeländsk", "Belgisk",
                    "Peruansk", "Dansk", "Malaysisk", "Filipino", "Ivorian", "Kubanisch", "Fusion",
                    "Europäisch", "Asiatisch"):
            self.assertTrue(cmap[raw], raw)


if __name__ == "__main__":
    unittest.main()
