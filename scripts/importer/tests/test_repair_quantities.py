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
from contextlib import redirect_stderr, redirect_stdout

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))

import repair_quantities  # noqa: E402
from repair_quantities import Fetcher, UrlIndex, name_key  # noqa: E402

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
            "selector: suspect    recipes selected: 5    URLs loaded: 7",
            "recipes: matched 2 | ambiguous 1 | unmatched 2",
            "pages: fetched 0 | from cache 6 | 404 1 | fetch errors 0",
            "rows updated: 6 = ½-bug fixed 2 | rounding fixed 2 | unit changed 1 | now to-taste 1 | other 0",
            "lines inserted: 3 = EL/TL 2 | to-taste 1 | other 0",
            "servings changed: 1    source URLs set: 2",
            "held for review (TSV only): other changes 1 | uncertain item match 1 | unmatched ingredient 0",
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
        self.assertIn(("1", "db-only", "45"), actions)
        self.assertIn(("3", "ambiguous", ""), actions)
        self.assertIn(("4", "unmatched", ""), actions)
        self.assertIn(("5", "unmatched", ""), actions)

    def test_include_other_also_writes_held_changes(self):
        self.run_tool("--select", "suspect", "--include-other")
        sql = self.read("repair_suspect_TEST.sql")
        self.assertIn("recipe_ingredient_quantity_needed = 300,", sql)
        self.assertNotIn("recipe_ingredient_item_id = 59", sql)        # uncertain matches never go in

    def test_all_selects_every_recipe(self):
        out = self.run_tool("--select", "all")
        self.assertIn("recipes selected: 6", out)

    def test_offline_run_reports_uncached_pages_as_incomplete(self):
        os.remove(os.path.join(self.cache, "5f1a2b3c4d5e6f7a8b9c0d1e.json"))
        out = self.run_tool("--select", "suspect")
        self.assertIn("not cached (offline) 1", out)
        self.assertIn("incomplete, re-run to retry", out)


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

    def test_name_keys_ignore_umlaut_spelling(self):
        self.assertEqual(name_key("Hähnchen-Curry"), name_key("haehnchen-curry"))
        self.assertEqual(name_key("Ofengemüse"), UrlIndex.slug_key(
            "https://www.hellofresh.de/recipes/ofengemuse-7a1b2c3d4e5f6a7b8c9d0e1f"))

    def test_candidates_exact_first_then_with_subtitle(self):
        index = UrlIndex(["https://x/recipes/ofengemuse-mit-feta-7b1b2c3d4e5f6a7b8c9d0e1f",
                          "https://x/recipes/ofengemuse-7a1b2c3d4e5f6a7b8c9d0e1f",
                          "https://x/recipes/ofengemusesuppe-7c1b2c3d4e5f6a7b8c9d0e1f"])
        urls, truncated = index.candidates("Ofengemüse", 5)
        self.assertEqual(["https://x/recipes/ofengemuse-7a1b2c3d4e5f6a7b8c9d0e1f",
                          "https://x/recipes/ofengemuse-mit-feta-7b1b2c3d4e5f6a7b8c9d0e1f"], urls)
        self.assertFalse(truncated)


if __name__ == "__main__":
    unittest.main()
