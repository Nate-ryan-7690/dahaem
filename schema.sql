-- DAHÄM database schema (MariaDB). Generated from the live DB.
-- 25 tables. Regenerate with: mariadb data/kitchen_organizer.db .schema

CREATE TABLE brand ( brand_id INTEGER PRIMARY KEY AUTO_INCREMENT,

	brand_name TEXT NOT NULL UNIQUE);

CREATE TABLE inventory_item (
	inventory_item_id	INTEGER PRIMARY KEY AUTO_INCREMENT,
	inventory_item_name_english	TEXT NOT NULL,
	inventory_item_name_deutsch	TEXT,
	inventory_item_quantity	INTEGER NOT NULL DEFAULT 0,
	inventory_item_unit	TEXT NOT NULL,
	inventory_item_category	TEXT NOT NULL,
	inventory_item_subcategory	TEXT,
	inventory_item_earliest_exp_date DATE);

CREATE TABLE meal_plan ( meal_plan_id INTEGER PRIMARY KEY AUTO_INCREMENT,

	meal_plan_week_start_date DATE NOT NULL UNIQUE);

CREATE TABLE tag ( tag_id INTEGER PRIMARY KEY AUTO_INCREMENT,

	tag_name TEXT NOT NULL UNIQUE,

	tag_category TEXT NOT NULL);

CREATE TABLE recipe ( recipe_id INTEGER PRIMARY KEY AUTO_INCREMENT,

	recipe_name TEXT NOT NULL,

	recipe_servings INTEGER NOT NULL,

	recipe_servings_max INT NULL,

	recipe_cook_time INTEGER NOT NULL,

	recipe_prep_time INTEGER NOT NULL,

	recipe_calories INTEGER,

	recipe_protein INTEGER,

	recipe_carbs INTEGER,

	recipe_fat INTEGER,

	recipe_fibre INTEGER,

	recipe_sugar INTEGER,

	recipe_source_url TEXT,

	CONSTRAINT recipe_servings_max_above_servings
		CHECK (recipe_servings_max IS NULL OR recipe_servings_max > recipe_servings));

CREATE TABLE store ( store_id INTEGER PRIMARY KEY AUTO_INCREMENT, 

	store_name TEXT NOT NULL UNIQUE);

CREATE TABLE household ( household_id INTEGER PRIMARY KEY AUTO_INCREMENT,
	household_name TEXT NOT NULL,
	created_date DATE NOT NULL);

CREATE TABLE app_user ( user_id INTEGER PRIMARY KEY AUTO_INCREMENT,
	user_email TEXT NOT NULL UNIQUE,
	user_display_name TEXT NOT NULL,
	user_password_hash TEXT NOT NULL,
	user_created_date DATE NOT NULL);

CREATE TABLE meal_plan_recipe(meal_plan_recipe_id INTEGER,

	meal_plan_recipe_plan_id INTEGER,

	meal_plan_recipe_meal_time TEXT,

	PRIMARY KEY(meal_plan_recipe_id,meal_plan_recipe_plan_id),

	FOREIGN KEY(meal_plan_recipe_id) REFERENCES recipe(recipe_id),

	FOREIGN KEY(meal_plan_recipe_plan_id) REFERENCES meal_plan(meal_plan_id));

CREATE TABLE recipe_ingredient (recipe_ingredient_item_id INTEGER,

	recipe_ingredient_recipe_id INTEGER NOT NULL,

	recipe_ingredient_unit TEXT NOT NULL,

	recipe_ingredient_quantity_needed DECIMAL(10,3) NULL,

	recipe_ingredient_to_taste BOOLEAN NOT NULL DEFAULT FALSE,

	PRIMARY KEY(recipe_ingredient_item_id,recipe_ingredient_recipe_id),

	CONSTRAINT recipe_ingredient_quantity_or_to_taste
		CHECK ((recipe_ingredient_quantity_needed IS NULL) = (recipe_ingredient_to_taste <> 0)),

	FOREIGN KEY(recipe_ingredient_item_id) REFERENCES inventory_item(inventory_item_id),

	FOREIGN KEY(recipe_ingredient_recipe_id) REFERENCES recipe(recipe_id));

