-- DAHÄM database schema (SQLite). Generated from the live DB.
-- 24 tables. Regenerate with: sqlite3 data/kitchen_organizer.db .schema

PRAGMA foreign_keys = ON;

CREATE TABLE "brand" ( "brand_id" INTEGER,

	"brand_name" TEXT NOT NULL UNIQUE, 

	PRIMARY KEY("brand_id" AUTOINCREMENT));

CREATE TABLE "inventory_item" (
	"inventory_item_id"	INTEGER,
	"inventory_item_name_english"	TEXT NOT NULL,
	"inventory_item_name_deutsch"	TEXT,
	"inventory_item_quantity"	INTEGER NOT NULL DEFAULT 0,
	"inventory_item_unit"	TEXT NOT NULL,
	"inventory_item_category"	TEXT NOT NULL,
	"inventory_item_subcategory"	TEXT,
	"inventory_item_earliest_exp_date"	DATE,
	PRIMARY KEY("inventory_item_id" AUTOINCREMENT)
);

CREATE TABLE "meal_plan" ( "meal_plan_id" INTEGER,

	"meal_plan_week_start_date" DATE NOT NULL UNIQUE,

	PRIMARY KEY("meal_plan_id" AUTOINCREMENT));

CREATE TABLE "meal_plan_recipe"("meal_plan_recipe_id" INTEGER,

	"meal_plan_recipe_plan_id" INTEGER,

	"meal_plan_recipe_meal_time" TEXT,

	PRIMARY KEY("meal_plan_recipe_id","meal_plan_recipe_plan_id"),

	FOREIGN KEY("meal_plan_recipe_id") REFERENCES "recipe"("recipe_id"),

	FOREIGN KEY("meal_plan_recipe_plan_id") REFERENCES "meal_plan"("meal_plan_id"));

CREATE TABLE "purchase_history"("purchase_history_purchase_id" INTEGER,

	"purchase_history_store_item_id" INTEGER NOT NULL,

	"purchase_history_discount_id" INTEGER,

	"purchase_history_was_discounted" BOOLEAN NOT NULL,

	"purchase_history_purchase_date" DATE NOT NULL,

	"purchase_history_quantity_purchased" INTEGER NOT NULL,

	"purchase_history_price_paid" REAL NOT NULL,

	"purchase_history_expiration_date" DATE NOT NULL,

	PRIMARY KEY("purchase_history_purchase_id" AUTOINCREMENT),

	FOREIGN KEY("purchase_history_store_item_id") REFERENCES "store_item"("store_item_id"),

	FOREIGN KEY("purchase_history_discount_id") REFERENCES "store_discount"("store_discount_id"));

CREATE TABLE "recipe" ( "recipe_id" INTEGER,

	"recipe_name" TEXT NOT NULL,

	"recipe_servings" INTEGER NOT NULL,

	"recipe_cook_time" INTEGER NOT NULL,

	"recipe_prep_time" INTEGER NOT NULL,

	"recipe_calories" INTEGER,

	"recipe_protein" INTEGER,

	"recipe_carbs" INTEGER,

	"recipe_fat" INTEGER,

	"recipe_fibre" INTEGER,

	"recipe_sugar" INTEGER,

	PRIMARY KEY("recipe_id" AUTOINCREMENT));

CREATE TABLE "recipe_ingredient" ("recipe_ingredient_item_id" INTEGER,

	"recipe_ingredient_recipe_id" INTEGER NOT NULL,

	"recipe_ingredient_unit" TEXT NOT NULL,

	"recipe_ingredient_quantity_needed" INTEGER NOT NULL,

	PRIMARY KEY("recipe_ingredient_item_id","recipe_ingredient_recipe_id"),

	FOREIGN KEY("recipe_ingredient_item_id") REFERENCES "inventory_item"("inventory_item_id"),

	FOREIGN KEY("recipe_ingredient_recipe_id") REFERENCES "recipe"("recipe_id"));

CREATE TABLE "recipe_instructions" ("recipe_instructions_step_id" INTEGER,

	"recipe_instructions_recipe_id" INTEGER NOT NULL,

	"recipe_instructions_step_number" INTEGER NOT NULL,

	"recipe_instructions_instruction" TEXT NOT NULL,

	"recipe_instructions_notes" TEXT,

	"recipe_instructions_source" TEXT NOT NULL,

	PRIMARY KEY("recipe_instructions_step_id" AUTOINCREMENT),

	FOREIGN KEY("recipe_instructions_recipe_id") REFERENCES "recipe"("recipe_id"));

