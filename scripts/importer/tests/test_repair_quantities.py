"""Repair tool on fixtures: a JSON snapshot instead of the database, a pre-filled page cache
instead of the network (--offline). Checks the guarded SQL against the committed expected file
(which RepairSqlTest on the Java side applies to a throwaway MariaDB) and the summary.

Run from the repo root:  python3 -m unittest discover -s scripts/importer/tests
"""
import io
import json
import os
import shutil
import sys
import tempfile
import unittest
from decimal import Decimal
from contextlib import redirect_stderr, redirect_stdout

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

import repair_quantities  # noqa: E402
from repair_quantities import Fetcher, build_page_index, name_key, title_key  # noqa: E402

FIXTURES = os.path.join(HERE, "fixtures")
REPAIR = os.path.join(FIXTURES, "repair")
EXPECTED_SQL = os.path.join(REPAIR, "expected_repair_suspect.sql")


def fill_cache(cache_dir):
    """Pretend every page in pages.json was fetched before (the 404 one included)."""
    with open(os.path.join(REPAIR, "pages.json"), encoding="utf-8") as f:
        pages = json.load(f)
    fetcher = Fetcher(cache_dir, offline=True)
    for url, page in pages.items():
        if page == 404:
            fetcher._store(fetcher._path(url), url, "404", None)
        else:
            with open(os.path.join(FIXTURES, "jsonld", page), encoding="utf-8") as f:
                fetcher._store(fetcher._path(url), url, "ok", json.load(f))


