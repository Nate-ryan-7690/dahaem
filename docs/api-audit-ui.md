# API audit for the UI

**Date:** 2026-10-23 · **Status:** read-only analysis, no code changed · **Backlog item:** "API audit for the UI"
(`docs/BACKLOG.md`, section "API work for the frontend (from the UX spec)")

**Sources:** `docs/ux-product-spec.md` (§4 journeys, §5 pages, §12 support tickets), the controllers in
`src/main/java/de/dahaem/api/`, the generated reference `docs/API.md`, `schema.sql`. Nothing was run against a
database. Where the spec leaves a decision open this document lists an open question for Nate; it decides nothing.

## Summary

- **10 pages** audited: Login, Home, Meal Planner, Recipes, Shopping list, Inventory, Offers, Purchases / receipts /
  finances, Profile / household settings, Help & Support. (The first seven are the ones the backlog item names; the
  last three are the other pages the spec defines, §5.8, §5.9 and §12.)
- **87 calls** needed in total. **14 are covered** by an existing endpoint, **21 partly** (the endpoint exists but is
  inadequate), **52 are MISSING**. Per-page counts are in the table "Counts" below.
- The API today has **24 endpoints** (`docs/API.md`), none for stores, offers, settings, households beyond `/me`,
  receipts or support.
- **The backlog's "known so far" was slightly off in one place:** meal plans can also *remove* a recipe
  (`DELETE /api/meal-plans/{id}/recipes/{recipeId}`). They still cannot be edited or moved, and one week cannot be
  fetched by date.
- **Five findings look like bugs or contract problems**, recorded in the backlog and not fixed (see "Possible bugs").
  The most important: `GET /api/auth/me` cannot work for the PHP frontend, because under `X-Internal-Token` the
  principal is the fixed name `internal-api`, never a user, so no endpoint knows *which user or household* is asking.
- **19 open questions** for Nate are at the end.

Legend: **OK** = exists and fits · **PARTIAL** = exists but inadequate (what is wrong is in the notes) ·
**MISSING** = no endpoint.

---

## 1. Login (spec §5.1)

| # | Needed call | Existing endpoint | Notes |
|---|---|---|---|
| 1.1 | Check email + password, start a user session | `POST /api/auth/login` (form fields) — **PARTIAL** | Designed for the browser PWA (JSESSIONID cookie). The PHP frontend authenticates with `X-Internal-Token` and keeps its own session, so it needs "verify these credentials, tell me the user id" and not a Java session. No lockout / rate limit on repeated failures (not in the spec either). |
| 1.2 | Who is logged in (greeting, household) | `GET /api/auth/me` — **PARTIAL** | Reads the user from `authentication.getName()`. With the internal token that name is `internal-api`, so it answers 401 "Login required". Nothing on the API tells it *which* user the PHP session belongs to. |
| 1.3 | Pass the acting user on every other call | **MISSING** | No header / parameter for "on behalf of user X". Without it no endpoint can be scoped to a household or a user. Same root cause as 1.2. |
| 1.4 | CSRF token | `GET /api/auth/csrf` — OK | Only needed for the browser-session path; the PHP path is exempt. |
| 1.5 | Log out | `POST /api/auth/logout` — OK | Ends the Java session; PHP must still destroy its own. |
| 1.6 | Session expiry / "session expired" signal | **PARTIAL** | A request without a valid session gets a JSON 401, which is enough to react to. Expiry time and a distinction "wrong password" vs "expired" are not specified. |
| 1.7 | Create account / accept invitation | **MISSING** | Only a command-line runner (`UserAdminRunner`) creates users. Spec decision 6 (invitation vs registration) is open. |

## 2. Home (spec §5.2, §2A)

