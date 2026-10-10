#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Load REVIEWED recipe .sql files into MariaDB  (step 2 of the pipeline).

Same loader as load_recipes.py (each recipe in its own transaction, dedup by
recipe_name, id-0 and duplicate-item lines dropped), but refuses any file still
containing a "REVIEW" or "TODO" marker from extract.py, so you can't load
un-reviewed drafts. DRY-RUN by default; --commit writes. The database comes from
DAHAEM_DB_URL / DAHAEM_DB_USER / DAHAEM_DB_PASSWORD (see db.py).

Usage:
  # after reviewing drafts and moving the good ones out of _drafts/:
  python load.py --dir "../recipes"            # dry run: what would load
  python load.py --dir "../recipes" --commit   # actually insert
"""
import argparse, glob, os, sys

import load_recipes

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.abspath(os.path.join(HERE, "..", ".."))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default=os.path.join(PROJECT, "scripts", "recipes"))
    ap.add_argument("--commit", action="store_true", help="write to the DB (default is dry-run)")
    ap.add_argument("--force-review", action="store_true", help="also load files with REVIEW/TODO markers")
    args = ap.parse_args()

    import db
    files = sorted(glob.glob(os.path.join(args.dir, "*.sql")))
    print("database:", db.describe())
    con = db.connect()
    try:
        st, errs = load_recipes.load_files(con, files, args.commit, require_reviewed=not args.force_review)
    finally:
        con.close()
    print("mode:", "COMMIT" if args.commit else "DRY-RUN (no writes)")
    print(f"done: {st['loaded']} {'loaded' if args.commit else 'would load'}, {st['skip_dup']} already in DB, "
          f"{st['skip_review']} still have REVIEW/TODO markers, {st['errors']} errors")
    for name, msg in errs:
        print("   !", name, "->", msg)


if __name__ == "__main__":
    main()