CREATE TABLE recipe_instructions (recipe_instructions_step_id INTEGER PRIMARY KEY AUTO_INCREMENT,

	recipe_instructions_recipe_id INTEGER NOT NULL,

	recipe_instructions_step_number INTEGER NOT NULL,

	recipe_instructions_instruction TEXT NOT NULL,

	recipe_instructions_notes TEXT,

	recipe_instructions_source TEXT NOT NULL,

	FOREIGN KEY(recipe_instructions_recipe_id) REFERENCES recipe(recipe_id));

CREATE TABLE recipe_tag (recipe_tag_recipe_id INTEGER,

	recipe_tag_id INTEGER,

	PRIMARY KEY(recipe_tag_recipe_id,recipe_tag_id),

	FOREIGN KEY(recipe_tag_recipe_id) REFERENCES recipe(recipe_id),

	FOREIGN KEY(recipe_tag_id) REFERENCES tag(tag_id));

CREATE TABLE store_product (
    store_product_id                INTEGER PRIMARY KEY AUTO_INCREMENT,
    store_product_store_id          INTEGER NOT NULL,
    store_product_external_id       TEXT NOT NULL,   -- the chain's own product id
    store_product_brand             TEXT,
    store_product_name              TEXT NOT NULL,
    store_product_sales_unit        TEXT,            -- e.g. 250-g-Packung
    store_product_regular_price     DECIMAL(10,2),
    store_product_base_price        DECIMAL(10,2),            -- price per base unit
    store_product_base_unit         TEXT,            -- e.g. kg, Liter
    store_product_deposit           DECIMAL(10,2),            -- Pfand per unit (null = none)
    store_product_category          TEXT,
    store_product_description       TEXT,
    store_product_url               TEXT,
    store_product_is_available      BOOLEAN NOT NULL,
    store_product_first_seen        DATE NOT NULL,
    store_product_last_seen         DATE NOT NULL,
    store_product_inventory_item_id INTEGER,  
    UNIQUE (store_product_store_id, store_product_external_id),
	FOREIGN KEY(store_product_store_id) REFERENCES store(store_id),
	FOREIGN KEY(store_product_inventory_item_id) REFERENCES inventory_item(inventory_item_id));

CREATE TABLE store_item (store_item_id INTEGER PRIMARY KEY AUTO_INCREMENT,
	store_item_brand_id INTEGER NOT NULL, 
	store_item_store_id INTEGER NOT NULL, 
	store_item_item_id INTEGER NOT NULL, 
	store_item_package_unit TEXT NOT NULL, 
	store_item_package_size INTEGER NOT NULL, 
	store_item_price DECIMAL(10,2), 
	store_item_is_available BOOLEAN NOT NULL, 
	store_item_deposit DECIMAL(10,2), 
	store_item_store_product_id INTEGER, 
	store_item_external_id TEXT,
	FOREIGN KEY(store_item_brand_id) REFERENCES brand(brand_id),
	FOREIGN KEY(store_item_store_id) REFERENCES store(store_id),
	FOREIGN KEY(store_item_item_id) REFERENCES inventory_item(inventory_item_id),
	FOREIGN KEY(store_item_store_product_id) REFERENCES store_product(store_product_id));

CREATE TABLE store_discount(store_discount_id INTEGER PRIMARY KEY AUTO_INCREMENT,

	store_discount_store_item_id INTEGER NOT NULL,

	store_discount_start_date DATE NOT NULL,

	store_discount_end_date DATE NOT NULL,

	store_discount_price DECIMAL(10,2) NOT NULL,

	store_discount_notes TEXT, 
	
	store_discount_deal_quantity INTEGER NOT NULL DEFAULT 1, 
	
	store_discount_deal_price DECIMAL(10,2),

	FOREIGN KEY(store_discount_store_item_id) REFERENCES store_item(store_item_id));