CREATE TABLE "recipe_tag" ("recipe_tag_recipe_id" INTEGER,

	"recipe_tag_id" INTEGER,

	PRIMARY KEY("recipe_tag_recipe_id","recipe_tag_id"),

	FOREIGN KEY("recipe_tag_recipe_id") REFERENCES "recipe"("recipe_id"),

	FOREIGN KEY("recipe_tag_id") REFERENCES "tag"("tag_id"));

CREATE TABLE "shopping_list" ( "shopping_list_id" INTEGER,

	"shopping_list_meal_plan_id" INTEGER,

	"shopping_list_created_date" DATE NOT NULL,

	"shopping_list_status" BOOLEAN NOT NULL,

	PRIMARY KEY("shopping_list_id" AUTOINCREMENT),

	FOREIGN KEY("shopping_list_meal_plan_id") REFERENCES "meal_plan"("meal_plan_id"));

CREATE TABLE "shopping_list_item"("shopping_list_item_id" INTEGER,

	"shopping_list_id" INTEGER NOT NULL,

	"shopping_list_item_store_item_id" INTEGER,

	"shopping_list_item_inventory_item_id" INTEGER,

	"shopping_list_item_store_section" TEXT,

	"shopping_list_item_quantity_needed" INTEGER NOT NULL,

	"shopping_list_item_is_discounted" BOOLEAN,

	"shopping_list_item_purchase_status" BOOLEAN NOT NULL,

	"shopping_list_item_is_manual" BOOLEAN NOT NULL,

	PRIMARY KEY("shopping_list_item_id" AUTOINCREMENT),

	FOREIGN KEY("shopping_list_id") REFERENCES "shopping_list"("shopping_list_id"),

	FOREIGN KEY("shopping_list_item_store_item_id") REFERENCES "store_item"("store_item_id"),

	FOREIGN KEY("shopping_list_item_inventory_item_id") REFERENCES "inventory_item"("inventory_item_id"));

CREATE TABLE "store" ( "store_id" INTEGER, 

	"store_name" TEXT NOT NULL UNIQUE, 

	PRIMARY KEY("store_id" AUTOINCREMENT));

CREATE TABLE store_catalog_change (
    store_catalog_change_id            INTEGER PRIMARY KEY AUTOINCREMENT,
    store_catalog_change_run_id        INTEGER NOT NULL REFERENCES store_import_run(store_import_run_id),
    store_catalog_change_product_id    INTEGER NOT NULL REFERENCES store_product(store_product_id),
    store_catalog_change_type          TEXT NOT NULL,  -- NEW / REMOVED / RETURNED / PRICE
    store_catalog_change_old_price     REAL,
    store_catalog_change_new_price     REAL,
    store_catalog_change_detected_date DATE NOT NULL);

CREATE TABLE "store_discount"("store_discount_id" INTEGER,

	"store_discount_store_item_id" INTEGER NOT NULL,

	"store_discount_start_date" DATE NOT NULL,

	"store_discount_end_date" DATE NOT NULL,

	"store_discount_price" REAL NOT NULL,

	"store_discount_notes" TEXT, store_discount_deal_quantity INTEGER NOT NULL DEFAULT 1, store_discount_deal_price REAL,

	PRIMARY KEY("store_discount_id" AUTOINCREMENT),

	FOREIGN KEY("store_discount_store_item_id") REFERENCES "store_item"("store_item_id"));

CREATE TABLE store_import_run (
    store_import_run_id          INTEGER PRIMARY KEY AUTOINCREMENT,
    store_import_run_store_id    INTEGER NOT NULL REFERENCES store(store_id),
    store_import_run_kind        TEXT NOT NULL,      -- RANGE / OFFERS
    store_import_run_started_at  TEXT NOT NULL,
    store_import_run_finished_at TEXT,
    store_import_run_status      TEXT NOT NULL,      -- RUNNING / OK / PARTIAL / FAILED
    store_import_run_pages       INTEGER,
    store_import_run_items       INTEGER,
    store_import_run_notes       TEXT);