| # | Needed call | Existing endpoint | Notes |
|---|---|---|---|
| 2.1 | Greeting + household name | `GET /api/auth/me` — **PARTIAL** | See 1.2. |
| 2.2 | This week's plan (compact) | `GET /api/meal-plans` — **PARTIAL** | Returns *every* plan ever made, no date filter; the frontend would have to filter. Per entry only `recipeId`, `recipeName`, `mealTime`: no image, no date, no servings. |
| 2.3 | Meal images in the compact plan | **MISSING** | `MealPlanDto.PlannedRecipe` has no `thumbnailUrl`; the picture fields live only on the recipe DTOs, so the Home would need one `GET /api/recipes/{id}` per meal (the N+1 the spec §7 forbids). |
| 2.4 | Open a planned meal's details | `GET /api/recipes/{id}` — OK | |
| 2.5 | Suggestions from offers + inventory, with the reason | `GET /api/suggestions/by-savings`, `/by-expiring` — **PARTIAL** | Returns `recipeId`, `recipeName`, `score`, `matchingItemIds` only. No store / product / price / valid-until, no "in stock", no "still uncovered" list, no image. By-savings uses the curated `store_discount` table only, not the flyer offers in `store_offer`. No combined offers + inventory ranking; no diet filter. |
| 2.6 | Store deck (logos, pinned first) | **MISSING** | No store list at all (see 7.1, 7.5). |
| 2.7 | Expiring / low-stock summary | `GET /api/inventory/expiring`, `/low-stock` — OK | `threshold` is whole units; ignores that units differ (a threshold of 1 means 1 kg and 1 pack alike). |
| 2.8 | Shopping-list summary | **MISSING** | No "current open list" or list index; the id of a list is only known from the call that created it (see 5.2). |
| 2.9 | "Household essentials" summary | **MISSING** | Needs categories beyond food and a restock rule; inventory has `category` but no "restock due" notion. Spec decision 11 is open. |
| 2.10 | Chores / to-dos due | **MISSING** | Later-phase idea (spec decision 12); listed only for completeness. |

## 3. Meal Planner (spec §5.3)

| # | Needed call | Existing endpoint | Notes |
|---|---|---|---|
| 3.1 | Load one week by date | **MISSING** | Only `GET /api/meal-plans` (all weeks). Needs `?week=YYYY-MM-DD` or `/api/meal-plans/week/{date}` (spec §7: "fetch only the week needed"). |
| 3.2 | Create the week's plan | `POST /api/meal-plans` — OK | 409 for a second plan for the same week; the UI would rather get-or-create. `weekStartDate` is not checked to be a Monday. |
| 3.3 | Add a meal to *any* day, *any number* of times | `POST /api/meal-plans/{id}/recipes` — **PARTIAL** | Body is `recipeId` + a **required** non-blank `mealTime` text (a fixed slot, which the spec rejects). No date; the same recipe twice on one plan is a 409 because `meal_plan_recipe` is keyed (recipe, plan). Already in the backlog as "Meal plan: any day, any number of meals". |
| 3.4 | Remove a planned meal | `DELETE /api/meal-plans/{id}/recipes/{recipeId}` — **PARTIAL** | Exists, but addresses the recipe, not the entry, so it cannot remove one of two entries of the same recipe (which cannot exist yet). Needs an entry id. |
| 3.5 | Edit / move a planned meal (day, servings, label) | **MISSING** | No PUT / PATCH. |
| 3.6 | Servings per planned meal | **MISSING** | No servings column on the plan entry; `?people=N` is only a parameter of the shopping-list generation, one value for the whole plan. |
| 3.7 | Previous / next week | **MISSING** | Follows from 3.1: needs a week-by-date read. |
| 3.8 | Recipe picker (search, filter, page, thumbnails) | `GET /api/recipes` — **PARTIAL** | Returns all ~11,000 recipes. See 4.1. |
| 3.9 | Planned meal with image and detail | **MISSING** | See 2.3. |
| 3.10 | Generate shopping needs from the week | `POST /api/meal-plans/{id}/shopping-list` — OK | One `people` for the whole plan; see 3.6. Persisted list problems: see 5.x. |
| 3.11 | Plan is shared household state | **MISSING** | `meal_plan` has no household column (docs/DATABASE.md: one shared household today). Backlog "Household isolation". |

## 4. Recipes (spec §5.4)

