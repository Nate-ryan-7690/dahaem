#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Ingredient catalog scan (run after a batch of drafts exists).

Reads every draft .sql in _drafts/ and pulls the ingredients the matcher could
NOT confidently place (the `-- REVIEW (...)` comments extract.py leaves). It
aggregates them across all recipes and splits into:

  ingredients_missing.tsv    conf=none : no catalog match at all -> candidates to ADD
  ingredients_ambiguous.tsv  like/multi : matched-but-uncertain -> candidates for overrides.json

Each row has an occurrence count so you tackle the common ones first. This does
not touch the DB or the network; it only reads the drafts.

Usage:
  python scan_ingredients.py
"""
import glob, os, re, sys
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.abspath(os.path.join(HERE, "..", ".."))
DRAFTS = os.path.join(PROJECT, "scripts", "recipes", "_drafts")

REVIEW_RE = re.compile(r"--\s*REVIEW\s*\(([^)]+)\):\s*(.*?)\s*\|\s*(.*)$")

def main():
    files = glob.glob(os.path.join(DRAFTS, "*.sql"))
    missing = Counter()          # name -> count  (conf none)
    ambiguous = {}               # name -> (count, conf, note)
    amb_count = Counter()
    for fp in files:
        for line in open(fp, encoding="utf-8"):
            m = REVIEW_RE.search(line)
            if not m:
                continue
            conf, name, note = m.group(1).strip(), m.group(2).strip(), m.group(3).strip()
            if conf == "none":
                missing[name] += 1
            else:
                amb_count[name] += 1
                ambiguous[name] = (conf, note)

    mp = os.path.join(DRAFTS, "ingredients_missing.tsv")
    with open(mp, "w", encoding="utf-8") as f:
        f.write("count\tgerman_name\n")
        for name, c in missing.most_common():
            f.write(f"{c}\t{name}\n")
    ap = os.path.join(DRAFTS, "ingredients_ambiguous.tsv")
    with open(ap, "w", encoding="utf-8") as f:
        f.write("count\tconf\tgerman_name\tbest_guesses\n")
        for name, c in amb_count.most_common():
            conf, note = ambiguous[name]
            f.write(f"{c}\t{conf}\t{name}\t{note}\n")

    out = (f"drafts scanned: {len(files)}\n"
           f"MISSING (no match, need adding): {len(missing)} unique -> {mp}\n"
           f"AMBIGUOUS (uncertain match): {len(ambiguous)} unique -> {ap}\n"
           f"\nTop 15 missing:\n" +
           "\n".join(f"  {c:>4}  {n}" for n, c in missing.most_common(15)))
    sys.stdout.buffer.write((out + "\n").encode("utf-8"))

if __name__ == "__main__":
    main()
