"""Catalogue batch 2b (migration 0015): the new items are in the seed and the migration alike, the spellings of the
rematch review map to them, and the item rule holds (a generic line maps to the plain item, a variety is its own).

Run from the repo root: python3 -m unittest discover -s scripts/importer/tests
"""
import json
import os
import re
import sys
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.join(HERE, "..", "..", "..")
sys.path.insert(0, os.path.dirname(HERE))

import recipe_parse  # noqa: E402
from extract import Catalog, normalize  # noqa: E402

SEED = os.path.join(ROOT, "seed", "inventory_catalog.sql")
MIGRATION = os.path.join(ROOT, "migrations", "0015_catalog_batch_2b.sql")
FIRST, LAST = 1833, 1899
ITEM = re.compile(r"INSERT IGNORE INTO inventory_item \([^)]*\) VALUES \((\d+), '((?:[^']|'')*)', '((?:[^']|'')*)', "
                  r"\d+, '([^']*)', '([^']*)', (NULL|'[^']*'), NULL\);")


def items_of(path):
    out = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            m = ITEM.search(line)
            if m:
                out[int(m[1])] = (m[2].replace("''", "'"), m[3].replace("''", "'"), m[4], m[5], m[6].strip("'"))
    return out


def overrides():
    with open(os.path.join(os.path.dirname(HERE), "overrides.json"), encoding="utf-8") as f:
        return json.load(f)


SEED_ITEMS = items_of(SEED)
CATALOG = Catalog([(i, en, de) for i, (en, de, *_r) in SEED_ITEMS.items()], overrides())


def match(raw):
    """Item id the importer's matcher gives for an ingredient line."""
    return CATALOG.match(recipe_parse.parse_ingredient(raw).name)


class SeedAndMigrationTest(unittest.TestCase):
    def test_the_new_ids_are_contiguous_and_the_same_in_seed_and_migration(self):
        migration = items_of(MIGRATION)
        self.assertEqual(list(range(FIRST, LAST + 1)), sorted(migration))
        for i in range(FIRST, LAST + 1):
            self.assertEqual(SEED_ITEMS[i], migration[i], f"item {i} differs between seed and migration")

    def test_no_name_twice(self):
        names = [SEED_ITEMS[i][1].lower() for i in range(FIRST, LAST + 1)]
        self.assertEqual(len(names), len(set(names)))
        every = [v[1].lower() for v in SEED_ITEMS.values()]
        for n in names:
            self.assertEqual(1, every.count(n), f"{n} exists twice in the catalogue")


