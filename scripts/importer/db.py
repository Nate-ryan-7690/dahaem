#!/usr/bin/env python3
"""MariaDB connection for the importer scripts, configured exactly like the Java side:

  DAHAEM_DB_URL       jdbc:mariadb://host[:port]/database   (the "jdbc:" prefix is optional)
  DAHAEM_DB_USER
  DAHAEM_DB_PASSWORD

Nothing is hard-coded and the password is never printed. The driver (PyMySQL, see
requirements.txt) is imported only when a connection is actually opened, so the parser and
its tests run without it.
"""
import os
import re
from urllib.parse import urlsplit

URL_ENV, USER_ENV, PASSWORD_ENV = "DAHAEM_DB_URL", "DAHAEM_DB_USER", "DAHAEM_DB_PASSWORD"


def parse_url(url):
    """'jdbc:mariadb://db:3306/dahaem?x=y' -> ('db', 3306, 'dahaem')."""
    url = re.sub(r"^jdbc:", "", (url or "").strip())
    parts = urlsplit(url)
    if parts.scheme not in ("mariadb", "mysql") or not parts.hostname or not parts.path.strip("/"):
        raise ValueError(f"{URL_ENV} must look like jdbc:mariadb://host:3306/database")
    return parts.hostname, parts.port or 3306, parts.path.strip("/")


def describe():
    """'db:3306/dahaem as dahaem' for log lines (no password)."""
    host, port, name = parse_url(_env(URL_ENV))
    return f"{host}:{port}/{name} as {_env(USER_ENV)}"


def connect():
    """Open a connection (autocommit off). Raises SystemExit with a clear message if unset."""
    import pymysql
    host, port, name = parse_url(_env(URL_ENV))
    return pymysql.connect(host=host, port=port, database=name, user=_env(USER_ENV),
                           password=_env(PASSWORD_ENV), charset="utf8mb4", autocommit=False)


def _env(name):
    value = os.environ.get(name)
    if not value:
        raise SystemExit(f"{name} is not set (see scripts/importer/README.md)")
    return value
