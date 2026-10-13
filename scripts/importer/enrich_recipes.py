#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Enrich the stored recipes with data from the recipe pages' JSON-LD that the importer never read:
description, image links, cuisine, rating and publish date. It only READS: the page cache that
repair_quantities.py filled (scripts/importer/_cache/pages/, one file per recipe URL:
{url, status, recipe}) and MariaDB (a read-only transaction, via db.py). It writes a guarded SQL
file and a report. Applying the SQL is a separate, manual step (see README.md in this folder),
after a snapshot, with migrations/0010_recipe_enrichment.sql applied first.

Matching: a cache page belongs to the recipe whose recipe_source_url is the page's URL. Nothing
else (no names, no slugs). Recipes without a URL, and URLs without a cached page, stay as they are.

Fields (page JSON-LD -> recipe column):
  description        -> recipe_description       (non-empty text)
  image              -> recipe_image_url         (an http(s) URL; LINKED, never copied)
  thumbnailUrl       -> recipe_thumbnail_url
  (image or thumb)   -> recipe_image_source      ('hellofresh', so the images can be replaced in bulk later)
  recipeCuisine      -> recipe_cuisine           (clean German name via config/cuisine_map.json;
                                                  a raw value that is not in the map stays NULL and is reported)
  aggregateRating    -> recipe_rating_avg (0 - 5, 2 decimals), recipe_rating_count (>= 0)
  datePublished      -> recipe_published_date    (the date part)
recipeCategory is ignored on purpose.

The SQL has ONE UPDATE per recipe: WHERE recipe_id = N AND at least one of its fields is still NULL,
and every field is set only while it is NULL (COALESCE); the image URLs and their source are one
group, set together only while all three are NULL. A re-run therefore changes nothing, and a
value set later (an own image, a corrected cuisine) is never overwritten. (MariaDB reads the SET
assignments left to right, so the image source comes first.) There is NO BEGIN/COMMIT on purpose: run the file inside ONE transaction.

Values are written as escaped literals into the file, validated first (numbers, dates, URLs are
re-typed, text is escaped); the database is only read, with a fixed SELECT.

The database comes from DAHAEM_DB_URL / DAHAEM_DB_USER / DAHAEM_DB_PASSWORD (db.py). For tests
and offline review, --state FILE.json replaces the database read with a JSON snapshot.

Usage:
  python enrich_recipes.py                      # reads the default cache, writes _enrich/enrich_<stamp>.sql/.txt
  python enrich_recipes.py --cache DIR --out DIR --cuisine-map FILE
