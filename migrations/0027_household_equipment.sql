-- What a household does NOT have in the kitchen (MariaDB).
--
-- Why: many of the imported recipes are HelloFresh Thermomix recipes ("Mixtopf", "Varoma"); a household without a Thermomix
-- cannot cook them. Decided by Nate (2026-11-12): the household says what it does not have, and the recipe lists, the search
-- and the suggestions leave the recipes out that need it. Presets for other equipment can follow the same way.
--
-- What it does: creates the table household_unavailable_equipment: one row per household and equipment code the household
-- does NOT have (a missing row means "has it", so nothing changes for anybody until a household sets it). Known codes at
-- the moment: 'thermomix'. Nothing existing is changed or deleted.
-- Re-runnable: CREATE TABLE IF NOT EXISTS skips it when it is there. MariaDB commits every statement on its own.
--
-- ORDER: apply this file BEFORE the API of the same PR starts (the new API reads the table). Needs 0022 first.
-- SNAPSHOT FIRST (CLAUDE.md rule 3, even though this only adds a table), then apply from the PHP project folder (the one
-- with compose.yaml):
--   docker compose exec -T db sh -c 'exec mariadb -u root -p"$MARIADB_ROOT_PASSWORD" "$MARIADB_DATABASE"' < ../dahaem/migrations/0027_household_equipment.sql
-- Or paste the file into the SQL tab of phpMyAdmin with the DAHÄM database selected in the left sidebar.
-- The last statement checks it: every line must say OK. "CHECK" means that change did not apply: stop and ask.
-- Not applied to the live database by the PR that adds it.

CREATE TABLE IF NOT EXISTS household_unavailable_equipment (
	household_unavailable_equipment_household_id INTEGER     NOT NULL,
	household_unavailable_equipment_code         VARCHAR(30) NOT NULL,
	PRIMARY KEY (household_unavailable_equipment_household_id, household_unavailable_equipment_code),
	CONSTRAINT household_unavailable_equipment_household_fk
		FOREIGN KEY (household_unavailable_equipment_household_id) REFERENCES household(household_id));

-- The check: every line must say OK.
SELECT 'household_unavailable_equipment exists' AS what,
       IF((SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA = DATABASE()
           AND TABLE_NAME = 'household_unavailable_equipment') = 1, 'OK', 'CHECK') AS result
UNION ALL
SELECT 'the foreign key to household exists',
       IF((SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE()
           AND TABLE_NAME = 'household_unavailable_equipment' AND CONSTRAINT_TYPE = 'FOREIGN KEY') = 1, 'OK', 'CHECK');
