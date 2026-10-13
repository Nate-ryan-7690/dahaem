#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Find the recipe ingredient lines the importer could not match to a catalogue item and therefore DROPPED
(recipe_ingredient.recipe_ingredient_item_id is NOT NULL). A recipe whose "Speck" line was dropped looks
complete and could be tagged vegetarisch, which is exactly the error the diet filter must never make. The
lines go into recipe_unmatched_ingredient (migrations/0012_diet_filter.sql); a recipe with ANY row there is
"unklar" for diet purposes.

It only READS: the page cache that repair_quantities.py filled (scripts/importer/_cache/pages/, one file per
recipe URL: {url, status, recipe}) and MariaDB (a read-only transaction, via db.py). It writes a guarded SQL
file, a TSV with every line and a report. Applying the SQL is a separate, manual step (see README.md in this
folder), after a snapshot, with migrations/0012_diet_filter.sql applied first.

For every recipe with a recipe_source_url:
  * the cached page's recipeIngredient lines are parsed with recipe_parse and matched EXACTLY as the importer
    and the repair tool do (the catalogue read from the database + overrides.json, water skipped);
  * a line whose item is not among the recipe's stored ingredient items is 'unmatched' (the raw line is stored);
  * a recipe without a URL, without a cached page, or with a page that has no recipe (404, empty) gets ONE
    'unchecked' row without a line: nothing is known about what its missing lines might be.

The SQL has one INSERT per row, each guarded on the row not existing yet (and on the recipe still existing), so
it can be applied twice. There is NO BEGIN/COMMIT on purpose: run the file inside ONE transaction. Values are
written as escaped literals; the database is only read, with fixed SELECTs.

The database comes from DAHAEM_DB_URL / DAHAEM_DB_USER / DAHAEM_DB_PASSWORD (db.py). For tests and offline
review, --state FILE.json replaces the database read with a JSON snapshot.

Usage:
  python find_unmatched_ingredients.py                  # default cache, writes _unmatched/unmatched_<stamp>.sql/.tsv/.txt
  python find_unmatched_ingredients.py --cache DIR --out DIR --top 60
