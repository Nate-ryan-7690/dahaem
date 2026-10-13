# DAHÄM — UX, Product Design & Implementation Brief

**Document status:** Living working specification  
**Started:** 13 October 2026  
**Purpose:** Capture product decisions, UX requirements, architecture recommendations, dependencies, open questions, and implementation work so the owner can review the plan and hand it to coding agents without having to reconstruct decisions from chat.

> This is a planning document, not authorization to change the repositories or live database. Items marked **Proposed** still need owner approval. Do not silently turn proposals into requirements.

---

## 1. Product vision and design principles

DAHÄM is a self-hosted household-management platform. Food and shopping are the first practical domain, but the longer-term vision includes general household inventory, recurring supplies, pet supplies, household finances, and planning.

DAHÄM should help households gather and connect information, surface useful possibilities, and understand prices, availability, and consequences. **People make the decisions; the application must not silently choose meals or products for them.**

### Design direction

- Fresh, modern, minimalistic, and approachable to a broad range of people—not tailored only to the owner.
- Clear and warm, not clinical, corporate, childish, or overly decorative.
- Minimalism must improve understanding, not hide important actions or information.
- Avoid defaulting to a generic admin dashboard or designing pages to mirror backend entities.
- Design around user goals and connected household workflows; let implementation architecture follow the UX requirements.
- Responsive by design: desktop and mobile may use different compositions when that improves usability.
- Privacy is a product principle: household data is private, is never to be sold, and should not be accessed by the operator unless needed to help with a user-reported issue and access is appropriately authorized. The technical design must make this principle real, not merely a policy statement.
- Keep controls understandable and accessible; important meaning must not depend on color alone.
- **Selected visual direction: Fresh & Natural.** This is a confirmed broad direction, not a final color palette. DAHÄM should feel fresh, calm, welcoming, and modern, supporting the goal of making healthy, fresh meals easier to manage without stress and helping households reduce food waste and unnecessary shopping. Green is welcome as a brand cue, but should be used thoughtfully rather than covering every surface or making the interface feel overly themed.
- Actual colors, typography, imagery, and component styles are not finalized. Define and test these details against accessibility, clarity, broad appeal, and a minimal visual hierarchy.
- **Theme proposal, not yet confirmed:** light as the initial/default theme, with a dark theme available as a user preference.

### Visual system — broad direction selected, details still open

The owner selected **Fresh & Natural** because DAHÄM should help people make healthy, fresh meals without the stress that can lead to relying on fast food or prepared meals, while also helping reduce food waste and unnecessary shopping. The owner likes green but explicitly recognizes that personal taste should not make the product feel designed only for them. Use green as a considered brand accent, balanced with warm, neutral surfaces and strong readable text; do not make every component green.

The next design workstream is to refine the actual palette, typography, spacing/rhythm, imagery, icon style, component shapes, and interaction states. Keep the experience fresh, modern, minimalistic, and approachable to a wide range of households. Avoid a generic admin-dashboard aesthetic and use visual elements to support clarity and everyday usefulness, not decoration.

The owner is leaning toward a warm off-white/cream base rather than clean white. In the first palette comparison, the two strongest green candidates were soft sage and muted olive. The owner personally prefers olive, but thinks sage communicates freshness and health more clearly. **Current directional recommendation (not a final palette decision): use soft sage as DAHÄM's primary brand green, and explore muted olive as a restrained secondary natural accent only if the two work together without making the interface visually muddy.** Do not force a choice based only on personal taste or symbolism; evaluate the colors in actual UI screens and at accessible contrast levels.

Next, compare the sage-led direction—with warm off-white surfaces, readable dark text, and potentially a small amount of olive—with an olive-led alternative in representative components and home-page sections. Check contrast and color-blind accessibility; define semantic roles for action, focus, warning, error, sale/discount, and stock states. Store logos and supermarket campaign colors must remain distinct within DAHÄM's own visual system.

The broad Fresh & Natural direction is decided. Warm off-white is the current background preference, not yet final. Sage-led versus olive-led palette, exact color tokens, light/dark theme defaults, and all remaining visual-system details remain open.

## Visual direction update — green palette exploration (13 October 2026)

The owner is torn between soft sage and muted olive: olive is the personal favorite, while sage seems to communicate freshness and health more directly. Do not treat the personal preference as the sole criterion. Recommended next exploration: a sage-led Fresh & Natural palette, with warm off-white and an optional restrained olive supporting accent, compared against an olive-led alternative in actual home-page and component previews. No exact color palette is approved yet.

## 2. Decisions and direction captured so far

### Confirmed from the discussion

1. The next work is planning the PHP UI/UX for DAHÄM, not translating the Java/backend structure into a frontend design.
2. Track decisions and outstanding work in this living brief so the owner can review it later and provide it to coding agents.
3. Login is required as the entry point.
4. After login, each user gets a personal home-page experience (for example, their greeting/profile preferences).
5. The home page should put the meal plan first, rather than leading with generic statistics.
6. The meal plan should have a calendar-like layout and show meal images where available.
7. **Freedom is key:** users must not be forced into fixed breakfast/lunch/dinner slots. They can add meals freely to any day, with as many planned meals as they need.
8. On desktop, the calendar should show the entire seven-day week at once.
9. Mobile layout should adapt to the smaller screen rather than merely compressing the desktop's seven columns. Exact mobile interaction is not yet finalized.
10. Meal planning and recipe functionality are substantial enough to warrant dedicated user-facing space rather than being squeezed into the home page.
11. Performance must not be sacrificed for visual design quality.
12. Ask **one question at a time**. Keep questions short and avoid requiring the owner to write an essay to move planning forward.

### Current architectural recommendations (not all explicitly approved)