class RepairToolTest(unittest.TestCase):

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
            repair_quantities.main(["--state", os.path.join(REPAIR, "state.json"),
                                    "--urls", os.path.join(REPAIR, "recipe_urls.txt"),
                                    "--cache", self.cache, "--out", self.out, "--offline",
                                    "--stamp", "TEST", *extra])
        return stdout.getvalue()

    def read(self, name):
        with open(os.path.join(self.out, name), encoding="utf-8") as f:
            return f.read()

    def test_suspect_run_writes_the_expected_guarded_sql(self):
        self.run_tool("--select", "suspect")
        with open(EXPECTED_SQL, encoding="utf-8") as f:
            self.assertEqual(f.read(), self.read("repair_suspect_TEST.sql"))

    def test_sql_is_guarded_and_has_no_transaction_control(self):
        self.run_tool("--select", "suspect")
        statements = [l for l in self.read("repair_suspect_TEST.sql").splitlines() if l and not l.startswith("--")]
        self.assertTrue(statements)
        for s in statements:
            self.assertNotRegex(s.upper(), r"^(BEGIN|START TRANSACTION|COMMIT|ROLLBACK)")
            if s.startswith("UPDATE recipe_ingredient"):
                self.assertIn("recipe_ingredient_quantity_needed <=>", s)
                self.assertIn("BINARY recipe_ingredient_unit =", s)
            elif s.startswith("INSERT"):
                self.assertIn("WHERE NOT EXISTS", s)
            elif "recipe_servings =" in s:
                self.assertRegex(s, r"AND recipe_servings = \d+;$")
            elif "recipe_source_url" in s:
                self.assertTrue(s.endswith("AND recipe_source_url IS NULL;"))
            else:
                self.fail("unexpected statement: " + s)

    def test_summary(self):
        out = self.run_tool("--select", "suspect")
        for expected in [
            "selector: suspect    recipes selected: 6    URLs loaded: 8",
            "recipes: matched 3 | ambiguous 1 | unmatched 2 (of which 2 incomplete, re-run to retry)",
            "pages: fetched 0 | from cache 7 | 404 1 | fetch errors 0 | robots.txt-disallowed 0 | not cached (offline) 1",
            "rows updated: 7 = ½-bug fixed 3 | rounding fixed 2 | unit changed 1 | now to-taste 1 | from the steps 0 | other 0",
            "lines inserted: 3 = EL/TL 2 | to-taste 1 | from the steps 0 | 1 Stück 0 | other 0",
            "servings changed: 1 (ranges with a maximum: 0) | servings held for review 1    source URLs set: 3",
            "held for review (TSV only): other changes 1 | uncertain item match 2 | unexplained unit change 1"
            " | unmatched ingredient 0 | amountless line without a safe amount 0",
        ]:
            self.assertIn(expected, out)
        self.assertIn("nothing was applied to the database", out)
        self.assertEqual(out.split("\n\nwrote")[0].strip(), self.read("repair_suspect_TEST.txt").strip())

    def test_review_tsv_lists_held_and_unmatched(self):
        self.run_tool("--select", "suspect")
        rows = [l.split("\t") for l in self.read("repair_suspect_TEST.tsv").splitlines()[1:]]
        actions = {(r[0], r[3], r[5]) for r in rows}
        self.assertIn(("1", "review-held", "270"), actions)            # 250 g -> 300 g: not a known bug
        self.assertIn(("1", "review-uncertain-match", "59"), actions)  # "Gurke" only fuzzily matched
        self.assertIn(("2", "review-unit", "1070"), actions)
        self.assertIn(("2", "review-uncertain-match", "143"), actions)
        self.assertIn(("7", "review-servings", ""), actions)
        self.assertIn(("1", "db-only", "45"), actions)
        self.assertIn(("3", "ambiguous", ""), actions)
        self.assertIn(("4", "unmatched", ""), actions)
        self.assertIn(("5", "unmatched", ""), actions)

    def tsv(self):
        header, *rows = [l.split("\t") for l in self.read("repair_suspect_TEST.tsv").splitlines()]
        return [dict(zip(header, r)) for r in rows]

    def sql_statements(self):
        return [l for l in self.read("repair_suspect_TEST.sql").splitlines() if l and not l.startswith("--")]

    def test_existing_row_with_uncertain_match_gets_no_update(self):
        # stored 5 Stück on item 59; the page says "½ Stück Gurke", which only fuzzily matches 59.
        # A ½-bug by its numbers, but an uncertain match must not "correct" a stored row.
        self.run_tool("--select", "suspect")
        self.assertFalse([s for s in self.sql_statements() if "recipe_ingredient_item_id = 59" in s])
        row = next(r for r in self.tsv() if r["item_id"] == "59")
        self.assertEqual(("review-uncertain-match", "half-bug", "5", "Stück", "0.5", "Stück", "½ Stück Gurke", "like"),
                         (row["action"], row["class"], row["old_quantity"], row["old_unit"], row["new_quantity"],
                          row["new_unit"], row["page_line"], row["match"]))

    def test_unit_change_needs_a_confident_and_explained_match(self):
        self.run_tool("--select", "suspect")
        sql = self.sql_statements()
        # confident, and the old parser made exactly 2 Stück of "2 Prisen Zucker": a parser fix -> SQL
        self.assertTrue([s for s in sql if "recipe_ingredient_item_id = 1061" in s and "'Prise'" in s])
        # confident, but the old parser would have stored "1 TL Honig" as 1 TL, not 1 Stück -> held
        self.assertFalse([s for s in sql if "recipe_ingredient_recipe_id = 2 AND recipe_ingredient_item_id = 1070" in s])
        # uncertain: "1 Packung Bratapfel" fuzzily matched to Apfel -> held
        self.assertFalse([s for s in sql if "recipe_ingredient_item_id = 143" in s])
        by_item = {(r["recipe_id"], r["item_id"]): r for r in self.tsv() if r["item_id"]}
        self.assertEqual("review-unit", by_item[("2", "1070")]["action"])
        self.assertIn("would have stored 1 TL", by_item[("2", "1070")]["note"])
        self.assertEqual(("review-uncertain-match", "unit"), (by_item[("2", "143")]["action"], by_item[("2", "143")]["class"]))

    def test_page_without_recipe_yield_writes_no_servings(self):
        self.run_tool("--select", "suspect")
        sql = self.sql_statements()
        self.assertFalse([s for s in sql if "recipe_servings" in s and "recipe_id = 7" in s])
        self.assertTrue([s for s in sql if "recipe_ingredient_recipe_id = 7" in s])   # its ½-bug is still fixed
        row = next(r for r in self.tsv() if r["action"] == "review-servings")
        self.assertEqual("7", row["recipe_id"])
        self.assertTrue(row["note"].startswith("recipeYield missing, stored 2 kept"))

    def test_bundle_already_at_four_is_not_set_back_by_a_page_without_yield(self):
        # second run after the bundle recipe was repaired to 4; its page now has no recipeYield
        with open(os.path.join(REPAIR, "state.json"), encoding="utf-8") as f:
            state = json.load(f)
        next(r for r in state["recipes"] if r["id"] == 2)["servings"] = 4
        state_path = os.path.join(self.tmp, "state.json")
        with open(state_path, "w", encoding="utf-8") as f:
            json.dump(state, f, ensure_ascii=False)
        url = "https://www.hellofresh.de/recipes/familien-bolognese-bundle-4p-6a1b2c3d4e5f6a7b8c9d0e1f"
        with open(os.path.join(FIXTURES, "jsonld", "familien_bolognese_bundle.json"), encoding="utf-8") as f:
            page = json.load(f)
        del page["recipeYield"]
        fetcher = Fetcher(self.cache, offline=True)
        for name in ["Familien-Bolognese  mit Spaghetti", page["name"]]:    # without and with the bundle title
            with self.subTest(name=name):
                page["name"] = name
                fetcher._store(fetcher._path(url), url, "ok", page)
                stdout = io.StringIO()
                with redirect_stdout(stdout), redirect_stderr(io.StringIO()):
                    repair_quantities.main(["--state", state_path, "--urls", os.path.join(REPAIR, "recipe_urls.txt"),
                                            "--cache", self.cache, "--out", self.out, "--offline", "--stamp", "TEST",
                                            "--select", "suspect"])
                self.assertFalse([s for s in self.sql_statements() if "recipe_servings" in s and "recipe_id = 2" in s])
                self.assertTrue([s for s in self.sql_statements() if "recipe_ingredient_recipe_id = 2" in s])  # page was used
                held = [r for r in self.tsv() if r["recipe_id"] == "2" and r["action"] == "review-servings"]
                if "4P" in name:
                    self.assertEqual([], held)                              # bundle title: reliable 4, equal
                else:
                    self.assertTrue(held[0]["note"].startswith("recipeYield missing, stored 4 kept"))

    def test_include_other_also_writes_held_changes(self):
        self.run_tool("--select", "suspect", "--include-other")
        sql = self.read("repair_suspect_TEST.sql")
        self.assertIn("recipe_ingredient_quantity_needed = 300,", sql)
        self.assertNotIn("recipe_ingredient_item_id = 59", sql)        # uncertain matches never go in
        self.assertNotIn("recipe_ingredient_item_id = 143", sql)
        self.assertNotIn("recipe_ingredient_recipe_id = 2 AND recipe_ingredient_item_id = 1070", sql)

    def test_all_selects_every_recipe(self):
        out = self.run_tool("--select", "all")
        self.assertIn("recipes selected: 7", out)

    def test_offline_run_reports_uncached_pages_as_incomplete(self):
        os.remove(os.path.join(self.cache, "5f1a2b3c4d5e6f7a8b9c0d1e.json"))
        out = self.run_tool("--select", "suspect")
        self.assertIn("not cached (offline) 2", out)
        self.assertIn("incomplete, re-run to retry", out)