| # | Needed call | Existing endpoint | Notes |
|---|---|---|---|
| 4.1 | Search by name / ingredient, filters, paging | `GET /api/recipes` — **PARTIAL** | Returns every recipe, no `q`, no `page` / `size`, no sort. Only diet / tag / item rules filter. The server-side diet filter loads the facts of every recipe per request. Already in the backlog ("Recipe search and paging"). |
| 4.2 | Small card DTO | `GET /api/recipes` — **PARTIAL** | `RecipeSummaryDto` already has name, thumbnail, times, tags, cuisine, rating; it also carries the whole `diet` block, which a card rarely needs. |
| 4.3 | Recipe detail, ingredients, instructions | `GET /api/recipes/{id}` — OK | |
| 4.4 | Scale to N people | `GET /api/recipes/{id}?people=N&basis=` — OK | Scaling stays in the backend as the spec asks. |
| 4.5 | Why is this recipe suggested (offers / stock evidence) | **MISSING** | See 2.5. |
| 4.6 | Add to the meal plan | `POST /api/meal-plans/{id}/recipes` — **PARTIAL** | See 3.3. |
| 4.7 | Add to the shopping list (one recipe or a few) | `GET /api/shopping-list?recipeIds=` — **PARTIAL** | Builds a list without saving; there is no "add these recipes' needs to a saved list". See 5.2 / 5.6. |
| 4.8 | Tags of a recipe, add a tag | `GET` / `POST /api/recipes/{id}/tags` — OK | Not a UI need in the spec; exists. |
| 4.9 | Save a recipe for later / favourites | **MISSING** | Mentioned only in the future inspiration idea (§2A); not needed for the first release. |
| 4.10 | List of tags / cuisines for the filter UI | **MISSING** | No endpoint lists tags, cuisines or presets (`config/diet_presets.json`), so the filter controls cannot be built from data. |

## 5. Shopping list (spec §5.5, journey C)

| # | Needed call | Existing endpoint | Notes |
|---|---|---|---|
| 5.1 | Preview needs for some recipes (not saved) | `GET /api/shopping-list?recipeIds=&people=` — OK | A GET that computes; does not subtract inventory (the persisted path does). |
| 5.2 | Create + save a list from a plan | `POST /api/meal-plans/{id}/shopping-list` — **PARTIAL** | Saves, but: items that no store sells are silently left out; the unit is dropped; fractional quantities are rounded up to an integer (TODO in `ShoppingListService`). |
| 5.3 | Get the current / latest / all lists | **MISSING** | Only `GET /api/shopping-lists/{listId}`; no index, no "open list of this week", no list by meal plan. |
| 5.4 | Load a saved list with options | `GET /api/shopping-lists/{listId}` — **PARTIAL** | `quantityNeeded` is an `int` with **no unit**; `isManual` is returned but nothing can add a manual line; the per-line `purchase_status` column is not in the DTO. Options are present (store, brand, package, prices, sale, deposit, till price, packs). |
| 5.5 | Choose / change / clear the store product of a line | `PUT /api/shopping-lists/{listId}/items/{itemId}/store-item` — OK | |
| 5.6 | Add a manual line (any item, free text) | **MISSING** | Column `is_manual` exists; no endpoint. |
| 5.7 | Change quantity, remove a line | **MISSING** | |
| 5.8 | Tick a line "bought" | **MISSING** | `shopping_list_item_purchase_status` exists in the schema, never written. |
| 5.9 | Complete the shopping → purchase history | **MISSING** | `shopping_list_status` is returned as `status` but nothing sets it; no link from a ticked line to `POST /api/purchases`. Backlog "Shopping list lifecycle". |
| 5.10 | Refresh prices, warn on expired / changed offer | **MISSING** | Options are recomputed on each load, but the list stores no price snapshot, so "changed since you chose" cannot be told. `validUntil` is present, so "expired" is derivable client-side. |
| 5.11 | Group by store | derivable from 5.4 | Only for lines with a selection; no endpoint needed, but no `store_section` either (column exists, unused). |
| 5.12 | Unavailable / unpriced items made explicit | **MISSING** | Dropped on save (see 5.2); the preview shows empty `storeOptions`. |
| 5.13 | Delete / archive a list | **MISSING** | |

## 6. Inventory (spec §5.6, journey E)