- Give the recipe catalogue and meal planner separate routes/pages because browsing recipes and organizing a week are different user tasks.
- Reuse recipe cards, recipe details, image handling, search, and data services between the catalogue and the meal planner's recipe picker.
- The home page uses a compact view of the weekly plan; it should not implement a second, independent meal-planning system.
- When adding a meal, a recipe picker can open from the calendar so the user retains context, while a dedicated Recipes page remains available for deeper browsing. This interaction is recommended, not yet confirmed.
- A responsive desktop week grid and mobile selected-day view are likely a good starting point; validate the visual design before locking this in.
- Avoid building a single-page application solely for the sake of a modern UI. Server-rendered PHP plus focused JavaScript can provide interactive calendar/picker behavior if implemented cleanly.

## 2A. Home page content hierarchy and future inspiration content

### Current home-page direction

The home page should remain focused rather than becoming a long dashboard. The current proposed order is:

1. **Weekly meal plan** — the primary, user-relevant section.
2. **Recipe/meal suggestions linked to current offers and household inventory** — show why each suggestion is relevant, what is on offer, and what the household already has. These suggestions are not the same as general editorial inspiration.
3. **Store offers** — recognizable store branding, pinned stores shown first, and a horizontally scrollable set of participating stores. The exact brand-banner composition differs by device and remains open.
4. **Household essentials** — a compact cross-category summary of things needing attention (e.g. pet supplies, cleaning products, toiletries or other household items running low), linking to the relevant management view.

### Future idea: culinary inspiration and discovery

The owner would eventually like DAHÄM to offer inspiration that is **not dependent on current discounts**. This goes beyond nutrition articles or generic recipe highlights: it should recreate the feeling of culinary discovery the owner valued in HelloFresh—being introduced to exciting dishes from around the world that they might never otherwise have encountered. This is an experience goal, not a request to copy HelloFresh's interface or business model. DAHÄM should make discovery feel approachable, inviting, and rewarding, for people with different cooking skills and backgrounds.

Potential content and experiences include:

- Introducing dishes and food traditions from different cultures around the world, including the context that makes them interesting.
- Making unfamiliar recipes feel achievable with clear instructions and helpful technique guidance.
- Baking ideas and guided baking projects.
- Homemade foods and techniques, such as making noodles from scratch.
- Seasonal meal highlights, ingredient spotlights, and cooking inspiration.
- Trustworthy nutrition articles and practical food education.
- A natural path from discovery to viewing a recipe, saving it for later, adding it to the meal plan, or generating the related shopping needs—without requiring that the recipe be on sale.

The experience should support serendipity and variety: show people possibilities they would not necessarily search for themselves, while explaining enough for them to confidently decide whether to try them. Represent different culinary traditions respectfully and avoid stereotypes; nutrition and health claims should be trustworthy, appropriately sourced, and reviewed.

This is a future content/inspiration idea, not a requirement for the initial home-page build. Keep it conceptually separate from sale/inventory-driven recipe opportunities:

- **Recipe opportunities** answer: “Why is this meal useful to consider right now?” They are based on signals such as current offers and household inventory.
- **Culinary inspiration** answers: “What could I discover, learn to make, or enjoy cooking?” It can surface dishes, baking projects, culinary traditions, and educational content regardless of discounts.

The experience should be welcoming to a broad range of users and skill levels, not assume advanced cooking knowledge, and present cultural food traditions respectfully rather than reducing them to stereotypes. Nutrition and health claims should be trustworthy, appropriately sourced, and reviewed.

When this capability is designed, it could appear lower on the home page or in a dedicated discovery/inspiration area; placement and presentation remain open. Do not let editorial content push the weekly plan and practical household actions out of prominence.

**Status:** Future direction accepted in principle; exact format, placement, content sourcing, and whether content is curated, partner-provided, or generated remain undecided. Do not build this into the initial scope unless the owner reprioritizes it.

## 3. Product architecture context (not a UI template)

The backend describes capabilities; the UI should combine them around household tasks. Do not reproduce the backend package/table structure as navigation merely because it exists in code.

### Current repositories and stack

- **PHP presentation repository:** `Nate-ryan-7690/dahaem-frontend`
- **Java engine/API repository:** `Nate-ryan-7690/dahaem`
- PHP/Apache is the presentation layer, session/auth layer, and client of the internal API.
- Java/Spring Boot owns business logic behind the REST API.
- MariaDB is shared by the application services in the current deployment.
- The Java API is intended to remain internal to the Docker network; the browser talks to PHP, not directly to Java.
- PHP currently has `src/api.php` as an internal API client and `src/db.php` as a PDO connection helper. The current frontend also includes very basic `index.php` and `store.php` implementations that can be reviewed and evolved rather than assumed to be the final UX.

### Relevant existing backend capabilities documented in the project README

- Authentication endpoints for login/logout/current user and CSRF token handling exist on the Java side, but the frontend's correct auth/session integration still needs to be reviewed.
- Recipe listing and recipe-detail endpoints exist. Recipe detail includes ingredients, instructions, and serving scaling.
- Meal-plan list/create and add-recipe operations exist.
- Shopping-list generation can combine recipe requirements and store options; store-product selection has an endpoint.
- Inventory read endpoints include all items, low-stock items, and items approaching expiration.
- Purchase and spending-analytics endpoints are documented.
- Do not assume all UI-required write operations already exist. Confirm endpoint behavior before designing or implementing controls for inventory adjustments, shopping-list persistence/completion, user-specific settings, etc.
- Recipe search/pagination should be assessed before the PHP catalogue loads a large recipe collection. Do not fetch thousands of recipes to the browser for client-side filtering.

## 4. Core user journeys and connected information

