# Katalog, Kochbuch und Verwaltung — design

Status: **plan, agreed with Nate on 2026-11-12, no code yet.** This document is the contract for the backend PRs below and for the frontend agent. When a decision changes, change it here first.

## 1. What this is about

Today every household sees all ~12,000 recipes (the HelloFresh import) everywhere: search, the home page's "Passt gerade gut", the planner. The household wants to **choose** which recipes it uses and gets recommended. So:

- the big recipe set becomes a **catalogue** that households browse and add from;
- every household has its own **cookbook** (starts empty, holds at most 1,000 visible recipes);
- everything a household does with recipes (planner, shopping lists, suggestions, servings) uses its cookbook;
- a standalone **super admin** account maintains the site without seeing household-private data.

## 2. Decisions

| # | Decision |
|---|---|
| 1 | The **catalogue** is today's recipe tables, unchanged. For the alpha every household can browse it. Before a public listing it is replaced by a clean set (see 5.7). |
| 2 | A **cookbook** is new tables with a household column. Shared tables, not one set per household. |
| 3 | **Adding copies.** A recipe added from the catalogue is copied (ingredients, steps, tags) into the household's cookbook and keeps a link to its origin. Edits by anyone never reach another household. |
| 4 | **Limit:** 1,000 visible recipes per household (setting `dahaem.cookbook.limit`). Hidden recipes do not count. The super admin can set a per-household override. |
| 5 | **Nothing is deleted.** "Entfernen" hides a recipe (hidden flag + reason). Only an operator **purge** deletes for real. |
| 6 | Every member may do everything with recipes and tags. The household admin only controls access (invitations, removing members, rename). |
| 7 | **Creator rules** (a recipe is hidden when its creator leaves the household) apply only to recipes a person **typed in by hand**. Recipes copied from the catalogue, imported (later) or from the HelloFresh set have no creator rules. |
| 8 | Unsharing a shared recipe **withdraws** it: every household's copy that came from it is hidden with the reason `withdrawn`; the household cannot restore it, only an operator can. |
| 9 | Planned meals stay when their recipe is hidden. They show "Rezept nicht verfügbar" without steps; the start page shows a count with a link to the planner. |
| 10 | Existing meal plans, shopping lists and preferred servings keep working: a migration copies the recipes they use into the household's cookbook and re-points them. |
| 11 | Discovery: the pantry suggestions take `source=cookbook` (default) or `source=catalogue`. The frontend offers a toggle. |
| 12 | The **super admin** is a separate account without a household (platform role). It never sees a household's private cookbook, plans, lists or stock. |
| 13 | Out of scope for now: link import, paper and photo import (later: photo upload), moderation by anyone but the super admin, a person belonging to two households. |

## 3. Words

