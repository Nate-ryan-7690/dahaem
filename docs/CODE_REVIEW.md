# Code review

A point-in-time review of the codebase as of this writing: every `.java` file
under `src/main` and `src/test`, `pom.xml`, `.github/workflows/ci.yml`,
`config/`, and the Python scripts under `scripts/importer/`. This is a review
document, not a changelog — findings below are not yet fixed unless noted.

## Summary

The codebase is small, deliberately layered, and mostly well-executed for a
solo apprenticeship project. The `stores/` package (importers, matching,
promotion) is the most mature and best-tested part; `finance/` is unstarted;
`inventory/` and `mealplanner/` are simple, correct, and completely untested.
**No SQL injection was found anywhere** — every repository and every
DB-writing script parameterizes its writes. No secrets are committed, and
personal config is gitignored with a documented `.example` template. The main
risks are a small but pervasive resource leak in the shared DB connection
helper, a total absence of tests outside `shopping/`/`stores/`, and a couple of
permanently-committed placeholder/stub files.

GitHub's own Dependabot also currently flags **5 vulnerabilities on `main`
(2 high, 3 moderate)** — see the repo's Security tab. This review didn't
re-derive those (they're transitive-dependency CVEs, not something a manual
code read surfaces), but they're worth triaging alongside the findings below.

## Strengths

- **SQL injection discipline is excellent and consistent.** Every repository
  (`InventoryRepository`, `RecipeRepository`, `ShoppingListRepository`,
  `StoreRepository`) uses `PreparedStatement` with `?` placeholders throughout,
  including the dynamic `IN (...)` placeholder list in
  `ShoppingListRepository` (built from a `?`-count, never from values). The
  Python loaders (`dedup_catalog.py`, `add_ingredients.py`) parameterize every
  write too.
- **The layering rule is genuinely followed.** No `java.sql.*` import exists in
  any `service` class; no business logic was found inside a repository; model
  classes (`Item`, `Recipe`, the records in `stores/`) are pure data holders.
- **Transaction handling in `StoreRepository` / `StorePromotionService` /
  `StoreDiscountService`** is careful and correct: explicit commit/rollback
  pairs and batched writes, used consistently by every caller.
- **`HttpFetcher` is well-built**: fixed hostnames only (no SSRF surface from
  user input), polite backoff/retry, a distinct path for 404s, correct
  `InterruptedException` handling.
- **No secrets committed.** `config/stores.properties` (a real personal PLZ /
  store id) is gitignored with a documented `.example` template;
  `config/store_overrides.json` (committed) holds only catalog IDs.
- **The store price/discount domain logic is well-tested**
  (`StoreOfferTest`, `StorePromotionServiceTest`, `StoreMatchServiceTest`,
  `VariantParserTest`), including an explicitly acknowledged known limitation
  documented via a test (`VariantParserTest#multiPackSizeWithInternalCommaSplitsOnWrongComma`)
  rather than hidden.
- **CI actually runs the tests** (`mvn -B -q -DskipTests=false verify`), not
  just a compile.

## Findings: bugs / security

### High

