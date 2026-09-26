# Store-name matching: how it works and how it grows

`StoreMatchService` (used by both `match aldi` and the flyer-offer `discounts`
step) links a store's own product/offer names to `inventory_item` catalog rows
by name alone — no SQL in the service, all of it lives behind
`StoreRepository`. It is deliberately conservative: a store sells specific
branded items ("MILSANI Streichfein ungesalzen"), the catalog holds generic
ingredients ("butter"), and guessing wrong creates a wrong discount, so a
name that isn't confidently resolvable is left for a human rather than
guessed into the database.

## Match order

1. **`config/store_overrides.json`** — an exact map of normalized store name
   -> `inventory_item_id`. Always checked first and always wins.
2. **Exact match** — the normalized store name equals a catalog item's
   normalized English or German name outright.
3. **German-aware fuzzy match** — after dropping a small stoplist of
   descriptors (colors, sizes like `XXL`, generic words like `frisch`/
   `Aktion`, and a few fused cut/part suffixes like `-herzen`/`-scheiben`),
   each remaining word is compared against every catalog term:
   - **exact**: the word equals the term, or the term plus a common German
     plural/inflection ending (`süßkartoffeln` folds onto `süßkartoffel`);
   - **compound head**: the word *ends with* the term, optionally inflected,
     with something left over in front (`gemüsezwiebeln` ends with
     `zwiebeln` -> `zwiebel`). Terms under 4 characters are never used this
     way, and the match must land on the end of the word — never the middle
     or start — so short terms don't fire inside unrelated words
     (`kabellose` never matches a catalog term `bell`).

   When several catalog items match, the longest catalog term wins (most
   specific); a tie in length prefers an exact hit over a compound one; a
   remaining tie is left unmatched rather than guessed.

Everything that doesn't resolve is written to a review file
(`data/stores/unmatched_aldi.tsv` for `match aldi`,
`data/stores/unmatched_offers.tsv` for `discounts`) with both the raw and
normalized name, never silently dropped.

## Growing the match rate: review -> overrides

The fuzzy matcher is precision-first, not recall-first — it will still miss
real food (an unusual compound, a name the plural-folding doesn't cover, a
one-off descriptor). That's expected and by design: the workflow is to grow
`config/store_overrides.json` from what actually shows up in the review
files, rather than widen the algorithm's guesswork.

1. Run the relevant job (`match aldi` or `discounts`) and open the review TSV
   it wrote. Each row has the category, the raw name, and the normalized key
   the matcher computed for it (in parentheses).
2. For a name that's clearly a catalog item the matcher just couldn't reach,
   confirm which `inventory_item_id` it should map to.
3. Add an entry to the `"exact"` map in `config/store_overrides.json`, keyed
   by the normalized name shown in the review file:
   ```json
   { "exact": { "riesengarnelen tk": 118 } }
   ```
4. Re-run the job. The override is checked before any other rule, so it
   always applies from then on, including to future pulls of the same
   product/offer.

This keeps the curated catalog from being polluted by one-off guesses while
letting real, confirmed matches accumulate over time as the maintainer
reviews actual pulled data.