CREATE TABLE purchase_history(purchase_history_purchase_id INTEGER PRIMARY KEY AUTO_INCREMENT,

	purchase_history_store_item_id INTEGER NOT NULL,

	purchase_history_discount_id INTEGER,

	purchase_history_was_discounted BOOLEAN NOT NULL,

	purchase_history_purchase_date DATE NOT NULL,

	purchase_history_quantity_purchased INTEGER NOT NULL,

	purchase_history_price_paid DECIMAL(10,2) NOT NULL,

	purchase_history_expiration_date DATE NOT NULL,

	FOREIGN KEY(purchase_history_store_item_id) REFERENCES store_item(store_item_id),

	FOREIGN KEY(purchase_history_discount_id) REFERENCES store_discount(store_discount_id));

CREATE TABLE shopping_list ( shopping_list_id INTEGER PRIMARY KEY AUTO_INCREMENT,

	shopping_list_meal_plan_id INTEGER,

	shopping_list_created_date DATE NOT NULL,

	shopping_list_status BOOLEAN NOT NULL,

	FOREIGN KEY(shopping_list_meal_plan_id) REFERENCES meal_plan(meal_plan_id));

CREATE TABLE shopping_list_item(shopping_list_item_id INTEGER PRIMARY KEY AUTO_INCREMENT,

	shopping_list_id INTEGER NOT NULL,

	shopping_list_item_store_item_id INTEGER,

	shopping_list_item_inventory_item_id INTEGER,

	shopping_list_item_store_section TEXT,

	shopping_list_item_quantity_needed INTEGER NOT NULL,

	shopping_list_item_is_discounted BOOLEAN,

	shopping_list_item_purchase_status BOOLEAN NOT NULL,

	shopping_list_item_is_manual BOOLEAN NOT NULL,

	FOREIGN KEY(shopping_list_id) REFERENCES shopping_list(shopping_list_id),

	FOREIGN KEY(shopping_list_item_store_item_id) REFERENCES store_item(store_item_id),

	FOREIGN KEY(shopping_list_item_inventory_item_id) REFERENCES inventory_item(inventory_item_id));

CREATE TABLE store_import_run (
    store_import_run_id          INTEGER PRIMARY KEY AUTO_INCREMENT,
    store_import_run_store_id    INTEGER NOT NULL,
    store_import_run_kind        TEXT NOT NULL,      -- RANGE / OFFERS
    store_import_run_started_at  DATETIME NOT NULL,
    store_import_run_finished_at DATETIME,
    store_import_run_status      TEXT NOT NULL,      -- RUNNING / OK / PARTIAL / FAILED
    store_import_run_pages       INTEGER,
    store_import_run_items       INTEGER,
    store_import_run_notes       TEXT,
	FOREIGN KEY(store_import_run_store_id) REFERENCES store(store_id));

CREATE TABLE store_catalog_change (
    store_catalog_change_id            INTEGER PRIMARY KEY AUTO_INCREMENT,
    store_catalog_change_run_id        INTEGER NOT NULL,
    store_catalog_change_product_id    INTEGER NOT NULL,
    store_catalog_change_type          TEXT NOT NULL,  -- NEW / REMOVED / RETURNED / PRICE
    store_catalog_change_old_price     DECIMAL(10,2),
    store_catalog_change_new_price     DECIMAL(10,2),
    store_catalog_change_detected_date DATE NOT NULL,
	FOREIGN KEY(store_catalog_change_run_id) REFERENCES store_import_run(store_import_run_id),
	FOREIGN KEY(store_catalog_change_product_id) REFERENCES store_product(store_product_id));

