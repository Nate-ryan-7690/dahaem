#!/usr/bin/env python3
"""Load recipe draft .sql files (from extract.py) into MariaDB.

We do NOT replay the draft SQL verbatim, because the schema forces three fixes the
raw files don't make:
  * recipe_name is not unique -> we skip a draft whose name is already in the DB
    (or an earlier file this run), instead of duplicating it.
  * recipe_ingredient.item_id has an FK -> lines with id 0 (no catalog match) are
    dropped; their names still live in the draft comments for later cleanup.
  * recipe_ingredient PK is (item_id, recipe_id) -> a recipe can't list the same
    catalog id twice, so duplicates within a recipe are dropped, keeping the first
    (extract.py already merges same-unit duplicates; this is the safety net).

Quantities are exact decimals (DECIMAL(10,3)); a to-taste line has quantity NULL and
to_taste 1 (needs migrations/0005_recipe_quantities.sql). Drafts written before that
change (integer quantity, no to_taste field) still load, as quantity rows.

The database comes from DAHAEM_DB_URL / DAHAEM_DB_USER / DAHAEM_DB_PASSWORD (see db.py).
Each recipe loads in its own transaction; a failure rolls that recipe back and is logged,
so one bad draft never aborts the batch. DRY-RUN by default: nothing is written unless
--commit is given. This tool only INSERTs new recipes; it never deletes or updates.
"""
import argparse, glob, os, re, sys
from decimal import Decimal

HERE = os.path.dirname(os.path.abspath(__file__))
DRAFTS = os.path.normpath(os.path.join(HERE, "..", "recipes", "_drafts"))

# one ingredient row -> item_id, unit, qty (decimal or NULL), optional to_taste flag
ING = re.compile(r",\s*(\d+),\s*'((?:[^']|'')*)',\s*(\d+(?:\.\d+)?|NULL)(?:,\s*([01]))?\)")


def read_sql_str(s, i):
    """s[i] is a single quote; return (unescaped value, index past the closing quote)."""
    i += 1
    out = []
    while i < len(s):
        if s[i] == "'":
            if i + 1 < len(s) and s[i + 1] == "'":
                out.append("'"); i += 2; continue
            return "".join(out), i + 1
        out.append(s[i]); i += 1
    raise ValueError("unterminated SQL string")


def parse_draft(text):
    """Return (recipe_dict, ingredients[list of (id, unit, qty Decimal|None, to_taste bool)], steps[(n, text)])."""
    lines = text.split("\n")

    # --- recipe row: VALUES ('name', servings, cook, prep, cal, prot, carb, fat, fibre, sugar[, 'url'|NULL]);
    vi = next(i for i, l in enumerate(lines) if l.startswith("VALUES ("))
    vline = lines[vi]
    q = vline.index("'")
    name, j = read_sql_str(vline, q)
    tail = vline[j:]
    url = None
    uq = tail.find("'")
    if uq != -1:                             # trailing source URL string (new drafts)
        url, _ = read_sql_str(tail, uq)
        tail = tail[:uq]
    else:
        tail = tail[:tail.rindex(")")]       # drop "...);"
    nums = [x.strip() for x in tail.split(",")][1:]   # skip empty piece before first number
    nums = [x for x in nums if x]
    to_i = lambda x: None if x == "NULL" else int(x)
    recipe = dict(name=name, servings=to_i(nums[0]), cook=to_i(nums[1]), prep=to_i(nums[2]),
                  cal=to_i(nums[3]), prot=to_i(nums[4]), carb=to_i(nums[5]),
                  fat=to_i(nums[6]), fibre=to_i(nums[7]), sugar=to_i(nums[8]), url=url)

    # --- block boundaries ---
    ing_start = next((i for i, l in enumerate(lines) if l.startswith("INSERT INTO recipe_ingredient")), None)
    ins_start = next((i for i, l in enumerate(lines) if l.startswith("INSERT INTO recipe_instructions")), None)

    ingredients = []
    if ing_start is not None:
        end = ins_start if ins_start is not None else len(lines)
        for l in lines[ing_start + 1:end]:
            m = ING.search(l)
            if m:
                qty = None if m.group(3) == "NULL" else Decimal(m.group(3))
                to_taste = m.group(4) == "1" or qty is None
                ingredients.append((int(m.group(1)), m.group(2).replace("''", "'"), qty, to_taste))

    steps = []
    if ins_start is not None:
        end = next((i for i, l in enumerate(lines) if l.strip() == "COMMIT;"), len(lines))
        for l in lines[ins_start + 1:end]:
            m = re.match(r"\s*\(\(SELECT.*?\),\s*(\d+),\s*", l)
            if not m:
                continue
            step_no = int(m.group(1))
            q = l.index("'", m.end() - 1)
            body, _ = read_sql_str(l, q)
            steps.append((step_no, body))

    return recipe, ingredients, steps