CREATE TABLE "store_item" (store_item_id INTEGER PRIMARY KEY AUTOINCREMENT, store_item_brand_id INTEGER NOT NULL REFERENCES brand(brand_id), store_item_store_id INTEGER NOT NULL REFERENCES store(store_id), store_item_item_id INTEGER NOT NULL REFERENCES inventory_item(inventory_item_id), store_item_package_unit TEXT NOT NULL, store_item_package_size INTEGER NOT NULL, store_item_price REAL, store_item_is_available BOOLEAN NOT NULL, store_item_deposit REAL, store_item_store_product_id INTEGER REFERENCES store_product(store_product_id), store_item_external_id TEXT);

CREATE TABLE store_offer (
    store_offer_id           INTEGER PRIMARY KEY AUTOINCREMENT,
    store_offer_store_id     INTEGER NOT NULL REFERENCES store(store_id),
    store_offer_product_id   INTEGER REFERENCES store_product(store_product_id),  -- null if not in the range
    store_offer_external_id  TEXT NOT NULL,
    store_offer_brand        TEXT,
    store_offer_name         TEXT NOT NULL,
    store_offer_sales_unit   TEXT,
    store_offer_sale_price   REAL NOT NULL,        -- per unit
    store_offer_deal_quantity INTEGER NOT NULL DEFAULT 1,  -- units required for a multi-buy deal
    store_offer_deal_price   REAL,                 -- total for the deal quantity (null = normal single offer)
    store_offer_normal_price REAL,
    store_offer_base_price   REAL,
    store_offer_base_unit    TEXT,
    store_offer_deposit      REAL,                 -- Pfand per unit
    store_offer_promo_text   TEXT,
    store_offer_category     TEXT,
    store_offer_region       TEXT NOT NULL DEFAULT '',
    store_offer_valid_from   DATE NOT NULL,
    store_offer_valid_until  DATE NOT NULL,
    store_offer_pulled_on    DATE NOT NULL, store_offer_source TEXT NOT NULL DEFAULT 'STORE',
    UNIQUE (store_offer_store_id, store_offer_external_id, store_offer_valid_from, store_offer_region));

CREATE TABLE store_price_history (
    store_price_history_id            INTEGER PRIMARY KEY AUTOINCREMENT,
    store_price_history_product_id    INTEGER NOT NULL REFERENCES store_product(store_product_id),
    store_price_history_price         REAL NOT NULL,
    store_price_history_base_price    REAL,
    store_price_history_recorded_date DATE NOT NULL);

CREATE TABLE store_product (
    store_product_id                INTEGER PRIMARY KEY AUTOINCREMENT,
    store_product_store_id          INTEGER NOT NULL REFERENCES store(store_id),
    store_product_external_id       TEXT NOT NULL,   -- the chain's own product id
    store_product_brand             TEXT,
    store_product_name              TEXT NOT NULL,
    store_product_sales_unit        TEXT,            -- e.g. "250-g-Packung"
    store_product_regular_price     REAL,
    store_product_base_price        REAL,            -- price per base unit
    store_product_base_unit         TEXT,            -- e.g. "kg", "Liter"
    store_product_deposit           REAL,            -- Pfand per unit (null = none)
    store_product_category          TEXT,
    store_product_description       TEXT,
    store_product_url               TEXT,
    store_product_is_available      BOOLEAN NOT NULL,
    store_product_first_seen        DATE NOT NULL,
    store_product_last_seen         DATE NOT NULL,
    store_product_inventory_item_id INTEGER REFERENCES inventory_item(inventory_item_id),  -- set by matching later
    UNIQUE (store_product_store_id, store_product_external_id));

CREATE TABLE store_product_variant (
    store_product_variant_id         INTEGER PRIMARY KEY AUTOINCREMENT,
    store_product_variant_product_id INTEGER NOT NULL REFERENCES store_product(store_product_id),
    store_product_variant_name       TEXT NOT NULL,  -- flavour, e.g. "Natur"
    store_product_variant_size       TEXT);

CREATE TABLE "tag" ( "tag_id" INTEGER,

	"tag_name" TEXT NOT NULL UNIQUE,

	"tag_category" TEXT NOT NULL,

	PRIMARY KEY("tag_id" AUTOINCREMENT));

-- Identity: who may log in, and which household they belong to. Named app_user, not user
-- (a reserved word in some engines). user_email is stored trimmed and lower-cased by the app.
-- user_password_hash is a BCrypt hash. Plaintext passwords are never stored.
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
