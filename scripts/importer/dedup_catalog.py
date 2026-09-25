#!/usr/bin/env python3
"""Catalog hygiene: merge exact-duplicate inventory_item rows.

Some ingredients exist twice in inventory_item (e.g. schnittlauch #47 and #129),
which caused exact-multi matches. This finds rows with the identical deutsch name,
keeps the lowest id as canonical, repoints every reference to it, then deletes the
duplicates. Only three tables reference inventory_item: recipe_ingredient,
store_product, store_item.

recipe_ingredient has a composite PK (item_id, recipe_id), so repointing a duplicate
onto the canonical can collide with a row the recipe already has; those colliding
duplicate rows are deleted rather than updated.

Dry-run by default; pass --commit to apply.
"""
import argparse, os, sqlite3, sys

HERE = os.path.dirname(os.path.abspath(__file__))
DB = os.path.normpath(os.path.join(HERE, "..", "..", "data", "kitchen_organizer.db"))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--db", default=DB)
    ap.add_argument("--commit", action="store_true", help="apply changes (default is dry-run)")
    args = ap.parse_args()

    con = sqlite3.connect(args.db)
    con.execute("PRAGMA foreign_keys=ON")

    groups = con.execute(
        "SELECT lower(trim(inventory_item_name_deutsch)) n, group_concat(inventory_item_id) ids "
        "FROM inventory_item GROUP BY n HAVING count(*) > 1 ORDER BY n").fetchall()

    total_del = 0
    repoint = {"recipe_ingredient": 0, "store_product": 0, "store_item": 0}
    ri_collisions = 0

    cur = con.cursor()
    for name, ids in groups:
        parts = sorted(int(x) for x in ids.split(","))
        canonical, dupes = parts[0], parts[1:]
        print(f"  {name:28} keep #{canonical}, merge {dupes}")
        for d in dupes:
            # recipe_ingredient: drop rows that would collide with an existing (canonical, recipe) pair
            coll = cur.execute(
                "SELECT count(*) FROM recipe_ingredient WHERE recipe_ingredient_item_id = ? "
                "AND recipe_ingredient_recipe_id IN (SELECT recipe_ingredient_recipe_id FROM recipe_ingredient "
                "WHERE recipe_ingredient_item_id = ?)", (d, canonical)).fetchone()[0]
            ri_collisions += coll
            if args.commit:
                cur.execute(
                    "DELETE FROM recipe_ingredient WHERE recipe_ingredient_item_id = ? "
                    "AND recipe_ingredient_recipe_id IN (SELECT recipe_ingredient_recipe_id FROM recipe_ingredient "
                    "WHERE recipe_ingredient_item_id = ?)", (d, canonical))
                repoint["recipe_ingredient"] += cur.execute(
                    "UPDATE recipe_ingredient SET recipe_ingredient_item_id = ? WHERE recipe_ingredient_item_id = ?",
                    (canonical, d)).rowcount
                repoint["store_product"] += cur.execute(
                    "UPDATE store_product SET store_product_inventory_item_id = ? WHERE store_product_inventory_item_id = ?",
                    (canonical, d)).rowcount
                repoint["store_item"] += cur.execute(
                    "UPDATE store_item SET store_item_item_id = ? WHERE store_item_item_id = ?",
                    (canonical, d)).rowcount
                cur.execute("DELETE FROM inventory_item WHERE inventory_item_id = ?", (d,))
            total_del += 1

    if args.commit:
        con.commit()
        print("\nFK check:", con.execute("PRAGMA foreign_key_check").fetchall()[:3])
    print(f"\nmode: {'COMMIT' if args.commit else 'DRY-RUN (no changes)'}")
    print(f"duplicate groups: {len(groups)}   rows deleted: {total_del}")
    if args.commit:
        print("repointed references:", repoint)
    print("recipe_ingredient collision rows (deleted, not repointed):", ri_collisions)
    con.close()


if __name__ == "__main__":
    main()