- **Katalog / catalogue:** the shared recipes (`recipe`, `recipe_ingredient`, `recipe_instructions`, `recipe_tag`, `recipe_unmatched_ingredient`). Read-only for households.
- **Kochbuch / cookbook:** a household's own recipes (`cookbook_*`).
- **Source** of a cookbook recipe: `catalogue` (copied), `own` (typed in). Later: `link`, `paper`.
- **Hidden:** `hidden_at` set, with a reason: `removed` (the household removed it, may restore), `creator_left` (the creator left, only restorable through the creator's grant), `withdrawn` (its origin was unshared or withdrawn, only an operator can restore).
- **Platform role:** a role on an account for the whole site (`SUPER_ADMIN`), separate from the household role (`ADMIN` / `MEMBER`).

## 4. Data model

### 4.1 Catalogue: unchanged
The five recipe tables stay as they are, including the frozen column `recipe.recipe_preferred_servings`. Households never write to them.

### 4.2 Cookbook tables (new, migration 0028, empty at first)
They mirror the catalogue tables **with the same column names** where they exist, so the recipe code can run on either set (see 8).

- `cookbook_recipe`: `recipe_id` (own id sequence), `household_id` NOT NULL (FK), all recipe data columns of `recipe` (name, servings, servings_max, cook_time, prep_time, calories, protein, carbs, fat, fibre, sugar, source_url, description, image_url, thumbnail_url, image_source, cuisine, rating_avg, rating_count, published_date), plus
  `source_kind` (`catalogue`/`own`), `origin_recipe_id` NULL (FK to `recipe`, `ON DELETE SET NULL`), `added_by_user_id` NULL (FK; for `own` it is the creator), `created_at`, `hidden_at` NULL, `hidden_reason` NULL (`removed`/`creator_left`/`withdrawn`), `hidden_by_user_id` NULL.
  Constraints: hidden_at and hidden_reason are both set or both NULL; servings checks as in `recipe`.
- `cookbook_recipe_ingredient`: as `recipe_ingredient` (item id, recipe id, unit, quantity or "to taste"), FK to `cookbook_recipe`. An ingredient is always matched to a catalogue item (`inventory_item`); lines that could not be matched go to
- `cookbook_recipe_unmatched_ingredient`: as `recipe_unmatched_ingredient`. A recipe with such a line is "unklar" for diet purposes, as today.
- `cookbook_recipe_instruction`: as `recipe_instructions`.
- `cookbook_recipe_tag`: `recipe_id`, `tag_id`, `source` (`manual`/`derived`). System tags (Tier, Ernährung, Zeit, Nährwerte, Küche, Inhalt, Ausstattung) are copied from the origin at add time and recomputed when a recipe is created or edited.
- `household_tag`: a household's own tags (`household_id`, `tag_id`) over the shared `tag` table; a household sees system tags plus its own, never another household's.

Indexes: `(household_id, hidden_at)`, `(household_id, origin_recipe_id)` unique among visible recipes (a recipe is in a cookbook once), name search as today.

### 4.3 Changes to existing tables (migration 0029, one PR, see 7)
`meal_plan_recipe.meal_plan_recipe_id`, `shopping_list_item_recipe.shopping_list_item_recipe_recipe_id` and `household_recipe_servings.household_recipe_servings_recipe_id` point at **cookbook** recipe ids after the migration (their foreign keys are re-pointed from `recipe` to `cookbook_recipe`).

### 4.4 Household and account columns
- `household.household_cookbook_limit_override` INT NULL (NULL = the setting; set only by the super admin).
- `app_user.user_platform_role` VARCHAR(20) NULL (`SUPER_ADMIN`), `app_user.user_disabled_at` DATETIME NULL.

### 4.5 Admin tables (new)
- `admin_audit`: who (user id), when, action code, target (type, id), a short detail text. Written for every admin action.
- `server_error`: when, endpoint (method + path without query), status, error code, a short message, the acting user id. Written when the API answers `INTERNAL_ERROR`; kept for 90 days.

## 5. Rules

1. **Add one** (`POST /api/cookbook/recipes {"recipeId": 123}`): the recipe must exist in the catalogue and not already be in the cookbook (visible or hidden `removed` → offer restore), the cookbook must have room. The copy is one transaction (recipe, ingredients, unmatched lines, steps, tags).
2. **Bulk add** copies up to 200 recipes per request and reports what was added, skipped (already there) and why it stopped (`FULL` or `CAP`), so the frontend can call again. **Add by filter** takes the same filters as the catalogue search and adds the matches, newest relevance first, up to the free space and the cap.
3. **Limit:** `visible = hidden_at IS NULL`. Restoring needs room (409 `COOKBOOK_FULL`). The limit is checked inside the transaction (no race between two members).
4. **Remove / restore / bulk:** only `removed` can be restored by the household. `creator_left` and `withdrawn` rows are read-only for it.
5. **Edit:** every member may edit any cookbook recipe (it is the household's own copy). Editing recomputes the derived tags and the "unklar" status.
6. **Planned meals** whose recipe is hidden: the planner answer marks them `available: false` with `unavailableReason` and gives no steps; shopping lists already made keep their lines; a new list skips unavailable meals and says so.
7. **Withdraw** (the operator withdraws a catalogue recipe or a creator unshares a shared one): all linked copies become hidden `withdrawn`. For the HelloFresh set this is also the way to lock it before a public listing: withdraw the whole set from the catalogue, replace it with a clean one; households keep nothing of it.
8. **Equipment** (Thermomix): applies to catalogue browsing, the catalogue discovery and the cookbook. Copies keep their tags.
9. **Servings:** a household's preferred servings per recipe are keyed by the cookbook recipe.
10. **Purge** (operator only): deletes a hidden cookbook recipe or a catalogue recipe for real, with all rows that depend on it; typed confirmation, audit entry.

## 6. API

Existing calls keep their shapes; new calls are under `/api/cookbook/**` and `/api/admin/**`. All need `X-Acting-User` (admin calls: an account with the platform role, no household needed). Errors use the usual JSON with stable codes; new codes: `COOKBOOK_FULL` (409), `ALREADY_IN_COOKBOOK` (409, with `cookbookRecipeId`), `RECIPE_HIDDEN` (409), `NOT_SUPER_ADMIN` (403), `ACCOUNT_DISABLED` (403).

### 6.1 Catalogue (existing `/api/recipes/*`)
`GET /api/recipes/search`, `GET /api/recipes`, `GET /api/recipes/{id}`: unchanged, plus `inCookbook` (and `cookbookRecipeId`) per card and in the detail.

### 6.2 Cookbook
| Call | What |
|---|---|
| `GET /api/cookbook/summary` | `{count, limit, free, hidden}` |
| `GET /api/cookbook/recipes` | list and search: `q`, `tags`, `source`, `removed=false\|true\|only`, `sort`, paging as the catalogue search; cards like the catalogue cards plus `source`, `hiddenReason` |
| `GET /api/cookbook/recipes/{id}` | detail (steps scaled like today) plus `source`, `editable`, `originRecipeId`, `hiddenReason`, `householdPreferredServings` |
| `POST /api/cookbook/recipes` | add one `{recipeId}` |
| `POST /api/cookbook/recipes/bulk` | add several `{recipeIds}` |
| `POST /api/cookbook/recipes/from-search` | add by filter (the search parameters as the body) |
| `DELETE /api/cookbook/recipes/{id}` | remove (hide, `removed`) |
| `POST /api/cookbook/recipes/remove` | remove several `{recipeIds}` |
| `POST /api/cookbook/recipes/{id}/restore` | restore (needs room) |
| `GET /api/cookbook/tags`, `POST`, `PUT /{id}`, `DELETE /{id}` | household tags |
| `PUT /api/cookbook/recipes/{id}/tags` | set the household tags of a recipe |
| `POST /api/cookbook/recipes/own`, `PUT /api/cookbook/recipes/{id}` | create / edit (per-field errors: `{"errors": [{"field","code","message"}]}`) |
| `GET /api/ingredients/search?q=` | catalogue items for the ingredient field |
| `POST /api/cookbook/ingredient-matches` | text lines in, match proposals out; the user confirms |

### 6.3 Other existing calls that change
- Meal plan calls take **cookbook** recipe ids; a planned meal has `available` and `unavailableReason`; `GET /api/meal-plans/unavailable-count` gives `{count}` for the start page.
- Shopping list generation and the preferred servings use cookbook recipes.
- `GET /api/suggestions/pantry?source=cookbook|catalogue` (default `cookbook`); `by-expiring` and `by-savings` follow the same parameter.
- `GET /api/household/summary`-style data for the start page stays in the existing calls.

### 6.4 People (later, outline only)
Create an own household, join with a code while logged in, "who am I" refresh, my recipes per household, grant and withdraw the old household's use, take recipes along as a copy, share / unshare / report. Designed when the cookbook is in use.

## 7. Moving the data of the first household (PR C8a)
A migration with a dry-run report first:
1. For every household: collect the catalogue recipe ids used by its meal plans, shopping lists and preferred servings.
2. Copy each into that household's cookbook (a recipe used by two households is copied twice), with `origin_recipe_id` set.
3. Re-point `meal_plan_recipe`, `shopping_list_item_recipe`, `household_recipe_servings` to the copies; swap the three foreign keys.
4. Re-runnable (it skips what is done); checks at the end must say OK.
Staging first, snapshot before live, rollback = restore the snapshot. The cookbook of the first household then holds only what its plans and lists use (probably a few dozen recipes).

## 8. How the code stays modular
- Package `cookbook`: copy engine, limit, hide / restore, catalogue-to-cookbook mapping. Nothing else knows how a copy is made.
- `RecipeTables`: a small descriptor (table names for the catalogue or the cookbook) that the recipe repositories use, so the same SQL runs on either set. Only seven classes touch recipe SQL: `RecipeRepository`, `ShoppingListRepository`, `SuggestionRepository`, `RecipeTagRepository`, `TagRepository`, `DietFilterRepository`, `MealPlanRepository`.
- The diet-filter facts are cached for the catalogue (as now) and loaded per household for a cookbook (at most 1,000 recipes).
- The admin area has its own controllers (`/api/admin/**`) and its own caller type; it never uses the household resolver.

## 9. The super admin

A separate account **without a household**. Anyone who does maintenance gets their own account with the platform role (the audit log then says who did what); the role is one flag for now (`SUPER_ADMIN`); a narrower `STAFF` role can follow if needed.

**Read-only (tier 1):** site overview (counts of accounts, households, catalogue recipes, cookbook sizes); households (name, member count, cookbook size, limit and override, last activity, no content); accounts (email, name, household, roles, created, disabled); the catalogue with how many cookbooks hold each recipe and its withdrawn / reported state; reports; the latest server errors; invitations per household (counts, never codes); system status (version, applied migrations).

**Actions (tier 2, audited):** set a household's cookbook limit override; revoke any invitation; create an invitation for a household or start a new household with its first admin (support onboarding); disable / enable an account; unlock a login-throttled address.

**Destructive (tier 3, typed confirmation + audit):** withdraw a catalogue recipe; purge a recipe; purge the hidden recipes of a household.

**Never available, even to the super admin:** the contents of a household's cookbook, plans, shopping lists, stock or purchases; acting as another user; reading passwords or invitation codes.

## 10. The PR series

Each PR is deployable on its own and has tests; sizes S / M / L are for the review. "Visible" means a user sees a change.

**Cookbook (C)**
| PR | What | Size | Visible |
|---|---|---|---|
| C0 | This document | S | no |
| C1 | Migration 0028: the empty cookbook tables, `household_tag`, the limit override column | S | no |
| C2a | `RecipeTables`; `RecipeRepository` / `RecipeService` / search use it (catalogue only) | M | no |
| C2b | The same for `SuggestionRepository` and the shopping-list ingredient query | M | no |
| C2c | The same for `RecipeTagRepository`, `TagRepository`, `DietFilterRepository` | M | no |
| C3 | Copy engine, add one, limit and errors, `GET /api/cookbook/summary` | M | new calls |
| C4 | Cookbook list, search, detail; `inCookbook` on the catalogue | M | new calls |
| C5 | Remove, restore, bulk remove, removed view | M | new calls |
| C6 | Bulk add and add by filter | M | new calls |
| C7 | Household tags | M | new calls |
| C8a | Migration 0029: copy what plans and lists use, re-point, swap the foreign keys | L | no, if it works |
| C8b | "Rezept nicht verfügbar" marker and the count | M | planner |
| C9 | Shopping lists and preferred servings on cookbook recipes | M | switch |
| C10 | Pantry suggestions: `source`, equipment filter on both sets | M | home page |
| C11 | Create / edit own recipes with per-field errors | L | new |
| C12 | Ingredient search and match proposals | M | new |
| C13 | Photo upload (storage, limits, served only through the API) | M | later |

**Admin (A)**
| PR | What | Size |
|---|---|---|
| A1 | Platform role, `NOT_SUPER_ADMIN`, admin caller, read-only overview and lists (tier 1 without errors) | M |
| A2 | `admin_audit` and the tier 2 actions, `user_disabled_at` and `ACCOUNT_DISABLED` | M |
| A3 | `server_error` store and the errors view | S |
| A4 | Tier 3: withdraw and purge, after the catalogue withdraw rule exists | M |

**Later, after real use of the cookbook (L):** share / unshare / report, the removed-person flow, the base set for new households, link import, paper import.

Stopping points: after C7 the cookbook exists but nothing uses it; after C10 the app runs on the cookbook; A1 to A3 can run in parallel to C-work.

## 11. Risks and deploy notes
- **C8a** is the only PR that changes live plan and list data: dry run on staging, snapshot, staging first, rollback from the snapshot. C2a to C2c must not change any result (the existing tests are the proof).
- **C10** changes what the home page recommends: until the cookbook has recipes the cookbook suggestions are empty, so the toggle to the catalogue must be easy to find.
- Copying 1,000 recipes in one request would be slow: bulk add is capped at 200 per request.
- Migrations are applied by hand after a snapshot, before the API starts, as for all others.

## 12. Open items (decide later)
- The base set that loads into every new household's cookbook: authored in the repo seed, not needed for the alpha.
- Who may share (every member, with a rights statement) and what the statement says.
- A `STAFF` role.
- A clean public catalogue before an app-store listing.
