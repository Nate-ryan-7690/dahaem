#!/usr/bin/env python3
"""Re-match existing recipe draft .sql files against the CURRENT catalog + overrides.

The drafts in scripts/recipes/_drafts/ were built by extract.py against an older,
smaller catalog. Since then we added ~119 ingredients + ~37 overrides and fixed the
pantry filter. This pass upgrades the drafts WITHOUT re-fetching from HelloFresh
(that already ran; re-crawling costs money/time).

It can only touch lines that still carry the ingredient name, i.e. the `-- REVIEW`
lines. For each one it:
  * DROPS it if the name is now a pantry staple (retroactive fix of the old leak),
  * PROMOTES it to a clean match if the catalog/overrides now resolve it
    confidently (override / exact / rule),
  * otherwise KEEPS it flagged, with a refreshed comment.
Cleanly-matched lines (no comment) are left as-is: their name was not stored and
they were already confident. Each line's SELECT prefix is preserved verbatim, so
recipe names containing commas/quotes are never corrupted.

Dry-run by default; pass --write to overwrite the drafts in place.
"""
import argparse, glob, json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from extract import Catalog, is_pantry, load_overrides, strip_lead_unit  # reuse the exact same matcher + rules

DRAFTS = os.path.normpath(os.path.join(HERE, "..", "recipes", "_drafts"))

# One ingredient VALUES line. The SELECT prefix is kept verbatim (recipe names hold
# commas/quotes); only the id, the optional REVIEW comment and the trailing
# terminator are ours to rewrite.
LINE = re.compile(
    r"^(?P<prefix>\s*\(\(SELECT recipe_id FROM recipe WHERE recipe_name='(?:[^']|'')*'\),\s*)"
    r"(?P<id>\d+)"
    r"(?P<mid>,\s*'(?:[^']|'')*',\s*(?:\d+(?:\.\d+)?|NULL)(?:,\s*[01])?\))"   # qty; to_taste in new drafts
    r"\s*[,;]?"                                       # terminator here in the rewritten format
    r"(?:\s*(?P<comment>--\s*REVIEW\b[^\n]*?))?"
    r"\s*[,;]?\s*$"                                   # ...or here in the original format
)
# Pull the ingredient name out of a REVIEW comment: "-- REVIEW (conf): NAME | note"
REVIEW = re.compile(r"--\s*REVIEW\s*\(([^)]*)\):\s*(.*?)\s*\|\s*(.*)$")

CLEAN = {"override", "exact", "rule"}   # confident enough to store without a review flag


def rematch_file(path, cat, stats):
    """Return the new file text if it changed, else None."""
    with open(path, encoding="utf-8") as f:
        text = f.read()
    lines = text.split("\n")

    start = end = None
    for i, ln in enumerate(lines):
        if start is None and ln.startswith("INSERT INTO recipe_ingredient"):
            start = i + 1
        elif start is not None and ln.startswith("INSERT INTO recipe_instructions"):
            end = i
            break
    if start is None or end is None:
        stats["skipped_shape"] += 1
        return None

    kept = []          # each: (prefix, id, mid, comment_or_None)  OR ("RAW", raw_line)
    changed = False
    for ln in lines[start:end]:
        if not ln.strip():
            continue
        m = LINE.match(ln)
        if not m:
            stats["unparsed_lines"] += 1
            kept.append(("RAW", ln))               # never silently drop unrecognised data
            continue
        prefix, cur_id, mid, comment = m.group("prefix"), m.group("id"), m.group("mid"), m.group("comment")
        if not comment:
            kept.append((prefix, cur_id, mid, None))
            continue

        rev = REVIEW.search(comment)
        if not rev:
            kept.append((prefix, cur_id, mid, comment))
            continue
        name = rev.group(2).strip()
        _, name = strip_lead_unit(name)          # peel "Stück "/"Dose " etc. left glued by the old parser

        if is_pantry(name, name):
            stats["pantry_dropped"] += 1
            changed = True
            continue

        new_id, conf, note = cat.match(name)
        if conf in CLEAN:
            kept.append((prefix, str(new_id), mid, None))
            stats["promoted"] += 1
            if str(new_id) != cur_id:
                stats["id_changed"] += 1
            changed = True
        else:
            new_id_s = str(new_id if new_id is not None else 0)
            new_comment = "-- REVIEW (%s): %s | %s" % (conf, name, note)
            kept.append((prefix, new_id_s, mid, new_comment))
            stats["still_review"] += 1
            if new_id_s != cur_id or new_comment != comment.strip():
                changed = True

    if not any(k[0] != "RAW" for k in kept):
        stats["emptied"] += 1                      # dropping all rows -> invalid SQL; leave file
        return None
    if not changed:
        return None

    out = []
    n = len(kept)
    for idx, k in enumerate(kept):
        term = ";" if idx == n - 1 else ","
        if k[0] == "RAW":
            out.append(k[1])
            continue
        prefix, iid, mid, comment = k
        body = prefix + iid + mid
        out.append(body + term + ("   " + comment if comment else ""))

    stats["files_changed"] += 1
    return "\n".join(lines[:start] + out + lines[end:])


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--dir", default=DRAFTS)
    ap.add_argument("--limit", type=int, default=0, help="process only the first N drafts (testing)")
    ap.add_argument("--write", action="store_true", help="overwrite drafts in place (default is dry-run)")
    args = ap.parse_args()

    import db                                   # catalog is read from MariaDB (DAHAEM_DB_* env vars)
    con = db.connect()
    try:
        cat = Catalog.from_db(con, load_overrides())
    finally:
        con.close()

    files = sorted(glob.glob(os.path.join(args.dir, "*.sql")))
    if args.limit:
        files = files[:args.limit]

    stats = dict(files=len(files), files_changed=0, promoted=0, id_changed=0,
                 pantry_dropped=0, still_review=0, emptied=0,
                 skipped_shape=0, unparsed_lines=0)

    for path in files:
        new_text = rematch_file(path, cat, stats)
        if new_text is not None and args.write:
            with open(path, "w", encoding="utf-8") as f:
                f.write(new_text)

    print("mode:        ", "WRITE" if args.write else "DRY-RUN (no files touched)")
    print("drafts seen: ", stats["files"])
    print("would change:" if not args.write else "changed:    ", stats["files_changed"])
    print("promoted to clean match:", stats["promoted"], "(of those, id changed:", stats["id_changed"], ")")
    print("pantry lines dropped:   ", stats["pantry_dropped"])
    print("still flagged for review:", stats["still_review"])
    print("emptied (left untouched):", stats["emptied"])
    print("wrong-shape files:      ", stats["skipped_shape"])
    print("unparsed value lines:   ", stats["unparsed_lines"])


if __name__ == "__main__":
    main()