| # | Needed call | Existing endpoint | Notes |
|---|---|---|---|
| 6.1 | List stock with quantity, unit, expiry | `GET /api/inventory` — **PARTIAL** | The whole catalogue ordered by English name, including items at quantity 0; no search, filter by category, or paging. `quantity` is an integer. |
| 6.2 | Low-stock items | `GET /api/inventory/low-stock` — OK | See 2.7 about units. |
| 6.3 | Expiring items | `GET /api/inventory/expiring` — **PARTIAL** | Only the single `earliestExpDate` per item; no batches. |
| 6.4 | Add stock | **MISSING** | Only `POST /api/purchases` changes stock, as a side effect. |
| 6.5 | Consume / correct stock | **MISSING** | |
| 6.6 | Change an expiry date | **MISSING** | |
| 6.7 | History of changes | **MISSING** | No history table. Backlog "Inventory writes". |
| 6.8 | Low-stock → add to the shopping list | **MISSING** | Needs 5.6. |
| 6.9 | Low-stock → plan a recipe | `GET /api/suggestions/by-expiring` — **PARTIAL** | Expiring only; nothing for low-stock. |
| 6.10 | Inventory check-in ("voll / halb / fast leer") | **MISSING** | Backlog idea; not in the spec. |

## 7. Offers (spec §5.7, §2A)

| # | Needed call | Existing endpoint | Notes |
|---|---|---|---|
| 7.1 | List participating stores (name, logo) | **MISSING** | `store` has only an id and a name. No logo, no endpoint. Backlog "Stores and pinned stores". |
| 7.2 | Current offers of one store | **MISSING** | The `store_offer` table is filled by the importers; no read endpoint. |
| 7.3 | Offers by category / search / paging | **MISSING** | |
| 7.4 | Offer detail (product, price, deal, deposit, valid from / until, source, region) | **MISSING** | All columns are in `store_offer` / `store_offer_price_change`. |
| 7.5 | Pinned stores per user | **MISSING** | No table. Presentation order only, never a filter. |
| 7.6 | Offers connected to generic items / recipes, with coverage | **PARTIAL** | Only through the shopping list's `storeOptions` and by-savings suggestions, both limited to curated `store_discount`. |
| 7.7 | Uncertain matches shown as uncertain | **MISSING** | Match data exists (`store_product_inventory_item_id`, variants, review reports) but is not exposed. |
| 7.8 | Offer validity / region | **MISSING** | `store_offer_region` exists; no way to choose a region (see open question). |

## 8. Purchases / receipts / finances (spec §5.8, journey D — "later workflow")

| # | Needed call | Existing endpoint | Notes |
|---|---|---|---|
| 8.1 | Record a purchase (updates inventory) | `POST /api/purchases` — OK | Takes a `storeItemId`; one line per call. |
| 8.2 | Purchase history | `GET /api/purchases` — **PARTIAL** | Whole history, no paging, no date filter, only ids (`storeItemId`) — no product, store or item name. |
| 8.3 | Spending total (period) | `GET /api/analytics/spending` — **PARTIAL** | One number; no breakdown by store / category / month. |
| 8.4 | Upload / import a receipt | **MISSING** | Receipt import is a command-line runner (`ReceiptImportRunner`). |
| 8.5 | Review matches, confirm or correct | **MISSING** | |
| 8.6 | Confirm → purchases + inventory | **MISSING** | The receipt import "never touches stock" by design. |
| 8.7 | Price history of a product | **MISSING** | `store_price_history` exists. |

## 9. Profile / household settings (spec §5.9, journey F, decisions 6 and 7)

| # | Needed call | Existing endpoint | Notes |
|---|---|---|---|
| 9.1 | Read / change own profile (display name, password) | **MISSING** | |
| 9.2 | Personal settings (language, theme) | **MISSING** | No table. Which settings are personal is open (decision 7). |
| 9.3 | Household settings (shared) | **MISSING** | |
| 9.4 | Members: list, invite, remove, roles | **MISSING** | Tables `household_member` / role exist; only `/me` reads them. |
| 9.5 | Food rules per person (hard / soft) | **MISSING** | The recipe endpoints take the rules as parameters; nowhere to *store* them. Backlog "Food questionnaire". |
| 9.6 | Data scoped by household | **MISSING** | Backlog "Household isolation". |

## 10. Help & Support (spec §12 — backend "do now", pages later)

| # | Needed call | Existing endpoint | Notes |
|---|---|---|---|
| 10.1 | Create / list / read tickets | **MISSING** | Whole subsystem; backlog "Support tickets: backend foundation". |
| 10.2 | Messages (user-visible vs staff-only) | **MISSING** | |
| 10.3 | Change status / close | **MISSING** | |
| 10.4 | Review, approve, decline, revoke a support access request | **MISSING** | |
| 10.5 | Audit events visible to the household | **MISSING** | |

---

## Counts

