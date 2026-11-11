-- Invitations into a household (MariaDB).
--
-- Why: a second person (Nate's wife, later alpha testers) needs an account in an existing household without the operator
-- typing a command on the server. Decided by Nate (2026-11-11): an admin of the household makes an invitation, the code is
-- shown once and works once, and whoever enters it on the "Konto erstellen" page becomes a MEMBER of that household.
-- Open sign-up is not wanted: without a code nobody can make an account.
--
-- What it does: creates the table household_invite. Nothing existing is changed or deleted.
--   - the code itself is NEVER stored, only its SHA-256 (household_invite_code_hash) and its last four characters
--     (household_invite_code_hint, so an admin can tell the open invitations apart in a list)
--   - household_invite_expires_at: 7 days after creation; used_at / revoked_at are set once, never cleared
-- Re-runnable: CREATE TABLE IF NOT EXISTS skips it when it is there. MariaDB commits every statement on its own.
--
-- SNAPSHOT FIRST (CLAUDE.md rule 3, even though this only adds a table), then apply from the PHP project folder (the one
-- with compose.yaml):
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0025_household_invite.sql
-- Or paste the file into the SQL tab of phpMyAdmin with the DAHÄM database selected in the left sidebar.
-- The last statement checks it: every line must say OK. "CHECK" means that change did not apply: stop and ask.
-- ORDER: apply this file BEFORE the API of the same PR starts. Not applied to the live database by the PR that adds it.

CREATE TABLE IF NOT EXISTS household_invite (
	household_invite_id           INTEGER PRIMARY KEY AUTO_INCREMENT,
	household_invite_household_id INTEGER     NOT NULL,
	household_invite_code_hash    CHAR(64)    NOT NULL,
	household_invite_code_hint    VARCHAR(8)  NOT NULL,
	household_invite_note         VARCHAR(100) NULL,
	household_invite_created_by   INTEGER     NOT NULL,
	household_invite_created_at   DATETIME    NOT NULL,
	household_invite_expires_at   DATETIME    NOT NULL,
	household_invite_used_at      DATETIME    NULL,
	household_invite_used_by      INTEGER     NULL,
	household_invite_revoked_at   DATETIME    NULL,
	UNIQUE KEY household_invite_code (household_invite_code_hash),
	INDEX household_invite_by_household (household_invite_household_id),
	CONSTRAINT household_invite_household_fk
		FOREIGN KEY (household_invite_household_id) REFERENCES household(household_id),
	CONSTRAINT household_invite_created_by_fk
		FOREIGN KEY (household_invite_created_by) REFERENCES app_user(user_id),
	CONSTRAINT household_invite_used_by_fk
		FOREIGN KEY (household_invite_used_by) REFERENCES app_user(user_id));

-- The check: every line must say OK.
SELECT 'household_invite exists' AS what,
       IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'household_invite') = 1, 'OK', 'CHECK') AS result
UNION ALL
SELECT 'the code hash is unique',
       IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'household_invite' AND INDEX_NAME = 'household_invite_code' AND NON_UNIQUE = 0) = 1, 'OK', 'CHECK')
UNION ALL
SELECT 'the three foreign keys exist',
       IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND TABLE_NAME = 'household_invite' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 3, 'OK', 'CHECK');
