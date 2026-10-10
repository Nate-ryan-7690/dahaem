#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Repair recipe ingredient quantities from the recipe pages: writes a REVIEW TSV and a
GUARDED SQL file. It never changes the database itself: it only READS MariaDB (recipes,
their ingredient rows, the catalog) and writes files. Applying the SQL is a separate,
manual step (see README.md in this folder), after a snapshot.

What it fixes (see migrations/0005_recipe_quantities.sql, which must be applied first):
  * the ½-bug: "½ Stück Zitrone" stored as 5 Stück (¼ -> 25, ¾ -> 75, ⅓ -> 333, ⅔ -> 667)
  * rounding: every amount was rounded half-up and forced to >= 1 ("1½" -> 2, "¼ TL" -> 1)
  * units: "Esslöffel"/"Teelöffel" lines were dropped; "Prisen" etc. stored as Stück
  * "nach Geschmack"/amountless lines: dropped or stored as 1 Stück -> to-taste rows
  * "Bundle 4P" recipes: amounts for 4 people stored with servings 2 -> servings 4
and records each matched page in recipe.recipe_source_url.

Steps:
  1. select recipes: "suspect" (non-g/ml rows with quantity 5, 25, 75, 333 or 667) or "all"
  2. match each to a page: URL slug ~ recipe name, confirmed by the JSON-LD name cut the
     way the importer cut titles; reports matched / ambiguous / unmatched
  3. fetch politely: --delay seconds between requests (default 2), a normal browser user
     agent, robots.txt respected; every page is cached on disk (--cache), so a re-run (after
     a crash, Ctrl-C or a network outage) skips what it already has: resumable, can run for
     hours in the background (see README.md)
  4. parse with recipe_parse + the importer's catalog/overrides matching, compare with the
     stored rows, write:
       _repair/repair_<selector>_<stamp>.sql   guarded statements, NO BEGIN/COMMIT
       _repair/repair_<selector>_<stamp>.tsv   one line per change / held line / unmatched recipe
       _repair/repair_<selector>_<stamp>.txt   the summary printed at the end

Every UPDATE is guarded on the row's current value (it does nothing if the row changed since
this run read it), every INSERT on the row not existing yet, the servings UPDATE on the old
servings and the URL UPDATE on the URL still being empty.

Only listed in the TSV, never in the SQL:
  * any change to a row whose page line matches its item only uncertainly (like / multi):
    review-uncertain-match. An earlier wrong match must not be "corrected" on a guess;
  * a unit change the old parser cannot explain (the stored row is not exactly what the old
    parser made of this page line): review-unit;
  * servings from a page without a readable recipeYield (and no bundle-for-4 title): the 2 would
    be a guess, so the stored value is kept: review-servings;
  * changes that are not one of the known bugs, and new lines that are neither spoons nor
    to-taste (review-held), unless --include-other is given.

The database comes from DAHAEM_DB_URL / DAHAEM_DB_USER / DAHAEM_DB_PASSWORD (db.py). For tests
and offline review, --state FILE.json replaces the database read with a JSON snapshot.

Usage:
  python repair_quantities.py --select suspect                 # first pass (~1,550 rows)
  python repair_quantities.py --select all                     # overnight; same cache, resumable
  python repair_quantities.py --select suspect --offline       # rebuild the files from cache only
