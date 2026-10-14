"""Item matching by words, not by substrings: the wrong 'like' matches of the 2026-10-14 rematch review
(Cola in chocolate, Rum in Crumble / Rumpsteak, Aprikose in Aprikosennektar) must be gone, the useful ones
(Gurke / Gurken, Gurke / Snackgurke) must stay.

Run from the repo root:  python3 -m unittest discover -s scripts/importer/tests
"""
import os
import sys
import unittest

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from extract import Catalog, words_inside  # noqa: E402

ITEMS = [(1698, "cola", "Cola"), (1712, "rum", "Rum"), (1676, "eggs", "Eier"), (1686, "carrots", "Karotten"),
         (892, "apricots", "Aprikose"), (59, "mini cucumber", "snackgurke"), (1000, "tomatoes", "Tomaten"),
         (1070, "honey", "honig"), (2001, "potato salad", "kartoffel salat")]


class WordsInsideTest(unittest.TestCase):
    def inside(self, needle, hay):
        return words_inside(needle.split(), hay.split())

    def test_whole_words(self):
        self.assertTrue(self.inside("honig", "honig senf dressing"))
        self.assertTrue(self.inside("senf dressing", "honig senf dressing"))
        self.assertFalse(self.inside("honig senf", "senf honig dressing"), "same order, next to each other")

    def test_inflection_and_compound_heads(self):
        self.assertTrue(self.inside("tomate", "tomaten"))
        self.assertTrue(self.inside("gurke", "gurken"))
        self.assertTrue(self.inside("gurke", "snackgurke"), "the head of a German compound")
        self.assertFalse(self.inside("rum", "drum"), "too short for a compound head")

    def test_substrings_are_not_matches(self):
        self.assertFalse(self.inside("cola", "chocolate"))
        self.assertFalse(self.inside("rum", "crumble"))
        self.assertFalse(self.inside("rum", "rumpsteak"))
        self.assertFalse(self.inside("aprikose", "aprikosennektar"))
        self.assertFalse(self.inside("eier", "eierlikoer"), "start of a longer word")


class CatalogLikeTest(unittest.TestCase):
    def setUp(self):
        self.catalog = Catalog(ITEMS, {})

    def match(self, name):
        return self.catalog.match(name)[:2]

    def test_the_wrong_matches_are_gone(self):
        for name in ("Chocolate Chip Cookies Bio", "Mousse au Chocolat", "Apple Crumble", "Bio Rumpsteak",
                     "Aprikosennektar", "Schoko Osterhase"):
            self.assertEqual((None, "none"), self.match(name), name)

    def test_the_useful_ones_stay_and_are_still_only_a_like(self):
        self.assertEqual((59, "like"), self.match("Gurke"))
        self.assertEqual((1000, "like"), self.match("Tomate"))
        self.assertEqual((1070, "like"), self.match("Honig-Senf-Dressing"))
        self.assertEqual((2001, "like"), self.match("Kartoffel"))

    def test_exact_matches_and_overrides_are_untouched(self):
        self.assertEqual((1698, "exact"), self.match("Cola"))
        self.assertEqual((1676, "override"), Catalog(ITEMS, {"exact": {"ostereier": 1676}}).match("Ostereier")[:2])


if __name__ == "__main__":
    unittest.main()
