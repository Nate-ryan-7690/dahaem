# Security Policy

DAHÄM is a self-hosted, single/two-user household application. It is not a multi-tenant service.

## How it is deployed (and what protects what)

- **The Java API (`dahaem-api`) is internal only.** It publishes no host port; only the PHP front end reaches it, over
  the container network. Every `/api/**` request must carry the shared secret `INTERNAL_API_TOKEN` in the
  `X-Internal-Token` header (constant-time comparison). The API **refuses to start** if that token or the database
  settings (`DAHAEM_DB_URL`, `DAHAEM_DB_USER`, `DAHAEM_DB_PASSWORD`) are missing or blank (fail closed); the secrets come
  from the environment only and never from the repository.
- **The PHP front end** is the only thing a browser talks to. When it is reachable from the internet (a Cloudflare Tunnel
  with Cloudflare Access in front) the Access login is an *outer gate only*: the app does not trust anything Access adds,
  because the app is also reachable directly on the home network. The application's own login and CSRF protection are
  what count.
- **Audit log.** The API writes one line per login, failed login, write and refused request to the logger `audit`
  (method, path without query string, status, caller, client IP; never a body, password or token). The PHP front end passes
  the real client address in the `X-Client-Ip` header; it is believed only on a request that carries the internal token.
- **Outside calls.** The only call that depends on user input is the optional barcode lookup (Open Food Facts, off unless
  `DAHAEM_BARCODE_LOOKUP=openfoodfacts`): it sends the barcode number only, with a generic client name, short timeouts, no
  redirects and a ceiling of 30 lookups per minute. The scheduled store refresh (off unless `REFRESH_ENABLED=true`)
  fetches fixed third-party URLs.

## Known limits (see docs/BACKLOG.md, "Go-live review")

- All logged-in users share one pool of data: **per-household scoping is not implemented**, so ids in a request are not
  checked against an owner. This is acceptable only while the installation holds one household.
- There is **no login lockout or rate limit** in the API yet; passwords are BCrypt hashes with a minimum length of 10.
- There are no self-service sign-up or password reset: accounts are created and reset by the operator.
- Which side owns the user session (PHP or the API) is still to be decided.

## Supported versions

This project has not reached a `1.0` release. There is one line of development
(`main`); security fixes land there. There are no maintained older versions.

## Reporting a vulnerability

If you find a security issue in this repo (for example: SQL injection in a
repository method, a store importer that could be tricked into fetching or
executing something unintended, or credential/secret handling in
`config/stores.properties` or the import scripts), please report it privately:
use **Report a vulnerability** on the repository's
[Security tab](https://github.com/Nate-ryan-7690/dahaem/security/advisories/new)
(GitHub private vulnerability reporting). Please do not open a public issue
with exploit details; an issue is fine for anything that is not sensitive. This
is a personal/apprenticeship project without a formal SLA; expect an
acknowledgement within a few days and a fix on a best-effort basis.

## Scope notes for reviewers

- Store importers (`de.dahaem.stores`) make outbound HTTP requests to
  third-party sites (ALDI Nord, Penny, aktionspreis.de) with a spoofed
  User-Agent. They are not designed to accept untrusted input — the URLs and
  request shapes are fixed by the client classes, not user-supplied.
- `config/stores.properties` holds a personal store id and postal code, not a
  credential; it's gitignored because it's personal data, not because it's a
  secret. There are no API keys or passwords in this repo's config.
- Authentication (`de.dahaem.api.SecurityConfig`, `de.dahaem.auth`): a way to
  reach `/api/**` without the internal token or a session, to forge a state-changing
  request without the CSRF token, or to recover a password from what is stored, is a
  valid report.
- All SQL in the repository layer is expected to be parameterized
  (`PreparedStatement` with `?` placeholders; only the number of placeholders of an
  `IN (...)` list is built). A finding of string-concatenated SQL built from external
  input is a valid report.
