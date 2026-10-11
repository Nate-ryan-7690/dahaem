#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Amounts written in the cooking steps of a HelloFresh recipe, for ingredient lines that have none.

A step says "In einer Pfanne 1 EL [1,5 EL | 2 EL] Öl* erhitzen": 1 EL is the amount for 2 people,
the bracket holds 3 | 4 people. The app scales from the 2-person value, so only the amount OUTSIDE
the bracket is read (a bracket-only "[y]" has no 2-person value and is ignored). Several mentions
of the same ingredient in one recipe are added up.

Pure functions, no network, no database. Matching is by the ingredient's German wording, in two
levels, so a human can be asked about the unsure ones:
  exact   the step names the ingredient as the line does ("Butter" / "Butter"; "(Oliven-)Öl" matches
          both "Öl" and "Olivenöl", which HelloFresh uses for the same bottle);
  family  same kind of ingredient, different wording ("Essig" for a line "Weißweinessig", "Senf" for
          "mittelscharfer Senf", "Zitronensaft" for "Zitrone"): never used without a human.
"""
import re
from decimal import Decimal
from fractions import Fraction

_FR = {"½": Fraction(1, 2), "¼": Fraction(1, 4), "¾": Fraction(3, 4), "⅓": Fraction(1, 3),
       "⅔": Fraction(2, 3), "⅛": Fraction(1, 8)}
_FRC = "".join(_FR)
NUM = (rf"(?:\d+\s?[{_FRC}]|[{_FRC}]|\d+\s+\d/\d|\d{{2,}}/\d|\d/\d|\d+[.,]\s?\d+|\d+)")
# measuring units (and countable pack words) a step may put behind an amount
UNIT = (r"(?:EL|El|ETL|TL|Tl|kg|ml|g|l|L|Msp\.?|Prisen?|Bund|Zehen?|Stück|Dosen?|Packung|Tropfen|"
        r"Handvoll|Päckchen|Becher|Scheiben?|Zweige?|Blätter|Blatt)")
_NOT_LETTER = r"(?![A-Za-zÄÖÜäöüß])"
MENTION = re.compile(
    rf"(?<![\w.,/{_FRC}])(?P<o>{NUM})(?P<range>\s*[–-]\s*{NUM})?\s*(?P<u>{UNIT})?{_NOT_LETTER}\s*"
    rf"(?:\[(?P<b>[^\]]*)\])?\s*(?:(?P<u2>{UNIT}){_NOT_LETTER})?\s*(?P<noun>[^\n,.;:()\[\]\d{_FRC}]{{0,60}})")
_UNIT_NAME = {"L": "l", "El": "EL", "ETL": "EL", "Tl": "TL", "Msp": "Messerspitze", "Msp.": "Messerspitze",
              "Prisen": "Prise", "Zehen": "Zehe", "Dosen": "Dose", "Scheibe": "Scheibe", "Blätter": "Blatt"}

FAMILIES = [  # (family, test on one lower-case word)
    ("öl", lambda w: w.endswith("öl")),
    ("essig", lambda w: "essig" in w or "balsamico" in w),
    ("zucker", lambda w: w.endswith("zucker")),
    ("mehl", lambda w: w.endswith("mehl") and w not in ("pankomehl", "semmelmehl")),
    ("buttermilch", lambda w: w == "buttermilch"),
    ("butter", lambda w: (w.startswith("butter") and w != "buttermilch") or w == "margarine"),
    ("milch", lambda w: w in ("milch", "vollmilch")),
    ("honig", lambda w: w == "honig"),
    ("salz", lambda w: w.endswith("salz")),
    ("pfeffer", lambda w: w.startswith("pfeffer") or w.endswith("pfeffer")),
    ("senf", lambda w: w.endswith("senf")),
    ("schmand", lambda w: w == "schmand"),
    ("eier", lambda w: w in ("ei", "eier", "eigelb", "eiweiß")),
    ("zitrone", lambda w: w.startswith("zitron")),
    ("limette", lambda w: w.startswith("limett")),
    ("sahne", lambda w: w.endswith("sahne")),
    ("brühe", lambda w: "brühe" in w or "bouillon" in w),
    ("wasser", lambda w: w == "wasser"),
]
# no step amount found: these become "to taste" (rule of 2026-10-11), everything else is reviewed
TO_TASTE_FAMILIES = {"salz", "pfeffer", "öl", "essig"}
# "1 Stück" makes no sense for these: "Stück Zucker" without an amount goes to review
UNCOUNTABLE_FAMILIES = {"butter", "zucker", "honig", "mehl", "milch", "senf", "brühe", "schmand",
                        "sahne", "wasser", "buttermilch"}
_FILLER = {"der", "die", "das", "dem", "den", "des", "etwas", "restliche", "restlichen", "restlicher",
           "restliches", "gesamten", "gesamte", "ein", "eine", "einen", "einem", "kleine", "kleinen",
           "großen", "große", "frisch", "frischen", "heiße", "heißen", "heißem", "heißes", "heißer",
           "weiche", "weicher", "weichen", "weiches", "weichem", "warme", "warmen", "warmem", "kalte",
           "kalten", "kaltem", "flüssige", "flüssigen", "geschmolzene", "geschmolzenen", "gehackte",
           "gehackten", "ca", "gestrichenen", "gestrichenem", "gehäuften", "etwa", "weitere",
           "weiteren", "zusätzliche", "zusätzlichen", "gutes", "gute"}
_CUT = re.compile(r"\s+(?:und|oder|sowie|mit|in|auf|zu|dazu|dann|dabei|über|für|aus)\s+|\s+(?:und|oder)$")


def _value(text):
    """'1,5' / '1.5' / '1, 5' / '1½' / '1 1/2' / '11/2' (= 1½) / '1/2' -> Fraction."""
    s = text.strip()
    m = re.fullmatch(rf"(\d+)\s?([{_FRC}])", s)
    if m:
        return Fraction(int(m[1])) + _FR[m[2]]
    if s in _FR:
        return _FR[s]
    m = re.fullmatch(r"(\d+)\s+(\d)/(\d)", s)
    if m:
        return int(m[1]) + Fraction(int(m[2]), int(m[3]))
    m = re.fullmatch(r"(\d)(\d)/(\d)", s)              # "11/2": 1 and 1/2 (no space), not 11/2
    if m and int(m[2]) < int(m[3]):
        return int(m[1]) + Fraction(int(m[2]), int(m[3]))
    m = re.fullmatch(r"(\d+)/(\d+)", s)
    if m:
        return Fraction(int(m[1]), int(m[2]))
    m = re.fullmatch(r"(\d+)[.,]\s?(\d+)", s)
    if m:
        return Fraction(int(m[1])) + Fraction(int(m[2]), 10 ** len(m[2]))
    return Fraction(int(s))


def _dec(f):
    d = Decimal(f.numerator) / Decimal(f.denominator)
    d = d.quantize(Decimal("0.001")) if d.as_tuple().exponent < -3 else d
    d = d.normalize()
    return d.quantize(Decimal(1)) if d == d.to_integral() else d


def _words(text):
    t = text.replace("(Oliven-)Öl", "Öl").replace("(Oliven-)öl", "Öl")
    return [w for w in re.split(r"[\s/]+", re.sub(r"[*„“\"()]", "", t).lower()) if w]


def family(word):
    for name, test in FAMILIES:
        if test(word):
            return name
    return None


def ingredient_key(name):
    """Ingredient line name -> (words without filler, family or None)."""
    words = [w for w in _words(name) if w not in _FILLER]
    fam = next((f for f in (family(w) for w in reversed(words)) if f), None)
    return words, fam


def mentions(step):
    """Every '<amount> [<3p> | <4p>] <noun>' of a step: dicts amount (Decimal, the 2-person value),
    unit (canonical or None for a bare count), noun. Time, °C, cm etc. have no unit in UNIT and are
    never read; a mention with a range ('1 – 2 EL') is skipped, a bracket-only value is no 2p amount."""
    out = []
    for m in MENTION.finditer(step or ""):
        unit = m["u"] or m["u2"]
        if m["range"] or (m["u"] and m["u2"]):
            continue
        if unit is None and not m["noun"][:1].isupper():
            continue
        try:
            amount = _dec(_value(m["o"]))
        except (ValueError, ZeroDivisionError):
            continue
        out.append({"amount": amount, "unit": _UNIT_NAME.get(unit, unit), "noun": m["noun"].strip(),
                    "raw": m.group(0).strip()})
    return out


def _match(words, fam, noun):
    """'exact' / 'family' / None: does this step noun name the ingredient?"""
    noun = _CUT.split(noun)[0]
    nwords = _words(noun)[:4]
    if fam:
        for i, w in enumerate(nwords):
            if family(w) == fam:
                phrase = [x for x in nwords[: i + 1] if x not in _FILLER]
                if phrase == words:
                    return "exact"
                if fam == "öl" and len(phrase) == 1 and len(words) == 1 and \
                        {phrase[0], words[0]} <= {"öl", "olivenöl"}:
                    return "exact"           # "(Oliven-)Öl", "Öl" and "Olivenöl" are one bottle
                return "family"
        return None
    if not words:
        return None
    last = words[-1].replace("-", "")
    stem = last[: max(4, len(last) - 2)]
    for w in nwords[:3]:
        w = w.replace("-", "")
        if w.startswith(stem):
            return "exact" if len(w) <= len(last) + 2 else "family"
    return None


def step_amount(name, steps):
    """What the steps say about ingredient `name` (a line without a number).
    -> (status, amount, unit, notes)
       'found'      amount/unit in the steps, several mentions summed
       'uncertain'  only a same-kind mention with different wording; never used
       'mixed'      exact mentions in different units (1 EL + 1 TL): never used
       'none'       nothing"""
    words, fam = ingredient_key(name)
    exact, loose = [], []
    for step in steps:
        for m in mentions(step):
            kind = _match(words, fam, m["noun"])
            if kind == "exact":
                exact.append(m)
            elif kind == "family":
                loose.append(m)
    if exact:
        units = {m["unit"] or "Stück" for m in exact}
        if len(units) > 1:
            return "mixed", None, None, ["; ".join(m["raw"] for m in exact)]
        total = sum((m["amount"] for m in exact), Decimal(0))
        note = "from the steps: " + " + ".join(f"{m['amount']} {m['unit'] or 'Stück'}" for m in exact)
        return "found", _dec(Fraction(total)), units.pop(), [note]
    if loose:
        return "uncertain", None, None, ["; ".join(m["raw"] for m in loose)]
    return "none", None, None, []
