#!/usr/bin/env python3
"""Build the DAHÄM demo SQLite database from the repo's schema and seed SQL files.

Loads, in order, with foreign keys enforced:
  1. schema.sql                                  - the 24-table schema
  2. seed/inventory_catalog.sql                   - the generic inventory_item catalog
  3. scripts/recipes/samples/sample_recipes.sql   - 15 sample recipes + ingredients + instructions
  4. seed/demo_data.sql                           - fabricated demo data (stores, meal plan, tags, shopping list)

Usage:
    python setup_db.py [--db PATH] [--force]
"""
import argparse
import sqlite3
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent

# Must match de.dahaem.shared.Database.DEFAULT_DB_PATH.
DEFAULT_DB_PATH = HERE / "data" / "kitchen_organizer.db"

SCRIPTS = [
    HERE / "schema.sql",
    HERE / "seed" / "inventory_catalog.sql",
    HERE / "scripts" / "recipes" / "samples" / "sample_recipes.sql",
    HERE / "seed" / "demo_data.sql",
]

SUMMARY_TABLES = [
    "inventory_item",
    "recipe",
    "recipe_ingredient",
    "store",
    "brand",
    "store_item",
    "store_discount",
    "meal_plan",
    "meal_plan_recipe",
    "tag",
    "recipe_tag",
    "shopping_list",
    "shopping_list_item",
]

ORPHAN_CHECK_SQL = """
    SELECT COUNT(*)
    FROM recipe_ingredient ri
    LEFT JOIN inventory_item i ON i.inventory_item_id = ri.recipe_ingredient_item_id
    LEFT JOIN recipe r ON r.recipe_id = ri.recipe_ingredient_recipe_id
    WHERE i.inventory_item_id IS NULL OR r.recipe_id IS NULL
"""


class SetupError(Exception):
    pass


def build_database(db_path: Path, force: bool = False) -> dict:
    """Create db_path fresh and load all SCRIPTS into it. Returns a summary dict."""
    db_path = Path(db_path)

    if db_path.exists():
        if not force:
            raise SetupError(f"{db_path} already exists. Use --force to overwrite it.")
        db_path.unlink()
        for sidecar in (db_path.with_name(db_path.name + "-wal"), db_path.with_name(db_path.name + "-shm")):
            if sidecar.exists():
                sidecar.unlink()

    missing = [p for p in SCRIPTS if not p.exists()]
    if missing:
        joined = "\n".join(f"  {p}" for p in missing)
        raise SetupError(f"missing required SQL file(s):\n{joined}")

    db_path.parent.mkdir(parents=True, exist_ok=True)

    conn = sqlite3.connect(str(db_path))
    try:
        conn.execute("PRAGMA foreign_keys = ON")
        loaded = []
        for script_path in SCRIPTS:
            sql = script_path.read_text(encoding="utf-8")
            conn.executescript(sql)
            loaded.append(str(script_path.relative_to(HERE)))

        fk_violations = conn.execute("PRAGMA foreign_key_check").fetchall()
        orphans = conn.execute(ORPHAN_CHECK_SQL).fetchone()[0]

        row_counts = {}
        for table in SUMMARY_TABLES:
            row_counts[table] = conn.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]
    finally:
        conn.close()

    return {
        "db_path": db_path,
        "loaded": loaded,
        "row_counts": row_counts,
        "orphans": orphans,
        "fk_violations": fk_violations,
    }


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--db", default=str(DEFAULT_DB_PATH),
                         help="path to the SQLite database to create (default: %(default)s)")
    parser.add_argument("--force", action="store_true", help="overwrite the database if it already exists")
    return parser.parse_args(argv)


def main(argv=None) -> int:
    args = parse_args(argv)
    try:
        summary = build_database(Path(args.db), force=args.force)
    except SetupError as e:
        print(f"error: {e}", file=sys.stderr)
        return 1

    for script in summary["loaded"]:
        print(f"loaded {script}")

    print()
    print(f"Database created at {summary['db_path']}")
    print("Row counts:")
    for table, count in summary["row_counts"].items():
        print(f"  {table}: {count}")

    if summary["fk_violations"]:
        print(f"error: {len(summary['fk_violations'])} foreign key violation(s) found", file=sys.stderr)
        return 1
    print("Foreign key check: OK")

    print(f"Orphan recipe_ingredient rows: {summary['orphans']}")
    if summary["orphans"] != 0:
        print("error: found orphan recipe_ingredient rows", file=sys.stderr)
        return 1

    return 0


if __name__ == "__main__":
    sys.exit(main())
