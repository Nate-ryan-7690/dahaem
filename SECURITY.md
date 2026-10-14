# Security Policy

DAHÄM is a self-hosted, single/two-user household application. It is not a
multi-tenant service. Its REST API requires a login (Spring Security session
login with CSRF protection, BCrypt password hashes, operator-created accounts,
no public sign-up) and binds to `127.0.0.1` by default; exposing it is only
supported behind a TLS-terminating reverse proxy (see
[docs/VISION.md](docs/VISION.md#running-it-on-the-network)). All logged-in users
currently share one pool of data: per-household scoping is not implemented yet.

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
  reach `/api/**` without a session, to forge a state-changing request without
  the CSRF token, or to recover a password from what is stored, is a valid report.
- All SQL in the repository layer is expected to be parameterized
  (`PreparedStatement` with `?` placeholders). A finding of string-concatenated
  SQL built from external input is a valid report.