These flows come from the established DAHÄM product vision and should inform the UI. They are not separate, disconnected features.

### A. Offers → recipe opportunities → meal choice

1. DAHÄM ingests and maintains store catalogues and time-limited offers.
2. Recipes are connected to generic household ingredients.
3. The system finds recipe opportunities that overlap with current offers and household inventory.
4. UI explains why a recipe is suggested: which ingredients are covered by offers, the concrete store product/price, what is already in stock, and which ingredients remain uncovered.
5. Household chooses whether to cook the recipe and can carry specific product/offer selections into shopping.

**Rule:** show alternatives and evidence; do not automatically choose a meal, store, or product.

### B. Meal plan → shopping requirements

1. User adds recipes freely to calendar days; fixed meal slots are not required.
2. Servings/quantities may be adjusted where supported.
3. Recipe ingredients become purchase requirements.
4. Shared ingredients across recipes should aggregate to avoid duplicates.
5. Existing household inventory reduces what needs to be bought, where the current system supports the required quantity/unit calculation.
6. User can move from the planned meal to its recipe or related shopping requirements without losing context.

### C. Shopping list → store/product selection → real shopping

1. Shopping requirements remain generic household needs (e.g. milk) until a user selects a concrete store product.
2. Show eligible store products, package sizes, brands, relevant current prices, and offers; distinguish quantity needed from package size and purchase quantity.
3. Keep multiple options visible where available; do not silently pick the cheapest one.
4. Let users change selections (for example, ALDI milk to PENNY milk).
5. Group items by store for practical shopping, while retaining a coherent overall list.
6. Refresh/check relevant price/offer information when appropriate. Warn if a selected offer expired or a price changed; do not silently substitute.
7. Make unavailable/unpriced items explicit and provide a way to resolve them when backend capabilities permit.
8. Shopping completion should become purchase history rather than simply disappearing.

### D. Purchases/receipts → inventory and finance → better future choices

1. Receipt import should extract candidate lines, quantities, prices, date, and store.
2. Propose matches to store products and shopping items.
3. Ask the household to confirm or correct uncertain matches before consequential updates.
4. Confirmed purchases update purchase history and reconcile inventory.
5. Purchase history supports spending insights and price-history/trust improvements.
6. Confirmed corrections can improve future product matching and suggestions.

### E. Inventory → recipes and purchasing

- Inventory should be the best available representation of what the household currently has, with quantities and units that can be compared to recipe needs and store package sizes.
- Track useful changes such as additions, consumption, corrections, and expiry when implemented.
- Low-stock and expiring items should link to sensible actions: adjust stock, plan a recipe, or add a requirement to the shopping list.
- Do not ask the household to buy what it already has, provided the inventory quantities and units support that conclusion.

### F. Household sharing

- Personal presentation (greeting, language/theme preference, individual notifications) can vary by user.
- Household data (meal plan, inventory, shopping list, purchases and shared settings) needs one shared authoritative state for members of a household.
- Simultaneous devices/users should converge on the same state. Conflict handling, authorization, audit history, and multi-household isolation are future platform requirements, not problems to paper over in the UI.

## 5. Pages and responsibility boundaries

### 5.1 Login / session entry — required first

Purpose: securely identify the user and load the correct personal/household context.

UX requirements:
- Email and password, show/hide password.
- Clear invalid-credentials and validation messages; no exposure of sensitive internal errors.
- Loading/submission state and keyboard accessibility.
- Session persistence and explicit logout; sensible behavior when a session expires.
- Registration/invitation/account provisioning decision is open. Do not invent public self-registration until account and household setup has been planned.

### 5.2 Home page — personal entry point, meal plan first

Purpose: answer “what is happening with our meals this week, and what needs attention next?”

