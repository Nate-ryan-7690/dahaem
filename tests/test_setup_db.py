#!/usr/bin/env python3
"""Smoke test: build the demo database into a temp file and check it's sound.

Run with: python3 -m unittest tests.test_setup_db -v
(or: python3 tests/test_setup_db.py)
"""
import sqlite3
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

import setup_db


class SetupDbSmokeTest(unittest.TestCase):
    def setUp(self):
        tmp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(tmp_dir.cleanup)
        self.db_path = Path(tmp_dir.name) / "smoke.db"

    def test_full_load_has_no_orphans_and_populates_every_table(self):
        summary = setup_db.build_database(self.db_path, force=False)

        self.assertEqual(summary["orphans"], 0, "recipe_ingredient must not reference a missing recipe or item")
        self.assertEqual(summary["fk_violations"], [], "PRAGMA foreign_key_check must report no violations")

        counts = summary["row_counts"]
        self.assertGreater(counts["inventory_item"], 0)
        self.assertEqual(counts["recipe"], 15)
        self.assertGreater(counts["recipe_ingredient"], 0)
        for table in ("store", "brand", "store_item", "store_discount",
                      "meal_plan", "meal_plan_recipe", "tag", "recipe_tag",
                      "shopping_list", "shopping_list_item"):
            self.assertGreater(counts[table], 0, f"{table} should have demo rows")

    def test_refuses_to_overwrite_without_force(self):
        setup_db.build_database(self.db_path, force=False)
        with self.assertRaises(setup_db.SetupError):
            setup_db.build_database(self.db_path, force=False)

    def test_force_rebuilds_cleanly(self):
        setup_db.build_database(self.db_path, force=False)
        summary = setup_db.build_database(self.db_path, force=True)
        self.assertEqual(summary["orphans"], 0)

    def test_every_recipe_ingredient_resolves_against_the_live_schema(self):
        setup_db.build_database(self.db_path, force=False)
        conn = sqlite3.connect(str(self.db_path))
        try:
            conn.execute("PRAGMA foreign_keys = ON")
            row = conn.execute("""
                SELECT COUNT(*)
                FROM recipe_ingredient ri
                JOIN inventory_item i ON i.inventory_item_id = ri.recipe_ingredient_item_id
                JOIN recipe r ON r.recipe_id = ri.recipe_ingredient_recipe_id
            """).fetchone()
            total = conn.execute("SELECT COUNT(*) FROM recipe_ingredient").fetchone()
            self.assertEqual(row[0], total[0])
        finally:
            conn.close()


if __name__ == "__main__":
    unittest.main()
