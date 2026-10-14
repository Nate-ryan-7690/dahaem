"""Rematch tool on made-up recipes: a JSON snapshot instead of the database, a pre-filled page cache instead of the
real one (fixtures/rematch). Checks every case, the guarded SQL against the committed expected file (which
RematchUnmatchedSqlTest on the Java side applies to a throwaway MariaDB) and the report.

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

import rematch_unmatched as tool  # noqa: E402

FIXTURES = os.path.join(HERE, "fixtures", "rematch")
EXPECTED_SQL = os.path.join(FIXTURES, "expected_rematch.sql")


def fill_cache(cache_dir):
    os.makedirs(cache_dir)
    with open(os.path.join(FIXTURES, "pages.json"), encoding="utf-8") as f:
        pages = json.load(f)
    for n, (url, recipe) in enumerate(pages.items()):
        with open(os.path.join(cache_dir, f"page{n}.json"), "w", encoding="utf-8") as f:
            json.dump({"url": url, "status": "ok", "recipe": recipe}, f)


class RematchTest(unittest.TestCase):

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.cache = os.path.join(self.tmp, "cache")
        self.out = os.path.join(self.tmp, "out")
        fill_cache(self.cache)
        self.stdout = io.StringIO()
        with redirect_stdout(self.stdout), redirect_stderr(io.StringIO()):
            tool.main(["--state", os.path.join(FIXTURES, "state.json"), "--cache", self.cache,
                       "--out", self.out, "--stamp", "TEST"])
        self.rows = {}
        for line in self.read("rematch_TEST.tsv").splitlines()[1:]:
            c = line.split("\t")
            self.rows[(int(c[1]), c[5])] = dict(zip(tool.TSV_HEADER, c))

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def read(self, name):
        with open(os.path.join(self.out, name), encoding="utf-8") as f:
            return f.read()

    def statements(self):
        return [l for l in self.read("rematch_TEST.sql").splitlines() if not l.startswith("--") and l.strip()]

    def row(self, recipe, raw):
        return self.rows[(recipe, raw)]

    def case(self, recipe, raw):
        return self.row(recipe, raw)["case"]

    # ----- the cases -----
    def test_sql_matches_the_expected_file(self):
        with open(EXPECTED_SQL, encoding="utf-8") as f:
            self.assertEqual(f.read(), "\n".join(self.statements()) + "\n")

    def test_oil_family_is_covered_by_an_olive_oil_the_recipe_has(self):
        # the recipe stores "olivenöl mild" (#1023): Öl and (Oliven-)Öl* are the same bottle, no row of their own
        for raw in ("Öl", "(Oliven-)Öl*"):
            self.assertEqual("covered", self.case(1, raw))
            self.assertEqual("1023", self.row(1, raw)["covered_by"])
        # a different oil is its own ingredient: inserted with its amount
        sun = self.row(1, "2 Esslöffel Sonnenblumenöl")
        self.assertEqual(("insert", "1028", "2", "EL", "0"),
                         (sun["case"], sun["item_id"], sun["quantity"], sun["unit"], sun["to_taste"]))

    def test_salt_and_pepper_family(self):
        # stored Himalaya salt covers "nach Geschmack Salz" (item Speisesalz); no pepper stored: to-taste insert
        self.assertEqual("covered", self.case(1, "nach Geschmack Salz"))
        self.assertEqual("1152", self.row(1, "nach Geschmack Salz")["covered_by"])
        pepper = self.row(1, "Pfeffer")
        self.assertEqual(("insert", "1156", "", "", "1"),
                         (pepper["case"], pepper["item_id"], pepper["quantity"], pepper["unit"], pepper["to_taste"]))

    def test_vinegar_family_follows_the_repair_tools_definition(self):
        self.assertEqual("covered", self.case(4, "Weißweinessig"), "the bare Essig stands for any vinegar")
        self.assertEqual("1055", self.row(4, "Weißweinessig")["covered_by"])
        self.assertEqual("insert", self.case(5, "Weißweinessig"), "Balsamico and Weißwein are two ingredients")
        self.assertEqual("1", self.row(5, "Weißweinessig")["to_taste"])

    def test_same_item_twice_in_one_recipe(self):
        a, b = self.row(2, "100 g Honig"), self.row(2, "50 g Honig")
        self.assertEqual(("insert", "150", "g"), (a["case"], a["quantity"], a["unit"]))
        self.assertEqual(("insert", "150", "g"), (b["case"], b["quantity"], b["unit"]))
        inserts = [s for s in self.statements() if s.startswith("INSERT") and "recipe_id = 2)" in s]
        self.assertEqual(1, len(inserts), "one row for both lines")
        for raw in ("100 g Honig", "2 Esslöffel Honig"):
            self.assertEqual("review", self.case(3, raw), "different units: nothing inserted")
        self.assertFalse([s for s in self.statements() if s.startswith("INSERT") and "recipe_id = 3)" in s])
        self.assertIn("different units", self.row(3, "100 g Honig")["note"])
        # an amount beats a to-taste line, two to-taste lines are one row
        self.assertEqual("100", self.row(18, "Zucker nach Geschmack")["quantity"])
        self.assertEqual("1", self.row(17, "Salz")["to_taste"])
        self.assertEqual(1, len([s for s in self.statements() if s.startswith("INSERT") and "recipe_id = 17)" in s]))

    def test_amounts_come_from_the_steps_like_the_repair_tool(self):
        h = self.row(15, "Honig")
        self.assertEqual(("insert", "1", "EL", "0"), (h["case"], h["quantity"], h["unit"], h["to_taste"]))

    def test_the_item_the_recipe_already_has_is_covered(self):
        self.assertEqual("covered", self.case(16, "3 Esslöffel Honig"))

    def test_new_meat_items_are_matched_through_the_overrides(self):
        for raw, item, qty in (("300 g Hirschsteak", "1822", "300"), ("400 g Rinderkeulenstreifen", "1660", "400"),
                               ("100 g Schweinsbraten-Aufschnitt mit grünem Pfeffer", "1823", "100")):
            r = self.row(9, raw)
            self.assertEqual(("insert", item, qty, "g"), (r["case"], r["item_id"], r["quantity"], r["unit"]))

    def test_non_food_is_ignored_and_skewers_are_checked_against_the_steps(self):
        self.assertEqual("non-food", self.case(6, "8 Stück Holzspieße"))
        self.assertEqual("non-food", self.case(6, "4 Stück Schaschlikspieße"), "a step puts food on them")
        self.assertEqual("review", self.case(7, "4 Stück Schaschlikspieße"), "maybe the prepared meat skewers")

    def test_no_item_stays_and_is_counted(self):
        self.assertEqual("no-item", self.case(8, "Zauberpulver XY"))
        noitem = self.read("rematch_TEST_noitem.tsv").splitlines()
        self.assertEqual(["lines\trecipes\tname", "2\t2\tZauberpulver XY"], noitem)

    def test_ambiguous_goes_to_the_review_tsv_and_changes_nothing(self):
        review = [l.split("\t") for l in self.read("rematch_TEST_review.tsv").splitlines()[1:]]
        self.assertEqual({"300 g Kartoffel", "5 g Honig", "100 g Honig", "2 Esslöffel Honig",
                          "4 Stück Schaschlikspieße"}, {c[5] for c in review})
        self.assertEqual(6, len(review))
        self.assertIn("uncertain item match (like)", self.row(12, "300 g Kartoffel")["note"])
        self.assertIn("no longer on the cached page", self.row(13, "5 g Honig")["note"])
        self.assertIn("page not in the cache", self.row(14, "100 g Honig")["note"])
        sql = "\n".join(self.statements())
        for raw in ("300 g Kartoffel", "5 g Honig", "Zauberpulver XY"):
            self.assertNotIn(f"= '{raw}'", sql)

    def test_unchecked_rows_are_never_touched(self):
        sql = "\n".join(self.statements())
        self.assertNotIn("keine Quell-URL", sql)
        self.assertNotIn("Seite nicht im Cache", sql)
        self.assertNotIn("'unchecked'", sql)

    # ----- the SQL -----
    def test_every_statement_is_guarded_and_there_is_no_transaction_control(self):
        stmts = self.statements()
        inserts = [s for s in stmts if s.startswith("INSERT")]
        deletes = [s for s in stmts if s.startswith("DELETE")]
        self.assertEqual(12, len(inserts))
        self.assertEqual(24, len(deletes))
        for s in inserts:
            self.assertTrue(s.startswith("INSERT INTO recipe_ingredient "))
            self.assertIn("WHERE EXISTS (SELECT 1 FROM recipe WHERE recipe_id = ", s)
            self.assertIn("AND EXISTS (SELECT 1 FROM inventory_item WHERE inventory_item_id = ", s)
            self.assertIn("AND NOT EXISTS (SELECT 1 FROM recipe_ingredient WHERE ", s)
        for s in deletes:
            self.assertRegex(s, r"^DELETE FROM recipe_unmatched_ingredient WHERE recipe_unmatched_ingredient_id = \d+ "
                                r"AND recipe_unmatched_ingredient_recipe_id = \d+ "
                                r"AND recipe_unmatched_ingredient_line_no <=> \d+ "
                                r"AND BINARY recipe_unmatched_ingredient_raw_text = '.*' "
                                r"AND recipe_unmatched_ingredient_reason = 'unmatched'")
        joined = "\n".join(inserts + deletes).upper()
        for word in ("BEGIN", "COMMIT", "START TRANSACTION", "UPDATE ", "DROP", "TRUNCATE"):
            self.assertNotIn(word, joined)
        # the deletes that depend on an item being there say which item
        self.assertEqual(21, len([d for d in deletes if "AND EXISTS (SELECT 1 FROM recipe_ingredient" in d]))

    def test_report(self):
        out = self.stdout.getvalue()
        self.assertEqual(out.split("\n\nwrote")[0], self.read("rematch_TEST.txt").rstrip("\n"))
        self.assertIn("stored unmatched rows read: 32 ('unchecked' rows, never touched: 2)", out)
        self.assertIn("a) matches an item now, row inserted: 15", out)
        self.assertIn("a) matches an item now, already covered: 6", out)
        self.assertIn("b) non-food, ignored: 3", out)
        self.assertIn("c) no item yet, stays unmatched: 2", out)
        self.assertIn("d) review, nothing changed: 6", out)
        self.assertIn("recipe_ingredient rows to insert: 12", out)
        self.assertIn("recipe_unmatched_ingredient rows to delete: 24", out)
        self.assertIn("recipes that stop being 'unklar' because of this run: 10 (still unklar: 8)", out)
        self.assertRegex(out, r"\n\s+4\s+3\s+Honig\n")      # 4 Honig lines stay (recipes 3, 13, 14), all in review

    def test_the_tool_never_writes_to_a_database(self):
        with open(os.path.join(os.path.dirname(HERE), "rematch_unmatched.py"), encoding="utf-8") as f:
            code = f.read()
        self.assertIn("SET SESSION TRANSACTION READ ONLY", code)
        for call in ("commit()", ".execute(\"INSERT", ".execute(\"DELETE", ".execute(\"UPDATE"):
            self.assertNotIn(call, code)


class StoredFormTest(unittest.TestCase):
    """The finder stores a page line trimmed; the page keeps its trailing blank / no-break space. The 2026-10-14 run
    held back ~100 Öl, (Oliven-)Öl*, Zucker and Salz lines as 'no longer on the cached page' because of this."""

    ITEMS = [(1022, "olive oil", "olivenöl"), (1061, "white sugar", "weißer zucker"), (1148, "table salt", "speisesalz")]
    LINES = ["(Oliven-)Öl*\u00a0", " Öl ", "Zucker\u00a0\t", "1 Prise nach Geschmack Salz\u00a0"]

    def run_rematch(self):
        recipe = {"id": 1, "name": "Made-up Blanks", "source_url": "https://example.test/recipes/blanks-0001"}
        page = {"name": "x", "recipeYield": "2", "recipeIngredient": self.LINES,
                "recipeInstructions": [{"text": "Alles kochen."}]}
        unmatched = [{"id": n, "recipe_id": 1, "line_no": n, "raw_text": tool.stored_form(raw), "reason": "unmatched"}
                     for n, raw in enumerate(self.LINES, 1)]
        state = tool.State({1: recipe}, {}, self.ITEMS, unmatched)
        pages = {"https://example.test/recipes/blanks-0001": ("ok", page)}
        return tool.run(state, pages)

    def test_stored_form(self):
        self.assertEqual("(Oliven-)Öl*", tool.stored_form("(Oliven-)Öl*\u00a0"))
        self.assertEqual("Öl", tool.stored_form(" Öl "))
        self.assertEqual("a b", tool.stored_form("a\x00b"))
        self.assertEqual(500, len(tool.stored_form("x" * 600)))

    def test_lines_with_blanks_are_found_on_the_page(self):
        res = self.run_rematch()
        cases = {r.raw: (r.case, r.item_id) for r in res.rows}
        self.assertEqual(4, len(cases))
        self.assertTrue(all(c == tool.INSERT for c, _i in cases.values()), cases)
        self.assertEqual({1022, 1061, 1148}, {i for _c, i in cases.values()})

    def test_the_inserted_rows(self):
        res = self.run_rematch()
        rows = {(i[1], i[2], str(i[3]), i[4]) for i in res.inserts}
        self.assertEqual({(1022, "", "None", True), (1061, "", "None", True), (1148, "Prise", "1", False)}, rows)


class FamilyCoverTest(unittest.TestCase):
    NAMES = {1: "olivenöl", 2: "sonnenblumenöl", 3: "essig", 4: "weißweinessig", 5: "speisesalz", 6: "honig",
             7: "natives olivenöl extra", 8: "kampot-pfeffer"}

    def cover(self, line, *stored):
        return tool.family_cover(line, set(stored), self.NAMES)

    def test_oil(self):
        self.assertEqual([1], self.cover("Öl", 1))
        self.assertEqual([7], self.cover("(Oliven-)Öl*", 7))
        self.assertEqual([1], self.cover("Olivenöl", 1, 2))
        self.assertEqual([], self.cover("Öl", 2), "sunflower oil is another oil")
        self.assertEqual([], self.cover("Sonnenblumenöl", 1))

    def test_vinegar(self):
        self.assertEqual([3], self.cover("Weißweinessig", 3))
        self.assertEqual([3], self.cover("Balsamicoessig", 3, 4), "bare Essig stands for any vinegar")
        self.assertEqual([3], self.cover("Essig", 3))
        self.assertEqual([4], self.cover("Essig", 4))
        self.assertEqual([], self.cover("Balsamicoessig", 4), "two specific vinegars differ")

    def test_salt_and_pepper_only_when_the_line_is_the_bare_word(self):
        self.assertEqual([5], self.cover("Salz", 5))
        self.assertEqual([5], self.cover("nach Geschmack Salz".replace("nach Geschmack ", ""), 5))
        self.assertEqual([8], self.cover("Pfeffer", 8))
        self.assertEqual([], self.cover("Pfeffer", 6))
        self.assertEqual([], self.cover("Kampot-Pfeffer", 8, 5), "a specific pepper is its own ingredient")
        self.assertEqual([], self.cover("Meersalz", 5))

    def test_other_families_are_never_covered(self):
        self.assertEqual([], self.cover("Honig", 6))
        self.assertEqual([], self.cover("Zwiebel", 5))


class NonFoodTest(unittest.TestCase):
    def test_config(self):
        cfg = tool.load_non_food()
        self.assertEqual(("yes", "not food (non_food.json)"), tool.non_food_check("holzspiesse", [], cfg))
        self.assertEqual("yes", tool.non_food_check("holzspiesse aus buche", [], cfg)[0])
        self.assertEqual("yes", tool.non_food_check("backpapier", [], cfg)[0])
        self.assertIsNone(tool.non_food_check("honig", [], cfg))
        self.assertEqual("review", tool.non_food_check("schaschlikspiesse", [], cfg)[0], "no page, no steps")
        self.assertEqual("yes", tool.non_food_check("schaschlikspiesse", ["Alles auf die Spieße aufstecken."], cfg)[0])
        self.assertEqual("review", tool.non_food_check("schaschlikspiesse", ["Spieße braten."], cfg)[0])


if __name__ == "__main__":
    unittest.main()
