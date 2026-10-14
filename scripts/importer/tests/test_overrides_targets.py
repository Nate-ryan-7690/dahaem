"""Every item an override points at must exist in the catalogue seed: a typo in an id would silently map recipe
lines to the wrong item (or to none). Run from the repo root: python3 -m unittest discover -s scripts/importer/tests
"""
import json
import os
import re
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
SEED = os.path.join(HERE, "..", "..", "..", "seed", "inventory_catalog.sql")

# Items that live databases have but the seed does not yet (docs/BACKLOG.md "Seed sync"); overrides may use them.
NOT_IN_SEED_YET = {129, 1034, 1179, 1216, 1218, 1431, 1432, 1433, 1505}


def seed_ids():
    ids = set()
    with open(SEED, encoding="utf-8") as f:
        for line in f:
            m = re.search(r"INSERT IGNORE INTO inventory_item \([^)]*\) VALUES \((\d+),", line)
            if m:
                ids.add(int(m.group(1)))
    return ids


class OverrideTargetsTest(unittest.TestCase):
    def test_every_override_points_at_a_seeded_item(self):
        with open(os.path.join(os.path.dirname(HERE), "overrides.json"), encoding="utf-8") as f:
            overrides = json.load(f)
        ids = seed_ids()
        self.assertGreater(len(ids), 1800)
        targets = list(overrides["exact"].items()) + [(r["contains"], r["item_id"]) for r in overrides["contains_rules"]]
        missing = sorted((k, v) for k, v in targets if v not in ids and v not in NOT_IN_SEED_YET)
        self.assertEqual([], missing, "overrides that point at an item the seed does not have")

    def test_the_new_lines_of_this_batch_are_mapped(self):
        with open(os.path.join(os.path.dirname(HERE), "overrides.json"), encoding="utf-8") as f:
            exact = json.load(f)["exact"]
        self.assertEqual(377, exact["pflanzliche margarine"])
        self.assertEqual(414, exact["hartkaese ital art dont use"])
        self.assertEqual(267, exact["grillhaxe"])
        self.assertEqual(263, exact["krustenbraten"])
        for key, item in (("pastrami mit pfefferrand", 1829), ("krakauer", 1830), ("gefluegelwuerste", 1831),
                          ("hackfleischzubereitung aus haehnchen", 1832), ("veganes filet nach lachs art", 1824),
                          ("planted veganes pulled in bbq marinade", 1825),
                          ("vegane knusperstaebchen nach fisch art", 1826), ("veganes schnitzel", 1827),
                          ("veganes gyros", 1828)):
            self.assertEqual(item, exact[key], key)


if __name__ == "__main__":
    unittest.main()
