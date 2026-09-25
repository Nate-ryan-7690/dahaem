#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
HelloFresh importer, stage 0: the puller.

Fetches the site's recipe sitemap (robots.txt-sanctioned) and writes every
recipe URL to a plain text file, one per line. That list is the input for
stage 1 (extract.py --list <file>), which batches through it.

This does ONE request (the sitemap), not a crawl.

Usage:
  python discover.py                       # -> recipe_urls.txt
  python discover.py --out my_urls.txt
"""
import argparse, gzip, os, re, sys
import urllib.request as urlreq

SITEMAP = "https://www.hellofresh.de/sitemap_recipe_pages.xml"
UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
HERE = os.path.dirname(os.path.abspath(__file__))

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--out", default=os.path.join(HERE, "recipe_urls.txt"))
    ap.add_argument("--sitemap", default=SITEMAP)
    args = ap.parse_args()

    print("fetching sitemap:", args.sitemap, file=sys.stderr)
    data = urlreq.urlopen(urlreq.Request(args.sitemap, headers={"User-Agent": UA}), timeout=60).read()
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    xml = data.decode("utf-8", "replace")

    seen, urls = set(), []
    for loc in re.findall(r"<loc>(.*?)</loc>", xml):
        loc = loc.strip()
        if "/recipes/" in loc and loc not in seen:
            seen.add(loc)
            urls.append(loc)

    with open(args.out, "w", encoding="utf-8") as f:
        f.write("\n".join(urls) + "\n")
    print(f"wrote {len(urls)} recipe URLs -> {args.out}")

if __name__ == "__main__":
    main()
