#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HelloFresh importer, step 1: downloaded PDF -> draft recipe .sql

Input is the folder of downloaded HelloFresh PDFs (named <hash>-de-DE.pdf). For
each PDF we take the hash from the filename, fetch that recipe's structured
JSON-LD from hellofresh.de (public, no login), and generate a reviewable .sql.
The recipe pages carry a JSON-LD Recipe block with clean title, yield, time,
full nutrition (incl. fibre), ingredient strings and numbered steps, so there is
no fragile PDF-layout parsing. Ingredient german names are matched to the
inventory_item catalog via overrides.json (see that file).

Nothing is written to the DB here (the catalog is READ from MariaDB, configured by the
DAHAEM_DB_* environment variables, see db.py). Per PDF it writes:
  - scripts/recipes/_drafts/<Title>.sql   (review, then load.py)
  - a line in the match report (stdout + _drafts/_report.txt)
and renames the source PDF to <Title>.pdf.

Ingredient parsing lives in recipe_parse.py: exact decimal amounts (½ -> 0.5), spoons as
EL/TL, "nach Geschmack"/amountless lines as to-taste rows, bundle-for-4 servings.

Usage:
  python extract.py                 # default folder, only *-de-DE.pdf
  python extract.py --no-rename     # don't rename the PDFs
  python extract.py --delay 1.5     # seconds between fetches (be polite)