1. **`src/main/java/de/dahaem/shared/Database.java:27`** — `connection.createStatement().execute("PRAGMA foreign_keys = ON")`
   creates a `Statement` that is never closed. Because every repository method
   in the app calls `Database.getConnection()` and gets a brand-new physical
   SQLite connection each time, this leaks one `Statement` per DB call across
   the entire codebase. A sustained run (e.g. `StoreImportRunner refresh
   monthly`, which opens hundreds of connections crawling ALDI's range)
   accumulates unclosed `Statement` handles for the life of the JVM. Fix: wrap
   in try-with-resources, e.g. `try (Statement st = connection.createStatement()) { st.execute(...); }`.
2. **No `busy_timeout` / connection pooling.** `Database.getConnection()` opens
   a fresh connection per call with no `PRAGMA busy_timeout`. SQLite
   serializes writers; without a busy timeout, any overlapping write (a future
   web request racing a cron-triggered `StoreImportRunner`, or two importer
   jobs with overlapping windows) fails immediately with `SQLITE_BUSY` rather
   than waiting briefly and retrying, surfacing as an opaque `RuntimeException`
   wherever the race is lost.

### Medium

3. **`src/main/java/de/dahaem/finance/{Purchase,PurchaseRepository,PurchaseService}.java`**
   are all literally `public class X {\n}` — permanently-committed placeholder
   code with zero fields, methods, or logic. Harmless today (the README marks
   `finance` as "planned"), but it ships an empty package that reads like a
   real feature; it should be filled in deliberately or removed rather than
   accreted around.
4. **`StoreRepository.findOrCreateStore` (lines ~225-248)** is a check-then-act
   race: `SELECT ... WHERE store_name = ?`, then `INSERT` if absent, with no
   transaction or `INSERT ... ON CONFLICT` upsert tying the two together.
   `AggregatorClient` even comments that display names must match existing
   store rows "or a duplicate store is created" — the author is aware
   duplication is possible, just not from this exact race. Low probability
   today (jobs run sequentially in one process) but latent the moment any job
   path runs concurrently with another.
5. **`src/main/java/de/dahaem/Main.java:43`** hardcodes `sampleRecipeId = 10`
   to "prove the JOIN works." On a fresh DB with fewer than 10 recipes (e.g.
   only the 15 shipped samples loaded with non-contiguous IDs),
   `findAllIngredients(10)` / `findAllInstructions(10)` silently return empty
   lists with no error — giving a false impression the query path is broken
   rather than that the id doesn't exist.

### Low

6. **`scripts/importer/extract.py:29`** — `DEFAULT_IN` is a hardcoded,
   personal Windows path. Not a secret, but non-portable, and running the
   script with defaults on another machine fails silently (no existence check
   before `glob.glob`; it just prints "0 recipes" with no explanation).
7. **`HttpFetcher.get`/`post`** still sleep (up to ~12s) after the *final*
   failed retry attempt before throwing — wastes up to ~30s total across 4
   attempts on a call that's already going to fail.
8. **`StoreImportRunner.loadOverrides`** catches a bare `Exception` around
   reading `config/store_overrides.json` and only logs to stderr, continuing
   with an empty map. A malformed `store_overrides.json` silently degrades
   match quality (everything that would've matched via override now lands in
   the unmatched review file) without ever failing the run or showing up in
   the `ImportSummary`.

## Findings: style / design / cleanup

1. **N+1 query pattern** in `ShoppingListService.buildFromRecipes`: one query
   aggregates ingredients, then `loadStoreCandidates` is called once per
   aggregated ingredient in a loop. Fine at current scale; will need batching
   once shopping lists grow.
2. **`InventoryService.lowStock` / `expiringWithin`** load the entire
   `inventory_item` table via `findAll()` and filter in Java instead of
   pushing a `WHERE` clause into the repository. Correct layering, but a
   missed opportunity once the catalog grows.
3. **Inconsistent null-vs-`Optional` convention.** `InventoryRepository` /
   `RecipeRepository` return `Optional<T>`; the `stores`/`shopping` packages
   represent "no value" as nullable boxed types directly on records. Not
   wrong — records can't hold `Optional` fields cleanly — but there's no
   stated convention, so a reader has to infer it per package.
4. **Two near-duplicate demo mains** (`Main.java`, `ShoppingListDemo.java`),
   both explicitly documented as temporary. Reasonable interim state; flagging
   because they'll need deliberate removal once a real entry point exists,
   rather than bit-rotting alongside it.
5. Every repository uniformly wraps `SQLException` in an unchecked
   `RuntimeException`, so callers can't distinguish "transient SQLITE_BUSY,
   retry" from "schema is broken, stop." Not wrong at this size, but worth a
   named exception type if retry logic is ever added (ties to High finding
   #2).
6. `scripts/importer/load.py` / `load_recipes.py` execute whole `.sql` files
   via `executescript`/hand-rolled parsing rather than parameterized
   statements. Not a live injection risk (the SQL is generated offline from
   the developer's own trusted, reviewed input, never end-user input at
   runtime) — noted because the review explicitly checked DB-writing Python
   scripts for this pattern.

## Test coverage gaps

Only `src/test/java/de/dahaem/shopping/` (1 file) and
`src/test/java/de/dahaem/stores/` (6 files) have any tests. **Zero coverage**
for:

- **`inventory/`** — the highest-risk gap. `InventoryRepository`'s SQL mapping
  (including nullable-date parsing) and `InventoryService`'s
  `lowStock`/`expiringWithin` boundary logic (an inclusive-of-boundary-day
  comparison) are unverified, and this is core domestic-facing logic a user
  would directly notice being wrong.
- **`mealplanner/`** — `RecipeRepository`'s three JOIN queries and its
  nullable-int mapping have no coverage.
- **`finance/`** — moot today (stub classes, see finding #3), flag for when
  it's built out.
- **`shared/Database`** — the one class every other class depends on
  (connection URL construction, the `dahaem.db.path` override, the leak in
  finding #1) has no dedicated test; it's only exercised indirectly via
  `ShoppingListServiceTest`'s use of the same property.
- **`Main` / `ShoppingListDemo`** — not unit-testable as written (both are
  `public static void main`), consistent with being demo runners; low
  priority to test directly, but worth noting they'd block refactoring into a
  real web controller with confidence.

## Build / CI notes

- **`pom.xml`**: Java 17 pinned via `maven.compiler.release`. Dependency
  versions are current and reasonable (`sqlite-jdbc 3.45.3.0`,
  `jackson-databind 2.17.2`, `junit-jupiter 5.10.2`); `maven-shade-plugin` and
  `maven-surefire-plugin` are both pinned explicitly, avoiding drift.
- **`.github/workflows/ci.yml`**: single job, Temurin 17 with Maven cache,
  then `mvn -B -q -DskipTests=false verify`. Tests do run (`verify` triggers
  surefire) — confirmed this is not a "tests exist but CI skips them"
  situation.
- **No static-analysis / lint step.** No Checkstyle, PMD, SpotBugs, or
  error-prone configured anywhere. For a project explicitly built "to
  production practices," this is a reasonable next addition — the unclosed
  `Statement` in finding #1 is exactly the class of bug SpotBugs/PMD catches
  automatically.
- **No coverage reporting** (JaCoCo etc.) — given the gaps above, this would
  make the `inventory`/`mealplanner` gap visible in CI instead of only in a
  manual review like this one.
- The `-q` flag on the CI `mvn verify` command suppresses most output, which
  could make a genuine test failure harder to diagnose from CI logs alone
  (the build still fails, just with less detail).

## Suggested priority order

1. Fix the `Statement` leak in `Database.java` (finding #1) — small, safe,
   high-value fix.
2. Add tests for `inventory/` (finding: test coverage gaps) — highest-risk
   untested code, user-facing.
3. Triage the 5 Dependabot alerts on `main`.
4. Decide `finance/`'s fate (build it out or remove the stubs) so it stops
   reading as a half-finished feature.
5. Everything else (busy_timeout, the `findOrCreateStore` race, static
   analysis) is worth doing but lower urgency at the project's current single-
   process, single-user scale.
