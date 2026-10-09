# CLAUDE.md — DAHÄM (Java backend)

DAHÄM is a self-hosted household-management backend (groceries first: inventory, recipes,
meal planning, shopping lists, store prices/offers, purchases). Java 17 + Spring Boot, a
thin REST API over plain-JDBC services. It is the **engine** in an architecture where a
separate PHP app is the presentation layer and **MariaDB** is the shared store; this
service is deployed as an internal-only container (`dahaem-api`) behind the PHP frontend.

The rules below are **hard ground rules** — they override any instinct to "just fix it
quickly." If a rule blocks you, stop and ask the maintainer.

## RULE 1 — Never commit to `main`. PRs only.
- Never commit or push to `main`. Always: branch → do the work → push → open a **pull
  request for approval**. Nothing merges without the maintainer's review.
- Before editing anything, run `git branch --show-current`; if it is `main`, create/switch
  to a feature branch **first**, then edit. (This has gone wrong before by editing while on
  `main` after a merge left the checkout there.)
- End every PR description with: `🤖 Generated with [Claude Code](https://claude.com/claude-code)`.

## RULE 2 — Never touch a live / production database.
- No `DROP`, `DELETE`, `TRUNCATE`, destructive `ALTER`, data-losing migration, or
  schema/data re-import against a **live or production** database (the real MariaDB holds
  the only copy of the household's data).
- Tests and experiments use a **throwaway** database only — Testcontainers (`mariadb`), or a
  temporary DB/schema you create and drop. Never the live DB.
- Reads and normal application writes are fine; ad-hoc destructive SQL against real data is not.
- History to remember: a `setup_db.py --force` once deleted the real (gitignored) SQLite
  database — ~4 days of work — because it overwrote an existing file without checking.
  Treat every database as containing real, unbacked-up data.

## RULE 3 — If a live-DB change is truly required and approved: snapshot first, keep ≤ 3.
Only after the maintainer approves a change to live data/schema:
1. **Snapshot first** — a timestamped logical dump (the snapshot is non-negotiable and comes
   *before* the destructive step; a dump is always safe, the destructive step is not allowed
   without it). For the MariaDB deployment:
   ```bash
   mariadb-dump -h <host> -u <user> -p<pass> --databases <db> > snapshots/dahaem-$(date +%Y%m%d-%H%M%S).sql
   ```
2. **Rotate — keep at most 3**, newest replaces oldest:
   ```bash
   ls -1t snapshots/dahaem-*.sql | tail -n +4 | xargs -r rm --
   ```
3. Only then make the change; restore from the snapshot if anything breaks.
- Snapshots contain real data → keep them **git-ignored**; never commit one.

## RULE 4 — Secrets & config from environment only.
- DB connection and any internal token come from env vars (`DAHAEM_DB_URL`,
  `DAHAEM_DB_USER`, `DAHAEM_DB_PASSWORD`, internal API token) — never hardcoded, never
  committed. Do not commit `.env`, credentials, or snapshots.

## Codebase conventions
- Package-by-feature under `de.dahaem`. Strict layers: **model** (plain data) / **repository**
  (ALL SQL, every statement parameterized — never string-concatenate values) / **service**
  (logic, no SQL/UI) / **api** (thin Spring controllers + DTOs). Spring is a thin layer over
  the plain services; no JPA/Hibernate.
- Product principle: **surface, don't decide** — the app presents options (prices, savings,
  store choices); the user chooses. Never auto-pick the cheapest store or a meal.
- Scope is **all household shopping**, not food-only. Pantry staples (oil, salt, pepper,
  sugar, flour) are real tracked ingredients.
- Build (shaded runnable jar): `mvn -DskipTests package` → `target/dahaem-1.0-SNAPSHOT-shaded.jar`
  (main class `de.dahaem.api.DahaemApplication`). CI runs the tests on JDK 17.