UX direction:
- Personalized greeting/profile context.
- Compact weekly meal calendar is the first and dominant section.
- Show the whole week's overview in a compact way and meal pictures when available; missing images must have a designed fallback.
- Clicking a planned meal opens its details; users can navigate to the full planner.
- Immediately beneath the weekly meal plan, show a small set of meal/recipe suggestions based on current offers and household inventory. Explain why each suggestion is relevant (e.g. discounted ingredients, ingredients already in stock, and what remains uncovered). Suggestions are optional; they must not automatically alter the user's plan.
- Further down, supporting sections may include the store-offers section, expiring/low-stock items, and a shopping-list summary. These must be actionable, not vanity metrics.
- DAHÄM serves the whole household, not only meal planning. Home should eventually surface relevant non-food needs too, such as pet supplies, cleaning products, and recurring household essentials. Prefer a single flexible **Household essentials / Around the home** summary that surfaces things needing attention (e.g. running low or due for restock), rather than giving every category its own large permanent homepage section. Detailed management can live in dedicated views as those capabilities mature. This is a recommendation to guide the design, not a finalized layout.
- Include a visually clear supermarket-discovery/offers section (e.g. “See what's on sale”) featuring participating store identities/logos such as ALDI, Lidl, REWE, famila, and other available stores. Selecting a store should take the user to relevant current offers for that store.
- Store presentation should support discovery without overwhelming the home page. The current direction is a horizontally scrollable row/card deck of stores, with user-favorited/pinned stores prioritized; other participating stores remain discoverable.
- Store identities have distinct branding and promotional layouts. **Open design point:** should the home page show one prominent selected-store feature at a time, using that store's logo and banner/promotion style, or show several store-branded features/cards simultaneously? The owner leans toward one store at a time on mobile and has not decided what makes most sense on desktop. Do not lock this layout until discussed.
- Users should be able to personalize which stores they commonly shop at; store preferences must affect presentation/order, not silently restrict access to other available stores or offers.
- Keep the homepage useful as DAHÄM expands beyond food: include a lightweight route into whole-household essentials (including pet and cleaning supplies), but avoid cluttering the meal-first page with a separate large section for every future domain.
- **Chores / household to-dos — proposed later-phase capability:** recurring household tasks (e.g. take out bins, clean litter tray, replace a filter) fit the broader household-management vision. Do not expand the initial MVP into a general-purpose to-do/project-management app. If added later, keep it focused on household upkeep and recurring routines, with only due/overdue tasks surfaced in a compact Home summary. This remains open for owner decision.
- Do not assume third-party store banners are available in a consistent format or can be reused freely. Review the available assets/data and use a consistent branded fallback if official promotional assets are unavailable or unsuitable.
- Do not show fabricated placeholder figures as real household data. Development mock data must be clearly identified.
- Avoid duplicating business logic or having the home calendar diverge from the full meal planner.

### 5.3 Meal Planner — dedicated page

Purpose: organize the household's week.

Requirements:
- Desktop shows all seven days at once.
- Flexible planning: no mandatory fixed meal periods. Any day can have zero, one, or many meals.
- Each planned meal can display an image (when available), title, and useful detail without excessive visual density.
- Add/edit/remove or move planned meals should be easy and clearly confirmed.
- Previous/next week navigation.
- A recipe picker should make it convenient to add meals. Opening it as an overlay/drawer is a recommendation; final interaction pattern not yet approved.
- Support servings/scale details when the backend permits it.
- Mobile uses a deliberately redesigned view rather than squeezing the desktop grid. A row of days plus a selected-day list is a candidate direction, not yet finalized.
- Meal plan is shared household state, even though the page is reached from a personal home page.

### 5.4 Recipes — dedicated page (recommended architecture)

Purpose: explore the catalogue beyond the immediate task of filling a calendar slot.

Requirements:
- Fast search, paging, and filters based on actual available data.
- Recipe card: name, image/fallback, and truly available useful metadata.
- Details include ingredients and instructions; serving scaling should reuse backend behavior rather than reimplementing scaling inconsistently in PHP.
- Clear actions to add to the meal plan and/or shopping workflow, with distinct meanings.
- Recipe opportunities based on offers/inventory should explain why each recipe is relevant and show evidence/alternatives.
- Reuse recipe UI pieces in the planner picker; the page itself remains a separate task space.

### 5.5 Shopping list — planned connected workflow

Requirements are in journey C above. Important UX distinction: generating requirements, persisting a list, selecting a store product, marking an item bought, and recording the purchase are different states and should not be conflated. Verify which operations already exist in the API before implementation.

### 5.6 Inventory — planned connected workflow

Requirements are in journey E above. The list should make stock quantity/unit and expiry/low-stock state easy to understand. Confirm write API support before exposing interactive controls.

### 5.7 Offers / recipe opportunities — planned connected workflow

The current basic store-discount screen is a starting point only. The long-term experience should connect offers to generic ingredients and recipes, show concrete products/brands/prices/offer periods and provenance as available, and explain coverage and unresolved items. Never silently guess uncertain mappings or make a purchase choice for the user.

### 5.8 Purchases / receipts / finances — later workflow

Keep purchase confirmation, receipt matching, inventory update, spending, and budget capabilities connected. Consequential updates from ambiguous receipt lines must require confirmation.

### 5.9 Later platform areas

- User profile/preferences and notifications.
- Shared household members, permissions, sync/conflict handling and audit history.
- Security/remote access/backup hardening and multi-household isolation.
- Future non-food household supplies, pet supplies, clothing, recurring needs, and general household planning. A focused recurring household-chores feature may fit here later; a broad general-purpose task manager is outside the current core scope unless separately approved.

Do not put all future areas in initial navigation if they are not functional. Planned/coming-soon states must be intentional and clearly marked.

## 6. Design-system work required before broad page implementation

Do not decide visual tokens based only on text descriptions. Prepare a small set of distinct visual concepts and compare them against real key screens before selecting the direction.

The approved system should specify:
- Typography and hierarchy.
- Color tokens, theme behavior, and contrast.
- Spacing, density, borders, corner treatment, and focus states.
- Image ratios, cropping, placeholder/fallback treatment, and loading behavior.
- Buttons, forms, badges, alerts, empty states, loading states, errors, dialogs/drawers, and keyboard behavior.
- Desktop, tablet, and mobile breakpoints/layout transitions.
- Interaction patterns for adding/editing meals, searching recipes, choosing offers/products, and updating shared lists.

The resulting style must be fresh, modern, minimalist, approachable to varied households, and function-first. Avoid designing a generic dashboard by default.

## 7. Performance and implementation guidelines

- Keep PHP as presentation/session/API-client layer; Java remains the authoritative business-logic/API layer.
- Prefer server-side recipe search and pagination. Never load the full large recipe database into the browser just to filter it.
- Fetch only the calendar week needed for the current view; load recipe instructions/details when opened rather than for every calendar entry.
- Use appropriately sized images/thumbnails in calendars and search results; load larger assets for detail views. Provide fallbacks for missing images.
- Avoid N+1 API requests; prefer appropriate batch/list endpoints for a page's visible data where useful.
- Keep shared CSS/design tokens centralized. Load page-specific JavaScript only where necessary; do not create a large frontend framework without a concrete need.
- Define loading, empty, error, stale-data, and retry states before claiming a workflow is complete.
- Maintain accessibility and avoid controls available only on hover.
- Do not make up endpoint functionality. Check current Java controllers/services/API docs and record missing endpoints as explicit backend tasks.
- Visual quality and performance are both requirements. If a visual solution is expensive, test simpler options rather than sacrificing either one by assumption.

## 8. Suggested implementation sequence

This is the current suggested sequence, not yet a commitment to implement every item in this exact order.

1. **Review existing frontend/backend contracts and capture baseline.** Inspect PHP auth, `api.php`, existing page routes, Java API endpoints, and current responsive assets. No live DB mutation.
2. **Approve user journeys and information architecture.** Login, Home, Meal Planner, Recipes, then connected shopping/stock/offer flows.
3. **Explore and approve visual direction.** Create real screen concepts for login, home, and weekly planner; compare styles before locking tokens.
4. **Foundation:** session/auth integration, shared application shell, navigation, design tokens, accessible common components, responsive behavior.
5. **Recipe catalogue/details:** search, server-side paging/filtering, images/fallbacks, servings and calls to actions.
6. **Meal planner:** week query/display, flexible slots, add/edit/remove/move flows, recipe picker, mobile composition.
7. **Home page:** reuse the meal-plan data/components in a compact weekly overview, then add only useful connected summaries.
8. **Shopping list and offer selection:** verify/close API gaps, respect generic-vs-concrete product separation, refresh prices and handle changed/expired offers.
9. **Inventory integration:** reliable quantities/units and stock/expiry states; verify write endpoints.
10. **Purchase/receipt/finance loop:** review-confirm-update flow and spending history.
11. **Support/ticket subsystem foundation (parallel workstream):** finalize the domain model, lifecycle, API contracts, authorization gate, audit model, and security tests now. The core ticketing backend can be implemented before a hosted service exists; actual operator access to household content must remain disabled unless a valid, ticket-bound, explicitly user-approved grant is enforced server-side.
12. **Polish and validation:** responsive, accessibility, performance, error/empty/loading states, household multi-user behavior and end-to-end user journeys.

## 9. Required repository and data-safety rules for coding agents

These rules already exist in the repositories and must be followed by any agent working on this plan:

- Never commit or push directly to `main`. Create a feature branch, make the changes there, push it, and open a pull request for owner review. Nothing merges without owner review.
- Never perform destructive changes or schema/data reimports against the live MariaDB database. Tests use a throwaway test database.
- Any approved change to live data/schema requires a timestamped snapshot first, with no more than three snapshots retained, and an explicit owner-approved plan. Do not do such work as part of UI design by default.
- Never run `docker compose down -v`, remove/recreate the live DB volume, or overwrite the live database/export.
- Secrets belong in environment variables; do not commit `.env`, credentials, or real-data snapshots.
- In PHP, use prepared statements for SQL and escape rendered user/data values with `htmlspecialchars()` (or the appropriate context-specific escaping). Prefer the internal API for business logic; do not duplicate Java business rules in PHP.
- Keep uncertain product/receipt mappings unresolved until reviewed rather than silently guessing.
- Do not alter unrelated work or branches. Review current Git state before edits and clearly report scope.

## 10. Decision log and open items

### Decided

- Flexible meal planning, no fixed meal-period slots.
- Entire seven-day calendar visible on desktop.
- Mobile should have an adaptive, purpose-designed layout.
- Calendar meal entries should support a meal picture when available.
- Meal planning deserves its own dedicated page.
- Home should be user-personalized in presentation but connect to shared household data.
- Immediately below the Home page's weekly meal-plan section, show a few meal/recipe suggestions informed by current supermarket offers and household inventory; make the reason for each suggestion visible and never add it to the plan automatically.
- The Home page should also have a supermarket/store offers section with a “see what's on sale” purpose, showing recognizable participating stores and linking to their current offers.
- Future direction: DAHÄM should aim to offer the joy of culinary discovery the owner valued in HelloFresh—introducing people to dishes from around the world they might never otherwise encounter. This is an experience goal, not a request to copy HelloFresh. Inspiration may include cultural food traditions, approachable guided recipes/techniques, baking projects, homemade noodles, seasonal highlights, and trustworthy nutrition education. It should lead naturally to saving a recipe, planning it, or creating shopping needs, even when no items are on sale. Keep it separate from offer/inventory-driven recipe opportunities; exact placement and content sourcing remain open, and it is not initial-release scope.
- DAHÄM is for the whole household, including pet supplies, cleaning products, and other household essentials; the homepage should account for these without displacing the meal-first hierarchy.
- Users should be able to pin/favorite stores. The current direction is to show pinned stores first in a horizontally scrollable store-card deck while keeping other participating stores accessible.
- Product/UX planning is separate from backend architecture; frontend should be designed around people and tasks.
- Performance must not be traded away for visual quality.
- The support/ticket system's background architecture should be defined now rather than treated as a wholly future feature. Its core ticketing foundation can be implemented as an independent backend workstream; operator access to household content remains disabled unless the full ticket-bound authorization gate is in place.
- Maintain this living brief and ask one concise question at a time.

### Recommendations awaiting explicit confirmation

- Separate Recipes and Meal Planner routes/pages, sharing recipe components and backend services.
- Add-meal opens a contextual recipe picker over the calendar; full browsing remains on Recipes.
- Light theme as initial default and optional dark theme.
- Desktop week grid plus mobile day-selector/list.
- Reuse a compact calendar on Home rather than a separate implementation.

### Open decisions — settle one at a time

1. Visually compare a sage-led palette with restrained olive secondary accents against an olive-led palette, both paired with warm off-white/cream. The owner personally likes olive but currently believes sage communicates freshness and health better. This is a directional hypothesis, not a final color choice. Judge both on representative DAHÄM screens before locking the palette. Then decide typography, spacing, imagery, icon style, component shapes, semantic color roles, and light/dark theme behavior.
2. The exact desktop calendar cell/column behavior when a day has many meals.
3. The mobile calendar interaction.
4. The add-meal recipe-picker interaction.
5. Recipe image sourcing and fallback behavior.
6. Account creation/invitation and first-household setup.
7. Which user preferences should be personal versus household-wide.
8. Verify API support for all UI writes and identify missing endpoints.
9. Decide which home-page supporting sections are included in the first release.
10. Decide the store-offers composition on each device: one prominent store-branded banner/feature at a time versus several store-branded cards/features shown simultaneously. Current preference is leaning toward one store at a time on mobile; desktop remains open.
11. Decide the exact homepage treatment for whole-household needs: recommended starting point is one compact, cross-category 'Household essentials' summary rather than separate large blocks for pets, cleaning, and every future domain.
12. Decide whether to add a focused household chores / recurring-tasks capability in a later phase. Recommendation: keep it out of the initial core build; if adopted, limit it to household upkeep and recurring routines instead of becoming a general-purpose to-do app.
13. Design future Culinary Inspiration around the desired feeling of discovering exciting dishes from around the world that users might not otherwise encounter. Decide whether it belongs lower on Home or in a dedicated Inspiration/Discover view, and define content sourcing, respectful cultural context, guidance for different skill levels, and editorial/nutrition quality expectations.
14. **Monetization and licensing:** decide whether to preserve a fully usable self-hosted edition if an optional paid hosted/convenience service is ever offered; separately choose whether/when to publish the repositories and under what licence. No monetization model is selected or approved for implementation.

## 11. Acceptance criteria for the initial UI foundation

Do not call the foundation complete until:
- Login and logout work through a reviewed, secure session flow.
- Desktop and mobile layouts are usable, not just visually resized.
- Home foregrounds the weekly meal plan.
- The full week can be viewed on desktop, with freely addable meals and no forced breakfast/lunch/dinner categories.
- Planned meals show appropriately sized images when available and attractive fallbacks otherwise.
- Adding a meal preserves context and connects to real recipe/meal-plan data when integration is implemented.
- Recipe search does not require loading the entire catalogue into the browser.
- Loading, empty, success, and error states are handled.
- Shared household plan changes remain shared; personal preferences do not accidentally fork household state.
- No production data is changed as a side effect of UI development.
- All changes are on a feature branch in a reviewable PR, not merged automatically.

---

## 12. Monetization and commercial optionality — open planning topic

**Current status:** The owner is unsure whether DAHÄM will ever be monetized, but wants to understand the options early enough not to make avoidable architectural or licensing decisions. This is not approval to build payment flows, subscriptions, advertisements, or commercial-only features now.

### Guiding approach

- Build DAHÄM first to solve real household problems and validate it with actual users. Do not let speculative revenue requirements delay or distort the core product.
- Preserve the ability to commercialize later with clean interfaces and deployment boundaries, without premature abstractions or a second codebase.
- Treat household data as sensitive. Privacy, exportability, secure access, backup, and clear user consent are product requirements, not paid luxuries. If a hosted option is ever offered, apply privacy-by-design and clearly explain data processing.
- **Owner's privacy commitment:** DAHÄM must never sell users' personal or household data. The operator should not routinely inspect individual household data; access should happen only when necessary to investigate a user-reported problem, and should be explicitly authorized, limited to what is needed, time-bounded where possible, and auditable. This commitment applies even if DAHÄM later becomes a centrally hosted service.
- Do not use household data for advertising profiles, data brokerage, or undisclosed commercial targeting. Avoid third-party trackers and collect the minimum operational telemetry needed to run and secure the service. Any optional analytics or non-essential data collection should be transparent and consent-based.
- A hosted service will still need access controls for application operation, backups, security response, and troubleshooting. Do not promise that operators are technically incapable of accessing data until the architecture supports that claim. Encryption at rest protects storage/media, but by itself does not prevent the running application or privileged server operators from accessing plaintext. Evaluate stronger controls, including explicit user-granted support access and end-to-end encryption where compatible with DAHÄM's search, suggestions, and automation.
- Do not allow paid placements or commercial relationships to secretly influence recipe suggestions, prices, or store/product ranking. Any sponsorship or affiliate relationship must be plainly labelled; users must remain in control of meal and product choices.
- Avoid ad-heavy design. Advertising would undermine the intended calm, minimal experience and could weaken trust in price comparisons.

### Hosting direction and privacy-by-design requirements

**Owner's future idea (direction to plan for, not a launch commitment):** if DAHÄM grows, the owner may run a larger server and offer a hosted website and/or app, with household data stored on DAHÄM-managed infrastructure. This is different from the current self-hosted deployment and brings additional obligations for isolation, security, availability, backups, support, account lifecycle, and data protection.

Design the future hosted option around these requirements:

1. **No sale of user data.** Never sell personal, household, inventory, meal, recipe-preference, purchase, or usage data. Do not exchange it for advertising revenue.
2. **Tenant isolation.** Every household's records must be isolated in authorization checks and data access paths; one household must never be able to retrieve or mutate another household's data.
3. **Least-privilege operator access.** Operator/support accounts should not have routine broad access to household content. Separate infrastructure administration from support access where practical.
4. **User-authorized support access.** Prefer a workflow in which the user reports an issue and explicitly grants support access to the relevant household or diagnostic scope. Access should expire, be restricted to the issue, and create an audit record visible to the operator and, where appropriate, the household. Do not browse household data out of curiosity or for routine product analysis.
5. **Data minimization.** Avoid collecting personal data that is not needed. Application logs must not record passwords, session secrets, internal tokens, or unnecessary recipe, inventory, receipt, and purchase contents.
6. **Security basics by default.** Use TLS for network transport; appropriate encryption for stored data and backups; secure session and credential handling; protected secrets; tested backups/restores; incident response; and dependency/security maintenance. Encryption at rest is not equivalent to making data unreadable to the application operator.
7. **Household control.** Plan for data export, account/household deletion and clearly explained retention, including how backups expire. Explain what data is stored, for what purpose, where it is processed, and which service providers can handle it.
8. **Transparent service operations.** Minimize third-party processors and tracking. Any necessary processors (for example, payment handling or transactional email) must be disclosed and used only for the stated purpose. Never claim “only you can access the data” unless the technical architecture and operational practices genuinely enforce it.
9. **No monetization pressure through privacy.** Privacy, basic account security, and protection from data sale are baseline commitments, not premium features.

These are product/architecture requirements to plan and verify, not a claim that the current application already implements them. A hosted offering should not launch until its security, operational, and applicable legal requirements have been assessed.

### Support/ticket subsystem — architecture to be completed now

**Owner clarification:** Do not defer the entire ticket system to a hypothetical hosted-service phase. Define the background architecture now, and treat the core ticketing backend as a concrete, independently implementable subsystem. The in-app Help & Support UI can be delivered in a later frontend phase if needed. The sensitive capability—allowing support personnel to inspect household content—must remain disabled by default and cannot be enabled in any hosted deployment until the ticket-bound, explicit-consent authorization gate and its tests are complete.

The owner's privacy intent is that support should not need routine access to household data. A ticket is the reason/context for help, not consent in itself. The user must be asked separately before any household-content access is enabled.

#### Core domain model (proposed implementation contract)

Keep support ticket data separate from ordinary household domain records, and expose it through the Java backend rather than allowing PHP to query support tables directly.

- **SupportTicket** — opaque ID, household ID, creator user ID, subject, category, description, status, priority (only if operationally needed), created/updated timestamps, resolved/closed timestamps, and optional assignment to a support agent. The user must only be able to access tickets they are authorized to see within their household.
- **SupportTicketMessage** — ticket ID, author identity/type (household user, support agent, or system), body, created timestamp, and attachments metadata where applicable. Customer-visible replies must be distinct from internal staff notes; internal notes must never be returned to a household user.
- **SupportAccessRequest** — ticket ID, household ID, requesting support identity, stated purpose, requested data categories/scopes, request timestamp, and request status. A request is not an authorization grant.
- **SupportAccessGrant** — created only after an authorized household user explicitly approves the request. Bind it to exactly one ticket and household, the approving user, approved scope, created time, expiry, and revocation state/time. Enforce a default-deny policy. No household-wide or indefinite grant.
- **SupportAccessAuditEvent** — append-only events for access requested, granted, denied, revoked, expired, attempted, and performed. Include actor, ticket, household, scope/category, timestamp, and outcome. Avoid logging the private content itself unless strictly necessary.
- **SupportAttachment** (optional, if attachments are enabled) — metadata for a file linked to a ticket/message, with size/type limits, access control, retention policy, and malware scanning before staff access. Do not ingest a full household export or private content automatically.

Use opaque IDs and enforce tenant/household authorization on every request. The exact database table names and enum representations can follow existing Java naming conventions, but the domain boundaries and security behavior above must be preserved.

#### Ticket lifecycle

- Suggested user-visible states: **Open**, **Waiting for DAHÄM support**, **Waiting for you**, **Resolved**, and **Closed**. Keep status transitions explicit and validated by the backend.
- Users can create a ticket, read their authorized tickets, send replies, see status changes, and close a ticket. A reopen policy can be added, but must be explicit.
- Support agents can triage and reply to tickets only through a support-specific authorized service/API. Staff-only notes, if implemented, are separate from messages visible to the user.
- Closing a ticket must revoke its active support-access grant. A grant must also expire automatically and be revocable by the user before expiry. Extending access requires a fresh explicit approval.
- A ticket can be opened and handled without household-content access. Support can use the user's description and any diagnostics that the user consciously shares.

#### API/service boundaries

Define and test backend service contracts for these operations (final paths/names may follow existing API conventions):

- Create and list the current user's/household's tickets; retrieve a single authorized ticket.
- Add a customer-visible message; list the ticket conversation with role-aware visibility.
- Change ticket status through allowed transitions; close a ticket.
- Let a support agent request access with a reason and precise scope.
- Let an authorized household user review the request and approve or decline it; revoke an active grant.
- Provide a narrowly scoped support-data access service that performs all grant checks before returning any household content.
- Read support audit events for authorized support/security roles, with suitable audit visibility for the household where practical.

All authorization and privacy checks live in Java/backend service boundaries. PHP renders the Support UI and calls the API. A PHP checkbox or UI flag alone is never a security boundary. Do not expose the internal Java API directly to browsers or give support users general-purpose database credentials.

#### Access gate (mandatory before any staff content view in a hosted deployment)

Every support-data read must prove all of the following on the server for that request:

1. An existing ticket is in a state that allows investigation.
2. A grant was explicitly approved by an authorized user from the same household.
3. The grant references this exact ticket and household.
4. The grant is active, not expired or revoked, and the scope includes the requested data category/action.
5. The support actor is authenticated and has the required least-privilege support role.
6. The attempted access is written to the audit log, including denied attempts where appropriate.

If any check fails, access is denied. Test at minimum: no ticket, no grant, wrong household, wrong ticket, unauthorized approving user, insufficient scope, expired grant, revoked grant, closed ticket, and insufficient support role. These negative tests are core acceptance criteria, not optional hardening.

Opening a ticket, replying to one, or attaching diagnostic information must never automatically grant access to recipes, inventory, shopping lists, purchases, receipts, preferences, or any other household content. Support access must not be implemented as a raw SQL/database browser. The only path to household content for support is the scoped service/API with the authorization gate above.

#### Diagnostics and data minimization

- Default to user-provided description and non-sensitive operational diagnostics.
- Explain diagnostic contents before submission and avoid including household records, receipt images, credentials, session cookies/tokens, or unrelated content by default.
- Support access can be declined or revoked without deleting the ticket or blocking ordinary communication.
- Record a purpose for each access request and only request the smallest scope that could reasonably investigate the problem.
- Define attachment retention, ticket retention, export/deletion treatment, and audit retention before a hosted launch. The access-grant expiry duration is still an open policy decision; it must be finite and enforced by the backend.

#### Build boundary and sequence

- **Do now:** complete the ticket/support architecture and contracts; implement the core ticket, message, role/visibility, access-request/grant, and append-only audit foundations in a feature branch with migrations and tests, using a throwaway test database. Keep this subsystem independent from the home-page UI work.
- **Frontend later or alongside a suitable UI phase:** build Help & Support pages for ticket creation, ticket history, conversation/replies, status, explicit support-access request review, grant state, expiry, and revocation.
- **Before hosted launch:** complete operational support roles, secure staff authentication, grant expiration/revocation behavior, restricted data-access adapters, monitoring, retention policies, abuse cases, and security review. No staff household-content access may be exposed before the full gate passes tests.
- **Self-hosted deployments:** the server owner administers their own installation. Hosted operator access is a distinct trust boundary and must not be assumed to exist in self-hosted mode.

**Status:** Architecture and backend foundation are now in scope. The visual details of the support UI and final policies (exact allowable scopes, finite default grant duration, support roles, audit visibility, attachment policy, and retention periods) remain to be specified. Those policy values must be configurable or straightforward to amend without weakening the mandatory security model.

### Monetization routes to consider — none selected

1. **Optional donations/supporter contributions.** Low implementation burden and compatible with a community project, but unpredictable and unlikely to reliably fund ongoing operations by themselves.
2. **Optional managed/hosted service subscription.** Users pay to avoid server setup, updates, backups, remote access, and operational maintenance. This is a plausible fit if DAHÄM attracts users who value the experience but do not want to self-host. It introduces real recurring obligations: uptime, security, support, billing, and data-protection responsibilities.
3. **Paid setup, migration, or support.** One-time installation help or paid maintenance can be tested without immediately building a subscription system. Revenue depends on the owner's available time and is less scalable.
4. **Optional paid convenience/services.** Potential future candidates include managed backups, hosted sync/remote access, or resource-intensive receipt processing. These are examples to validate with users, not a commitment to put core features behind a paywall. Avoid creating an artificially crippled free tier or fragmenting the product prematurely.
5. **One-time paid download/licence.** Simple to explain, but ongoing hosting, content, and maintenance may not be funded as reliably as recurring services.
6. **Affiliate income or sponsorship from stores/food brands.** Potentially related to offers and culinary content, but availability of suitable partner programmes is unverified. It risks undermining trust if users suspect offers or suggestions are ranked commercially; never rely on this as the primary model without testing and explicit disclosure.
7. **Paid culinary content or memberships.** Possible only if the future discovery experience becomes valuable enough to support original, trustworthy recipes, lessons, or curated content. Do not assume this demand exists yet.
8. **Commercial/open-core or dual licensing.** A possible software-business route, but it requires an intentional licensing and contribution strategy and may add complexity. Do not choose it by default.

### Preliminary recommendation (proposal, not a decision)

Keep the current product and architecture focused on an excellent, usable household workflow. Preserve self-hosting as a first-class deployment option. If real user demand later appears, test the simplest model first—such as supporter contributions, paid setup/support, or an optional managed service—before adding complicated paid-feature boundaries. Do not build monetization infrastructure until there is evidence that people want DAHÄM and a clearer picture of what they would pay for.

### Licensing needs an explicit decision before public release

As checked during this planning discussion on 13 October 2026, neither the `dahaem` nor `dahaem-frontend` repository root currently contains a `LICENSE` file. This specification does not infer a licence or permission to redistribute either repository. Before making the code public, accepting outside contributions, or presenting DAHÄM as open source, decide the intended licensing/distribution model and check dependency compatibility. Keep this as a separate decision from whether the service will ever earn money; open source does not inherently prevent revenue from hosting or support, but licence choices affect future options.

### Open commercialization decisions

- **Hosted model:** The owner is considering a future centrally hosted service running on owner-managed larger infrastructure, accessed through a website and/or app. This is a planning direction, not a launch commitment; details and viability remain open.
- **Support access details:** Ticket/support architecture and backend foundations are in scope now. Still decide permitted data scopes, finite default grant duration, support roles, audit-log visibility, diagnostic/attachment handling, and closure/retention behavior. Opening a ticket alone must never grant household-data access. Finish and test the backend gate before any hosted support agent can view household content.
- **Self-hosted edition:** Decide whether a fully usable self-hosted edition will remain available alongside a possible hosted service. If hosted, paid value should primarily be convenience/operations rather than selling data or degrading privacy. No final business model is selected.
- Decide whether and when the repositories should be public and what licence/distribution model applies.
- Validate which users DAHÄM is for and what value they would actually pay for before selecting pricing or a paid-feature boundary.
- Assess hosting/security/support and any relevant legal/tax obligations before launching a paid service; do not treat this UX brief as legal or tax advice.

## How to maintain this document

Whenever we make a new product or UX decision:
1. Add it to **Decisions and direction captured so far** or the relevant page/flow section.
2. Move its entry from “Open decisions” to “Decided” when the owner confirms it.
3. Keep recommendations visibly marked as proposals until accepted.
4. Add implementation tasks and acceptance criteria where a decision changes scope.
5. Preserve the owner's one-question-at-a-time preference; don't present a long questionnaire.
