# Contributing

DAHÄM is an apprenticeship final project built to production practices, not (yet)
a project accepting outside contributions — but this doc captures the conventions
the existing code follows, for anyone reading, reviewing, or picking up work in
this repo.

## Setup

Requires Java 17+ and Maven.

```bash
mvn package
```

The app needs a SQLite database at `data/kitchen_organizer.db` (gitignored, not
distributed) with the tables described in [`docs/DATABASE.md`](docs/DATABASE.md)
already present — see that doc's [Gaps](docs/DATABASE.md#gaps) section, since
there is currently no in-repo way to create one from scratch. For store
importers, copy `config/stores.properties.example` to `config/stores.properties`
and fill in your own store id / postal code.

## Before pushing

```bash
mvn verify
```

This is what CI (`.github/workflows/ci.yml`) runs on every push/PR to `main`. It
compiles, runs the JUnit test suite (`src/test/java`), and packages the shaded
jar. Tests hit a real, temporary SQLite file (via the `dahaem.db.path` system
property on `de.dahaem.shared.Database`), not mocks, so no separate test-database
setup is needed.

## Code conventions

- **Layering.** Every feature package (`inventory`, `mealplanner`, `stores`,
  `shopping`, `finance`) follows `model` (plain records, no logic) /
  `repository` (all SQL for that feature, parameterized) / `service` (logic
  only, no SQL, no UI). See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for
  the full rule and why the console runners (`Main`, `ShoppingListDemo`,
  `StoreImportRunner`) are the deliberate exception.
- **SQL.** Always `PreparedStatement` with `?` placeholders — never build a query
  by string-concatenating external input. Table/column names in DDL (which are
  fixed, not user input) are the one place raw string SQL is fine, as in
  `StoreRepository`'s `SCHEMA` array.
- **JDBC resources.** Open `Connection` / `Statement` / `ResultSet` in
  try-with-resources so they always close, matching every existing repository
  method.
- **Comments.** Sparse and purposeful — explaining a non-obvious *why* (a
  matching rule, a scheduling dependency, a SQLite quirk), not restating what
  the code already says. Follow the existing style in
  `StoreMatchService`/`StoreImportService` rather than adding narration.

## Commit messages

This repo uses Conventional-Commits-style prefixes, seen in its history:
`feat:`, `fix:`, `ci:`, `test:`, `data:` (curated data files, e.g. a catalog or
recipe TSV). Keep the summary line short and imperative ("add", not "added").

## Tests

New logic belongs with a test, following the existing pattern in
`src/test/java/de/dahaem/stores/` — JUnit 5, one test class per class under
test, exercising pure logic directly and repository-backed logic against a real
temporary SQLite file. `inventory`, `mealplanner`, `finance`, and `shared`
currently have no test coverage; new work in those packages is a good place to
start closing that gap.

## Store importers

Importers (`de.dahaem.stores`) fetch public pages from third-party sites
politely: a fixed, spoofed User-Agent, a pause after every request
(`http.delay.ms` in `config/stores.properties`), and retries with backoff for
transient failures (`HttpFetcher`). If you add a new store client, follow that
pattern rather than hammering the source site, and keep the raw/verified
two-layer split described in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) —
new data lands in the raw layer first and only becomes a verified `store_item` /
`store_discount` row through matching/promotion, never directly.
