#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Pure parsing for HelloFresh recipe JSON-LD: amounts, units, "nach Geschmack" lines,
servings, titles. No network, no database: extract.py and repair_quantities.py both
build on this, and the unit tests drive it with saved JSON-LD fixtures.

Amounts are exact decimals (decimal.Decimal), never rounded and never forced to >= 1:
"½ Stück Zitrone" is 0.5 Stück. A line without an amount is "to taste": the recipe
needs the item, but names no quantity (quantity None, to_taste True).
"""
import re
from decimal import Decimal

import step_amounts

# ---------- amounts ----------
# Vulgar-fraction characters HelloFresh uses (and a few more for safety). Values are what the
# DECIMAL(10,3) column can hold: thirds are 0.333 / 0.667.
FRACTIONS = {
    "½": Decimal("0.5"), "¼": Decimal("0.25"), "¾": Decimal("0.75"),
    "⅓": Decimal("0.333"), "⅔": Decimal("0.667"),
    "⅛": Decimal("0.125"), "⅜": Decimal("0.375"), "⅝": Decimal("0.625"), "⅞": Decimal("0.875"),
    "⅕": Decimal("0.2"), "⅙": Decimal("0.167"),
}
_FRAC_CHARS = "".join(FRACTIONS)
_NUM = r"\d+(?:[.,]\d+)?"

# Tried in this order at one position: "1 1/2", "1/2", "1½" / "1 ½" / "½", "0,5" / "2.5" / "2".
AMOUNT_RE = re.compile(
    r"(?P<mixed>(?P<mw>\d+)\s+(?P<mn>\d+)\s*/\s*(?P<md>\d+))"
    r"|(?P<slash>(?P<sn>\d+)\s*/\s*(?P<sd>\d+))"
    rf"|(?P<uni>(?P<uw>\d+)?\s*(?P<uc>[{_FRAC_CHARS}]))"
    rf"|(?P<dec>{_NUM})"
)
_THOUSANDS = re.compile(r"[1-9]\d{0,2}(?:\.\d{3})+")   # German "1.500" = 1500


def _decimal(text):
    """'0,5' -> 0.5, '2.5' -> 2.5, '1.500' -> 1500 (German thousands separator)."""
    if _THOUSANDS.fullmatch(text):
        return Decimal(text.replace(".", ""))
    return Decimal(text.replace(",", "."))


def _amount_of(m):
    if m.group("mixed"):
        return Decimal(m.group("mw")) + Decimal(m.group("mn")) / Decimal(m.group("md"))
    if m.group("slash"):
        return Decimal(m.group("sn")) / Decimal(m.group("sd"))
    if m.group("uni"):
        whole = Decimal(m.group("uw")) if m.group("uw") else Decimal(0)
        return whole + FRACTIONS[m.group("uc")]
    return _decimal(m.group("dec"))


def _clean(d):
    """Exact value, at most 3 decimals (the column's scale), no trailing zeros: 0.500 -> 0.5."""
    d = d.quantize(Decimal("0.001")) if d.as_tuple().exponent < -3 else d
    d = d.normalize()
    return d.quantize(Decimal(1)) if d == d.to_integral() else d


def num(s):
    """First amount anywhere in s as a Decimal, or None. '1½ Stück' -> 1.5, '690 kcal' -> 690."""
    m = AMOUNT_RE.search(s or "")
    return _clean(_amount_of(m)) if m else None


def parse_amount(s):
    """Amount at the START of s -> (Decimal or None, rest). A range '1-2' keeps the first value."""
    s = s.strip()
    m = AMOUNT_RE.match(s)
    if not m:
        return None, s
    rest = s[m.end():]
    rest = re.sub(rf"^\s*[-–]\s*(?:{_NUM}|[{_FRAC_CHARS}])", "", rest)   # drop the upper end of a range
    return _clean(_amount_of(m)), rest.strip()


# ---------- units ----------
# Canonical units, looked up case-insensitively. Spoons stay spoons (no ml conversion); kg/l
# are kept as written (the importer never converted them).
UNIT_ALIASES = {
    "g": "g", "kg": "kg", "ml": "ml", "l": "l",
    "el": "EL", "esslöffel": "EL", "eßlöffel": "EL",
    "tl": "TL", "teelöffel": "TL",
    "prise": "Prise", "prisen": "Prise",
    "messerspitze": "Messerspitze", "messerspitzen": "Messerspitze", "msp": "Messerspitze",
    "zehe": "Zehe", "zehen": "Zehe",
    "bund": "Bund",
    "scheibe": "Scheibe", "scheiben": "Scheiben",
    "dose": "Dose", "dosen": "Dose",
    "packung": "Packung", "packungen": "Packung",
    "stück": "Stück",
}
UNITS = set(UNIT_ALIASES.values())
DEFAULT_UNIT = "Stück"
TO_TASTE_UNIT = ""      # unit stored on a to_taste row (it has no amount, so no unit either)


def canonical_unit(token):
    return UNIT_ALIASES.get((token or "").strip().rstrip(".").lower())


def split_unit(text):
    """'Esslöffel Olivenöl' -> ('EL', 'Olivenöl'); 'Zitrone' -> (None, 'Zitrone')."""
    parts = text.split(None, 1)
    if parts:
        unit = canonical_unit(parts[0])
        if unit:
            return unit, (parts[1] if len(parts) == 2 else "").strip()
    return None, text.strip()


# ---------- to taste ----------
_TO_TASTE = [
    re.compile(r"^nach\s+geschmack\s*:?\s+(?P<name>.+)$", re.I),
    re.compile(r"^(?P<name>.+?)\s*,?\s*\(?nach\s+geschmack\)?$", re.I),
    re.compile(r"^etwas\s+(?P<name>.+)$", re.I),
]


_NACH_GESCHMACK = re.compile(r"nach\s+geschmack|^\s*etwas\s", re.I)   # "to taste" whatever the item


class Line:
    """One parsed ingredient line. quantity is a Decimal, or None when to_taste is True."""
    __slots__ = ("raw", "quantity", "unit", "name", "to_taste")

    def __init__(self, raw, quantity, unit, name, to_taste):
        self.raw, self.quantity, self.unit, self.name, self.to_taste = raw, quantity, unit, name, to_taste

    def __repr__(self):
        return f"Line({self.quantity!r}, {self.unit!r}, {self.name!r}, to_taste={self.to_taste})"

    def __eq__(self, other):
        return isinstance(other, Line) and (self.quantity, self.unit, self.name, self.to_taste) == \
            (other.quantity, other.unit, other.name, other.to_taste)


def _to_taste(raw, name):
    _unit, name = split_unit(name)          # "Prise Salz" without a number: the unit is dropped too
    return Line(raw, None, TO_TASTE_UNIT, name.strip(), True)


def parse_ingredient(raw):
    """'100 g Raclette-Käse' -> Line(100, 'g', 'Raclette-Käse'); '½ Stück Zitrone' -> 0.5 Stück;
    '3 Esslöffel Olivenöl' -> 3 EL; 'Salz' / 'nach Geschmack Salz' / 'etwas Salz' -> to taste."""
    s = re.sub(r"\s+", " ", (raw or "").strip())
    for pattern in _TO_TASTE:
        m = pattern.match(s)
        if m:
            s = m.group("name").strip()     # "1 Prise Salz nach Geschmack" still keeps its 1 Prise
            break
    qty, rest = parse_amount(s)
    if qty is None or qty == 0:
        return _to_taste(raw, rest)         # no amount at all ("0 g Salz" too): needed, quantity unknown
    unit, name = split_unit(rest)
    if unit is None:
        unit, name = DEFAULT_UNIT, rest
    if not name:
        name = rest
    return Line(raw, qty, unit, name.strip(), False)


# ---------- the OLD importer, replayed ----------
# What extract.py did before the fixes, kept only so the repair tool can tell whether a stored row
# is exactly what the old parser made of a page line (then a difference is a parser bug, not a
# different line or a wrong item match). Bugs included on purpose: "½" -> 5, rounding, max(1).
OLD_UNITS = {"g", "kg", "ml", "l", "Stück", "EL", "TL", "Bund", "Dose", "Packung", "Prise", "Zehe",
             "Scheibe", "Scheiben"}
_OLD_FRAC = {"½": ".5", "¼": ".25", "¾": ".75", "⅓": ".333", "⅔": ".667"}


def _old_num(s):
    for k, v in _OLD_FRAC.items():
        s = s.replace(k, v)
    m = re.search(r"\d+(?:\.\d+)?", s.replace(",", "."))
    return float(m.group()) if m else None


def old_parse(raw):
    """(quantity, unit) the old importer stored for this page line."""
    s = (raw or "").strip()
    m = re.match(r"([\d.,½¼¾⅓⅔]+)\s*(\S+)?\s*(.*)", s)
    if not m or _old_num(m.group(1)) is None:
        parts = s.split(None, 1)
        unit = parts[0] if len(parts) == 2 and parts[0] in OLD_UNITS else None
        return Decimal(1), unit or "Stück"
    qty = max(1, int(_old_num(m.group(1)) + 0.5))
    tok = m.group(2) or ""
    return Decimal(qty), (tok if tok in OLD_UNITS else "Stück")


# ---------- titles and servings ----------
def cut_title(name):
    """The stored recipe_name: JSON-LD name cut at the subtitle (double space) or a marketing blurb."""
    title = re.split(r"\s{2,}|\s+Bei diesem Gericht|\s+verzehrfertiges", (name or "").strip())[0].strip()
    return title[:120].rstrip()


_TITLE_PEOPLE = [
    re.compile(r"\b(\d+)\s*P\b"),                       # "Bundle 4P", "4 P"
    re.compile(r"\bfür\s+(\d+)\s+Personen\b", re.I),    # "für 4 Personen"
    re.compile(r"\b(\d+)\s+Personen\b", re.I),
]
# "für 3 – 4 Personen", "2 – 4 Personen": a range of people, only when it says Personen
_TITLE_RANGE = re.compile(r"\b(\d+)\s*[–-]\s*(\d+)\s+Personen\b", re.I)
_TITLE_PORTIONS = re.compile(r"\b(\d+)\s+Portionen?\b", re.I)


def title_range(full_name):
    """(low, high) of a 'N – M Personen' title with low < high, else None."""
    m = _TITLE_RANGE.search(full_name or "")
    if m and int(m.group(1)) < int(m.group(2)):
        return int(m.group(1)), int(m.group(2))
    return None


def title_people(full_name):
    """Head count the full (uncut) JSON-LD name announces, e.g. 'Bundle 4P' -> 4, else None.
    A range ('3 – 4 Personen') has no single head count: None (see title_range)."""
    if title_range(full_name) or _TITLE_RANGE.search(full_name or ""):
        return None
    for pattern in _TITLE_PEOPLE:
        m = pattern.search(full_name or "")
        if m:
            return int(m.group(1))
    return None


def yield_people(recipe_yield):
    """recipeYield as an int: 2, '2', '2 Portionen', ['2'] -> 2; None if absent/unreadable."""
    if isinstance(recipe_yield, list):
        recipe_yield = recipe_yield[0] if recipe_yield else None
    if recipe_yield is None:
        return None
    m = re.search(r"\d+", str(recipe_yield))
    return int(m.group()) if m and int(m.group()) > 0 else None


# Where a servings number came from. Only the first three are facts about the recipe; DEFAULT is a
# guess the importer needs for a new recipe, and must never overwrite a stored value; REVIEW means
# the title contradicts itself and a human decides.
FROM_YIELD, FROM_BUNDLE_TITLE, FROM_RANGE_TITLE, DEFAULT, REVIEW = \
    "recipeYield", "bundle title", "range title", "default", "review"
DEFAULT_SERVINGS = 2


def servings_info(rec):
    """dict(servings, servings_max, notes, source) for a recipe.

    Rules (Nate, 2026-10-11): all scraped recipes are 2-person, so recipeYield wins, except
      * "N – M Personen" in the title is kept as a range: servings = N (the lower bound, used for
        scaling by default), servings_max = M;
      * a title naming 4 people ("für 4 Personen", "Bundle 4P", "OTP 4P") means 4 (HelloFresh bundles
        carry 4-person amounts but recipeYield 2);
      * such a 4-person title that also says "2 Portionen" contradicts itself: REVIEW, nothing written.
    Without a readable recipeYield (and no title rule) servings is the default 2 with source DEFAULT.
    Any other disagreement between title and yield is only reported."""
    notes = []
    from_yield = yield_people(rec.get("recipeYield"))
    name = rec.get("name")
    rng = title_range(name)
    from_title = title_people(name)
    servings, source = (from_yield, FROM_YIELD) if from_yield else (DEFAULT_SERVINGS, DEFAULT)
    smax = None
    if from_yield is None:
        notes.append(f"recipeYield missing or unreadable ({rec.get('recipeYield')!r})")
    if rng:
        servings, smax, source = rng[0], rng[1], FROM_RANGE_TITLE
        notes.append(f"title says {rng[0]} – {rng[1]} Personen, recipeYield {from_yield} -> {rng[0]} (max {rng[1]})")
    elif from_title == 4 and servings != 4:
        portions = [int(p) for p in _TITLE_PORTIONS.findall(name or "")]
        if any(p != 4 for p in portions):
            notes.append(f"REVIEW: title says 4 people but also {portions[0]} Portionen; servings not written")
            return {"servings": servings, "servings_max": None, "notes": notes, "source": REVIEW}
        notes.append(f"bundle for 4 in title, recipeYield {from_yield} -> 4")
        servings, source = 4, FROM_BUNDLE_TITLE
    elif from_title is not None and from_title != servings:
        notes.append(f"CONFLICT: title says {from_title} people, recipeYield {from_yield}; kept {servings}")
    elif source == FROM_YIELD:
        other = [int(p) for p in _TITLE_PORTIONS.findall(name or "") if int(p) != servings]
        if other:
            notes.append(f"title says {other[0]} Portionen, recipeYield {from_yield}; kept {servings}")
    return {"servings": servings, "servings_max": smax, "notes": notes, "source": source}


def servings_for(rec):
    """(servings, notes, source): servings_info without the range's upper bound."""
    info = servings_info(rec)
    return info["servings"], info["notes"], info["source"]


# ---------- one recipe ----------
CONFIDENT = ("override", "rule", "exact")        # item matches that may be acted on without a human


def merge_lines(items):
    """Two lines on the same catalog item (PK is item+recipe). items: dicts with item_id,
    quantity, unit, to_taste, name, conf. Same unit -> quantities summed; a quantity beats a
    to_taste line; different units -> the first is kept. Returns (merged, notes).

    Lines are only combined when BOTH matched the item confidently (conf in CONFIDENT). If
    either match is uncertain ("160 pikante Paprikasoße" fuzzily matched to Paprika next to "1 rote
    Paprika"), nothing is added up: the first line stays as it is and the later one is passed
    through on its own, marked "held_collision", so the repair tool lists it for review instead of
    writing a sum nobody checked."""
    out, by_id, notes = [], {}, []
    for it in items:
        iid = it["item_id"]
        if iid is None:
            out.append(it)
            continue
        if iid not in by_id:
            by_id[iid] = dict(it)
            out.append(by_id[iid])
            continue
        kept = by_id[iid]
        if kept.get("conf") not in CONFIDENT or it.get("conf") not in CONFIDENT:
            out.append(dict(it, held_collision=True))
            notes.append(f"REVIEW item #{iid}: '{it['name']}' ({it.get('conf')}) not combined with "
                         f"'{kept['name']}' ({kept.get('conf')}), an uncertain match is never added up")
            continue
        if kept["to_taste"] and it["to_taste"]:
            notes.append(f"duplicate to-taste item #{iid} ('{it['name']}') merged")
        elif kept["to_taste"] and not it["to_taste"]:
            kept.update({k: it[k] for k in ("quantity", "unit", "to_taste", "name", "raw", "conf", "note", "origin")
                         if k in it})
            notes.append(f"item #{iid}: '{it['name']}' has an amount, replaces the to-taste line")
        elif it["to_taste"]:
            notes.append(f"item #{iid}: to-taste line '{it['name']}' dropped, an amount exists")
        elif kept["unit"] == it["unit"]:
            kept["quantity"] = _clean(kept["quantity"] + it["quantity"])
            notes.append(f"item #{iid}: '{it['name']}' summed into {kept['quantity']} {kept['unit']}")
        else:
            notes.append(f"REVIEW item #{iid}: '{it['name']}' {it['quantity']} {it['unit']} dropped, "
                         f"kept first line {kept['quantity']} {kept['unit']} (different units)")
    return out, notes


def _strip_html(text):
    import html as _html
    text = re.sub(r"</p>\s*<p>|<br\s*/?>", "\n", text or "")
    return _html.unescape(re.sub(r"<[^>]+>", "", text)).replace("\\*", "*")


def instruction_steps(rec):
    """The cooking steps of a JSON-LD Recipe as plain text (some pages have none: instructions = 0)."""
    ins = rec.get("recipeInstructions")
    if not isinstance(ins, list):
        return []
    return [s for s in (_strip_html(i.get("text") if isinstance(i, dict) else str(i)) for i in ins) if s]


def _resolve_amountless(raw, line, steps, numbered_families):
    """Rules of 2026-10-11 for a line without a number that is not 'nach Geschmack':
    amount from the steps (several mentions added up) > salt / pepper / oil / vinegar -> to taste >
    'Stück X' -> 1 Stück (not for uncountable things like sugar) > review.
    Returns (line, origin, review_reason): origin 'steps' / 'one' / None; a review reason means the
    line is NOT written. A line whose kind of ingredient already has a numbered line has no row of its own."""
    words, fam = step_amounts.ingredient_key(line.name)
    if fam and fam in numbered_families:
        return None, None, "no row of its own: a numbered line of the same kind exists"
    status, amount, unit, notes = step_amounts.step_amount(line.name, steps)
    if status == "found":
        return Line(raw, amount, unit, line.name, False), "steps", "; ".join(notes)
    if status in ("uncertain", "mixed"):
        why = "mentions with different wording" if status == "uncertain" else "mentions in different units"
        return line, None, f"REVIEW: step amount unsure ({why}): {notes[0]}"
    if fam in step_amounts.TO_TASTE_FAMILIES:
        return line, None, None
    if re.match(r"\s*Stück\s", raw or "", re.I) and fam not in step_amounts.UNCOUNTABLE_FAMILIES:
        return Line(raw, Decimal(1), DEFAULT_UNIT, line.name, False), "one", None
    return line, None, "REVIEW: no amount in the ingredient list or the steps"


def parse_recipe(rec, catalog, is_pantry=None):
    """JSON-LD Recipe dict -> dict(title, servings, servings_max, notes, lines, reviews). lines are
    merged per item and carry item_id/conf/note from catalog.match(name), plus origin ('steps': the
    amount was read from the cooking steps, 'one': 'Stück X' without a number = 1 Stück).
    reviews are amountless lines that need a human (never written). is_pantry(name) lines (water)
    are skipped."""
    notes = []
    sinfo = servings_info(rec)
    servings, servings_source = sinfo["servings"], sinfo["source"]
    notes += sinfo["notes"]
    steps = instruction_steps(rec)
    parsed_lines = [(raw, parse_ingredient(raw)) for raw in rec.get("recipeIngredient") or []]
    numbered_families = {step_amounts.ingredient_key(l.name)[1] for _r, l in parsed_lines if not l.to_taste}
    numbered_families.discard(None)
    items, reviews = [], []
    for raw, line in parsed_lines:
        if not line.name or (is_pantry and is_pantry(line.name)):
            continue
        origin, why = None, None
        if line.to_taste and not _NACH_GESCHMACK.search(raw or ""):
            line, origin, why = _resolve_amountless(raw, line, steps, numbered_families)
        iid, conf, note = catalog.match(line.name if line else parse_ingredient(raw).name)
        if line is None or (why and why.startswith(("REVIEW", "no row"))):
            reviews.append({"raw": raw, "name": (line or parse_ingredient(raw)).name, "item_id": iid,
                            "conf": conf, "reason": why})
            continue
        if why:
            note = (note + "; " if note else "") + why
        items.append({"raw": raw, "name": line.name, "quantity": line.quantity, "unit": line.unit,
                      "to_taste": line.to_taste, "item_id": iid, "conf": conf, "note": note, "origin": origin})
    lines, mnotes = merge_lines(items)
    notes += mnotes
    return {"title": cut_title(rec.get("name")), "servings": servings, "servings_max": sinfo["servings_max"],
            "servings_source": servings_source, "notes": notes, "lines": lines, "reviews": reviews}


def find_recipe(o):
    """The JSON-LD object whose @type is (or includes) Recipe, searched recursively."""
    if isinstance(o, dict):
        t = o.get("@type")
        if t == "Recipe" or (isinstance(t, list) and "Recipe" in t):
            return o
        for v in o.values():
            r = find_recipe(v)
            if r:
                return r
    elif isinstance(o, list):
        for v in o:
            r = find_recipe(v)
            if r:
                return r
    return None


def recipe_from_html(html):
    """The Recipe JSON-LD block of a recipe page, or None."""
    import json
    for block in re.findall(r'<script[^>]*application/ld\+json[^>]*>(.*?)</script>', html, re.S):
        try:
            rec = find_recipe(json.loads(block))
        except ValueError:
            rec = None
        if rec:
            return rec
    return None


def sql_decimal(d):
    """Decimal -> SQL literal ('0.5', '2'), None -> NULL."""
    return "NULL" if d is None else format(_clean(d), "f")