class NameMatchingRunTest(unittest.TestCase):
    """End to end on a tiny database: matching goes by page name, never by URL slug."""

    def setUp(self):
        self.tmp = tempfile.mkdtemp()
        self.cache = os.path.join(self.tmp, "cache")
        self.out = os.path.join(self.tmp, "out")
        with open(os.path.join(REPAIR, "state.json"), encoding="utf-8") as f:
            full = json.load(f)
        self.items = full["items"]

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def page(self, name, lines):
        return {"@type": "Recipe", "name": name, "recipeYield": 2, "totalTime": "PT20M", "recipeIngredient": lines}

    def run_tool(self, recipes, ingredients, pages):
        """recipes: [(id, name)], ingredients: [(recipe_id, item_id, unit, qty)], pages: {slug: json-ld}."""
        state = {"recipes": [{"id": i, "name": n, "servings": 2, "source_url": None} for i, n in recipes],
                 "ingredients": [{"recipe_id": r, "item_id": i, "unit": u, "quantity": q, "to_taste": False}
                                 for r, i, u, q in ingredients],
                 "items": self.items}
        state_path = os.path.join(self.tmp, "state.json")
        urls_path = os.path.join(self.tmp, "urls.txt")
        with open(state_path, "w", encoding="utf-8") as f:
            json.dump(state, f)
        fetcher = Fetcher(self.cache, offline=True)
        with open(urls_path, "w", encoding="utf-8") as f:
            for n, (slug, rec) in enumerate(pages.items()):
                url = f"https://www.hellofresh.de/recipes/{slug}-{n:024x}"
                f.write(url + "\n")
                fetcher._store(fetcher._path(url), url, "ok", rec)
        stdout = io.StringIO()
        with redirect_stdout(stdout), redirect_stderr(io.StringIO()):
            repair_quantities.main(["--state", state_path, "--urls", urls_path, "--cache", self.cache,
                                    "--out", self.out, "--offline", "--stamp", "T", "--select", "suspect"])
        with open(os.path.join(self.out, "repair_suspect_T.tsv"), encoding="utf-8") as f:
            header, *rows = [l.rstrip("\n").split("\t") for l in f]
        with open(os.path.join(self.out, "repair_suspect_T.sql"), encoding="utf-8") as f:
            sql = f.read()
        return stdout.getvalue(), [dict(zip(header, r)) for r in rows], sql

    def test_slug_that_has_nothing_to_do_with_the_name_still_matches(self):
        out, rows, sql = self.run_tool(
            [(10, "Käsespätzle all‘italiana mit Mozzarella und Pinienkernen")],
            [(10, 186, "Stück", 5)],
            {"spaetzle-pfanne-zufaellig": self.page(
                "Käsespätzle all‘Italiana mit Mozzarella und Pinienkernen  Geschmolzen", ["½ Stück Zitrone"]),
             "kaesespaetzle-all-italiana-mit-mozzarella": self.page("Etwas ganz anderes", ["1 Stück Zitrone"])})
        self.assertIn("recipes: matched 1 | ambiguous 0 | unmatched 0", out)
        self.assertIn("quantity_needed = 0.5, recipe_ingredient_unit = 'Stück'", sql)

    def test_umlaut_spelling_and_the_title_cut_on_the_stored_name(self):
        out, rows, sql = self.run_tool(
            [(10, "Haehnchen-Curry verzehrfertiges Gericht enthält max. 20% Kohlenhydrate")],
            [(10, 186, "Stück", 5)],
            {"x": self.page("Hähnchen-Curry  mit Reis", ["½ Stück Zitrone"])})
        self.assertIn("matched 1 |", out)

    def test_two_pages_with_one_name_are_ambiguous_and_write_nothing(self):
        out, rows, sql = self.run_tool(
            [(10, "Ofengemüse")], [(10, 186, "Stück", 5)],
            {"a": self.page("Ofengemüse", ["½ Stück Zitrone"]),
             "b": self.page("Ofengemüse  mit Feta", ["½ Stück Zitrone"])})      # same lines: still held
        self.assertIn("matched 0 | ambiguous 1 | unmatched 0", out)
        self.assertEqual(["ambiguous"], [r["action"] for r in rows])
        self.assertNotIn("UPDATE recipe_ingredient", sql)
        self.assertNotIn("INSERT", sql)

    def test_two_stored_recipes_with_one_name_are_ambiguous(self):
        out, rows, sql = self.run_tool(
            [(10, "Ofengemüse"), (11, "ofengemuese")], [(10, 186, "Stück", 5), (11, 186, "Stück", 5)],
            {"a": self.page("Ofengemüse", ["½ Stück Zitrone"])})
        self.assertIn("matched 0 | ambiguous 2", out)
        self.assertNotIn("UPDATE recipe_ingredient", sql)

    def test_no_page_with_the_name_is_unmatched(self):
        out, rows, _sql = self.run_tool(
            [(10, "Saftiges Rumpsteak")], [(10, 186, "Stück", 5)],
            {"saftiges-rumpsteak": self.page("Saftiges Rindersteak", ["½ Stück Zitrone"])})   # right slug, wrong name
        self.assertIn("matched 0 | ambiguous 0 | unmatched 1", out)
        self.assertEqual("no page has this name", rows[0]["note"])

    def test_uncertain_second_line_is_listed_on_its_own_not_in_the_sql(self):
        state = repair_quantities.State.from_json(os.path.join(REPAIR, "state.json"))
        plan = repair_quantities.Plan()
        recipe = state.recipes[1]
        line = lambda raw, qty, conf, held=False: {
            "raw": raw, "name": raw, "item_id": 59, "quantity": Decimal(qty), "unit": "Stück", "to_taste": False,
            "conf": conf, "note": "", **({"held_collision": True} if held else {})}
        parsed = {"servings": 2, "servings_source": "recipeYield", "notes": [],
                  "lines": [line("1 rote Paprika", 1, "exact"), line("160 pikante Paprikasoße", 160, "like", True)]}
        repair_quantities.plan_recipe(plan, state, recipe, "https://x/recipes/p-" + "0" * 24, parsed, False)
        self.assertNotIn("161", "\n".join(plan.sql))
        self.assertNotIn("160", "\n".join(plan.sql))
        held = [r for r in plan.tsv if r[3] == "review-uncertain-match"]
        self.assertEqual(1, len(held))
        self.assertIn("160 pikante Paprikasoße", held[0])


