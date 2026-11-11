-- Intake requests: a repeated submit is not recorded twice (MariaDB).
--
-- Why: on flaky reception a client may send the same intake again because it never saw the answer. With an optional
-- request id the API remembers what it answered, and a repeat of the same request gets the same answer back instead of
-- recording the groceries a second time.
--
-- What it does: creates intake_request, one row per request id that a client sent: the id (the primary key, so two
-- requests with the same id cannot both be processed), a fingerprint of the request body (the same id with a different
-- body is refused), when it arrived, and the answer once it is done (NULL while the request is being processed).
-- Nothing existing is changed or deleted. Re-runnable: CREATE TABLE IF NOT EXISTS skips what exists.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3), then apply from the PHP project folder (the one with compose.yaml):
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0021_intake_request.sql
-- Or paste the file into the SQL tab of phpMyAdmin with the DAHÄM database selected in the left sidebar.
-- The last statement checks it: it must say OK. "CHECK" means it did not apply: stop and ask.
-- Not applied to the live database by the PR that adds it.

CREATE TABLE IF NOT EXISTS intake_request (
    intake_request_id      VARCHAR(64) NOT NULL PRIMARY KEY,
    intake_request_hash    CHAR(64)    NOT NULL,
    intake_request_created DATETIME    NOT NULL,
    intake_request_result  MEDIUMTEXT  NULL);

-- The check: must say OK.
SELECT 'intake_request exists with its four columns' AS what,
       IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'intake_request') = 4, 'OK', 'CHECK') AS result;