"""
import argparse, glob, html as htmllib, json, math, os, re, sys, time
import urllib.request as urlreq

import recipe_parse
from recipe_parse import find_recipe as _find_recipe, num, sql_decimal

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.abspath(os.path.join(HERE, "..", ".."))
DEFAULT_IN = os.environ.get("DAHAEM_RECIPE_SCANS", os.path.join(PROJECT, "recipe_scans"))   # folder of the downloaded PDFs
DRAFTS = os.path.join(PROJECT, "scripts", "recipes", "_drafts")
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
UNITS = recipe_parse.UNITS

# ---------- fetch + JSON-LD ----------
def recipe_url(hash_id):
    return "https://www.hellofresh.de/recipes/r-" + hash_id

def fetch_recipe(hash_id, retries=4):
    """Returns Recipe dict, or None if fetched-but-empty. Raises after `retries` network failures."""
    url = recipe_url(hash_id)
    last = None
    for attempt in range(retries):
        try:
            html = urlreq.urlopen(urlreq.Request(url, headers={"User-Agent": UA}), timeout=30).read().decode("utf-8", "replace")
            for b in re.findall(r'<script[^>]*application/ld\+json[^>]*>(.*?)</script>', html, re.S):
                try:
                    rec = _find_recipe(json.loads(b))
                except Exception:
                    rec = None
                if rec:
                    return rec
            return None                       # fetched OK, no Recipe block = empty
        except Exception as e:
            last = e
            time.sleep(min(30, 3 * (attempt + 1)))   # 3s,6s,9s,12s backoff
    raise last

# ---------- helpers ----------
UML = str.maketrans({"ä": "ae", "ö": "oe", "ü": "ue", "ß": "ss", "Ä": "ae", "Ö": "oe", "Ü": "ue"})
def round_half_up(x):
    return int(math.floor(x + 0.5))

def normalize(label):
    s = re.sub(r"\d+\)", " ", label)
    s = re.sub(r"\b[A-Z]{2}\b(\s*\|\s*[A-Z]{2}\b)*", " ", s)
    s = s.replace("**", " ")
    s = s.lower().translate(UML)
    s = re.sub(r"[^a-z0-9 ]", " ", s)
    return re.sub(r"\s+", " ", s).strip()

def sql_str(s):
    return s.replace("'", "''")

def pascal(title):
    t = title.translate(UML)
    t = re.sub(r"[^A-Za-z0-9 ]", " ", t)
    return "".join(w[:1].upper() + w[1:] for w in t.split())[:80]   # cap: Windows path limit

def iso_minutes(iso):
    if not iso:
        return None
    m = re.match(r"PT(?:(\d+)H)?(?:(\d+)M)?", iso)
    if not m:
        return None
    return (int(m.group(1) or 0)) * 60 + int(m.group(2) or 0)

def nut_int(nut, key):
    v = (nut or {}).get(key)
    if not v:
        return None
    n = num(str(v))
    return round_half_up(float(n)) if n is not None else None

def strip_html(s):
    s = htmllib.unescape(s)
    s = re.sub(r"</p>\s*<p[^>]*>", " ", s)      # paragraph breaks -> space
    s = re.sub(r"<[^>]+>", "", s)
    s = s.replace("*", "")
    return re.sub(r"\s+", " ", s).strip()

# ---------- ingredient parsing + matching ----------
class Catalog:
    """Matches an ingredient name to an inventory_item id. items: (id, name_english, name_deutsch)."""
    def __init__(self, items, overrides):
        self.rows = [(i, normalize(de or ""), en) for i, en, de in items]
        self.exact = overrides.get("exact", {})
        self.rules = overrides.get("contains_rules", [])
        self.by_norm = {}
        for i, nd, en in self.rows:
            self.by_norm.setdefault(nd, []).append((i, en))

    def match(self, name):
        n = normalize(name)
        if n in self.exact:
            return self.exact[n], "override", ""
        for r in self.rules:
            if r["contains"] in n:
                return r["item_id"], "rule", r.get("note", "")
        if n in self.by_norm:
            c = self.by_norm[n]
            return (c[0][0], "exact", c[0][1]) if len(c) == 1 else (c[0][0], "exact-multi", "; ".join(f"#{i} {e}" for i, e in c))
        subs = [(i, en) for i, nd, en in self.rows if nd and (n in nd or nd in n)]
        if len(subs) == 1:
            return subs[0][0], "like", subs[0][1]
        if subs:
            return subs[0][0], "like-multi", "; ".join(f"#{i} {en}" for i, en in subs[:4])
        return None, "none", "NO MATCH -> add to catalog or overrides.json"

    @classmethod
    def from_db(cls, con, overrides):
        with con.cursor() as cur:
            cur.execute("SELECT inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch "
                        "FROM inventory_item")
            return cls(list(cur.fetchall()), overrides)

def load_overrides():
    with open(os.path.join(HERE, "overrides.json"), encoding="utf-8") as f:
        return json.load(f)

# Only WATER is used-but-never-bought, so only it is dropped. Salt, pepper, oil, sugar,
# flour, vinegar etc. ARE tracked ingredients in this app: users own and re-buy them, so
# they belong in the pantry and on the shopping list. (A meal-kit omits them because they
# are not shipped in the box — a logistics reason we deliberately do NOT inherit.)
# Staples are matched to catalog items via overrides.json (salz -> speisesalz, etc.).
# NB: keys must be spelled the way normalize() emits them (umlauts asciified: ö -> oe).
PANTRY = {"wasser"}

def is_pantry(raw, name):
    # Whole-word token match so "wasser" is dropped but compound foods ("rosenwasser",
    # "wasserkastanie") are kept.
    return any(tok in PANTRY for tok in normalize(name).split())

def strip_lead_unit(name):
    """'Stück Gemüsebrühe' -> ('Stück', 'Gemüsebrühe'); kept for rematch.py. Returns (unit_or_None, name)."""
    return recipe_parse.split_unit(name)

CONFIDENT = recipe_parse.CONFIDENT

# ---------- per recipe ----------
def process(hash_id, pdf_path, cat, rename, delay):
    """Fetch one recipe and write its draft .sql. Returns (result, flags, kind)."""
    try:
        rec = fetch_recipe(hash_id)
    except Exception as e:
        return None, [f"network error after retries: {e}"], "neterr"
    if delay:
        time.sleep(delay)
    if not rec:
        return None, ["fetched but no Recipe data (empty)"], "empty"
    flags = []
    parsed = recipe_parse.parse_recipe(rec, cat, is_pantry=lambda n: is_pantry(n, n))
    title, servings = parsed["title"], parsed["servings"]
    if not title:
        return None, ["no title in JSON-LD"], "empty"
    flags += parsed["notes"]
    cook = iso_minutes(rec.get("totalTime"))
    if cook is None:
        flags.append("time missing"); cook = 0
    nut = rec.get("nutrition") or {}
    nutvals = {"cal": nut_int(nut, "calories"), "prot": nut_int(nut, "proteinContent"),
               "carb": nut_int(nut, "carbohydrateContent"), "fat": nut_int(nut, "fatContent"),
               "fibre": nut_int(nut, "fiberContent"), "sugar": nut_int(nut, "sugarContent")}
    if nutvals["cal"] is None:
        flags.append("kcal missing")

    matched, reviews = [], []
    for ln in parsed["lines"]:
        if ln.get("held_collision"):             # same item as an earlier line, uncertain: never summed, never a 2nd row
            reviews.append((ln["name"], ln["item_id"], ln["conf"], "not combined with the earlier line of this item"))
            continue
        matched.append((ln["name"], ln["unit"], ln["quantity"], ln["to_taste"], ln["item_id"], ln["conf"], ln["note"]))
        if ln["conf"] not in CONFIDENT:
            reviews.append((ln["name"], ln["item_id"], ln["conf"], ln["note"]))
    for rv in parsed.get("reviews", []):         # amountless lines with no safe amount: not in the draft
        reviews.append((rv["name"], rv["item_id"], rv["conf"], rv["reason"]))
    if parsed.get("servings_max"):
        flags.append(f"REVIEW: servings range {servings} - {parsed['servings_max']}: the draft stores {servings}, "
                     f"recipe_servings_max is not written by the importer yet (docs/BACKLOG.md)")
    if not matched:
        flags.append("no ingredients parsed")

    steps = []
    for ins in (rec.get("recipeInstructions") or []):
        txt = ins.get("text") if isinstance(ins, dict) else str(ins)
        txt = strip_html(txt or "")
        if txt:
            steps.append(txt)

    # Old/discontinued recipes have valid pages but empty ingredient/step data.
    # There's nothing to import, so skip (logged to _failed) rather than write a broken draft.
    if not matched or not steps:
        why = []
        if not matched: why.append("no ingredients")
        if not steps: why.append("no steps")
        return None, [f"empty recipe ({', '.join(why)}) - skipped, nothing to import"], "empty"

    # Lines whose item is not among the recipe's rows are dropped by the loader (item id 0): keep them in the
    # draft so load_recipes.py can store them in recipe_unmatched_ingredient (the recipe is "unklar" for diets).
    stored_items = {m[4] for m in matched if m[4] is not None}
    unmatched = [(n, raw) for n, raw, _name in recipe_parse.unmatched_lines(rec, parsed, stored_items)]
    sql = build_sql(title, servings, cook, nutvals, matched, steps, recipe_url(hash_id), unmatched)
    os.makedirs(DRAFTS, exist_ok=True)
    fn = pascal(title) + ".sql"
    open(os.path.join(DRAFTS, fn), "w", encoding="utf-8").write(sql)
    if rename and pdf_path:
        newpdf = os.path.join(os.path.dirname(pdf_path), pascal(title) + ".pdf")
        if os.path.abspath(newpdf) != os.path.abspath(pdf_path) and not os.path.exists(newpdf):
            os.rename(pdf_path, newpdf)
    return ({"title": title, "file": fn, "cook": cook, "servings": servings, "nut": nutvals,
             "ings": matched, "reviews": reviews, "unmatched": unmatched, "steps": len(steps), "flags": flags}, flags, "ok")

UNMATCHED_MARK = "-- UNMATCHED "

def build_sql(title, servings, cook, nut, matched, steps, source_url=None, unmatched=()):
    """Draft SQL for one recipe. matched: (name, unit, qty Decimal|None, to_taste, item_id, conf, note).
    unmatched: (line_no, raw page line) of the lines that get no row; they are written as `-- UNMATCHED <no> <raw>`
    comment lines BEFORE the ingredient INSERT (so rematch.py, which rewrites that block, leaves them alone) and
    load_recipes.py stores them in recipe_unmatched_ingredient."""
    n = {k: ("NULL" if v is None else v) for k, v in nut.items()}
    sub = f"(SELECT recipe_id FROM recipe WHERE recipe_name='{sql_str(title)}')"
    url = "NULL" if not source_url else f"'{sql_str(source_url)}'"
    L = ["BEGIN;",
         "INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar, recipe_source_url)",
         f"VALUES ('{sql_str(title)}', {servings}, {cook}, 0, {n['cal']}, {n['prot']}, {n['carb']}, {n['fat']}, {n['fibre']}, {n['sugar']}, {url});"]
    for line_no, raw in unmatched:
        L.append(UNMATCHED_MARK + ("-" if line_no is None else str(line_no)) + " " + " ".join((raw or "").split()))
    L += ["INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed, recipe_ingredient_to_taste) VALUES"]
    rows = []
    for name, unit, qty, to_taste, iid, conf, note in matched:
        tag = "" if conf in CONFIDENT else f"   -- REVIEW ({conf}): {name} | {note}"
        rows.append((f"  ({sub}, {iid if iid is not None else 0}, '{sql_str(unit)}', {sql_decimal(qty)}, {1 if to_taste else 0})", tag))
    # terminator MUST precede the '--' comment, else the row-separating comma is
    # swallowed by the comment and the INSERT is a syntax error.
    L.append("\n".join(core + (";" if i == len(rows) - 1 else ",") + tag
                        for i, (core, tag) in enumerate(rows)))
    L.append("INSERT INTO recipe_instructions (recipe_instructions_recipe_id, recipe_instructions_step_number, recipe_instructions_instruction, recipe_instructions_notes, recipe_instructions_source) VALUES")
    srows = [f"  ({sub}, {i}, '{sql_str(s)}', NULL, 'HelloFresh')" for i, s in enumerate(steps, 1)]
    L.append(",\n".join(srows) + ";")
    L.append("COMMIT;")
    return "\n".join(L) + "\n"

HASH_RE = re.compile(r"[0-9a-f]{24}")
SITEMAP = "https://www.hellofresh.de/sitemap_recipe_pages.xml"

def hash_of(text):
    m = HASH_RE.search(text.strip().lower())
    return m.group() if m else None

def sitemap_hashes():
    import gzip
    d = urlreq.urlopen(urlreq.Request(SITEMAP, headers={"User-Agent": UA}), timeout=60).read()
    if d[:2] == b"\x1f\x8b":
        d = gzip.decompress(d)
    xml = d.decode("utf-8", "replace")
    seen, out = set(), []
    for loc in re.findall(r"<loc>(.*?)</loc>", xml):
        h = hash_of(loc)
        if h and h not in seen:
            seen.add(h); out.append(h)
    return out

def load_set(path):
    try:
        return set(l.strip() for l in open(path, encoding="utf-8") if l.strip())
    except FileNotFoundError:
        return set()

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--in", dest="indir", default=DEFAULT_IN)
    ap.add_argument("--glob", default="*-de-DE.pdf")
    ap.add_argument("--list", dest="listfile", default=None, help="text file of recipe URLs or bare hashes (from discover.py), one per line")
    ap.add_argument("--no-rename", action="store_true")
    ap.add_argument("--delay", type=float, default=1.0)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--resume", action="store_true", help="skip hashes already in _done.txt")
    ap.add_argument("--reverse", action="store_true", help="process the list newest-first (sitemap is oldest-first)")
    args = ap.parse_args()

    import db
    con = db.connect()
    try:
        cat = Catalog.from_db(con, load_overrides())
    finally:
        con.close()
    os.makedirs(DRAFTS, exist_ok=True)
    done_path, fail_path = os.path.join(DRAFTS, "_done.txt"), os.path.join(DRAFTS, "_failed.txt")
    done = load_set(done_path) if args.resume else set()

    # Build work items {hash: pdf_path_or_None} from PDF folder, a URL/hash list, and/or the sitemap.
    work = {}
    for fp in sorted(glob.glob(os.path.join(args.indir, args.glob))):
        h = hash_of(os.path.basename(fp))
        if h:
            work.setdefault(h, fp)
    if args.listfile:
        for line in open(args.listfile, encoding="utf-8"):
            if line.strip() and not line.lstrip().startswith("#"):
                h = hash_of(line)
                if h:
                    work.setdefault(h, None)
    items = [(h, fp) for h, fp in work.items() if h not in done]
    if args.reverse:
        items.reverse()
    if args.limit:
        items = items[:args.limit]

    rep = [f"# Import report - {len(items)} recipe(s) ({len(done)} already done)\n"]
    total_review = 0
    neterr_streak = 0
    for idx, (h, fp) in enumerate(items, 1):
        try:
            res, flags, kind = process(h, fp, cat, rename=not args.no_rename, delay=args.delay)
        except Exception as e:
            open(fail_path, "a", encoding="utf-8").write(h + "\n")
            rep.append(f"## {h}: ERROR ({type(e).__name__}: {e}) - skipped")
            neterr_streak = 0
            continue
        if idx % 25 == 0:
            print(f"  ...{idx}/{len(items)}", file=sys.stderr)
        if kind == "neterr":
            neterr_streak += 1
            rep.append(f"## {h}: NET-SKIP (left for resume) - {flags}")
            if neterr_streak >= 15:
                print(f"  network looks down ({neterr_streak} failures in a row) - stopping; re-run with --resume to continue", file=sys.stderr)
                break
            continue
        neterr_streak = 0
        if res is None:                       # empty recipe: log to _failed, won't retry
            open(fail_path, "a", encoding="utf-8").write(h + "\n")
            rep.append(f"## {h}: SKIPPED - {flags}\n"); continue
        open(done_path, "a", encoding="utf-8").write(h + "\n")
        auto = sum(1 for *_x, c, _n in res["ings"] if c in CONFIDENT)
        rep.append(f"## {res['title']}  ->  _drafts/{res['file']}")
        rep.append(f"   servings={res['servings']} time={res['cook']} steps={res['steps']} nutrition={res['nut']}")
        rep.append(f"   ingredients: {len(res['ings'])} ({auto} auto, {len(res['reviews'])} to review)")
        for name, iid, conf, note in res["reviews"]:
            rep.append(f"     REVIEW [{conf}] '{name}' -> guess #{iid} | {note}")
        for fl in res["flags"]:
            rep.append(f"     FLAG: {fl}")
        total_review += len(res["reviews"])
        rep.append("")
    rep.append(f"\nTOTAL ingredient lines to review: {total_review}")
    out = "\n".join(rep)
    print(out)
    os.makedirs(DRAFTS, exist_ok=True)
    open(os.path.join(DRAFTS, "_report.txt"), "w", encoding="utf-8").write(out)
    print(f"\nDrafts in: {DRAFTS}")

if __name__ == "__main__":
    main()