"""
import argparse, json, os, re, sys
from collections import Counter
from datetime import date, datetime
from decimal import Decimal, InvalidOperation

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from repair_quantities import sql_str  # noqa: E402

DEFAULT_CACHE = os.path.join(HERE, "_cache", "pages")
DEFAULT_OUT = os.path.join(HERE, "_enrich")
DEFAULT_CUISINE_MAP = os.path.join(HERE, "..", "..", "config", "cuisine_map.json")
IMAGE_SOURCE = "hellofresh"

# page field -> (column suffix, state key); the order here is the order in the report
COLUMNS = ["recipe_description", "recipe_image_url", "recipe_thumbnail_url", "recipe_image_source",
           "recipe_cuisine", "recipe_rating_avg", "recipe_rating_count", "recipe_published_date"]
KEY = {c: c[len("recipe_"):] for c in COLUMNS}      # state/JSON key: 'description', 'image_url', ...


# ---------- inputs ----------
def load_cuisine_map(path):
    with open(path, encoding="utf-8") as f:
        mapping = json.load(f)["map"]
    return {k.strip(): v for k, v in mapping.items()}


def load_pages(cache_dir):
    """-> ({url: recipe_dict}, number of cache files that are not a usable page). Reads files only."""
    pages, unusable = {}, 0
    for name in sorted(os.listdir(cache_dir)):
        if not name.endswith(".json"):
            continue
        try:
            with open(os.path.join(cache_dir, name), encoding="utf-8") as f:
                cached = json.load(f)
        except (OSError, ValueError):
            unusable += 1
            continue
        if cached.get("status") == "ok" and isinstance(cached.get("recipe"), dict) and cached.get("url"):
            pages[normalize_url(cached["url"])] = cached["recipe"]
        else:
            unusable += 1
    return pages, unusable


def normalize_url(url):
    return (url or "").strip().rstrip("/")


class State:
    """What the database holds for the recipes: {recipe_id: {'source_url': ..., 'description': ..., ...}}."""

    def __init__(self, recipes):
        self.recipes = recipes

    @classmethod
    def from_json(cls, path):
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        return cls({r["id"]: r for r in data["recipes"]})

    @classmethod
    def from_db(cls, con):
        with con.cursor() as cur:
            cur.execute("SET SESSION TRANSACTION READ ONLY")
            cur.execute("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() "
                        "AND TABLE_NAME = 'recipe' AND COLUMN_NAME IN (" + ", ".join(["%s"] * len(COLUMNS)) + ")",
                        COLUMNS)
            if cur.fetchone()[0] != len(COLUMNS):
                raise SystemExit("apply migrations/0010_recipe_enrichment.sql first (snapshot before)")
            cur.execute("SELECT recipe_id, recipe_source_url, " + ", ".join(COLUMNS) + " FROM recipe")
            recipes = {}
            for row in cur.fetchall():
                rec = {"id": row[0], "source_url": row[1]}
                rec.update(zip((KEY[c] for c in COLUMNS), row[2:]))
                recipes[row[0]] = rec
        return cls(recipes)


# ---------- reading one page ----------
def clean_text(value):
    if not isinstance(value, str):
        return None
    text = value.replace("\x00", "").strip()
    return text or None


def clean_url(value):
    """A URL string, or the first / 'url' of a list / object (JSON-LD allows both), only http(s)."""
    if isinstance(value, list):
        value = value[0] if value else None
    if isinstance(value, dict):
        value = value.get("url")
    text = clean_text(value)
    return text if text and re.match(r"https?://\S+$", text) else None


def clean_rating(block):
    """-> (avg Decimal with 2 places, count int) or (None, None). Both are needed to show a rating."""
    if not isinstance(block, dict):
        return None, None
    try:
        avg = Decimal(str(block.get("ratingValue"))).quantize(Decimal("0.01"))
        count = int(str(block.get("ratingCount")).strip())
    except (InvalidOperation, ValueError):
        return None, None
    if not (Decimal(0) <= avg <= Decimal(5)) or count < 0:
        return None, None
    return avg, count


def clean_date(value):
    if not isinstance(value, str):
        return None
    try:
        return datetime.strptime(value.strip()[:10], "%Y-%m-%d").date()
    except ValueError:
        return None


def read_page(page, cuisine_map):
    """-> ({column: value}, raw cuisine or None when unmapped). Only fields the page really has."""
    values = {}
    text = clean_text(page.get("description"))
    if text:
        values["recipe_description"] = text
    image, thumb = clean_url(page.get("image")), clean_url(page.get("thumbnailUrl"))
    if image:
        values["recipe_image_url"] = image
    if thumb:
        values["recipe_thumbnail_url"] = thumb
    if image or thumb:
        values["recipe_image_source"] = IMAGE_SOURCE
    unmapped = None
    if "recipeCuisine" in page:
        raw = page["recipeCuisine"]
        key = "" if raw is None else str(raw).strip()
        if key in cuisine_map:
            if cuisine_map[key]:
                values["recipe_cuisine"] = cuisine_map[key]
        else:
            unmapped = key
    avg, count = clean_rating(page.get("aggregateRating"))
    if avg is not None:
        values["recipe_rating_avg"], values["recipe_rating_count"] = avg, count
    published = clean_date(page.get("datePublished"))
    if published:
        values["recipe_published_date"] = published
    return values, unmapped


# ---------- SQL ----------
def sql_value(column, value):
    if isinstance(value, Decimal):
        return format(value, "f")
    if isinstance(value, int):
        return str(int(value))
    if isinstance(value, date):
        return "'" + value.isoformat() + "'"
    return sql_str(value)


IMAGE_COLUMNS = ("recipe_image_url", "recipe_thumbnail_url")


def update_statement(recipe_id, values):
    """One guarded UPDATE for the fields in `values` (all of them currently NULL).

    The image columns are one group: they are filled only while the image source and both image
    columns are still empty, so an own or generated image is never mixed with a linked one. The
    source is assigned FIRST (it checks the group is still empty); the URLs then follow while the
    source reads 'hellofresh'."""
    sets = []
    if "recipe_image_source" in values:
        source = sql_value("recipe_image_source", values["recipe_image_source"])
        sets.append("recipe_image_source = IF(recipe_image_source IS NULL AND recipe_image_url IS NULL "
                    f"AND recipe_thumbnail_url IS NULL, {source}, recipe_image_source)")
        for col in IMAGE_COLUMNS:
            if col in values:
                sets.append(f"{col} = IF(recipe_image_source = {source} AND {col} IS NULL, "
                            f"{sql_value(col, values[col])}, {col})")
    for col in COLUMNS:
        if col in values and col != "recipe_image_source" and col not in IMAGE_COLUMNS:
            sets.append(f"{col} = COALESCE({col}, {sql_value(col, values[col])})")
    guard = " OR ".join(f"{col} IS NULL" for col in COLUMNS if col in values)
    return f"UPDATE recipe SET {', '.join(sets)} WHERE recipe_id = {int(recipe_id)} AND ({guard});"


# ---------- the run ----------
def run(state, pages, cuisine_map):
    stats = Counter()
    filled, unmapped, sql = Counter(), Counter(), []
    used = set()
    for rid in sorted(state.recipes):
        recipe = state.recipes[rid]
        url = normalize_url(recipe.get("source_url"))
        if not url:
            stats["no_url"] += 1
            continue
        page = pages.get(url)
        if page is None:
            stats["url_without_page"] += 1
            continue
        stats["matched"] += 1
        used.add(url)
        values, raw_unmapped = read_page(page, cuisine_map)
        if raw_unmapped is not None:
            unmapped[raw_unmapped] += 1
        # only what is still NULL in the database; the image columns and their source are one group
        todo = {c: v for c, v in values.items() if recipe.get(KEY[c]) is None}
        if any(recipe.get(KEY[c]) is not None for c in IMAGE_COLUMNS + ("recipe_image_source",)):
            for col in IMAGE_COLUMNS + ("recipe_image_source",):
                todo.pop(col, None)
        if not todo:
            stats["already_complete" if values else "nothing_to_fill"] += 1
            continue
        for col in todo:
            filled[col] += 1
        stats["updated"] += 1
        sql.append(update_statement(rid, todo))
    stats["pages_without_recipe"] = len(set(pages) - used)
    return sql, stats, filled, unmapped


def summary(stats, filled, unmapped, n_pages, unusable):
    lines = [
        f"cache pages with a recipe: {n_pages} (cache files without a usable page: {unusable})",
        f"recipes matched by recipe_source_url: {stats['matched']}",
        f"recipes unmatched: {stats['no_url'] + stats['url_without_page']}"
        f" (no URL: {stats['no_url']}, URL without a cached page: {stats['url_without_page']})",
        f"recipes with an UPDATE: {stats['updated']}"
        f" (nothing new to fill: {stats['already_complete'] + stats['nothing_to_fill']})",
        "fields filled per column:",
    ]
    lines += [f"  {col:24} {filled[col]}" for col in COLUMNS]
    lines.append(f"unmapped cuisine values (left NULL, add them to config/cuisine_map.json): {sum(unmapped.values())} recipes")
    lines += [f"  {n:6}  {raw!r}" for raw, n in sorted(unmapped.items(), key=lambda kv: (-kv[1], kv[0]))]
    lines.append(f"cached pages without a DB recipe (not imported; count only): {stats['pages_without_recipe']}")
    return "\n".join(lines)


def write_outputs(sql, out_dir, stamp, summary_text):
    os.makedirs(out_dir, exist_ok=True)
    base = os.path.join(out_dir, f"enrich_{stamp}")
    header = [
        f"-- Recipe enrichment, generated {stamp} by scripts/importer/enrich_recipes.py",
        "-- Needs migrations/0010_recipe_enrichment.sql. Review the report (.txt) next to this file first.",
        "-- One UPDATE per recipe, guarded on the fields still being NULL: a re-run changes nothing and a",
        "-- value set later (e.g. an own image) is never overwritten. There is no BEGIN/COMMIT on purpose:",
        "-- run it inside ONE transaction after a snapshot (scripts/importer/README.md), e.g.",
        "--   { echo 'START TRANSACTION;'; cat THIS_FILE.sql; echo 'COMMIT;'; } | \\",
        "--     docker compose exec -T db sh -c 'exec mariadb -u root -p\"$MARIADB_ROOT_PASSWORD\" \"$MARIADB_DATABASE\"'",
        "-- The client stops at the first error, so COMMIT is never reached and nothing is kept.",
        "--",
    ] + ["-- " + l for l in summary_text.splitlines()]
    with open(base + ".sql", "w", encoding="utf-8") as f:
        f.write("\n".join(header + sql) + "\n")
    with open(base + ".txt", "w", encoding="utf-8") as f:
        f.write(summary_text + "\n")
    return base


def main(argv=None):
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--cache", default=DEFAULT_CACHE, help="page cache filled by repair_quantities.py")
    ap.add_argument("--out", default=DEFAULT_OUT, help="where the .sql/.txt go")
    ap.add_argument("--cuisine-map", default=DEFAULT_CUISINE_MAP, help="raw cuisine -> German name (JSON)")
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
    if not os.path.isdir(args.cache):
        raise SystemExit(f"page cache not found: {args.cache} (run repair_quantities.py first)")
    pages, unusable = load_pages(args.cache)
    sql, stats, filled, unmapped = run(state, pages, load_cuisine_map(args.cuisine_map))
    text = summary(stats, filled, unmapped, len(pages), unusable)
    stamp = args.stamp or datetime.now().strftime("%Y%m%d-%H%M%S")
    base = write_outputs(sql, args.out, stamp, text)
    print(text)
    print(f"\nwrote {base}.sql / .txt  (nothing was applied to the database)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
