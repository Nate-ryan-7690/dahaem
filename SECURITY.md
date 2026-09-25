# Security Policy

DAHÄM is a self-hosted, single/two-user household application. It is not a
multi-tenant service and does not currently ship an authenticated network-facing
component (see [Project status](README.md#project-status)) — most exposure today
is on the machine running the console app and the store-import jobs.

## Supported versions

This project has not reached a `1.0` release. There is one line of development
(`main`); security fixes land there. There are no maintained older versions.

## Reporting a vulnerability

If you find a security issue in this repo (for example: SQL injection in a
repository method, a store importer that could be tricked into fetching or
executing something unintended, or credential/secret handling in
`config/stores.properties` or the import scripts), please open a
[GitHub issue](https://github.com/Nate-ryan-7690/dahaem/issues) or contact the
maintainer directly rather than filing a public issue if the report includes
exploit details. This is a personal/apprenticeship project without a formal SLA;
expect an acknowledgement within a few days and a fix on a best-effort basis.

## Scope notes for reviewers

- Store importers (`de.dahaem.stores`) make outbound HTTP requests to
  third-party sites (ALDI Nord, Penny, aktionspreis.de) with a spoofed
  User-Agent. They are not designed to accept untrusted input — the URLs and
  request shapes are fixed by the client classes, not user-supplied.
- `config/stores.properties` holds a personal store id and postal code, not a
  credential; it's gitignored because it's personal data, not because it's a
  secret. There are no API keys or passwords in this repo's config.
- All SQL in the repository layer is expected to be parameterized
  (`PreparedStatement` with `?` placeholders). A finding of string-concatenated
  SQL built from external input is a valid report.
