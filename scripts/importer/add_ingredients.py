#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Add missing catalog items from a curated TSV.

Input is a tab-separated file you curate from ingredients_missing.tsv, with a
header and these columns (subcategory optional):

  english<TAB>deutsch<TAB>category<TAB>subcategory

For each row it inserts an inventory_item (quantity 0) if no item with that
german name already exists, and prints the new id. Because extract.py matches
recipe ingredients on the normalized GERMAN name, adding the item with its
canonical german label makes future imports resolve it automatically, no
overrides.json entry needed (use overrides only for aliases/qualifiers).

Usage:
  python add_ingredients.py --file ingredients_to_add.tsv          # dry run
  python add_ingredients.py --file ingredients_to_add.tsv --commit # actually insert
"""
import argparse, os, sqlite3, sys

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.abspath(os.path.join(HERE, "..", ".."))
DEFAULT_DB = os.path.join(PROJECT, "data", "kitchen_organizer.db")

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--file", required=True)
    ap.add_argument("--db", default=DEFAULT_DB)
    ap.add_argument("--commit", action="store_true", help="without this it's a dry run")
    args = ap.parse_args()

    con = sqlite3.connect(args.db)
    existing = {(d or "").strip().lower() for (d,) in
                con.execute("SELECT inventory_item_name_deutsch FROM inventory_item")}

    rows = []
    with open(args.file, encoding="utf-8") as f:
        header = f.readline()
        for ln, line in enumerate(f, 2):
            if not line.strip():
                continue
            parts = [p.strip() for p in line.rstrip("\n").split("\t")]
            if len(parts) < 3 or not parts[0] or not parts[1] or not parts[2]:
                print(f"  line {ln}: SKIP (need english<TAB>deutsch<TAB>category): {line.strip()!r}", file=sys.stderr)
                continue
            en, de, cat = parts[0], parts[1], parts[2]
            sub = parts[3] if len(parts) > 3 and parts[3] else None
            rows.append((en, de, cat, sub))

    added = skipped = 0
    for en, de, cat, sub in rows:
        if de.lower() in existing:
            print(f"  SKIP exists: {de}")
            skipped += 1
            continue
        if args.commit:
            cur = con.execute(
                "INSERT INTO inventory_item (inventory_item_name_english, inventory_item_name_deutsch, inventory_item_quantity, inventory_item_unit, inventory_item_category, inventory_item_subcategory) VALUES (?,?,0,'g',?,?)",
                (en, de, cat, sub))
            existing.add(de.lower())
            msg = f"  ADD #{cur.lastrowid}: {en} / {de}"
        else:
            msg = f"  WOULD ADD: {en} / {de}  [{cat}{'/' + sub if sub else ''}]"
        sys.stdout.buffer.write((msg + "\n").encode("utf-8"))
        added += 1
    if args.commit:
        con.commit()
    print(f"\n{'COMMITTED' if args.commit else 'DRY RUN'}: {added} to add, {skipped} already present")
    if not args.commit:
        print("re-run with --commit to insert")

if __name__ == "__main__":
    main()
