#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Re-sort the recipe lines stored in recipe_unmatched_ingredient (migration 0012, filled by
find_unmatched_ingredients.py) against the CURRENT catalogue and overrides. The old importer dropped lines it could
not match (Öl, Honig, Essig ...), many of which have a catalogue item today. Every stored 'unmatched' row ends in
exactly one case (decided by Nate, 2026-10-14):

  a) MATCHES AN ITEM NOW (catalogue + overrides.json, the importer's matching, confident matches only)
       - the recipe already has that item, or an item of the same family (Öl <-> Olivenöl <-> "(Oliven-)Öl*",
         the Essig family as step_amounts.py defines it, Salz / "nach Geschmack Salz", Pfeffer): COVERED. No insert,
         the unmatched row is resolved.
       - otherwise INSERT a recipe_ingredient row with quantity / unit / to_taste exactly as the importer parses the
         line (to taste for salt, pepper, oil, vinegar ... without an amount; amounts from the cooking steps via
         step_amounts.py, like the repair tool) and resolve the unmatched row.
       - two lines mapping to the same new item in one recipe: same unit -> summed; different units -> REVIEW,
         nothing inserted.
  b) NON-FOOD (non_food.json: Holzspieße, Backpapier ...): resolved as ignored, the recipe is not unklar because of it.
  c) NO ITEM YET: the row stays (the recipe stays unklar); the name is listed for the next catalogue batch.
  d) ANYTHING AMBIGUOUS -> REVIEW TSV, nothing changed: an uncertain item match (like / multi), an amountless line
     the steps cannot settle, a line that is no longer on the cached page, different units for one item ...

It only READS: MariaDB (a read-only transaction, via db.py) and the page cache that repair_quantities.py filled
(scripts/importer/_cache/pages/). It writes, next to each other:
  rematch_<stamp>.sql            guarded SQL, NO BEGIN/COMMIT (run it inside ONE transaction, see below)
  rematch_<stamp>.tsv            every stored unmatched row with its case
  rematch_<stamp>_review.tsv     only the rows of case d) -- Nate reads this before the SQL is applied
  rematch_<stamp>_noitem.tsv     the names of case c) with line / recipe counts (catalogue batch 2)
  rematch_<stamp>.txt            the report
'unchecked' rows (recipes without a page) are never touched.

The SQL: one INSERT per new recipe_ingredient row, each guarded on the recipe and the item existing and on no
(recipe, item) row existing yet; then one DELETE per resolved recipe_unmatched_ingredient row, by id, guarded on the
row still being what the tool read (recipe, line, text, reason) and, for a, on the recipe now having the item that
resolves it. It can be applied twice. Values are escaped literals; the database is only read, with fixed SELECTs.

The database comes from DAHAEM_DB_URL / DAHAEM_DB_USER / DAHAEM_DB_PASSWORD (db.py). --state FILE.json replaces the
database read with a JSON snapshot (tests and offline review).

Usage:
  python rematch_unmatched.py                       # default cache, writes _rematch/rematch_<stamp>.*
  python rematch_unmatched.py --cache DIR --out DIR --top 60