| Page | Calls | OK | PARTIAL | MISSING |
|---|---:|---:|---:|---:|
| 1 Login | 7 | 2 | 3 | 2 |
| 2 Home | 10 | 2 | 3 | 5 |
| 3 Meal Planner | 11 | 2 | 3 | 6 |
| 4 Recipes | 10 | 3 | 4 | 3 |
| 5 Shopping list | 13 | 3 | 2 | 8 |
| 6 Inventory | 10 | 1 | 3 | 6 |
| 7 Offers | 8 | 0 | 1 | 7 |
| 8 Purchases | 7 | 1 | 2 | 4 |
| 9 Profile / household | 6 | 0 | 0 | 6 |
| 10 Help & Support | 5 | 0 | 0 | 5 |
| **Total** | **87** | **14** | **21** | **52** |

Row 5.11 (group by store) is counted OK because the data to group by is already in the list answer.

## Possible bugs and contract problems found

Recorded in the backlog, **not fixed** (this PR is documentation only).

1. **`/api/auth/me` fails for the PHP frontend.** With `X-Internal-Token` the principal is `internal-api`;
   `AccountService.findByEmail("internal-api")` finds nothing → 401. No call carries the acting user.
2. **Items nobody sells are dropped from a saved shopping list** (`persistGeneratedList`:
   "nothing meaningful to persist"), which contradicts spec journey C step 7 ("make unavailable items explicit").
3. **A saved list loses the unit and rounds up to an integer** (`quantityNeeded` is `INTEGER`, TODO in code). "500 g"
   and "500 ml" become `500`.
4. **The inventory is subtracted without comparing units** (`missing = totalQuantity - inStock` in
   `persistGeneratedList`; `Item.getQuantity` is in the item's own unit). Needs a read-only check whether recipe unit
   and stock unit can differ for the same item before this is called a bug.
5. **Meal-plan endpoints load all plans to find one id** (`mealPlanService.allPlans().stream().filter(...)` in the
   add, remove and shopping-list handlers) and `GET /api/meal-plans` runs one query per plan. Harmless today, grows
   with the plan history.

## Open questions for Nate

Questions the spec already lists as open decisions are marked (spec #n).

1. How does the PHP frontend tell the API who the user is: a trusted `X-User-Id` header next to the internal token,
   or a login call that returns a user id PHP stores in its session? (Affects every endpoint; see 1.1–1.3.)
2. Account creation: invitation by a household admin, or something else? (spec #6)
3. Which preferences are personal and which household-wide? (spec #7)
4. Where are the food rules stored, and does a household apply the hard rules of every member to the shared plan?
   (backlog "Food questionnaire")
5. Meal-plan entry: should the same recipe be allowed several times a day / a week? (The spec says "as many planned
   meals as they need".)
6. Is "week" always Monday–Sunday, and may a plan span any date range?
7. Servings: one value per planned meal, or per plan, or per household default?
8. When a plan week is opened but no plan exists, should the API create it implicitly (get-or-create) or answer
   "none"?
9. Shopping list: one open list per household at a time, or several?
10. What does "ticked off" mean for inventory: does completing a list create purchases at the *selected* store
    items, and what happens to lines with no selection?
11. Should lines nobody currently sells stay on the list (as "unavailable") and may the user resolve them manually?
12. Offers: flyer `store_offer` rows versus curated `store_discount` rows — which one does the UI show, and is the
    store `region` chosen by the user or per household?
13. Store logos / banners: usage rights and fallback (backlog "Stores and pinned stores").
14. Home store deck: one store at a time or several on desktop? (spec #10; affects only the DTO if banners differ)
15. "Household essentials": what makes a non-food item "due" (a threshold, a schedule, a check-in)? (spec #11)
16. Inventory: are expiry dates per batch or one date per item? (Affects 6.3, 6.6.)
17. Recipe images: sourcing and fallback; does the API return a fallback or does PHP? (spec #5)
18. Receipts: is the upload a PHP concern (file to the API) or does PHP pass text? (spec journey D)
19. Chores and culinary inspiration: out of the first release, as the spec says? (spec #12, #13)

## How the counts were made

Each row of the ten tables above is one needed call, classified by the bold word in the "Existing endpoint" column
(**MISSING**, **PARTIAL**, otherwise OK). The counts were produced by a script over those rows; re-count after editing.