class FetcherTest(unittest.TestCase):
    """Fetching itself, with a fake opener: delay, robots.txt, caching, resume, 404."""

    def setUp(self):
        self.tmp = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.tmp)

    def opener(self, pages):
        calls = []

        class Response(io.BytesIO):
            def __enter__(self):
                return self

            def __exit__(self, *a):
                return False

        def open_(req, timeout=None):
            calls.append((req.full_url, req.get_header("User-agent")))
            body = pages[req.full_url]
            if isinstance(body, int):
                import urllib.error
                raise urllib.error.HTTPError(req.full_url, body, "x", {}, None)
            return Response(body.encode("utf-8"))
        return open_, calls

    def test_fetch_caches_and_a_rerun_skips_the_network(self):
        with open(os.path.join(FIXTURES, "jsonld", "page.html"), encoding="utf-8") as f:
            page = f.read()
        url = "https://www.hellofresh.de/recipes/familien-bolognese-bundle-4p-6a1b2c3d4e5f6a7b8c9d0e1f"
        gone = "https://www.hellofresh.de/recipes/weg-0a1b2c3d4e5f6a7b8c9d0e1f"
        pages = {repair_quantities.ROBOTS_URL: "User-agent: *\nDisallow: /secret/\n", url: page, gone: 404}
        open_, calls = self.opener(pages)

        first = Fetcher(self.tmp, delay=0, opener=open_)
        status, rec = first.get(url)
        self.assertEqual("ok", status)
        self.assertEqual("Familien-Bolognese", repair_quantities.recipe_parse.cut_title(rec["name"]))
        self.assertEqual(("404", None), first.get(gone))
        self.assertEqual("robots", first.get("https://www.hellofresh.de/secret/x")[0])
        self.assertTrue(all("Mozilla/5.0" in ua for _u, ua in calls))
        self.assertEqual(3, len(calls))                                # robots.txt + 2 pages

        again = Fetcher(self.tmp, delay=0, opener=open_)
        self.assertEqual("ok", again.get(url)[0])
        self.assertEqual("404", again.get(gone)[0])
        self.assertEqual(3, len(calls))                                # resumed from the cache
        self.assertEqual(2, again.stats["cache"])