CREATE TABLE store_offer (
    store_offer_id           INTEGER PRIMARY KEY AUTO_INCREMENT,
    store_offer_store_id     INTEGER NOT NULL,
    store_offer_product_id   INTEGER,  -- null if not in the range
    store_offer_external_id  TEXT NOT NULL,
    store_offer_brand        TEXT,
    store_offer_name         TEXT NOT NULL,
    store_offer_sales_unit   TEXT,
    store_offer_sale_price   DECIMAL(10,2) NOT NULL,        -- per unit
    store_offer_deal_quantity INTEGER NOT NULL DEFAULT 1,  -- units required for a multi-buy deal
    store_offer_deal_price   DECIMAL(10,2),                 -- total for the deal quantity (null = normal single offer)
    store_offer_normal_price DECIMAL(10,2),
    store_offer_base_price   DECIMAL(10,2),
    store_offer_base_unit    TEXT,
    store_offer_deposit      DECIMAL(10,2),                 -- Pfand per unit
    store_offer_promo_text   TEXT,
    store_offer_category     TEXT,
    store_offer_region       TEXT NOT NULL DEFAULT '',
    store_offer_valid_from   DATE NOT NULL,
    store_offer_valid_until  DATE NOT NULL,
    store_offer_pulled_on    DATE NOT NULL, store_offer_source TEXT NOT NULL DEFAULT 'STORE',
    UNIQUE (store_offer_store_id, store_offer_external_id, store_offer_valid_from, store_offer_region),
	FOREIGN KEY(store_offer_store_id) REFERENCES store(store_id),
	FOREIGN KEY(store_offer_product_id) REFERENCES store_product(store_product_id));

-- One row per price change of an existing offer seen on a re-pull (migrations/0007). A new offer has
-- none: its store_offer row is the first observation. old/new = stored value before / pulled value.
CREATE TABLE store_offer_price_change (
    store_offer_price_change_id               INTEGER PRIMARY KEY AUTO_INCREMENT,
    store_offer_price_change_offer_id         INTEGER NOT NULL,
    store_offer_price_change_old_sale_price   DECIMAL(10,2) NOT NULL,
    store_offer_price_change_new_sale_price   DECIMAL(10,2) NOT NULL,
    store_offer_price_change_old_normal_price DECIMAL(10,2),
    store_offer_price_change_new_normal_price DECIMAL(10,2),
    store_offer_price_change_old_deal_price   DECIMAL(10,2),
    store_offer_price_change_new_deal_price   DECIMAL(10,2),
    store_offer_price_change_observed_on      DATE NOT NULL,      -- pulled_on of the pull that saw the new price
    store_offer_price_change_created_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX store_offer_price_change_offer_observed (store_offer_price_change_offer_id, store_offer_price_change_observed_on),
	FOREIGN KEY(store_offer_price_change_offer_id) REFERENCES store_offer(store_offer_id));

CREATE TABLE store_price_history (
    store_price_history_id            INTEGER PRIMARY KEY AUTO_INCREMENT,
    store_price_history_product_id    INTEGER NOT NULL,
    store_price_history_price         DECIMAL(10,2) NOT NULL,
    store_price_history_base_price    DECIMAL(10,2),
    store_price_history_recorded_date DATE NOT NULL,
	FOREIGN KEY(store_price_history_product_id) REFERENCES store_product(store_product_id));

CREATE TABLE store_product_variant (
    store_product_variant_id         INTEGER PRIMARY KEY AUTO_INCREMENT,
    store_product_variant_product_id INTEGER NOT NULL,
    store_product_variant_name       TEXT NOT NULL,  -- flavour, e.g. Natur
    store_product_variant_size       TEXT,
	FOREIGN KEY(store_product_variant_product_id) REFERENCES store_product(store_product_id));

-- Identity: who may log in, and which household they belong to. Named app_user, not user
-- (a reserved word in some engines). user_email is stored trimmed and lower-cased by the app.
-- user_password_hash is a BCrypt hash. Plaintext passwords are never stored.

CREATE TABLE household_member ( household_member_id INTEGER PRIMARY KEY AUTO_INCREMENT,
	household_id INTEGER NOT NULL,
	user_id INTEGER NOT NULL,
	role TEXT NOT NULL,  -- ADMIN / MEMBER
	UNIQUE(household_id,user_id),
	FOREIGN KEY(household_id) REFERENCES household(household_id),
	FOREIGN KEY(user_id) REFERENCES app_user(user_id));
