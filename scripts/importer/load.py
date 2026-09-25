#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Load reviewed recipe .sql files into the DB  (step 2 of the pipeline).

Runs every *.sql in the target dir that isn't already in the DB (dedup by
recipe_name), inside its own transaction. Refuses any file still containing a
"REVIEW" or "TODO" marker from extract.py, so you can't load un-reviewed drafts.

Usage:
  # after reviewing drafts and moving the good ones out of _drafts/:
  python load.py --dir "../recipes"           # loads reviewed files
  python load.py --dir "../recipes/_drafts" --force-review   # (not recommended)
"""
import argparse, glob, os, re, sqlite3, sys

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.abspath(os.path.join(HERE, "..", ".."))
DEFAULT_DB = os.path.join(PROJECT, "data", "kitchen_organizer.db")

def recipe_name(sql):
    m = re.search(r"WHERE recipe_name='((?:[^']|'')*)'", sql)
    return m.group(1).replace("''", "'") if m else None

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default=os.path.join(PROJECT, "scripts", "recipes"))
    ap.add_argument("--db", default=DEFAULT_DB)
    ap.add_argument("--force-review", action="store_true")
    args = ap.parse_args()

    con = sqlite3.connect(args.db)
    existing = {r[0] for r in con.execute("SELECT recipe_name FROM recipe")}
    files = sorted(f for f in glob.glob(os.path.join(args.dir, "*.sql")))
    loaded = skipped = 0
    for f in files:
        sql = open(f, encoding="utf-8").read()
        name = recipe_name(sql)
        base = os.path.basename(f)
        if name is None:
            print(f"SKIP {base}: no recipe_name found"); skipped += 1; continue
        if name in existing:
            print(f"SKIP {base}: '{name}' already in DB"); skipped += 1; continue
        if not args.force_review and ("REVIEW" in sql or "TODO" in sql):
            print(f"SKIP {base}: still has REVIEW/TODO markers - finish review first"); skipped += 1; continue
        try:
            con.executescript(sql); con.commit()
            existing.add(name); loaded += 1
            print(f"LOAD {base}: '{name}'")
        except Exception as e:
            con.rollback()
            print(f"FAIL {base}: {e}"); skipped += 1
    print(f"\ndone: {loaded} loaded, {skipped} skipped")
    print("recipes now:", con.execute("SELECT COUNT(*) FROM recipe").fetchone()[0])

if __name__ == "__main__":
    main()
