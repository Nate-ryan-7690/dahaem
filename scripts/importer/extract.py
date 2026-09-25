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

Nothing is written to the DB here. Per PDF it writes:
  - scripts/recipes/_drafts/<Title>.sql   (review, then load.py)
  - a line in the match report (stdout + _drafts/_report.txt)
and renames the source PDF to <Title>.pdf.

Usage:
  python extract.py                 # default folder, only *-de-DE.pdf
  python extract.py --no-rename     # don't rename the PDFs
  python extract.py --delay 1.5     # seconds between fetches (be polite)
"""
import argparse, glob, html as htmllib, json, math, os, re, sqlite3, sys, time
import urllib.request as urlreq

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.abspath(os.path.join(HERE, "..", ".."))
DEFAULT_IN = r"C:\Users\Schüler\Documents\Akademie Überlingen\IT umschulng Module 1\Projekte\Kitchen Organizer DB\Recipe Scans"
DEFAULT_DB = os.path.join(PROJECT, "data", "kitchen_organizer.db")
DRAFTS = os.path.join(PROJECT, "scripts", "recipes", "_drafts")
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
UNITS = {"g", "kg", "ml", "l", "Stück", "EL", "TL", "Bund", "Dose", "Packung", "Prise", "Zehe", "Scheibe", "Scheiben"}

# ---------- fetch + JSON-LD ----------
def fetch_recipe(hash_id, retries=4):
    """Returns Recipe dict, or None if fetched-but-empty. Raises after `retries` network failures."""
    url = "https://www.hellofresh.de/recipes/r-" + hash_id
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

def _find_recipe(o):
    if isinstance(o, dict):
        t = o.get("@type")
        if t == "Recipe" or (isinstance(t, list) and "Recipe" in t):
            return o
        for v in o.values():
            r = _find_recipe(v)
            if r:
                return r
    elif isinstance(o, list):
        for v in o:
            r = _find_recipe(v)
            if r:
                return r
    return None

# ---------- helpers ----------
UML = str.maketrans({"ä": "ae", "ö": "oe", "ü": "ue", "ß": "ss", "Ä": "ae", "Ö": "oe", "Ü": "ue"})
FRAC = {"½": ".5", "¼": ".25", "¾": ".75", "⅓": ".333", "⅔": ".667"}

def round_half_up(x):
    return int(math.floor(x + 0.5))

def num(s):
    for k, v in FRAC.items():
        s = s.replace(k, v)
    s = s.replace(",", ".")
    m = re.search(r"\d+(?:\.\d+)?", s)
    return float(m.group()) if m else None

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
    return round_half_up(n) if n is not None else None

def strip_html(s):
    s = htmllib.unescape(s)
    s = re.sub(r"</p>\s*<p[^>]*>", " ", s)      # paragraph breaks -> space
    s = re.sub(r"<[^>]+>", "", s)
    s = s.replace("*", "")
    return re.sub(r"\s+", " ", s).strip()

# ---------- ingredient parsing + matching ----------
class Catalog:
    def __init__(self, db, overrides):
        con = sqlite3.connect(db)
        self.rows = [(i, normalize(de or ""), en) for i, en, de in con.execute(
            "SELECT inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch FROM inventory_item")]
        con.close()
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

# pantry staples ("Nicht in Deiner Lieferung enthalten") we never store as recipe ingredients
# NB: keys must be spelled the way normalize() emits them (umlauts asciified: ö -> oe).
PANTRY = {"salz", "pfeffer", "wasser", "zucker", "mehl", "oel", "olivenoel", "sonnenblumenoel",
          "pflanzenoel", "essig", "weissweinessig", "balsamicoessig", "apfelessig", "rotweinessig", "honig"}

def is_pantry(raw, name):
    rn = normalize(raw)
    if "nach geschmack" in rn or "essloeffel" in rn or "teeloeffel" in rn:
        return True
    # Some HelloFresh lines have no leading number ("g Pfeffer", "Salz und Pfeffer"),
    # so parse_ingredient leaves unit/quantity noise glued to the name. Match staples
    # on whole-word tokens: "g pfeffer" -> {g, pfeffer} hits "pfeffer". Compound foods
    # stay one token ("paniermehl" != "mehl", "salzkartoffeln" != "salz") so they pass.
    return any(tok in PANTRY for tok in normalize(name).split())

def strip_lead_unit(name):
    """'Stück Gemüsebrühe' -> ('Stück', 'Gemüsebrühe'). Some HelloFresh lines give a
    unit with no number; without this the unit stays glued to the name and never
    matches. Returns (unit_or_None, cleaned_name)."""
    parts = name.split(None, 1)
    if len(parts) == 2 and parts[0] in UNITS:
        return parts[0], parts[1].strip()
    return None, name

def parse_ingredient(s):
    """'100 g Raclette-Käse' -> (qty, unit, name)."""
    s = s.strip()
    m = re.match(r"([\d.,½¼¾⅓⅔]+)\s*(\S+)?\s*(.*)", s)
    if not m or num(m.group(1)) is None:
        unit, name = strip_lead_unit(s)         # no number: still peel a leading unit
        return 1, (unit or "Stück"), name       # "Stück Gemüsebrühe" -> unit Stück, name Gemüsebrühe
    qty = max(1, round_half_up(num(m.group(1))))
    tok, rest = (m.group(2) or ""), (m.group(3) or "")
    if tok in UNITS:
        unit, name = tok, rest
    else:
        unit, name = "Stück", (tok + " " + rest).strip()
    return qty, unit, name.strip()

# ---------- per recipe ----------
def process(hash_id, pdf_path, cat, rename, delay):
    try:
        rec = fetch_recipe(hash_id)
    except Exception as e:
        return None, [f"network error after retries: {e}"], "neterr"
    if delay:
        time.sleep(delay)
    if not rec:
        return None, ["fetched but no Recipe data (empty)"], "empty"
    flags = []
    name = (rec.get("name") or "").strip()
    # drop the subtitle half (double-space separated) and any marketing blurb glued on
    title = re.split(r"\s{2,}|\s+Bei diesem Gericht|\s+verzehrfertiges", name)[0].strip()
    if len(title) > 120:
        title = title[:120].rstrip()
    if not title:
        return None, ["no title in JSON-LD"]
    cook = iso_minutes(rec.get("totalTime"))
    if cook is None:
        flags.append("time missing"); cook = 0
    try:
        servings = int(rec.get("recipeYield") or 2)
    except Exception:
        servings = 2
    nut = rec.get("nutrition") or {}
    nutvals = {"cal": nut_int(nut, "calories"), "prot": nut_int(nut, "proteinContent"),
               "carb": nut_int(nut, "carbohydrateContent"), "fat": nut_int(nut, "fatContent"),
               "fibre": nut_int(nut, "fiberContent"), "sugar": nut_int(nut, "sugarContent")}
    if nutvals["cal"] is None:
        flags.append("kcal missing")

    matched, reviews = [], []
    for raw in (rec.get("recipeIngredient") or []):
        qty, unit, iname = parse_ingredient(raw)
        if is_pantry(raw, iname):
            continue
        iid, conf, note = cat.match(iname)
        matched.append((iname, unit, qty, iid, conf, note))
        if conf not in ("override", "rule", "exact"):
            reviews.append((iname, iid, conf, note))
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

    sql = build_sql(title, servings, cook, nutvals, matched, steps)
    os.makedirs(DRAFTS, exist_ok=True)
    fn = pascal(title) + ".sql"
    open(os.path.join(DRAFTS, fn), "w", encoding="utf-8").write(sql)
    if rename and pdf_path:
        newpdf = os.path.join(os.path.dirname(pdf_path), pascal(title) + ".pdf")
        if os.path.abspath(newpdf) != os.path.abspath(pdf_path) and not os.path.exists(newpdf):
            os.rename(pdf_path, newpdf)
    return ({"title": title, "file": fn, "cook": cook, "servings": servings, "nut": nutvals,
             "ings": matched, "reviews": reviews, "steps": len(steps), "flags": flags}, flags, "ok")

def build_sql(title, servings, cook, nut, matched, steps):
    n = {k: ("NULL" if v is None else v) for k, v in nut.items()}
    sub = f"(SELECT recipe_id FROM recipe WHERE recipe_name='{sql_str(title)}')"
    L = ["BEGIN;",
         "INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar)",
         f"VALUES ('{sql_str(title)}', {servings}, {cook}, 0, {n['cal']}, {n['prot']}, {n['carb']}, {n['fat']}, {n['fibre']}, {n['sugar']});",
         "INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, recipe_ingredient_quantity_needed) VALUES"]
    rows = []
    for name, unit, qty, iid, conf, note in matched:
        tag = "" if conf in ("override", "rule", "exact") else f"   -- REVIEW ({conf}): {name} | {note}"
        rows.append((f"  ({sub}, {iid if iid is not None else 0}, '{sql_str(unit)}', {qty})", tag))
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
    ap.add_argument("--db", default=DEFAULT_DB)
    ap.add_argument("--no-rename", action="store_true")
    ap.add_argument("--delay", type=float, default=1.0)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--resume", action="store_true", help="skip hashes already in _done.txt")
    ap.add_argument("--reverse", action="store_true", help="process the list newest-first (sitemap is oldest-first)")
    args = ap.parse_args()

    overrides = json.load(open(os.path.join(HERE, "overrides.json"), encoding="utf-8"))
    cat = Catalog(args.db, overrides)
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
        auto = sum(1 for *_x, c, _n in res["ings"] if c in ("override", "rule", "exact"))
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