def clean_ingredients(ings):
    """Drop id-0 lines and duplicate item ids (keep first). Return (kept, n_id0, n_dup)."""
    kept, seen, n_id0, n_dup = [], set(), 0, 0
    for row in ings:
        iid = row[0]
        if iid == 0:
            n_id0 += 1; continue
        if iid in seen:
            n_dup += 1; continue
        seen.add(iid); kept.append(row)
    return kept, n_id0, n_dup


def load(con, recipe, ings, steps):
    """INSERT one recipe with its ingredients and steps on con (caller commits). Returns the id."""
    with con.cursor() as cur:
        cur.execute(
            "INSERT INTO recipe (recipe_name, recipe_servings, recipe_cook_time, recipe_prep_time, "
            "recipe_calories, recipe_protein, recipe_carbs, recipe_fat, recipe_fibre, recipe_sugar, "
            "recipe_source_url) VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s)",
            (recipe["name"], recipe["servings"], recipe["cook"], recipe["prep"], recipe["cal"],
             recipe["prot"], recipe["carb"], recipe["fat"], recipe["fibre"], recipe["sugar"], recipe.get("url")))
        rid = cur.lastrowid
        if ings:
            cur.executemany(
                "INSERT INTO recipe_ingredient (recipe_ingredient_recipe_id, recipe_ingredient_item_id, "
                "recipe_ingredient_unit, recipe_ingredient_quantity_needed, recipe_ingredient_to_taste) "
                "VALUES (%s,%s,%s,%s,%s)",
                [(rid, iid, unit, qty, 1 if to_taste else 0) for iid, unit, qty, to_taste in ings])
        if steps:
            cur.executemany(
                "INSERT INTO recipe_instructions (recipe_instructions_recipe_id, recipe_instructions_step_number, "
                "recipe_instructions_instruction, recipe_instructions_notes, recipe_instructions_source) "
                "VALUES (%s,%s,%s,%s,%s)",
                [(rid, n, body, None, "HelloFresh") for n, body in steps])
    return rid


def existing_names(con):
    with con.cursor() as cur:
        cur.execute("SELECT recipe_name FROM recipe")
        return {n.strip().lower() for (n,) in cur.fetchall()}


def load_files(con, files, commit, require_reviewed=False):
    """Load the given draft files. Returns (stats, error samples)."""
    st = dict(files=len(files), loaded=0, skip_dup=0, skip_noname=0, skip_review=0, errors=0,
              id0=0, dupitem=0, zero_ing=0)
    seen_names = existing_names(con)
    err_samples = []
    for path in files:
        with open(path, encoding="utf-8") as f:
            text = f.read()
        if require_reviewed and ("REVIEW" in text or "TODO" in text):
            st["skip_review"] += 1; continue
        try:
            recipe, ings, steps = parse_draft(text)
        except Exception as e:
            st["errors"] += 1
            if len(err_samples) < 8: err_samples.append((os.path.basename(path), "parse: " + str(e)))
            continue
        key = (recipe["name"] or "").strip().lower()
        if not key:
            st["skip_noname"] += 1; continue
        if key in seen_names:
            st["skip_dup"] += 1; continue
        kept, n0, nd = clean_ingredients(ings)
        st["id0"] += n0; st["dupitem"] += nd
        if not kept:
            st["zero_ing"] += 1
        seen_names.add(key)
        if commit:
            try:
                load(con, recipe, kept, steps)
                con.commit()
                st["loaded"] += 1
            except Exception as e:
                con.rollback()
                st["errors"] += 1
                seen_names.discard(key)
                if len(err_samples) < 8: err_samples.append((os.path.basename(path), "load: " + str(e)))
        else:
            st["loaded"] += 1     # would load
    return st, err_samples


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dir", default=DRAFTS)
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--commit", action="store_true", help="write to the DB (default is dry-run)")
    args = ap.parse_args()

    import db
    files = sorted(glob.glob(os.path.join(args.dir, "*.sql")))
    if args.limit:
        files = files[:args.limit]
    print("database:        ", db.describe())
    con = db.connect()
    try:
        st, err_samples = load_files(con, files, args.commit)
    finally:
        con.close()

    print("mode:            ", "COMMIT" if args.commit else "DRY-RUN (no writes)")
    print("draft files:     ", st["files"])
    print("loaded" if args.commit else "would load", ":", st["loaded"])
    print("skipped duplicate name:", st["skip_dup"])
    print("skipped (no name):", st["skip_noname"])
    print("id-0 lines dropped:", st["id0"])
    print("duplicate-item lines dropped:", st["dupitem"])
    print("recipes with 0 ingredients after cleanup:", st["zero_ing"])
    print("errors:", st["errors"])
    for name, msg in err_samples:
        print("   !", name, "->", msg)


if __name__ == "__main__":
    main()