class MatchingTest(unittest.TestCase):
    """Recipes are matched to pages by name, not by URL slug."""

    def test_name_keys_ignore_umlaut_spelling(self):
        self.assertEqual(name_key("Hähnchen-Curry"), name_key("haehnchen-curry"))

    def test_title_key_cuts_both_sides_like_the_importer(self):
        # page name: subtitle after a double space; stored name from an older import: marketing blurb kept
        page = "Käsige Bio-Hackbällchen  mit Rahmkohlrabi"
        self.assertEqual(title_key("Käsige Bio-Hackbällchen"), title_key(page))
        self.assertEqual(title_key("Käsige Bio-Hackbällchen verzehrfertiges Gericht enthält max. 20% Kohlenhydrate"),
                         title_key("Käsige Bio-Hackbällchen  mit Rahmkohlrabi verzehrfertiges Gericht"))
        self.assertEqual(title_key("Ofengemüse"), title_key("OFENGEMUESE"))
        self.assertNotEqual(title_key("Ofengemüse"), title_key("Ofengemüse mit Feta"))
        self.assertEqual(title_key("x" * 130), title_key("x" * 125))      # 120-character cut

    def test_index_is_built_from_the_page_names(self):
        tmp = tempfile.mkdtemp()
        try:
            fill_cache(tmp)
            urls = [l.strip() for l in open(os.path.join(REPAIR, "recipe_urls.txt"), encoding="utf-8")
                    if l.strip() and not l.startswith("#")]
            by_name, statuses = build_page_index(urls, Fetcher(tmp, offline=True))
        finally:
            shutil.rmtree(tmp)
        self.assertEqual(2, len(by_name[title_key("Ofengemüse")]))          # two pages, one name
        # the slug says "zitronen-hahnchen-mit-reis" but the page is called "Zitronen-Hähnchen mit Reis"
        self.assertIn(title_key("Zitronen-Hähnchen mit Reis"), by_name)
        self.assertEqual(1, statuses["404"])
        self.assertEqual(1, statuses["uncached"])