"""
import argparse, json, os, re, sys
from collections import Counter, defaultdict
from datetime import datetime

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import recipe_parse  # noqa: E402
from extract import Catalog, is_pantry, load_overrides  # noqa: E402
from repair_quantities import sql_str  # noqa: E402

DEFAULT_CACHE = os.path.join(HERE, "_cache", "pages")
DEFAULT_OUT = os.path.join(HERE, "_unmatched")
UNMATCHED, UNCHECKED = "unmatched", "unchecked"
MAX_RAW = 500                                   # recipe_unmatched_ingredient_raw_text is VARCHAR(500)
NO_URL, NO_PAGE, BAD_PAGE = "(keine Quell-URL)", "(Seite nicht im Cache)", "(Seite ohne Rezept: {})"


def normalize_url(url):
    return (url or "").strip().rstrip("/")


# ---------- inputs ----------
class State:
    """What the database holds: recipes {id: {id, name, source_url}}, stored ingredient items
    {recipe_id: {item_id}} and the catalogue items [(id, name_english, name_deutsch)]."""

    def __init__(self, recipes, stored, items):
        self.recipes, self.stored, self.items = recipes, stored, items

    @classmethod
    def from_json(cls, path):
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        stored = defaultdict(set)
        for r in data["ingredients"]:
            stored[r["recipe_id"]].add(r["item_id"])
        return cls({r["id"]: r for r in data["recipes"]}, stored, [tuple(i) for i in data["items"]])

    @classmethod
    def from_db(cls, con):
        with con.cursor() as cur:
            cur.execute("SET SESSION TRANSACTION READ ONLY")
            cur.execute("SELECT recipe_id, recipe_name, recipe_source_url FROM recipe")
            recipes = {i: {"id": i, "name": n, "source_url": u} for i, n, u in cur.fetchall()}
            cur.execute("SELECT recipe_ingredient_recipe_id, recipe_ingredient_item_id FROM recipe_ingredient")
            stored = defaultdict(set)
            for rid, iid in cur.fetchall():
                stored[rid].add(iid)
            cur.execute("SELECT inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch "
                        "FROM inventory_item")
            items = list(cur.fetchall())
        return cls(recipes, stored, items)


def load_pages(cache_dir):
    """-> ({normalized url: (status, recipe or None)}, number of cache files that could not be read)."""
    pages, unreadable = {}, 0
    for name in sorted(os.listdir(cache_dir)):
        if not name.endswith(".json"):
            continue
        try:
            with open(os.path.join(cache_dir, name), encoding="utf-8") as f:
                cached = json.load(f)
        except (OSError, ValueError):
            unreadable += 1
            continue
        if cached.get("url"):
            pages[normalize_url(cached["url"])] = (cached.get("status"), cached.get("recipe"))
        else:
            unreadable += 1
    return pages, unreadable


# ---------- the run ----------
class Result:
    def __init__(self):
        self.rows = []                 # (recipe_id, recipe_name, line_no or None, raw, name, reason)
        self.stats = Counter()
        self.names = Counter()         # normalized unmatched name -> lines
        self.name_recipes = defaultdict(set)
        self.name_spelling = defaultdict(Counter)


def run(state, pages):
    catalog = Catalog(state.items, load_overrides())
    pantry = lambda n: is_pantry(n, n)
    res = Result()
    for rid in sorted(state.recipes):
        recipe = state.recipes[rid]
        url = normalize_url(recipe.get("source_url"))
        if not url:
            res.stats["unchecked_no_url"] += 1
            res.rows.append((rid, recipe["name"], None, NO_URL, "", UNCHECKED))
            continue
        status, page = pages.get(url, (None, None))
        if status is None:
            res.stats["unchecked_no_page"] += 1
            res.rows.append((rid, recipe["name"], None, NO_PAGE, "", UNCHECKED))
            continue
        if status != "ok" or not isinstance(page, dict):
            res.stats["unchecked_bad_page"] += 1
            res.rows.append((rid, recipe["name"], None, BAD_PAGE.format(status), "", UNCHECKED))
            continue
        res.stats["checked"] += 1
        parsed = recipe_parse.parse_recipe(page, catalog, is_pantry=pantry)
        lines = recipe_parse.unmatched_lines(page, parsed, state.stored.get(rid, set()))
        if lines:
            res.stats["with_unmatched"] += 1
        for line_no, raw, name in lines:
            res.stats["lines"] += 1
            res.rows.append((rid, recipe["name"], line_no, raw, name, UNMATCHED))
            key = " ".join(name.lower().split())
            res.names[key] += 1
            res.name_recipes[key].add(rid)
            res.name_spelling[key][name] += 1
    return res


def summary(res, top):
    s = res.stats
    unchecked = s["unchecked_no_url"] + s["unchecked_no_page"] + s["unchecked_bad_page"]
    lines = [
        f"recipes checked against their cached page: {s['checked']}",
        f"  with unmatched lines: {s['with_unmatched']} ({s['lines']} lines in total)",
        f"recipes unchecked (one 'unchecked' row each): {unchecked}"
        f" (no URL: {s['unchecked_no_url']}, URL without a cached page: {s['unchecked_no_page']},"
        f" page without a recipe: {s['unchecked_bad_page']})",
        f"recipes that will be 'unklar' for diet purposes: {s['with_unmatched'] + unchecked}",
        f"the {top} most common unmatched names (lines, recipes):",
    ]
    for key, n in res.names.most_common(top):
        shown = res.name_spelling[key].most_common(1)[0][0]
        lines.append(f"  {n:6}  {len(res.name_recipes[key]):6}  {shown}")
    return "\n".join(lines)


# ---------- output ----------
def insert_statement(rid, line_no, raw, reason):
    raw = re.sub(r"[\x00-\x1f]+", " ", raw).strip()[:MAX_RAW]
    line = "NULL" if line_no is None else str(int(line_no))
    text = sql_str(raw)
    return (
        "INSERT INTO recipe_unmatched_ingredient (recipe_unmatched_ingredient_recipe_id, "
        "recipe_unmatched_ingredient_line_no, recipe_unmatched_ingredient_raw_text, "
        "recipe_unmatched_ingredient_reason) "
        f"SELECT {int(rid)}, {line}, {text}, {sql_str(reason)} FROM DUAL "
        f"WHERE EXISTS (SELECT 1 FROM recipe WHERE recipe_id = {int(rid)}) "
        "AND NOT EXISTS (SELECT 1 FROM recipe_unmatched_ingredient "
        f"WHERE recipe_unmatched_ingredient_recipe_id = {int(rid)} "
        f"AND recipe_unmatched_ingredient_line_no <=> {line} "
        f"AND BINARY recipe_unmatched_ingredient_raw_text = {text} "
        f"AND recipe_unmatched_ingredient_reason = {sql_str(reason)});")


def write_outputs(res, out_dir, stamp, summary_text):
    os.makedirs(out_dir, exist_ok=True)
    base = os.path.join(out_dir, f"unmatched_{stamp}")
    header = [
        f"-- Unmatched recipe ingredient lines, generated {stamp} by scripts/importer/find_unmatched_ingredients.py",
        "-- Needs migrations/0012_diet_filter.sql. Review the report (.txt) next to this file first.",
        "-- One INSERT per row, each skipped when that row already exists: applying it twice changes nothing.",
        "-- There is no BEGIN/COMMIT on purpose: run it inside ONE transaction after a snapshot",
        "-- (scripts/importer/README.md), e.g.",
        "--   { echo 'SET NAMES utf8mb4; START TRANSACTION;'; cat THIS_FILE.sql; echo 'COMMIT;'; } | \\",
        "--     docker compose exec -T db sh -c 'exec mariadb -u root -p\"$MARIADB_ROOT_PASSWORD\" \"$MARIADB_DATABASE\"'",
        "-- The client stops at the first error, so COMMIT is never reached and nothing is kept.",
        "--",
    ] + ["-- " + l for l in summary_text.splitlines()]
    sql = [insert_statement(rid, line_no, raw, reason) for rid, _n, line_no, raw, _name, reason in res.rows]
    with open(base + ".sql", "w", encoding="utf-8") as f:
        f.write("\n".join(header + sql) + "\n")
    with open(base + ".tsv", "w", encoding="utf-8") as f:
        f.write("recipe_id\trecipe_name\tline_no\treason\tname\tpage_line\n")
        for rid, rname, line_no, raw, name, reason in res.rows:
            f.write("\t".join(re.sub(r"[\t\r\n]+", " ", str(v)) for v in
                              (rid, rname, "" if line_no is None else line_no, reason, name, raw)) + "\n")
    with open(base + ".txt", "w", encoding="utf-8") as f:
        f.write(summary_text + "\n")
    return base


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--cache", default=DEFAULT_CACHE, help="page cache filled by repair_quantities.py")
    ap.add_argument("--out", default=DEFAULT_OUT, help="where the .sql/.tsv/.txt go")
    ap.add_argument("--top", type=int, default=60, help="how many unmatched names the report lists (default 60)")
    ap.add_argument("--state", help="JSON snapshot instead of reading MariaDB (tests / offline review)")
    ap.add_argument("--stamp", default=None, help=argparse.SUPPRESS)
    args = ap.parse_args(argv)

    if not os.path.isdir(args.cache):
        raise SystemExit(f"page cache not found: {args.cache} (run repair_quantities.py first)")
    if args.state:
        state = State.from_json(args.state)
    else:
        import db
        print("reading (read-only):", db.describe(), file=sys.stderr)
        con = db.connect()
        try:
            state = State.from_db(con)
        finally:
            con.close()
    pages, unreadable = load_pages(args.cache)
    res = run(state, pages)
    text = summary(res, args.top)
    if unreadable:
        text = f"warning: {unreadable} cache files could not be read\n" + text
    stamp = args.stamp or datetime.now().strftime("%Y%m%d-%H%M%S")
    base = write_outputs(res, args.out, stamp, text)
    print(text)
    print(f"\nwrote {base}.sql / .tsv / .txt  (nothing was applied to the database)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
