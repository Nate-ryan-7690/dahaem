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
   -> `inventory_item_id`. Always checked first and always wins, regardless
   of every rule below — a human override overrules the non-food guard too.
2. **Non-food guard** — rejected outright when the product's normalized
   name *or* category contains a cleaning/hygiene/household marker (a small,
   commented list in `StoreMatchService.NON_FOOD_MARKERS`: `reiniger`, `wc`,
   `wasch`, `putz`, `spül`/`spul`, `weichspüler`, `klar`, `kosmetik`,
   `shampoo`, `seife`, `dusch`, `duft`, `kerze`/`teelicht`, `batterie`,
   `papier`, `windel`). This is what stops a "WC-Reiniger Zitrone" from
   resolving to the catalog's lemon just because the word is in the name.
3. **Exact match** — the normalized store name equals a catalog item's
   normalized English or German name outright.
4. **German-aware fuzzy match, head noun only** — after dropping a small
   stoplist of descriptors (colors, sizes like `XXL`, generic words like
   `frisch`/`Aktion`, a few fused cut/part suffixes like
   `-herzen`/`-scheiben`) and any size/quantity token (`100`, `750ml`,
   `3x32g`, a bare unit like `g`/`stück`), only the **last remaining word —
   the head noun** — is compared against catalog terms, never an earlier
   word in the name:
   - **exact**: the head equals the term, or the term plus a common German
     plural/inflection ending (`süßkartoffeln` folds onto `süßkartoffel`);
   - **compound head**: the head word *ends with* the term, optionally
     inflected, with something left over in front (`gemüsezwiebeln` ends
     with `zwiebeln` -> `zwiebel`). Terms under 4 characters are never used
     this way, and the match must land on the end of the word — never the
     middle or start — so short terms don't fire inside unrelated words
     (`kabellose` never matches a catalog term `bell`).

   The head-noun restriction is what keeps a processed derivative from
   matching the raw ingredient just because the ingredient's name shows up
   earlier: "Kartoffel-Ringe" (head noun `Ringe`), "Kartoffel Püree" (head
   noun `Püree`) and "Semmel-Knödel oder Kartoffel-Klöße" (head noun
   `Klöße`) all stay unmatched against `kartoffel`, while "Kartoffeln"
   itself still matches, since the head noun *is* the catalog term. A word
   that isn't on the descriptor/size stoplist is never silently dropped
   just because it functions like a modifier — "Zitrone Plus" has head noun
   `Plus`, which blocks the match rather than falling back to `Zitrone`.

   A multi-word catalog term (e.g. an English phrase) still falls back to
   the old whole-name "contains" check, independent of the head-noun rule.

   When several catalog items match, the longest catalog term wins (most
   specific); a tie in length prefers an exact hit over a compound one; a
   remaining tie is left unmatched rather than guessed.

Everything that doesn't resolve — including anything the non-food guard or
the head-noun rule rejects — is written to a review file
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