class StepRulesRunTest(NameMatchingRunTest):
    """End to end: amountless lines (amount from the steps, 1 Stück, to taste, review) and servings ranges."""

    def page(self, name, lines, steps=(), recipe_yield=2):
        rec = {"@type": "Recipe", "name": name, "recipeYield": recipe_yield, "totalTime": "PT20M",
               "recipeIngredient": lines}
        rec["recipeInstructions"] = [{"@type": "HowToStep", "text": f"<p>{s}</p>"} for s in steps]
        return rec

    def run_all(self, recipes, ingredients, pages, servings=None):
        state = {"recipes": [{"id": i, "name": n, "servings": (servings or {}).get(i, 2), "source_url": None}
                             for i, n in recipes],
                 "ingredients": [{"recipe_id": r, "item_id": i, "unit": u, "quantity": q, "to_taste": False}
                                 for r, i, u, q in ingredients],
                 "items": self.items}
        state_path = os.path.join(self.tmp, "state.json")
        urls_path = os.path.join(self.tmp, "urls.txt")
        with open(state_path, "w", encoding="utf-8") as f:
            json.dump(state, f)
        fetcher = Fetcher(self.cache, offline=True)
        with open(urls_path, "w", encoding="utf-8") as f:
            for n, (slug, rec) in enumerate(pages.items()):
                url = f"https://www.hellofresh.de/recipes/{slug}-{n:024x}"
                f.write(url + "\n")
                fetcher._store(fetcher._path(url), url, "ok", rec)
        stdout = io.StringIO()
        with redirect_stdout(stdout), redirect_stderr(io.StringIO()):
            repair_quantities.main(["--state", state_path, "--urls", urls_path, "--cache", self.cache,
                                    "--out", self.out, "--offline", "--stamp", "T", "--select", "all"])
        with open(os.path.join(self.out, "repair_all_T.tsv"), encoding="utf-8") as f:
            header, *rows = [l.rstrip("\n").split("\t") for l in f]
        with open(os.path.join(self.out, "repair_all_T.sql"), encoding="utf-8") as f:
            sql = f.read()
        return stdout.getvalue(), [dict(zip(header, r)) for r in rows], sql

    def test_amountless_lines_follow_the_rules(self):
        out, rows, sql = self.run_all(
            [(1, "Pfannengericht"), (2, "Salat")],
            [(1, 1070, "Stück", 1), (1, 1148, "Stück", 1), (2, 565, "Stück", 1)],      # old importer: "1 Stück" each
            {"pfannengericht": self.page("Pfannengericht", ["Stück Honig", "Stück Salz", " Zucker", "nach Geschmack Pfeffer",
                                                            "Stück Petersilie"],
                                         ["1 EL Honig und 1 EL Honig verrühren.", "Mit Salz abschmecken."]),
             "salat": self.page("Salat", ["Stück Pita-Brote"], ["Pita füllen."])})
        # steps: Honig 2 EL replaces the old "1 Stück" (class from-steps); Salz 1 Stück -> to taste
        self.assertIn("recipe_ingredient_quantity_needed = 2, recipe_ingredient_unit = 'EL'", sql)
        self.assertIn("-- from-steps: Stück Honig", sql)
        self.assertIn("recipe_ingredient_to_taste = 1", sql)
        # Pfeffer (nach Geschmack) and Zucker (uncountable staple, no amount) are new to-taste rows
        self.assertIn("-- insert-to-taste: nach Geschmack Pfeffer", sql)
        self.assertIn("-- insert-to-taste:  Zucker", sql)
        # Petersilie: not countable, no amount anywhere: review, no SQL
        review = [r for r in rows if r["action"] == "review-amount"]
        self.assertEqual(["Stück Petersilie"], [r["page_line"].strip() for r in review])
        # "Stück Pita-Brote" = 1 Stück: the stored row already says so, nothing to do
        self.assertFalse([l for l in sql.splitlines() if "item_id = 565" in l])
        self.assertIn("from the steps 1", out)

    def test_range_title_sets_servings_and_maximum_once(self):
        out, rows, sql = self.run_all(
            [(1, "Crumble für 3 – 4 Personen | 40 Minuten"), (2, "Meat Bundle 4P")], [],
            {"crumble": self.page("Crumble für 3 – 4 Personen | 40 Minuten", ["1 Stück Zitrone"]),
             "bundle": self.page("Meat Bundle 4P", ["1 Stück Zitrone"])})
        self.assertIn("UPDATE recipe SET recipe_servings = 3, recipe_servings_max = 4 WHERE recipe_id = 1 "
                      "AND recipe_servings = 2 AND recipe_servings_max IS NULL;", sql)
        self.assertIn("UPDATE recipe SET recipe_servings = 4 WHERE recipe_id = 2 AND recipe_servings = 2;", sql)
        self.assertIn("servings changed: 2 (ranges with a maximum: 1)", out)

    def test_contradicting_title_is_held_for_review(self):
        out, rows, sql = self.run_all(
            [(1, "OTP 4P Birnen Parfait 2 Portionen | zubereiten")], [],
            {"otp": self.page("OTP 4P Birnen Parfait 2 Portionen | zubereiten", ["1 Stück Zitrone"])})
        self.assertNotIn("recipe_servings", "".join(l for l in sql.splitlines() if l.startswith("UPDATE")))
        self.assertEqual(["review-servings"], [r["action"] for r in rows if r["action"].startswith("review-serv")])

    def test_a_stored_range_is_not_written_twice(self):
        state_recipes = [(1, "Crumble für 3 – 4 Personen | 40 Minuten")]
        out, rows, sql = self.run_all(state_recipes, [],
                                      {"crumble": self.page(state_recipes[0][1], ["1 Stück Zitrone"])}, servings={1: 3})
        # servings already 3, but the maximum is still missing: only the maximum is set
        self.assertIn("UPDATE recipe SET recipe_servings_max = 4 WHERE recipe_id = 1 AND recipe_servings = 3 "
                      "AND recipe_servings_max IS NULL;", sql)


if __name__ == "__main__":
    unittest.main()
