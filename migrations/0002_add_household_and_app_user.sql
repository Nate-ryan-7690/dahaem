-- Adds the identity tables for login: household, app_user and household_member.
--
-- Only needed for a database created before this migration. A fresh database built from
-- schema.sql (e.g. via setup_db.py) already has these tables.
--
-- These are brand-new tables, so unlike 0001 the foreign keys and UNIQUE constraints are created
-- in full. No existing data is touched: inventory, recipes, meal plans etc. are not yet scoped to
-- a household (that is a separate, later migration).
--
-- After applying it, create the first login with:
--   java -cp target/dahaem-1.0-SNAPSHOT.jar de.dahaem.auth.UserAdminRunner create-user <email> <displayName>
--
-- Apply with:
--   sqlite3 data/kitchen_organizer.db < migrations/0002_add_household_and_app_user.sql

CREATE TABLE "household" ( "household_id" INTEGER,
	"household_name" TEXT NOT NULL,
	"created_date" DATE NOT NULL,
	PRIMARY KEY("household_id" AUTOINCREMENT));

CREATE TABLE "app_user" ( "user_id" INTEGER,
	"user_email" TEXT NOT NULL UNIQUE,
	"user_display_name" TEXT NOT NULL,
	"user_password_hash" TEXT NOT NULL,
	"user_created_date" DATE NOT NULL,
	PRIMARY KEY("user_id" AUTOINCREMENT));

CREATE TABLE "household_member" ( "household_member_id" INTEGER,
	"household_id" INTEGER NOT NULL,
	"user_id" INTEGER NOT NULL,
	"role" TEXT NOT NULL,  -- ADMIN / MEMBER
	PRIMARY KEY("household_member_id" AUTOINCREMENT),
	UNIQUE("household_id","user_id"),
	FOREIGN KEY("household_id") REFERENCES "household"("household_id"),
	FOREIGN KEY("user_id") REFERENCES "app_user"("user_id"));