class MappingsTest(unittest.TestCase):
    def test_nates_decisions_for_the_review_lines(self):
        for raw, want in (("2 Esslöffel Joghurt", 386), ("100 g Honig", 1070), ("2 Esslöffel Tahini-Paste", 913),
                          ("2 Esslöffel Kapern", 929), ("1 g Piment, Nelken Pfeffer, gemahlen", 1190),
                          ("Senf", 937), ("1 Teelöffel Gemüsebrühe", 780),
                          ("8 Stück Vegane Knusperstäbchen nach Fisch-Art (Inhalt: 240 g, 8 Stk.)", 1826),
                          ("250 ml afri cola®", 1698), ("1 dl Olivenöl", 1022), ("12 mg Balsamicoessig", 1053),
                          ("300 g gemischtes Hackfleisch mit Salz und Pfeffer", 271),
                          ("160 g Skipjack Thunfisch in Sonnenblumenöl", 849),
                          ("50 ml Honig-Senf-Dressing", 992), ("1 Stück Paulaner 2in1 alkoholfreies Bier", 1744)):
            iid, conf, _note = match(raw)
            self.assertEqual(want, iid, raw)
            self.assertIn(conf, recipe_parse.CONFIDENT, raw)

    def test_the_mapped_targets_have_the_expected_names(self):
        for iid, name in ((386, "naturjoghurt"), (913, "tahin"), (929, "kapern glas"), (1190, "piment"),
                          (780, "gemüsebrühe würfel"), (1698, "Cola"), (1022, "olivenöl"), (1053, "balsamico essig"),
                          (271, "gemischtes hackfleisch"), (849, "thunfisch in öl dose"),
                          (992, "honigsenf dressing"), (1744, "Biermix alkoholfrei"), (937, "senf mittelscharf")):
            self.assertEqual(name, SEED_ITEMS.get(iid, (None, None))[1], iid)

    def test_a_variety_is_its_own_item_and_the_generic_line_maps_to_the_plain_one(self):
        wanted = {"100 g Bio Sommerblüten Honig": "Bio-Sommerblütenhonig", "260 g Chipotle-Senf": "Chipotle-Senf",
                  "305 g Kräutersenf": "Kräutersenf", "140 g Biersenf": "Biersenf", "17 g Bier-Senf": "Biersenf",
                  "25 g Kirschkonfitüre": "Kirschkonfitüre",
                  "100 g Pastrami mit Pfefferrand (Inhalt: 100 g)": "Pastrami mit Pfefferrand",
                  "200 g Bunte Karotten": "Bunte Karotten", "2 Stück hartgekochte Eier": "gekochte Eier",
                  "30 g gehackter Knoblauch & Ingwer in Öl": "Knoblauch-Ingwer-Paste",
                  "1 Esslöffel Knoblauch-Zwiebel-Paste": "Knoblauch-Zwiebel-Paste",
                  "1 Esslöffel Knoblauch & Zwiebel gehackt in Rapsöl": "Knoblauch-Zwiebel-Paste",
                  "464 ml BBQ-Soße Honig-Senf": "BBQ-Soße Honig-Senf",
                  "400 ml BBQUE Sauce Honig-Senf": "BBQ-Soße Honig-Senf",
                  "100 g Ziegenfrischkäse-Crumble mit Honig": "Ziegenfrischkäse-Crumble mit Honig",
                  "100 g Ziegenfrischkäse-Crumble- mit Honig": "Ziegenfrischkäse-Crumble mit Honig",
                  "10 g eingelegter grüner Pfeffer": "grüner Pfeffer in Lake",
                  "12 ml Balsamicoessig, hell": "heller Balsamico", "10 ml Condimento-Bianco-Essig": "heller Balsamico",
                  "12 ml Balsamico-Weißweinessig": "heller Balsamico",
                  "300 g Ravioli mit Nduja und rotem Pfeffer": "Ravioli mit Nduja",
                  "150 g Tony's Chocolonely Schoko-Ostereier-Box": "Schoko-Ostereier",
                  "420 ml Kokos-Karotten-Suppe (Inhalt: 420 ml, entspricht 1 Portion)": "Karotten-Kokos-Suppe",
                  "1000 ml Karotten-Kokos-Suppe": "Karotten-Kokos-Suppe", "2 Stück Karotten Buns": "Karotten-Buns",
                  "3 Esslöffel Brühe": "Brühe"}
        for raw, name in wanted.items():
            iid, conf, _note = match(raw)
            self.assertEqual(name, SEED_ITEMS[iid][1], raw)
            self.assertIn(conf, recipe_parse.CONFIDENT, raw)

    def test_every_no_item_name_with_five_lines_has_its_item(self):
        wanted = {"Süße Soße, Asiatische Art": "Süße Soße Asiatische Art", "Spekulatius-Kekse": "Spekulatius-Kekse",
                  "Mini-Spekulatius": "Mini-Spekulatius", "Rescued Bruschetta Dip": "Bruschetta-Dip",
                  "Burger Sauce": "Burger-Sauce", "Kaminkäse": "Kaminkäse", "Gelbe Beete": "gelbe Bete",
                  "Torchietti": "Torchietti", "Rindfleisch": "Rindfleisch", "SalaRico®": "SalaRico",
                  "Olivia-Aufstrich": "Olivenaufstrich", "Frühstücksherzen": "Frühstücksherzen",
                  "Frühstücksherzen Mehrkorn": "Frühstücksherzen Mehrkorn", "Braune Soße": "Braune Soße",
                  "Crunchy Bites Smokey BBQ": "Crunchy Mix Smokey BBQ", "vegane Kräutercrème": "vegane Kräutercrème",
                  "Bauerngarten Bio-Käse": "Heumilchkäse mit Kräuterrinde"}
        for raw, name in wanted.items():
            iid, conf, _note = match(f"100 g {raw}")
            self.assertEqual(name, SEED_ITEMS[iid][1], raw)
            self.assertIn(conf, recipe_parse.CONFIDENT, raw)

    def test_the_two_spekulatius_items_are_separate(self):
        a = match("125 g Spekulatius-Kekse")[0]
        b = match("125 g Mini-Spekulatius")[0]
        self.assertNotEqual(a, b)


class NonFoodTest(unittest.TestCase):
    def test_ausstechformen_and_the_free_beer_are_not_ingredients(self):
        with open(os.path.join(os.path.dirname(HERE), "non_food.json"), encoding="utf-8") as f:
            exact = set(json.load(f)["exact"])
        for raw in ("3 Stück Ausstechformen", "275 ml Gratis Veltins Bier"):
            self.assertIn(normalize(recipe_parse.parse_ingredient(raw).name), exact, raw)


class UnitTypoTest(unittest.TestCase):
    def test_dl_is_100_ml_and_mg_is_the_ml_typo_of_the_source(self):
        line = recipe_parse.parse_ingredient("1 dl Olivenöl")
        self.assertEqual(("ml", "Olivenöl"), (line.unit, line.name))
        self.assertEqual(100, line.quantity)
        line = recipe_parse.parse_ingredient("2,5 dl Milch")
        self.assertEqual((250, "ml"), (line.quantity, line.unit))
        line = recipe_parse.parse_ingredient("12 mg Balsamicoessig")
        self.assertEqual((12, "ml", "Balsamicoessig"), (line.quantity, line.unit, line.name))


if __name__ == "__main__":
    unittest.main()