"""
import argparse, json, os, re, sys
from collections import Counter, defaultdict
from datetime import datetime
from decimal import Decimal

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import recipe_parse  # noqa: E402
import step_amounts  # noqa: E402
from extract import Catalog, is_pantry, load_overrides, normalize  # noqa: E402
from find_unmatched_ingredients import load_pages, normalize_url  # noqa: E402
from repair_quantities import sql_str  # noqa: E402

DEFAULT_CACHE = os.path.join(HERE, "_cache", "pages")
DEFAULT_OUT = os.path.join(HERE, "_rematch")
NON_FOOD_FILE = os.path.join(HERE, "non_food.json")

INSERT, COVERED, NON_FOOD, NO_ITEM, REVIEW = "insert", "covered", "non-food", "no-item", "review"
RESOLVED = {INSERT, COVERED, NON_FOOD}
CASE_TITLE = {
    INSERT: "a) matches an item now, row inserted",
    COVERED: "a) matches an item now, already covered",
    NON_FOOD: "b) non-food, ignored",
    NO_ITEM: "c) no item yet, stays unmatched",
    REVIEW: "d) review, nothing changed",
}


# ---------- inputs ----------
class State:
    """What the database holds: recipes {id: {id, name, source_url}}, stored ingredient items {recipe_id: {item_id}},
    the catalogue items [(id, name_english, name_deutsch)] and the stored recipe_unmatched_ingredient rows."""

    def __init__(self, recipes, stored, items, unmatched):
        self.recipes, self.stored, self.items, self.unmatched = recipes, stored, items, unmatched
        self.item_names = {i: (de or en or "") for i, en, de in items}

    @classmethod
    def from_json(cls, path):
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        stored = defaultdict(set)
        for r in data["ingredients"]:
            stored[r["recipe_id"]].add(r["item_id"])
        return cls({r["id"]: r for r in data["recipes"]}, stored, [tuple(i) for i in data["items"]],
                   [dict(u) for u in data["unmatched"]])

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
            cur.execute("SELECT recipe_unmatched_ingredient_id, recipe_unmatched_ingredient_recipe_id, "
                        "recipe_unmatched_ingredient_line_no, recipe_unmatched_ingredient_raw_text, "
                        "recipe_unmatched_ingredient_reason FROM recipe_unmatched_ingredient "
                        "ORDER BY recipe_unmatched_ingredient_id")
            unmatched = [{"id": a, "recipe_id": b, "line_no": c, "raw_text": d, "reason": e}
                         for a, b, c, d, e in cur.fetchall()]
        return cls(recipes, stored, items, unmatched)


def load_non_food(path=NON_FOOD_FILE):
    with open(path, encoding="utf-8") as f:
        cfg = json.load(f)
    steps_rules = {k: re.compile(v, re.I) for k, v in (cfg.get("if_steps") or {}).items() if not k.startswith("_")}
    return {"exact": set(cfg.get("exact", [])), "contains": list(cfg.get("contains", [])), "if_steps": steps_rules}


def non_food_check(key, steps, cfg):
    """key = normalized line name. -> None (food, or unknown), ('yes', why) or ('review', why)."""
    if key in cfg["exact"] or any(c in key for c in cfg["contains"]):
        return "yes", "not food (non_food.json)"
    rule = cfg["if_steps"].get(key)
    if rule is not None:
        if not steps:
            return "review", "skewers or food? the page is not in the cache, so no step can be read"
        if any(rule.search(s) for s in steps):
            return "yes", "skewers: a cooking step puts food on them"
        return "review", "skewers or the prepared meat skewers? no cooking step puts food on them"
    return None


# ---------- family cover ----------
def family_cover(line_name, stored_ids, item_names):
    """Ids of the recipe's stored items that already stand for this line by family (Nate, 2026-10-14), or [].
      Öl      the line is Öl / Olivenöl / (Oliven-)Öl and the recipe has Öl or an olive oil;
      Essig   the repair tool's definition (step_amounts): the same wording, or one side is the bare 'Essig';
              two specific vinegars (Balsamico and Weißwein) are different ingredients;
      Salz / Pfeffer   the line is the bare word and the recipe has any item of that family."""
    words, fam = step_amounts.ingredient_key(line_name)
    if fam not in ("öl", "essig", "salz", "pfeffer"):
        return []
    same = []
    for i in sorted(stored_ids):
        w, f = step_amounts.ingredient_key(item_names.get(i, ""))
        if f == fam:
            same.append((i, w))
    if fam == "öl":
        if words not in (["öl"], ["olivenöl"]):
            return []
        return [i for i, w in same if w == ["öl"] or "olivenöl" in w]
    if fam == "essig":
        return [i for i, w in same if w == words or words == ["essig"] or w == ["essig"]]
    return [i for i, _w in same] if words == [fam] else []


# ---------- the run ----------
class Row:
    def __init__(self, u, recipe):
        self.id, self.rid, self.line_no, self.raw, self.reason = \
            u["id"], u["recipe_id"], u.get("line_no"), u["raw_text"], u["reason"]
        self.recipe_name = (recipe or {}).get("name", "")
        self.name = ""
        self.case = None
        self.item_id = None
        self.covered_by = []
        self.quantity, self.unit, self.to_taste = None, "", False
        self.note = ""

    def decide(self, case, note="", item_id=None, covered_by=None):
        self.case, self.note = case, note
        if item_id is not None:
            self.item_id = item_id
        if covered_by:
            self.covered_by = list(covered_by)
        return self


class Result:
    def __init__(self):
        self.rows = []              # every stored 'unmatched' row, with its case
        self.inserts = []           # (rid, item_id, unit, quantity, to_taste, [row ids])
        self.unchecked = 0
        self.recipes_with_rows = set()


def run(state, pages, catalog=None, non_food=None):
    catalog = catalog or Catalog(state.items, load_overrides())
    non_food = non_food or load_non_food()
    res = Result()
    by_recipe = defaultdict(list)
    for u in state.unmatched:
        res.recipes_with_rows.add(u["recipe_id"])
        if u["reason"] != "unmatched":
            res.unchecked += 1
            continue
        by_recipe[u["recipe_id"]].append(Row(u, state.recipes.get(u["recipe_id"])))
    for rid in sorted(by_recipe):
        res.rows.extend(process_recipe(res, state, catalog, non_food, rid, by_recipe[rid], pages))
    return res


def stored_form(text):
    """A page line the way find_unmatched_ingredients.py stores it (control characters to spaces, trimmed, cut at 500
    characters). The page itself still has the trailing blank or no-break space, so compare in this form."""
    return re.sub(r"[\x00-\x1f]+", " ", text or "").strip()[:500]


def _cached_page(state, pages, rid):
    url = normalize_url((state.recipes.get(rid) or {}).get("source_url"))
    status, page = pages.get(url, (None, None)) if url else (None, None)
    return page if status == "ok" and isinstance(page, dict) else None


def process_recipe(res, state, catalog, non_food, rid, rows, pages):
    stored = set(state.stored.get(rid, set()))
    page = _cached_page(state, pages, rid)
    steps = recipe_parse.instruction_steps(page) if page else []
    parsed = (recipe_parse.parse_recipe(page, catalog, is_pantry=lambda n: is_pantry(n, n), merge=False)
              if page else None)
    entries = []                                    # (kind, entry) in page order
    if parsed:
        entries = sorted([("item", e) for e in parsed["lines"]] + [("review", e) for e in parsed["reviews"]],
                         key=lambda t: t[1]["line_no"])
    claimed = set()

    def find_entry(row):
        for n, (kind, e) in enumerate(entries):
            if n in claimed or stored_form(e["raw"]) != stored_form(row.raw):
                continue
            if row.line_no is not None and e["line_no"] != row.line_no:
                continue
            claimed.add(n)
            return kind, e
        return None

    candidates, subsumed = [], []
    for row in sorted(rows, key=lambda r: (r.line_no is None, r.line_no or 0, r.id)):
        line = recipe_parse.parse_ingredient(row.raw)
        row.name = line.name
        if not line.name:
            row.decide(REVIEW, "no ingredient name in the stored line")
            continue
        nf = non_food_check(normalize(line.name), steps, non_food)
        if nf:
            row.decide(NON_FOOD if nf[0] == "yes" else REVIEW, nf[1])
            continue
        iid, conf, note = catalog.match(line.name)
        if iid is None:
            row.decide(NO_ITEM, "no catalogue item or override")
            continue
        if conf not in recipe_parse.CONFIDENT:
            row.decide(REVIEW, f"uncertain item match ({conf}): #{iid} {state.item_names.get(iid, '')}; {note}",
                       item_id=iid)
            continue
        row.item_id = iid
        found = find_entry(row)
        if found is None:
            row.decide(REVIEW, "page not in the cache" if page is None
                       else "the line is no longer on the cached page (line number or text differs)", item_id=iid)
            continue
        kind, e = found
        if e["item_id"] != iid:
            row.decide(REVIEW, f"the page parse matched #{e['item_id']}, the stored line #{iid}", item_id=iid)
        elif kind == "review":
            if (e["reason"] or "").startswith("no row"):
                subsumed.append(row)
            else:
                row.decide(REVIEW, e["reason"], item_id=iid)
        else:
            row.quantity, row.unit, row.to_taste = e["quantity"], e["unit"], e["to_taste"]
            row.note = e["note"] or ""
            candidates.append(row)

    # a) already there, or covered by family
    pending = defaultdict(list)
    for row in candidates:
        if row.item_id in stored:
            row.decide(COVERED, "the recipe already has this item", covered_by=[row.item_id])
            continue
        cover = family_cover(row.name, stored, state.item_names)
        if cover:
            names = ", ".join(f"#{i} {state.item_names.get(i, '')}" for i in cover)
            row.decide(COVERED, f"the recipe already has an item of the same family: {names}", covered_by=cover)
            continue
        pending[row.item_id].append(row)

    # a) new rows; two lines on the same item are summed (same unit) or go to review (different units)
    inserted = set()
    for iid, group in sorted(pending.items()):
        amounts = [r for r in group if not r.to_taste]
        if len(group) == 1:
            r = group[0]
            res.inserts.append((rid, iid, r.unit, r.quantity, r.to_taste, [r.id]))
        elif not amounts:
            res.inserts.append((rid, iid, recipe_parse.TO_TASTE_UNIT, None, True, [r.id for r in group]))
        elif len({r.unit for r in amounts}) == 1:
            total = recipe_parse._clean(sum((r.quantity for r in amounts), Decimal(0)))
            res.inserts.append((rid, iid, amounts[0].unit, total, False, [r.id for r in group]))
        else:
            shown = " + ".join(f"{r.quantity} {r.unit}" for r in amounts)
            for r in group:
                r.decide(REVIEW, f"two lines for item #{iid} with different units ({shown}): nothing inserted",
                         item_id=iid)
            continue
        inserted.add(iid)
        qty = res.inserts[-1]
        for r in group:
            note = f"summed from {len(group)} lines" if len(group) > 1 and qty[3] is not None else r.note
            r.decide(INSERT, note, item_id=iid, covered_by=[iid])
            r.quantity, r.unit, r.to_taste = qty[3], qty[2], qty[4]

    # a to-taste line that has "no row of its own" because a numbered line of the same item exists
    for row in subsumed:
        if row.item_id in stored or row.item_id in inserted:
            row.decide(COVERED, "a numbered line of the same item covers it", covered_by=[row.item_id])
        else:
            row.decide(REVIEW, "its numbered sibling line is not resolved", item_id=row.item_id)
    return rows


# ---------- output ----------
def sql_decimal(d):
    return recipe_parse.sql_decimal(d)


def insert_statement(rid, iid, unit, quantity, to_taste, raws):
    return (
        "INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, "
        "recipe_ingredient_unit, recipe_ingredient_quantity_needed, recipe_ingredient_to_taste) "
        f"SELECT {int(rid)}, {int(iid)}, {sql_str(unit)}, {sql_decimal(quantity)}, {1 if to_taste else 0} FROM DUAL "
        f"WHERE EXISTS (SELECT 1 FROM recipe WHERE recipe_id = {int(rid)}) "
        f"AND EXISTS (SELECT 1 FROM inventory_item WHERE inventory_item_id = {int(iid)}) "
        "AND NOT EXISTS (SELECT 1 FROM recipe_ingredient "
        f"WHERE recipe_ingredient_recipe_id = {int(rid)} AND recipe_ingredient_item_id = {int(iid)});"
        f"  -- {_comment(' | '.join(raws))}")


def delete_statement(row):
    guard = ""
    if row.case in (INSERT, COVERED):
        ids = ", ".join(str(int(i)) for i in sorted(row.covered_by))
        guard = (" AND EXISTS (SELECT 1 FROM recipe_ingredient "
                 f"WHERE recipe_ingredient_recipe_id = {int(row.rid)} AND recipe_ingredient_item_id IN ({ids}))")
    line = "NULL" if row.line_no is None else str(int(row.line_no))
    return (
        "DELETE FROM recipe_unmatched_ingredient "
        f"WHERE recipe_unmatched_ingredient_id = {int(row.id)} "
        f"AND recipe_unmatched_ingredient_recipe_id = {int(row.rid)} "
        f"AND recipe_unmatched_ingredient_line_no <=> {line} "
        f"AND BINARY recipe_unmatched_ingredient_raw_text = {sql_str(row.raw)} "
        f"AND recipe_unmatched_ingredient_reason = 'unmatched'{guard};"
        f"  -- {row.case}: {_comment(row.raw)}")


def _comment(text):
    return re.sub(r"[\r\n\x00-\x1f]+", " ", text or "").replace("--", "-")


def sql_lines(res):
    raw_of = {r.id: r.raw for r in res.rows}
    out = [insert_statement(*ins[:5], [raw_of[i] for i in ins[5]]) for ins in res.inserts]
    out += [delete_statement(r) for r in res.rows if r.case in RESOLVED]
    resolved = sorted(r.id for r in res.rows if r.case in RESOLVED)
    if resolved:
        out.append("SELECT COUNT(*) AS resolved_rows_still_stored_expect_0_after_the_first_run "
                   "FROM recipe_unmatched_ingredient WHERE recipe_unmatched_ingredient_id IN ("
                   + ", ".join(str(i) for i in resolved) + ");")
    return out


def stats(state, res):
    cases = Counter(r.case for r in res.rows)
    remaining = defaultdict(int)                   # recipe -> rows that stay
    for u in state.unmatched:
        remaining[u["recipe_id"]] += 0
    for r in res.rows:
        if r.case not in RESOLVED:
            remaining[r.rid] += 1
    unchecked_by_recipe = Counter(u["recipe_id"] for u in state.unmatched if u["reason"] != "unmatched")
    for rid, n in unchecked_by_recipe.items():
        remaining[rid] += n
    freed = sorted(rid for rid in res.recipes_with_rows if remaining[rid] == 0)
    return cases, freed, sum(1 for rid in res.recipes_with_rows if remaining[rid] > 0)


def name_key(name):
    return " ".join(name.lower().split())


def remaining_names(res, cases):
    names, recs, spelling = Counter(), defaultdict(set), defaultdict(Counter)
    for r in res.rows:
        if r.case in cases:
            k = name_key(r.name or r.raw)
            names[k] += 1
            recs[k].add(r.rid)
            spelling[k][r.name or r.raw] += 1
    return names, recs, spelling


def summary(state, res, top):
    cases, freed, still = stats(state, res)
    total = len(res.rows)
    lines = [
        f"stored unmatched rows read: {total} ('unchecked' rows, never touched: {res.unchecked})",
        f"recipes with stored rows: {len(res.recipes_with_rows)}",
    ]
    for case in (INSERT, COVERED, NON_FOOD, NO_ITEM, REVIEW):
        lines.append(f"  {CASE_TITLE[case]}: {cases[case]}")
    lines.append(f"recipe_ingredient rows to insert: {len(res.inserts)}"
                 f" (to taste: {sum(1 for i in res.inserts if i[4])}, with an amount: "
                 f"{sum(1 for i in res.inserts if not i[4])})")
    lines.append(f"recipe_unmatched_ingredient rows to delete: {sum(cases[c] for c in RESOLVED)}")
    lines.append(f"recipes that stop being 'unklar' because of this run: {len(freed)}"
                 f" (still unklar: {still})")
    items = Counter(state.item_names.get(i[1], f"#{i[1]}") for i in res.inserts)
    lines.append("inserted rows per item (top 15): " + "; ".join(f"{n}x {name}" for name, n in items.most_common(15)))
    names, recs, spelling = remaining_names(res, {NO_ITEM, REVIEW})
    lines.append(f"the {top} most common names that stay (case c + d; lines, recipes):")
    for key, n in names.most_common(top):
        lines.append(f"  {n:6}  {len(recs[key]):6}  {spelling[key].most_common(1)[0][0]}")
    return "\n".join(lines)


TSV_HEADER = ["unmatched_id", "recipe_id", "recipe_name", "line_no", "case", "page_line", "name", "item_id",
              "item_name", "quantity", "unit", "to_taste", "covered_by", "note"]


def tsv_row(state, r):
    cells = [r.id, r.rid, r.recipe_name, "" if r.line_no is None else r.line_no, r.case, r.raw, r.name,
             "" if r.item_id is None else r.item_id, state.item_names.get(r.item_id, ""),
             sql_decimal(r.quantity) if r.case == INSERT and r.quantity is not None else "",
             r.unit if r.case == INSERT else "", ("1" if r.to_taste else "0") if r.case == INSERT else "",
             ",".join(str(i) for i in sorted(r.covered_by)) if r.case == COVERED else "", r.note]
    return "\t".join(re.sub(r"[\t\r\n]+", " ", str(c)) for c in cells)


def write_outputs(state, res, out_dir, stamp, text):
    os.makedirs(out_dir, exist_ok=True)
    base = os.path.join(out_dir, f"rematch_{stamp}")
    header = [
        f"-- Re-sort of recipe_unmatched_ingredient, generated {stamp} by scripts/importer/rematch_unmatched.py",
        "-- Needs migrations/0012_diet_filter.sql and 0013_catalog_meat_lines.sql. Read the report (.txt) and the",
        "-- review TSV next to this file first.",
        "-- INSERTs are guarded on the recipe and the item existing and on no (recipe, item) row; every DELETE is by id,",
        "-- guarded on the row still being what the tool read and (cases a) on the recipe now having its item.",
        "-- Applying it twice changes nothing. There is no BEGIN/COMMIT on purpose: run it inside ONE transaction",
        "-- after a snapshot (scripts/importer/README.md), and compare the row counts with the report, e.g.",
        "--   { echo 'SET NAMES utf8mb4; START TRANSACTION;'; cat THIS_FILE.sql; echo 'COMMIT;'; } | \\",
        "--     docker compose exec -T db sh -c 'exec mariadb -u root -p\"$MARIADB_ROOT_PASSWORD\" \"$MARIADB_DATABASE\"'",
        "-- (leave out COMMIT first and read the counts, or use ROW_COUNT() checks of your own). The client stops at the",
        "-- first error, so COMMIT is never reached and nothing is kept.",
        "--",
    ] + ["-- " + l for l in text.splitlines()]
    with open(base + ".sql", "w", encoding="utf-8") as f:
        f.write("\n".join(header + sql_lines(res)) + "\n")
    with open(base + ".tsv", "w", encoding="utf-8") as f:
        f.write("\t".join(TSV_HEADER) + "\n")
        for r in res.rows:
            f.write(tsv_row(state, r) + "\n")
    with open(base + "_review.tsv", "w", encoding="utf-8") as f:
        f.write("\t".join(TSV_HEADER) + "\n")
        for r in res.rows:
            if r.case == REVIEW:
                f.write(tsv_row(state, r) + "\n")
    names, recs, spelling = remaining_names(res, {NO_ITEM})
    with open(base + "_noitem.tsv", "w", encoding="utf-8") as f:
        f.write("lines\trecipes\tname\n")
        for key, n in names.most_common():
            f.write(f"{n}\t{len(recs[key])}\t{spelling[key].most_common(1)[0][0]}\n")
    with open(base + ".txt", "w", encoding="utf-8") as f:
        f.write(text + "\n")
    return base


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--cache", default=DEFAULT_CACHE, help="page cache filled by repair_quantities.py")
    ap.add_argument("--out", default=DEFAULT_OUT, help="where the .sql/.tsv/.txt go")
    ap.add_argument("--top", type=int, default=60, help="how many remaining names the report lists (default 60)")
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
    text = summary(state, res, args.top)
    if unreadable:
        text = f"warning: {unreadable} cache files could not be read\n" + text
    stamp = args.stamp or datetime.now().strftime("%Y%m%d-%H%M%S")
    base = write_outputs(state, res, args.out, stamp, text)
    print(text)
    print(f"\nwrote {base}.sql / .tsv / _review.tsv / _noitem.tsv / .txt  (nothing was applied to the database)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