"""
import argparse, bisect, hashlib, json, os, re, sys, time, unicodedata
import urllib.error, urllib.request, urllib.robotparser
from collections import Counter
from datetime import datetime
from decimal import Decimal

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import recipe_parse  # noqa: E402
from recipe_parse import sql_decimal  # noqa: E402
from extract import CONFIDENT, Catalog, is_pantry, load_overrides  # noqa: E402

DEFAULT_URLS = [os.path.join(HERE, "recipe_urls.txt"), os.path.join(HERE, "recipe_urls_tail.txt")]
DEFAULT_CACHE = os.path.join(HERE, "_cache", "pages")
DEFAULT_OUT = os.path.join(HERE, "_repair")
ROBOTS_URL = "https://www.hellofresh.de/robots.txt"
BROWSER_UA = ("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
              "(KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")

SUSPECT_VALUES = {Decimal(v) for v in (5, 25, 75, 333, 667)}
WEIGHT_UNITS = {"g", "ml"}
SPOONS = {"EL", "TL"}
HASH_RE = re.compile(r"-?([0-9a-f]{24})/?$")

# change classes (TSV "class" column and summary)
FRACTION, ROUNDING, UNIT, TO_TASTE, OTHER = "half-bug", "rounding", "unit", "to-taste", "other"
INS_SPOON, INS_TO_TASTE, INS_OTHER = "insert-spoon", "insert-to-taste", "insert-other"


# ---------- database state ----------
class State:
    """What the database holds: recipes {id: dict}, rows {recipe_id: {item_id: dict}}, catalog items."""

    def __init__(self, recipes, rows, items):
        self.recipes, self.rows, self.items = recipes, rows, items
        self.item_names = {i: en for i, en, _de in items}

    @classmethod
    def from_json(cls, path):
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        recipes = {r["id"]: dict(r, source_url=r.get("source_url")) for r in data["recipes"]}
        rows = {}
        for r in data["ingredients"]:
            q = r.get("quantity")
            rows.setdefault(r["recipe_id"], {})[r["item_id"]] = {
                "unit": r["unit"], "quantity": None if q is None else Decimal(str(q)),
                "to_taste": bool(r.get("to_taste"))}
        return cls(recipes, rows, [tuple(i) for i in data["items"]])

    @classmethod
    def from_db(cls, con):
        with con.cursor() as cur:
            cur.execute("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        "AND ((TABLE_NAME = 'recipe_ingredient' AND COLUMN_NAME = 'recipe_ingredient_to_taste') "
                        "OR (TABLE_NAME = 'recipe' AND COLUMN_NAME = 'recipe_source_url'))")
            if cur.fetchone()[0] != 2:
                raise SystemExit("apply migrations/0005_recipe_quantities.sql first (snapshot before it)")
            cur.execute("SELECT recipe_id, recipe_name, recipe_servings, recipe_source_url FROM recipe")
            recipes = {i: {"id": i, "name": n, "servings": s, "source_url": u} for i, n, s, u in cur.fetchall()}
            cur.execute("SELECT recipe_ingredient_recipe_id, recipe_ingredient_item_id, recipe_ingredient_unit, "
                        "recipe_ingredient_quantity_needed, recipe_ingredient_to_taste FROM recipe_ingredient")
            rows = {}
            for rid, iid, unit, qty, tt in cur.fetchall():
                rows.setdefault(rid, {})[iid] = {"unit": unit, "quantity": qty, "to_taste": bool(tt)}
            cur.execute("SELECT inventory_item_id, inventory_item_name_english, inventory_item_name_deutsch "
                        "FROM inventory_item")
            items = list(cur.fetchall())
        return cls(recipes, rows, items)

    def select(self, selector):
        """Recipe ids to repair, in id order."""
        if selector == "all":
            return sorted(self.recipes)
        return sorted(rid for rid, rows in self.rows.items() if rid in self.recipes and any(
            not r["to_taste"] and r["unit"] not in WEIGHT_UNITS and r["quantity"] in SUSPECT_VALUES
            for r in rows.values()))


# ---------- URL matching ----------
def name_key(text):
    """Comparable key for a recipe name or a URL slug: 'Hähnchen-Curry' and 'haehnchen-curry'
    and 'hahnchen-curry' all -> 'hahnchen-curry'."""
    t = (text or "").lower().replace("ß", "ss")
    t = unicodedata.normalize("NFKD", t)
    t = "".join(c for c in t if not unicodedata.combining(c))
    t = t.replace("ae", "a").replace("oe", "o").replace("ue", "u")
    return "-".join(re.findall(r"[a-z0-9]+", t))


def same_title(a, b):
    norm = lambda s: re.sub(r"\s+", " ", (s or "").strip()).casefold()
    return norm(a) == norm(b)


class UrlIndex:
    def __init__(self, urls):
        self.urls = urls
        self.by_key = {}
        for u in urls:
            self.by_key.setdefault(self.slug_key(u), []).append(u)
        self.keys = sorted(self.by_key)

    @staticmethod
    def slug_key(url):
        slug = url.rstrip("/").rsplit("/", 1)[-1]
        slug = HASH_RE.sub("", slug)
        return name_key(slug)

    def candidates(self, name, limit):
        """URLs whose slug equals the name, then URLs whose slug starts with it (a subtitle
        follows). Returns (urls, truncated)."""
        key = name_key(name)
        if not key:
            return [], False
        out = list(self.by_key.get(key, []))
        lo = bisect.bisect_left(self.keys, key + "-")
        hi = bisect.bisect_left(self.keys, key + "-￿")
        for k in self.keys[lo:hi]:
            out.extend(self.by_key[k])
        return out[:limit], len(out) > limit


def read_url_files(paths):
    urls, seen, missing = [], set(), []
    for p in paths:
        if not os.path.exists(p):
            missing.append(p)
            continue
        with open(p, encoding="utf-8") as f:
            for line in f:
                u = line.strip()
                if u and not u.startswith("#") and u not in seen:
                    seen.add(u)
                    urls.append(u)
    return urls, missing


# ---------- polite, cached fetching ----------
class Fetcher:
    """Fetches recipe pages and caches the extracted JSON-LD (or the 404) on disk, one file per
    URL. Network errors are NOT cached, so they are retried on the next run."""

    def __init__(self, cache_dir, delay=2.0, offline=False, user_agent=BROWSER_UA, opener=None, log=None):
        self.cache_dir, self.delay, self.offline, self.ua = cache_dir, delay, offline, user_agent
        self.opener = opener or urllib.request.urlopen
        self.log = log or (lambda msg: print(msg, file=sys.stderr, flush=True))
        self.stats = Counter()
        self._last = 0.0
        self._robots = None
        os.makedirs(cache_dir, exist_ok=True)

    def _path(self, url):
        m = HASH_RE.search(url.rstrip("/"))
        name = m.group(1) if m else hashlib.sha1(url.encode("utf-8")).hexdigest()[:24]
        return os.path.join(self.cache_dir, name + ".json")

    def _wait(self):
        pause = self.delay - (time.monotonic() - self._last)
        if pause > 0:
            time.sleep(pause)
        self._last = time.monotonic()

    def _allowed(self, url):
        if self._robots is None:
            self._robots = urllib.robotparser.RobotFileParser()
            self._wait()
            try:
                with self.opener(urllib.request.Request(ROBOTS_URL, headers={"User-Agent": self.ua}), timeout=30) as r:
                    self._robots.parse(r.read().decode("utf-8", "replace").splitlines())
            except Exception as e:                  # no robots.txt readable: be conservative and stop
                raise SystemExit(f"could not read {ROBOTS_URL} ({e}); not fetching without it")
        return self._robots.can_fetch(self.ua, url)

    def get(self, url):
        """-> ('ok', recipe_dict) | ('empty', None) | ('404', None) | ('error', msg) | ('robots', None)
        | ('uncached', None) in --offline mode."""
        path = self._path(url)
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                cached = json.load(f)
            self.stats["cache"] += 1
            return cached["status"], cached.get("recipe")
        if self.offline:
            self.stats["uncached"] += 1
            return "uncached", None
        if not self._allowed(url):
            self.stats["robots"] += 1
            return "robots", None
        last = None
        for attempt in range(3):
            self._wait()
            try:
                req = urllib.request.Request(url, headers={"User-Agent": self.ua, "Accept-Language": "de-DE,de;q=0.9"})
                with self.opener(req, timeout=30) as r:
                    html = r.read().decode("utf-8", "replace")
                rec = recipe_parse.recipe_from_html(html)
                status = "ok" if rec else "empty"
                self._store(path, url, status, rec)
                self.stats["fetched"] += 1
                return status, rec
            except urllib.error.HTTPError as e:
                if e.code in (404, 410):
                    self._store(path, url, "404", None)
                    self.stats["fetched"] += 1
                    return "404", None
                last = f"HTTP {e.code}"
                if e.code == 429:
                    time.sleep(60)                  # asked to slow down
            except Exception as e:                  # network trouble: retry, then leave for a re-run
                last = f"{type(e).__name__}: {e}"
            time.sleep(5 * (attempt + 1))
        self.stats["error"] += 1
        self.log(f"  fetch failed (will retry on the next run): {url} ({last})")
        return "error", last

    def _store(self, path, url, status, rec):
        tmp = path + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump({"url": url, "status": status, "recipe": rec}, f, ensure_ascii=False)
        os.replace(tmp, path)                       # atomic: a killed run never leaves half a file


# ---------- comparing a page with the stored rows ----------
def classify(old, new):
    """Class of an update from stored row `old` to parsed line `new` (dicts with quantity/unit/to_taste)."""
    if new["to_taste"]:
        return TO_TASTE if not old["to_taste"] else OTHER
    if old["to_taste"]:
        return OTHER
    oq, nq = old["quantity"], new["quantity"]
    if old["unit"] == new["unit"] and old["unit"] not in WEIGHT_UNITS and oq in SUSPECT_VALUES and nq < 1 \
            and any(nq * (10 ** k) == oq for k in (1, 2, 3)):
        return FRACTION
    if old["unit"] == new["unit"] and oq == old_importer_value(nq):
        return ROUNDING
    if old["unit"] != new["unit"] and oq in (nq, old_importer_value(nq)):
        return UNIT
    return OTHER


def unit_change_explained(old, line):
    """A unit change is a known equivalence only when the stored row is exactly what the old parser
    made of this very page line: the old parser knew fewer unit words ("3 Esslöffel Olivenöl" ->
    3 Stück; "2 Prisen Zucker" -> 2 Stück), so the new unit is a parser fix, not a guess."""
    return recipe_parse.old_parse(line["raw"]) == (old["quantity"], old["unit"])


def _old_str(raw):
    q, unit = recipe_parse.old_parse(raw)
    return f"{sql_decimal(q)} {unit}"


def old_importer_value(q):
    """What the old importer stored for amount q: max(1, round_half_up(q))."""
    return max(Decimal(1), (q + Decimal("0.5")).to_integral_value(rounding="ROUND_FLOOR"))


def same_row(old, new):
    return old["to_taste"] == new["to_taste"] and old["unit"] == new["unit"] and old["quantity"] == new["quantity"]


def lines_signature(parsed):
    return (parsed["servings"], tuple(sorted(
        (l["item_id"] or 0, str(l["quantity"]), l["unit"], l["to_taste"]) for l in parsed["lines"])))


def sql_str(s):
    return "'" + (s or "").replace("\\", "\\\\").replace("'", "''") + "'"


class Plan:
    def __init__(self):
        self.sql, self.tsv = [], []
        self.counts = Counter()

    def tsv_row(self, recipe, url, action, cls="", item_id="", old=None, new=None, raw="", conf="", note="",
                item_name=""):
        o = old or {}
        n = new or {}
        self.tsv.append([recipe["id"], recipe["name"], url or "", action, cls, item_id, item_name,
                         _fmt(o.get("quantity")), o.get("unit", ""), _fmt_bool(o.get("to_taste")),
                         _fmt(n.get("quantity")), n.get("unit", ""), _fmt_bool(n.get("to_taste")),
                         raw, conf, note])


def _fmt(q):
    return "" if q is None else sql_decimal(q)


def _fmt_bool(b):
    return "" if b is None else ("1" if b else "0")


def plan_recipe(plan, state, recipe, url, parsed, include_other):
    rid = recipe["id"]
    stored = state.rows.get(rid, {})
    plan.sql.append("")
    plan.sql.append(f"-- recipe {rid} {recipe['name']!r} <- {url}")
    for note in parsed["notes"]:
        plan.tsv_row(recipe, url, "note", note=note)

    if parsed["servings_source"] == recipe_parse.DEFAULT:
        # no readable recipeYield and no bundle title: 2 would only be a guess, never written
        plan.counts["servings_held"] += 1
        plan.tsv_row(recipe, url, "review-servings", "servings",
                     note=f"recipeYield missing, stored {recipe['servings']} kept; " + "; ".join(parsed["notes"]))
    elif parsed["servings"] != recipe["servings"]:
        plan.sql.append(f"UPDATE recipe SET recipe_servings = {parsed['servings']} "
                        f"WHERE recipe_id = {rid} AND recipe_servings = {recipe['servings']};")
        plan.counts["servings"] += 1
        plan.tsv_row(recipe, url, "update-servings", "servings",
                     note=f"{recipe['servings']} -> {parsed['servings']} (from {parsed['servings_source']}); "
                     + "; ".join(parsed["notes"]))

    if not recipe.get("source_url"):
        plan.sql.append(f"UPDATE recipe SET recipe_source_url = {sql_str(url)} "
                        f"WHERE recipe_id = {rid} AND recipe_source_url IS NULL;")
        plan.counts["source_url"] += 1

    seen = set()
    for line in parsed["lines"]:
        iid = line["item_id"]
        name = state.item_names.get(iid, "")
        new = {"quantity": line["quantity"], "unit": line["unit"], "to_taste": line["to_taste"]}
        if iid is None:
            plan.counts["unmatched_line"] += 1
            plan.tsv_row(recipe, url, "review-unmatched", "", "", None, new, line["raw"], line["conf"], line["note"])
            continue
        seen.add(iid)
        old = stored.get(iid)
        if old is not None:
            if same_row(old, new):
                plan.counts["unchanged"] += 1
                continue
            cls = classify(old, new)
            if line["conf"] not in CONFIDENT:
                # an uncertain item match is no ground for "correcting" a stored row: it may be
                # an earlier wrong match ("g Bratapfelgewürz" -> Apfel), whatever the class
                plan.counts["held_unconfident"] += 1
                plan.tsv_row(recipe, url, "review-uncertain-match", cls, iid, old, new, line["raw"], line["conf"],
                             f"uncertain item match: not in the SQL; {line['note']}", name)
                continue
            if cls == UNIT and not unit_change_explained(old, line):
                plan.counts["held_unit"] += 1
                plan.tsv_row(recipe, url, "review-unit", cls, iid, old, new, line["raw"], line["conf"],
                             "unit differs and the stored row is not what the old parser made of this line "
                             f"(it would have stored {_old_str(line['raw'])}): maybe a different line or a "
                             "wrong item match, not in the SQL", name)
                continue
            if cls == OTHER and not include_other:
                plan.counts["held_other"] += 1
                plan.tsv_row(recipe, url, "review-held", cls, iid, old, new, line["raw"], line["conf"],
                             "differs from the page, not a known bug: not in the SQL", name)
                continue
            plan.sql.append(
                "UPDATE recipe_ingredient SET "
                f"recipe_ingredient_quantity_needed = {sql_decimal(new['quantity'])}, "
                f"recipe_ingredient_unit = {sql_str(new['unit'])}, "
                f"recipe_ingredient_to_taste = {1 if new['to_taste'] else 0} "
                f"WHERE recipe_ingredient_recipe_id = {rid} AND recipe_ingredient_item_id = {iid} "
                f"AND recipe_ingredient_quantity_needed <=> {sql_decimal(old['quantity'])} "
                f"AND BINARY recipe_ingredient_unit = {sql_str(old['unit'])} "
                f"AND recipe_ingredient_to_taste = {1 if old['to_taste'] else 0};"
                f"  -- {cls}: {_comment(line['raw'])}")
            plan.counts["update:" + cls] += 1
            plan.tsv_row(recipe, url, "update", cls, iid, old, new, line["raw"], line["conf"], line["note"], name)
            continue
        # a line the stored recipe does not have
        cls = INS_TO_TASTE if new["to_taste"] else INS_SPOON if new["unit"] in SPOONS else INS_OTHER
        if line["conf"] not in CONFIDENT:
            plan.counts["held_unconfident"] += 1
            plan.tsv_row(recipe, url, "review-uncertain-match", cls, iid, None, new, line["raw"], line["conf"],
                         line["note"], name)
            continue
        if cls == INS_OTHER and not include_other:
            plan.counts["held_other"] += 1
            plan.tsv_row(recipe, url, "review-held", cls, iid, None, new, line["raw"], line["conf"],
                         "new line that is neither a spoon nor to-taste: not in the SQL", name)
            continue
        plan.sql.append(
            "INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, "
            "recipe_ingredient_unit, recipe_ingredient_quantity_needed, recipe_ingredient_to_taste) "
            f"SELECT {rid}, {iid}, {sql_str(new['unit'])}, {sql_decimal(new['quantity'])}, {1 if new['to_taste'] else 0} "
            "FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM recipe_ingredient "
            f"WHERE recipe_ingredient_recipe_id = {rid} AND recipe_ingredient_item_id = {iid});"
            f"  -- {cls}: {_comment(line['raw'])}")
        plan.counts[cls] += 1
        plan.tsv_row(recipe, url, "insert", cls, iid, None, new, line["raw"], line["conf"], line["note"], name)

    for iid, old in stored.items():
        if iid not in seen:
            plan.counts["db_only"] += 1
            plan.tsv_row(recipe, url, "db-only", "", iid, old, None, note="stored row not on the page: left as is",
                         item_name=state.item_names.get(iid, ""))


def _comment(text):
    return re.sub(r"[\r\n]+", " ", text or "").replace("--", "-")


# ---------- the run ----------
def run(state, urls, selector, fetcher, catalog, include_other=False, limit=0, max_candidates=5, log=None):
    log = log or (lambda msg: print(msg, file=sys.stderr, flush=True))
    index = UrlIndex(urls)
    plan = Plan()
    selected = state.select(selector)
    if limit:
        selected = selected[:limit]
    plan.counts["selected"] = len(selected)
    pantry = lambda n: is_pantry(n, n)
    started = time.monotonic()
    for n, rid in enumerate(selected, 1):
        recipe = state.recipes[rid]
        if recipe.get("source_url"):
            cands, truncated = [recipe["source_url"]], False
        else:
            cands, truncated = index.candidates(recipe["name"], max_candidates)
        confirmed, statuses = [], Counter()
        for url in cands:
            status, rec = fetcher.get(url)
            statuses[status] += 1
            if status == "ok" and same_title(recipe_parse.cut_title(rec.get("name")), recipe["name"]):
                confirmed.append((url, recipe_parse.parse_recipe(rec, catalog, is_pantry=pantry)))
        plan.counts["page_404"] += statuses["404"]
        if not confirmed:
            why = "no URL slug matches the name" if not cands else \
                f"no page confirmed the name ({', '.join(f'{k} {v}' for k, v in sorted(statuses.items()))})"
            if statuses["error"] or statuses["uncached"]:
                plan.counts["incomplete"] += 1
                why += "; some pages could not be read yet, re-run to retry"
            plan.counts["unmatched"] += 1
            plan.tsv_row(recipe, "", "unmatched", note=why)
            continue
        if len({lines_signature(p) for _u, p in confirmed}) > 1:
            plan.counts["ambiguous"] += 1
            plan.tsv_row(recipe, " ".join(u for u, _p in confirmed), "ambiguous",
                         note=f"{len(confirmed)} pages with this name disagree; nothing changed")
            continue
        url, parsed = confirmed[0]
        if truncated:
            parsed["notes"].append(f"more than {max_candidates} URL candidates; first {max_candidates} checked")
        plan.counts["matched"] += 1
        plan_recipe(plan, state, recipe, url, parsed, include_other)
        if n % 50 == 0:
            log(f"  {n}/{len(selected)} recipes, {time.monotonic() - started:.0f}s, "
                f"fetched {fetcher.stats['fetched']}, cached {fetcher.stats['cache']}")
    return plan


def summary(plan, fetcher, selector, n_urls):
    c, f = plan.counts, fetcher.stats
    ins = c[INS_SPOON] + c[INS_TO_TASTE] + c[INS_OTHER]
    upd = sum(v for k, v in c.items() if k.startswith("update:"))
    return "\n".join([
        f"selector: {selector}    recipes selected: {c['selected']}    URLs loaded: {n_urls}",
        f"recipes: matched {c['matched']} | ambiguous {c['ambiguous']} | unmatched {c['unmatched']}"
        + (f" (of which {c['incomplete']} incomplete, re-run to retry)" if c["incomplete"] else ""),
        f"pages: fetched {f['fetched']} | from cache {f['cache']} | 404 {c['page_404']} | fetch errors {f['error']}"
        f" | robots.txt-disallowed {f['robots']} | not cached (offline) {f['uncached']}",
        f"rows updated: {upd} = ½-bug fixed {c['update:' + FRACTION]} | rounding fixed {c['update:' + ROUNDING]}"
        f" | unit changed {c['update:' + UNIT]} | now to-taste {c['update:' + TO_TASTE]}"
        f" | other {c['update:' + OTHER]}",
        f"lines inserted: {ins} = EL/TL {c[INS_SPOON]} | to-taste {c[INS_TO_TASTE]} | other {c[INS_OTHER]}",
        f"servings changed: {c['servings']} | servings kept, recipeYield missing {c['servings_held']}"
        f"    source URLs set: {c['source_url']}",
        f"held for review (TSV only): other changes {c['held_other']} | uncertain item match "
        f"{c['held_unconfident']} | unexplained unit change {c['held_unit']} | unmatched ingredient "
        f"{c['unmatched_line']}",
        f"rows unchanged: {c['unchanged']}    stored rows not on the page (left as is): {c['db_only']}",
    ])


TSV_HEADER = ["recipe_id", "recipe_name", "url", "action", "class", "item_id", "item_name",
              "old_quantity", "old_unit", "old_to_taste", "new_quantity", "new_unit", "new_to_taste",
              "page_line", "match", "note"]


def write_outputs(plan, out_dir, selector, stamp, summary_text):
    os.makedirs(out_dir, exist_ok=True)
    base = os.path.join(out_dir, f"repair_{selector}_{stamp}")
    header = [
        f"-- Recipe quantity repair, selector '{selector}', generated {stamp} by scripts/importer/repair_quantities.py",
        "-- Needs migrations/0005_recipe_quantities.sql. Review the .tsv next to this file first.",
        "-- Every statement is guarded on the current value, so a row that changed since the run is left alone",
        "-- and the file can be applied twice. There is no BEGIN/COMMIT on purpose: run it inside ONE transaction",
        "-- after a snapshot (scripts/importer/README.md), e.g.",
        "--   { echo 'START TRANSACTION;'; cat THIS_FILE.sql; echo 'COMMIT;'; } | \\",
        "--     docker compose exec -T db sh -c 'exec mariadb -u root -p\"$MARIADB_ROOT_PASSWORD\" \"$MARIADB_DATABASE\"'",
        "-- The client stops at the first error, so COMMIT is never reached and nothing is kept.",
        "--",
    ] + ["-- " + l for l in summary_text.splitlines()]
    with open(base + ".sql", "w", encoding="utf-8") as f:
        f.write("\n".join(header + plan.sql) + "\n")
    with open(base + ".tsv", "w", encoding="utf-8") as f:
        f.write("\t".join(TSV_HEADER) + "\n")
        for row in plan.tsv:
            f.write("\t".join(re.sub(r"[\t\r\n]+", " ", str(v)) for v in row) + "\n")
    with open(base + ".txt", "w", encoding="utf-8") as f:
        f.write(summary_text + "\n")
    return base


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--select", required=True, choices=["suspect", "all"],
                    help="suspect: recipes with non-g/ml rows of 5/25/75/333/667; all: every recipe")
    ap.add_argument("--urls", action="append", help="URL list file(s); default recipe_urls.txt + recipe_urls_tail.txt")
    ap.add_argument("--cache", default=DEFAULT_CACHE, help="page cache (makes runs resumable)")
    ap.add_argument("--out", default=DEFAULT_OUT, help="where the .sql/.tsv/.txt go")
    ap.add_argument("--delay", type=float, default=2.0, help="seconds between requests (default 2)")
    ap.add_argument("--limit", type=int, default=0, help="only the first N selected recipes")
    ap.add_argument("--max-candidates", type=int, default=5, help="pages checked per recipe at most")
    ap.add_argument("--offline", action="store_true", help="never touch the network; cached pages only")
    ap.add_argument("--include-other", action="store_true",
                    help="also write changes that are not a known bug, and new non-spoon/non-to-taste lines")
    ap.add_argument("--state", help="JSON snapshot instead of reading MariaDB (tests / offline review)")
    ap.add_argument("--stamp", default=None, help=argparse.SUPPRESS)
    args = ap.parse_args(argv)

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
    urls, missing = read_url_files(args.urls or DEFAULT_URLS)
    for p in missing:
        print(f"warning: URL list not found: {p}", file=sys.stderr)
    catalog = Catalog(state.items, load_overrides())
    fetcher = Fetcher(args.cache, delay=args.delay, offline=args.offline)
    plan = run(state, urls, args.select, fetcher, catalog, include_other=args.include_other,
               limit=args.limit, max_candidates=args.max_candidates)
    text = summary(plan, fetcher, args.select, len(urls))
    stamp = args.stamp or datetime.now().strftime("%Y%m%d-%H%M%S")
    base = write_outputs(plan, args.out, args.select, stamp, text)
    print(text)
    print(f"\nwrote {base}.sql / .tsv / .txt  (nothing was applied to the database)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
