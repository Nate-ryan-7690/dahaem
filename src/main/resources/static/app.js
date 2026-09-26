// DAHÄM web frontend: a small hash-routed single page app over the /api endpoints.
// No build step and no framework; each screen is one async render function.
'use strict';

let view = document.getElementById('view');
const titleEl = document.getElementById('title');
const offlineEl = document.getElementById('offline');
const tabbarEl = document.querySelector('.tabbar');
const logoutEl = document.getElementById('logout');

// ---- Small helpers ----

const eur = new Intl.NumberFormat('de-DE', { style: 'currency', currency: 'EUR' });
const dayFmt = new Intl.DateTimeFormat('de-DE', { weekday: 'short', day: 'numeric', month: 'numeric' });
const shortFmt = new Intl.DateTimeFormat('de-DE', { day: 'numeric', month: 'numeric' });
const dateFmt = new Intl.DateTimeFormat('de-DE', { day: 'numeric', month: 'numeric', year: 'numeric' });

function esc(value) {
    return String(value ?? '').replace(/[&<>"']/g, c => ({
        '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
    }[c]));
}

// Catalog names are stored lower case ("zwiebel"); show them as a label would.
function label(name) {
    const s = String(name ?? '');
    return s.charAt(0).toUpperCase() + s.slice(1);
}

function qty(n) {
    return Number.isInteger(n) ? String(n) : String(Math.round(n * 100) / 100).replace('.', ',');
}

// API dates are ISO "YYYY-MM-DD"; parse as a local date, not UTC midnight.
function parseDate(iso) {
    const [y, m, d] = iso.split('-').map(Number);
    return new Date(y, m - 1, d);
}

function today() {
    const now = new Date();
    return new Date(now.getFullYear(), now.getMonth(), now.getDate());
}

function addDays(date, days) {
    const d = new Date(date);
    d.setDate(d.getDate() + days);
    return d;
}

function store(key, value) {
    try {
        if (value === undefined) return JSON.parse(localStorage.getItem(key));
        localStorage.setItem(key, JSON.stringify(value));
    } catch (e) {
        return null; // private mode or storage disabled: the app still works, it just forgets
    }
}

// ---- Picked recipes: a client-side selection for the "shop what's on sale" flow ----
const PICK_KEY = 'dahaem.picked';
function pickedList() { return store(PICK_KEY) || []; }
function isPicked(id) { return pickedList().some(p => p.id === id); }
function togglePick(id, name) {
    const list = pickedList();
    const i = list.findIndex(p => p.id === id);
    if (i >= 0) list.splice(i, 1); else list.push({ id, name });
    store(PICK_KEY, list);
    return i < 0; // true when it is now picked
}
// The shopping-list API returns storeOptions cheapest-till-price first; the first is the best buy.
function bestOption(line) {
    return line.storeOptions && line.storeOptions.length ? line.storeOptions[0] : null;
}

// ---- API ----

class ApiError extends Error {
    constructor(message, status) {
        super(message);
        this.status = status;
    }
}

// Thrown on a 401 from the API: the session is missing or expired, so the router shows the login screen.
class LoginRequired extends Error {}

// Thrown when the user navigated away while a request was in flight, so the old
// screen's render stops instead of drawing over the new one.
class Superseded extends Error {}

let routeToken = 0;

// Fetches JSON from the API. The service worker answers from its cache when the
// network is down and marks such responses, so the screen can say it is a saved copy.
async function api(path) {
    const token = routeToken;
    const data = await fetchJson(path);
    if (token !== routeToken) throw new Superseded();
    return data;
}

async function fetchJson(path) {
    let res;
    try {
        res = await fetch(path, { headers: { Accept: 'application/json' } });
    } catch (e) {
        setOffline(true);
        throw new ApiError('Could not reach the DAHÄM server. Check your connection and try again.');
    }
    if (res.status === 401) throw new LoginRequired();
    if (!res.ok) {
        let message = `The server answered ${res.status}.`;
        try {
            const body = await res.json();
            if (body && body.message) message = body.message;
        } catch (e) { /* not a JSON error body */ }
        throw new ApiError(message);
    }
    const data = await res.json();
    const cachedAt = res.headers.get('X-Dahaem-Cached');
    if (cachedAt) staleSince = cachedAt;
    setOffline(!!cachedAt);
    return data;
}

// Set when an answer came from the service worker's cache instead of the server.
let staleSince = null;

// POSTs or PUTs JSON to the API, with the session's CSRF header attached. Writes aren't served
// from the service worker's cache, so a failed fetch here means the request never reached the
// server, not a stale-but-usable answer.
async function apiWrite(path, method, body) {
    let headers;
    try {
        headers = { ...(await csrfHeaders()), Accept: 'application/json' };
    } catch (e) {
        if (e instanceof LoginRequired) throw e;
        setOffline(true);
        throw new ApiError("Can't save while offline. Check your connection and try again.");
    }
    if (body !== undefined) headers['Content-Type'] = 'application/json';

    let res;
    try {
        res = await fetch(path, { method, headers, body: body !== undefined ? JSON.stringify(body) : undefined });
    } catch (e) {
        setOffline(true);
        throw new ApiError("Can't save while offline. Check your connection and try again.");
    }
    if (res.status === 401) throw new LoginRequired();
    let payload = null;
    try { payload = await res.json(); } catch (e) { /* no body */ }
    if (!res.ok) {
        const message = payload && payload.message ? payload.message : `The server answered ${res.status}.`;
        throw new ApiError(message, res.status);
    }
    return payload;
}

// ---- State views ----

function showLoading(text = 'Loading…') {
    view.innerHTML = `<div class="state" role="status"><div class="spinner"></div>${esc(text)}</div>`;
}

function stateHtml(title, text, action = '') {
    return `<div class="state"><h2>${esc(title)}</h2><p>${text}</p>${action}</div>`;
}

function showError(err) {
    view.innerHTML = `<div class="state state-error" role="alert">
        <h2>Something went wrong</h2><p>${esc(err.message || err)}</p>
        <button class="btn btn-primary" type="button" data-action="retry">Try again</button></div>`;
    view.querySelector('[data-action="retry"]').addEventListener('click', route);
}

function staleNotice() {
    if (!staleSince) return '';
    const when = new Date(staleSince);
    const text = isNaN(when) ? '' : ` from ${when.toLocaleString('de-DE', { dateStyle: 'short', timeStyle: 'short' })}`;
    return `<div class="notice" role="status">Offline: showing the saved copy${esc(text)}.</div>`;
}

// ---- Shopping list ----

// Picks the plan to shop for: the one the user chose last, else the week containing
// today, else the next upcoming week, else the most recent past one.
function defaultPlan(plans) {
    const saved = store('dahaem.planId');
    const chosen = plans.find(p => p.id === saved);
    if (chosen) return chosen;
    const t = today();
    const byStart = [...plans].sort((a, b) => a.weekStartDate.localeCompare(b.weekStartDate));
    const current = byStart.find(p => {
        const start = parseDate(p.weekStartDate);
        return start <= t && t < addDays(start, 7);
    });
    return current || byStart.find(p => parseDate(p.weekStartDate) > t) || byStart[byStart.length - 1];
}

function planLabel(plan) {
    const start = parseDate(plan.weekStartDate);
    return `Week ${shortFmt.format(start)} – ${shortFmt.format(addDays(start, 6))}`;
}

function lineKey(line) {
    return `${line.inventoryItemId}|${line.unit}`;
}

function priceHtml(o) {
    if (!o) return '<span class="muted small">No store price</span>';
    const price = o.tillPrice ?? o.normalPrice;
    let html = price != null ? `<div class="price">${eur.format(price)}</div>` : '';
    if (o.onSale && o.normalPrice != null && price != null && o.normalPrice > price) {
        html += `<div class="price-was">${eur.format(o.normalPrice)}</div>`;
    }
    return html;
}

function optionDetails(o) {
    const parts = [];
    if (o.brandName) parts.push(o.brandName);
    if (o.packageSize) parts.push(`${o.packageSize} ${o.packageUnit || ''}`.trim());
    if (o.dealQuantity > 1 && o.dealPrice != null) parts.push(`${o.dealQuantity} for ${eur.format(o.dealPrice)}`);
    if (o.deposit) parts.push(`incl. ${eur.format(o.deposit)} deposit`);
    return parts.join(' · ');
}

async function renderPickedList() {
    titleEl.textContent = 'Shopping list';
    showLoading();
    const picks = pickedList();
    const bar = `<div class="toolbar">
        <a class="btn" href="#/savings">‹ Savings</a>
        <button class="btn" type="button" data-action="clear-picks" style="margin-left:auto">Clear picks</button>
    </div>`;
    if (picks.length === 0) {
        view.innerHTML = bar + stateHtml('No recipes picked yet',
            'Open Savings and tap + on the recipes you want to cook, then come back here.');
        wireClearPicks();
        return;
    }

    const recipeIds = [...new Set(picks.map(p => p.id))];
    const recipeNames = new Map(picks.map(p => [p.id, p.name]));
    const list = await api(`/api/shopping-list?recipeIds=${recipeIds.join(',')}`);
    if (list.items.length === 0) {
        view.innerHTML = bar + stateHtml('Nothing to buy', 'The picked recipes have no ingredients listed.');
        wireClearPicks();
        return;
    }

    // Group by store so the list reads in the order you walk the shops; no-price lines go last.
    const groups = new Map();
    for (const line of list.items) {
        const storeName = bestOption(line)?.storeName || '';
        if (!groups.has(storeName)) groups.set(storeName, []);
        groups.get(storeName).push(line);
    }
    const storeNames = [...groups.keys()].sort((a, b) => (a === '') - (b === '') || a.localeCompare(b, 'de'));

    const checkedKey = 'dahaem.checked.picked';
    const checked = new Set(store(checkedKey) || []);

    let html = staleNotice() + bar + `<div class="summary" id="summary"></div>
        <div class="progress" aria-hidden="true"><span id="progress-bar"></span></div>`;
    for (const storeName of storeNames) {
        const lines = groups.get(storeName).sort((a, b) => a.nameDeutsch.localeCompare(b.nameDeutsch, 'de'));
        html += `<h2 class="section-title">${esc(storeName || 'No store price yet')}</h2><ul class="list">`;
        for (const line of lines) {
            const o = bestOption(line);
            const key = lineKey(line);
            const id = `chk-${line.inventoryItemId}-${encodeURIComponent(line.unit)}`.replace(/%/g, '_');
            const forRecipes = line.sourceRecipeIds.map(rid => recipeNames.get(rid)).filter(Boolean).join(', ');
            const badge = o?.onSale
                ? ` <span class="pill pill-sale">Sale${o.validUntil ? ` until ${esc(dayFmt.format(parseDate(o.validUntil)))}` : ''}</span>`
                : '';
            html += `<li><label class="row shop-item${checked.has(key) ? ' done' : ''}" for="${id}">
                <input type="checkbox" id="${id}" data-key="${esc(key)}"${checked.has(key) ? ' checked' : ''}>
                <div class="row-main">
                    <div class="row-title">${esc(label(line.nameDeutsch))}${badge}</div>
                    <div class="row-sub">${esc(qty(line.totalQuantity))} ${esc(line.unit)}${o ? ` · ${esc(optionDetails(o))}` : ''}</div>
                    ${forRecipes ? `<div class="row-sub small clamp">For ${esc(forRecipes)}</div>` : ''}
                </div>
                <div class="row-end">${priceHtml(o)}</div>
            </label></li>`;
        }
        html += '</ul>';
    }
    view.innerHTML = html;

    const updateSummary = () => {
        const total = list.items.length;
        const done = list.items.filter(l => checked.has(lineKey(l))).length;
        const remaining = list.items
            .filter(l => !checked.has(lineKey(l)) && bestOption(l)?.tillPrice != null)
            .reduce((sum, l) => sum + bestOption(l).tillPrice, 0);
        document.getElementById('summary').innerHTML =
            `<span><strong>${done}</strong> of ${total} in the basket</span>
             <span class="muted" title="Sum of one pack per remaining line with a known price">≈ ${eur.format(remaining)} left</span>`;
        document.getElementById('progress-bar').style.width = `${total ? (done / total) * 100 : 0}%`;
    };
    updateSummary();

    view.addEventListener('change', e => {
        const box = e.target.closest('input[type="checkbox"][data-key]');
        if (!box) return;
        if (box.checked) checked.add(box.dataset.key); else checked.delete(box.dataset.key);
        box.closest('.shop-item').classList.toggle('done', box.checked);
        store(checkedKey, [...checked]);
        updateSummary();
    });
    wireClearPicks();
}

function wireClearPicks() {
    const btn = view.querySelector('[data-action="clear-picks"]');
    if (btn) btn.addEventListener('click', () => {
        store(PICK_KEY, []);
        store('dahaem.checked.picked', []);
        location.hash = '#/savings';
    });
}

async function renderShopping(planIdParam) {
    titleEl.textContent = 'Shopping list';
    showLoading();
    const plans = await api('/api/meal-plans');
    if (plans.length === 0) {
        view.innerHTML = stateHtml('No meal plan yet',
            'The shopping list is built from the recipes in a weekly meal plan. Create a plan first, then come back here.',
            '<a class="btn btn-primary" href="#/recipes" style="display:inline-grid;place-content:center">Browse recipes</a>');
        return;
    }
    const plan = plans.find(p => String(p.id) === planIdParam) || defaultPlan(plans);
    store('dahaem.planId', plan.id);

    const options = [...plans]
        .sort((a, b) => b.weekStartDate.localeCompare(a.weekStartDate))
        .map(p => `<option value="${p.id}"${p.id === plan.id ? ' selected' : ''}>${esc(planLabel(p))}</option>`)
        .join('');
    const toolbar = `<div class="toolbar">
        <select id="plan-select" aria-label="Meal plan">${options}</select>
        <button class="btn" type="button" data-action="reset" title="Uncheck everything">Reset</button>
    </div>
    <div class="toolbar">
        <button class="btn btn-primary" type="button" data-action="save-list" style="flex:1">Save this list</button>
    </div>
    <p class="save-status small" id="save-status" hidden></p>`;

    const recipeIds = [...new Set(plan.recipes.map(r => r.recipeId))];
    if (recipeIds.length === 0) {
        view.innerHTML = toolbar + stateHtml('Nothing to buy',
            'This meal plan has no recipes yet, so there is nothing on the list.');
        wireShoppingToolbar(plan, null);
        return;
    }

    const list = await api(`/api/shopping-list?recipeIds=${recipeIds.join(',')}`);
    if (list.items.length === 0) {
        view.innerHTML = toolbar + stateHtml('Nothing to buy', 'The recipes in this plan have no ingredients listed.');
        wireShoppingToolbar(plan, null);
        return;
    }

    // Group by store so the list reads in the order you walk the shops; lines without
    // a known store price go last.
    const groups = new Map();
    for (const line of list.items) {
        const storeName = bestOption(line)?.storeName || '';
        if (!groups.has(storeName)) groups.set(storeName, []);
        groups.get(storeName).push(line);
    }
    const storeNames = [...groups.keys()].sort((a, b) => (a === '') - (b === '') || a.localeCompare(b, 'de'));

    const checkedKey = `dahaem.checked.${plan.id}`;
    const checked = new Set(store(checkedKey) || []);
    const recipeNames = new Map(plan.recipes.map(r => [r.recipeId, r.recipeName]));

    let html = staleNotice() + toolbar + `<div class="summary" id="summary"></div>
        <div class="progress" aria-hidden="true"><span id="progress-bar"></span></div>`;
    for (const storeName of storeNames) {
        const lines = groups.get(storeName).sort((a, b) => a.nameDeutsch.localeCompare(b.nameDeutsch, 'de'));
        html += `<h2 class="section-title">${esc(storeName || 'No store price yet')}</h2><ul class="list">`;
        for (const line of lines) {
            const o = bestOption(line);
            const key = lineKey(line);
            const id = `chk-${line.inventoryItemId}-${encodeURIComponent(line.unit)}`.replace(/%/g, '_');
            const forRecipes = line.sourceRecipeIds.map(rid => recipeNames.get(rid)).filter(Boolean).join(', ');
            const badge = o?.onSale
                ? ` <span class="pill pill-sale">Sale${o.validUntil ? ` until ${esc(dayFmt.format(parseDate(o.validUntil)))}` : ''}</span>`
                : '';
            html += `<li><label class="row shop-item${checked.has(key) ? ' done' : ''}" for="${id}">
                <input type="checkbox" id="${id}" data-key="${esc(key)}"${checked.has(key) ? ' checked' : ''}>
                <div class="row-main">
                    <div class="row-title">${esc(label(line.nameDeutsch))}${badge}</div>
                    <div class="row-sub">${esc(qty(line.totalQuantity))} ${esc(line.unit)}${o ? ` · ${esc(optionDetails(o))}` : ''}</div>
                    ${forRecipes ? `<div class="row-sub small clamp">For ${esc(forRecipes)}</div>` : ''}
                </div>
                <div class="row-end">${priceHtml(o)}</div>
            </label></li>`;
        }
        html += '</ul>';
    }
    view.innerHTML = html;

    const updateSummary = () => {
        const total = list.items.length;
        const done = list.items.filter(l => checked.has(lineKey(l))).length;
        const remaining = list.items
            .filter(l => !checked.has(lineKey(l)) && bestOption(l)?.tillPrice != null)
            .reduce((sum, l) => sum + bestOption(l).tillPrice, 0);
        document.getElementById('summary').innerHTML =
            `<span><strong>${done}</strong> of ${total} in the basket</span>
             <span class="muted" title="Sum of one pack per remaining line with a known price">≈ ${eur.format(remaining)} left</span>`;
        document.getElementById('progress-bar').style.width = `${total ? (done / total) * 100 : 0}%`;
    };
    updateSummary();

    view.addEventListener('change', e => {
        const box = e.target.closest('input[type="checkbox"][data-key]');
        if (!box) return;
        if (box.checked) checked.add(box.dataset.key); else checked.delete(box.dataset.key);
        box.closest('.shop-item').classList.toggle('done', box.checked);
        store(checkedKey, [...checked]);
        updateSummary();
    });
    wireShoppingToolbar(plan, () => {
        checked.clear();
        store(checkedKey, []);
        view.querySelectorAll('input[data-key]').forEach(b => {
            b.checked = false;
            b.closest('.shop-item').classList.remove('done');
        });
        updateSummary();
    });
}

function wireShoppingToolbar(plan, onReset) {
    view.querySelector('#plan-select').addEventListener('change', e => {
        location.hash = `#/shopping/${e.target.value}`;
    });
    const reset = view.querySelector('[data-action="reset"]');
    if (onReset) reset.addEventListener('click', onReset); else reset.disabled = true;

    const token = routeToken;
    const saveBtn = view.querySelector('[data-action="save-list"]');
    const statusEl = view.querySelector('#save-status');
    saveBtn.addEventListener('click', async () => {
        saveBtn.disabled = true;
        saveBtn.textContent = 'Saving…';
        statusEl.hidden = true;
        try {
            const result = await apiWrite(`/api/meal-plans/${plan.id}/shopping-list`, 'POST');
            if (token !== routeToken) return;
            location.hash = `#/shopping-list/${result.shoppingListId}`;
        } catch (err) {
            if (token !== routeToken) return;
            if (err instanceof LoginRequired) { requireLogin(); return; }
            saveBtn.disabled = false;
            saveBtn.textContent = 'Save this list';
            statusEl.hidden = false;
            statusEl.textContent = err.message;
        }
    });
}

// ---- Persisted shopping list ----

function persistedLineHtml(item) {
    const name = label(item.nameDeutsch || item.nameEnglish);
    const groupName = `opt-${item.shoppingListItemId}`;
    const optionInput = (value, checked) =>
        `<input type="radio" name="${esc(groupName)}" value="${esc(value)}" data-item-id="${item.shoppingListItemId}"${checked ? ' checked' : ''}>`;
    const rows = [`<label class="option-row">
            ${optionInput('', item.selectedStoreItemId == null)}
            <span class="option-main"><span class="option-store muted">Not decided yet</span></span>
        </label>`]
        .concat(item.storeOptions.map(o => {
            const details = optionDetails(o);
            return `<label class="option-row">
                ${optionInput(String(o.storeItemId), item.selectedStoreItemId === o.storeItemId)}
                <span class="option-main">
                    <span class="option-store">${esc(o.storeName)}</span>
                    ${details ? `<span class="option-detail muted small">${esc(details)}</span>` : ''}
                </span>
                <span class="option-end">${priceHtml(o)}</span>
            </label>`;
        }))
        .join('');
    return `<li class="persisted-item" data-item-id="${item.shoppingListItemId}">
        <div class="row-main">
            <div class="row-title">${esc(name)}</div>
            <div class="row-sub">Need ${esc(qty(item.quantityNeeded))}</div>
        </div>
        <p class="option-error small" hidden></p>
        <div class="option-list" role="radiogroup" aria-label="Store option for ${esc(name)}">${rows}</div>
    </li>`;
}

async function renderPersistedShoppingList(listIdParam) {
    titleEl.textContent = 'Shopping list';
    showLoading();
    const listId = Number(listIdParam);
    const list = await api(`/api/shopping-lists/${encodeURIComponent(listId)}`);
    const backHref = list.mealPlanId != null ? `#/shopping/${list.mealPlanId}` : '#/shopping';

    if (list.items.length === 0) {
        view.innerHTML = staleNotice() + `<a class="back" href="${backHref}">‹ Build a list</a>` +
            stateHtml('Nothing to buy', 'Everything needed for this list is already in the pantry.');
        return;
    }

    view.innerHTML = staleNotice() + `<a class="back" href="${backHref}">‹ Build a list</a>
        <ul class="list persisted-list">${list.items.map(persistedLineHtml).join('')}</ul>`;

    // Tracks the last known-good selection per line, so a rejected PUT can revert to it.
    const selections = new Map(list.items.map(i => [i.shoppingListItemId, i.selectedStoreItemId]));
    const token = routeToken;

    view.addEventListener('change', async e => {
        const input = e.target.closest('input[type="radio"][data-item-id]');
        if (!input) return;
        const itemId = Number(input.dataset.itemId);
        const li = input.closest('.persisted-item');
        const errorEl = li.querySelector('.option-error');
        const newValue = input.value === '' ? null : Number(input.value);
        const previous = selections.get(itemId);
        const radios = li.querySelectorAll('input[type="radio"]');

        errorEl.hidden = true;
        radios.forEach(r => r.disabled = true);
        try {
            await apiWrite(`/api/shopping-lists/${listId}/items/${itemId}/store-item`, 'PUT', { storeItemId: newValue });
            if (token !== routeToken) return;
            selections.set(itemId, newValue);
        } catch (err) {
            if (token !== routeToken) return;
            if (err instanceof LoginRequired) { requireLogin(); return; }
            if (err.status === 404) { showError(err); return; }
            const revertValue = previous == null ? '' : String(previous);
            const revertInput = [...radios].find(r => r.value === revertValue);
            if (revertInput) revertInput.checked = true;
            errorEl.textContent = err.message;
            errorEl.hidden = false;
        } finally {
            if (token === routeToken) radios.forEach(r => r.disabled = false);
        }
    });
}

// ---- Inventory ----

const EXPIRING_DAYS = 7;
const MAX_ROWS = 200;

async function renderInventory() {
    titleEl.textContent = 'Pantry';
    showLoading();
    const [items, expiring] = await Promise.all([
        api('/api/inventory'),
        api(`/api/inventory/expiring?days=${EXPIRING_DAYS}`)
    ]);
    if (items.length === 0) {
        view.innerHTML = stateHtml('The catalog is empty', 'No inventory items exist in the database yet.');
        return;
    }
    const expiringIds = new Set(expiring.map(i => i.id));
    const t = today();
    let showAll = store('dahaem.inv.showAll') === true;
    let query = '';

    view.innerHTML = staleNotice() + `<div class="toolbar">
            <input type="search" id="inv-search" placeholder="Search pantry" aria-label="Search pantry" autocomplete="off">
            <select id="inv-scope" aria-label="Which items" style="flex:0 0 auto">
                <option value="stock">In stock</option>
                <option value="all">Whole catalog</option>
            </select>
        </div><div id="inv-list"></div>`;
    const scope = view.querySelector('#inv-scope');
    scope.value = showAll ? 'all' : 'stock';

    const rowHtml = item => {
        const exp = item.earliestExpDate ? parseDate(item.earliestExpDate) : null;
        const expired = exp && exp < t;
        const soon = !expired && expiringIds.has(item.id);
        const cls = expired ? ' expired' : soon ? ' expiring' : '';
        let badge = '';
        if (expired) badge = '<span class="pill pill-sale">Expired</span>';
        else if (soon) badge = `<span class="pill pill-warn">Use by ${esc(dayFmt.format(exp))}</span>`;
        const sub = [item.nameEnglish, item.subcategory || item.category].filter(Boolean).join(' · ');
        return `<li class="row inv-item${cls}">
            <div class="row-main">
                <div class="row-title">${esc(label(item.nameDeutsch))} ${badge}</div>
                <div class="row-sub">${esc(sub)}${exp && !badge ? ` · best before ${esc(dateFmt.format(exp))}` : ''}</div>
            </div>
            <div class="row-end qty">${esc(item.quantity)} ${esc(item.unit || '')}</div>
        </li>`;
    };

    const draw = () => {
        const q = query.trim().toLowerCase();
        let rows = items.filter(i => showAll || i.quantity > 0);
        if (q) rows = rows.filter(i => `${i.nameDeutsch} ${i.nameEnglish} ${i.category}`.toLowerCase().includes(q));
        // Expired first, then expiring soon, then by name.
        const rank = i => {
            const exp = i.earliestExpDate ? parseDate(i.earliestExpDate) : null;
            return exp && exp < t ? 0 : expiringIds.has(i.id) ? 1 : 2;
        };
        rows.sort((a, b) => rank(a) - rank(b) || a.nameDeutsch.localeCompare(b.nameDeutsch, 'de'));

        const target = view.querySelector('#inv-list');
        if (rows.length === 0) {
            target.innerHTML = q
                ? stateHtml('No matches', `Nothing in the ${showAll ? 'catalog' : 'pantry'} matches “${esc(query)}”.`)
                : stateHtml('Nothing in stock', 'No item has a quantity above zero. Switch to “Whole catalog” to see every item.');
            return;
        }
        const soonCount = rows.filter(i => rank(i) < 2).length;
        target.innerHTML = (soonCount ? `<p class="section-title">${soonCount} to use up soon</p>` : '')
            + `<ul class="list">${rows.slice(0, MAX_ROWS).map(rowHtml).join('')}</ul>`
            + (rows.length > MAX_ROWS ? `<p class="muted small" style="text-align:center">Showing ${MAX_ROWS} of ${rows.length}. Search to narrow it down.</p>` : '');
    };

    view.querySelector('#inv-search').addEventListener('input', e => { query = e.target.value; draw(); });
    scope.addEventListener('change', () => {
        showAll = scope.value === 'all';
        store('dahaem.inv.showAll', showAll);
        draw();
    });
    draw();
}

// ---- Recipes ----

function minutes(n) {
    return n ? `${n} min` : null;
}

async function renderRecipes() {
    titleEl.textContent = 'Recipes';
    showLoading();
    const recipes = await api('/api/recipes');
    if (recipes.length === 0) {
        view.innerHTML = stateHtml('No recipes yet', 'Import the sample recipes to get started.');
        return;
    }
    recipes.sort((a, b) => a.name.localeCompare(b.name, 'de'));
    view.innerHTML = staleNotice() + `<div class="toolbar">
            <input type="search" id="rec-search" placeholder="Search recipes" aria-label="Search recipes" autocomplete="off">
        </div><div id="rec-list"></div>`;
    const draw = q => {
        const rows = recipes.filter(r => r.name.toLowerCase().includes(q.trim().toLowerCase()));
        view.querySelector('#rec-list').innerHTML = rows.length === 0
            ? stateHtml('No matches', `No recipe matches “${esc(q)}”.`)
            : `<ul class="list">${rows.map(r => {
                const facts = [minutes(r.prepTime + r.cookTime), `${r.servings} servings`,
                    r.calories != null ? `${r.calories} kcal` : null].filter(Boolean).join(' · ');
                return `<li><a class="row row-link" href="#/recipes/${r.id}">
                    <div class="row-main"><div class="row-title">${esc(r.name)}</div>
                    <div class="row-sub">${esc(facts)}</div></div></a></li>`;
            }).join('')}</ul>`;
    };
    view.querySelector('#rec-search').addEventListener('input', e => draw(e.target.value));
    draw('');
}

async function renderRecipe(id) {
    titleEl.textContent = 'Recipe';
    showLoading();
    const r = await api(`/api/recipes/${encodeURIComponent(id)}`);
    titleEl.textContent = r.name;
    const facts = [
        r.prepTime ? `Prep ${r.prepTime} min` : null,
        r.cookTime ? `Cook ${r.cookTime} min` : null,
        `${r.servings} servings`
    ].filter(Boolean).map(f => `<span class="pill pill-muted">${esc(f)}</span>`).join('');

    const n = r.nutrition || {};
    const nutritionCells = [['kcal', n.calories], ['protein', n.protein, 'g'], ['carbs', n.carbs, 'g'],
        ['fat', n.fat, 'g'], ['fiber', n.fiber, 'g'], ['sugar', n.sugar, 'g']]
        .filter(([, v]) => v != null)
        .map(([name, v, unit = '']) => `<div><strong>${esc(v)}${unit}</strong><span class="muted small">${name}</span></div>`)
        .join('');

    const ingredients = r.ingredients.length === 0
        ? '<p class="muted">No ingredients listed.</p>'
        : `<ul class="list">${r.ingredients.map(i => `<li class="row">
                <div class="row-main"><div class="row-title">${esc(label(i.nameDeutsch))}</div>
                ${i.nameEnglish ? `<div class="row-sub">${esc(i.nameEnglish)}</div>` : ''}</div>
                <div class="row-end qty">${esc(qty(i.quantity))} ${esc(i.unit)}</div></li>`).join('')}</ul>`;

    const steps = r.instructions.length === 0
        ? '<p class="muted">No steps listed.</p>'
        : `<ol class="list steps">${[...r.instructions].sort((a, b) => a.stepNumber - b.stepNumber).map(s => `<li>
                <span class="step-no">${esc(s.stepNumber)}</span>
                <div>${esc(s.instruction)}${s.notes ? `<div class="row-sub">${esc(s.notes)}</div>` : ''}</div></li>`).join('')}</ol>`;

    view.innerHTML = staleNotice() + `<a class="back" href="#/recipes">‹ Recipes</a>
        <div class="detail-head"><h2>${esc(r.name)}</h2><div class="facts">${facts}</div></div>
        ${nutritionCells ? `<h3 class="section-title">Nutrition</h3><div class="list nutrition">${nutritionCells}</div>` : ''}
        <h3 class="section-title">Ingredients</h3>${ingredients}
        <h3 class="section-title">Steps</h3>${steps}`;
}

// ---- Meal plans ----

async function renderPlans(planIdParam) {
    titleEl.textContent = 'Meal plan';
    showLoading();
    const plans = await api('/api/meal-plans');
    if (plans.length === 0) {
        view.innerHTML = stateHtml('No meal plan yet', 'Weekly meal plans you create will show up here.');
        return;
    }
    const plan = plans.find(p => String(p.id) === planIdParam) || defaultPlan(plans);
    const options = [...plans]
        .sort((a, b) => b.weekStartDate.localeCompare(a.weekStartDate))
        .map(p => `<option value="${p.id}"${p.id === plan.id ? ' selected' : ''}>${esc(planLabel(p))}</option>`)
        .join('');

    let body;
    if (plan.recipes.length === 0) {
        body = stateHtml('No recipes planned', 'This week has no recipes yet.');
    } else {
        // Group by meal time, keeping the order the plan lists them in.
        const byMeal = new Map();
        for (const r of plan.recipes) {
            const meal = r.mealTime || 'Other';
            if (!byMeal.has(meal)) byMeal.set(meal, []);
            byMeal.get(meal).push(r);
        }
        body = [...byMeal].map(([meal, rs]) => `<h2 class="section-title">${esc(meal)}</h2>
            <ul class="list">${rs.map(r => `<li><a class="row row-link" href="#/recipes/${r.recipeId}">
                <div class="row-main"><div class="row-title">${esc(r.recipeName)}</div></div></a></li>`).join('')}</ul>`).join('');
        body += `<p style="margin-top:20px"><a class="btn btn-primary" style="display:grid;place-content:center" href="#/shopping/${plan.id}">Shopping list for this week</a></p>`;
    }

    view.innerHTML = staleNotice() + `<div class="toolbar">
        <select id="plan-view-select" aria-label="Meal plan">${options}</select></div>` + body;
    view.querySelector('#plan-view-select').addEventListener('change', e => {
        location.hash = `#/plans/${e.target.value}`;
    });
}

// ---- Savings ----

function suggestionRowHtml(s, whyText) {
    const picked = isPicked(s.recipeId);
    return `<li class="row-pickable">
        <a class="row row-link" href="#/recipes/${s.recipeId}">
            <div class="row-main"><div class="row-title">${esc(s.recipeName)}</div>
            <div class="row-sub">${esc(whyText(s.score))}</div></div></a>
        <button class="pick-btn${picked ? ' picked' : ''}" type="button" data-pick="${s.recipeId}"
            data-name="${esc(s.recipeName)}" aria-pressed="${picked}"
            title="Add this recipe to the shopping list">${picked ? '✓' : '+'}</button>
    </li>`;
}

function onSaleWhy(score) {
    return `${score} ingredient${score === 1 ? '' : 's'} on sale`;
}

function expiringSoonWhy(score) {
    return `${score} ingredient${score === 1 ? '' : 's'} expiring soon`;
}

// Surfaces recipes ranked by current signals (on-sale ingredients, soon-to-expire pantry
// items); the user still picks what to cook, this view only ranks and presents.
async function renderSavings() {
    titleEl.textContent = 'Savings';
    showLoading();
    const [savings, expiring] = await Promise.all([
        api('/api/suggestions/by-savings'),
        api('/api/suggestions/by-expiring?days=7')
    ]);

    const buildBar = `<div class="toolbar">
        <a class="btn btn-primary" id="build-list" style="flex:1;display:grid;place-content:center"
           href="#/list">Shopping list (${pickedList().length})</a></div>`;
    let html = staleNotice() + buildBar + '<h2 class="section-title">On sale now</h2>';
    html += savings.length === 0
        ? stateHtml('No savings-based suggestions right now', 'No recipe ingredients are currently on sale.')
        : `<ul class="list">${savings.map(s => suggestionRowHtml(s, onSaleWhy)).join('')}</ul>`;

    html += '<h2 class="section-title">Use it up</h2>';
    html += expiring.length === 0
        ? stateHtml('Nothing expiring soon', 'No pantry items are expiring within the next 7 days.')
        : `<ul class="list">${expiring.map(s => suggestionRowHtml(s, expiringSoonWhy)).join('')}</ul>`;

    view.innerHTML = html;

    // Tap + / ✓ to add or remove a recipe from the shopping-list selection.
    view.addEventListener('click', e => {
        const btn = e.target.closest('[data-pick]');
        if (!btn) return;
        e.preventDefault();
        const nowPicked = togglePick(Number(btn.dataset.pick), btn.dataset.name);
        btn.classList.toggle('picked', nowPicked);
        btn.textContent = nowPicked ? '✓' : '+';
        btn.setAttribute('aria-pressed', String(nowPicked));
        const build = document.getElementById('build-list');
        if (build) build.textContent = `Shopping list (${pickedList().length})`;
    });
}

// ---- Login ----

// Every POST needs the session's CSRF token in the header the server names.
async function csrfHeaders() {
    const csrf = await fetchJson('/api/auth/csrf');
    return { [csrf.headerName]: csrf.token };
}

// Sends the user to the login screen, remembering the current one so a successful login returns
// here. Used both by the router (a LoginRequired from a screen's own load) and by write actions
// outside the router's try/catch, such as a click or change handler's own apiWrite call.
function requireLogin() {
    store('dahaem.afterLogin', location.hash);
    location.hash = '#/login';
}

async function renderLogin() {
    titleEl.textContent = 'DAHÄM';
    view.innerHTML = `<form class="login-form" id="login-form" novalidate>
        <h2>Log in</h2>
        <label>Email<input type="email" name="email" autocomplete="username" required autofocus></label>
        <label>Password<input type="password" name="password" autocomplete="current-password" required></label>
        <p class="form-error" id="login-error" role="alert" hidden></p>
        <button class="btn btn-primary" type="submit">Log in</button>
    </form>`;
    const form = view.querySelector('#login-form');
    const errorEl = view.querySelector('#login-error');
    const showLoginError = text => { errorEl.textContent = text; errorEl.hidden = false; };

    form.addEventListener('submit', async e => {
        e.preventDefault();
        const button = form.querySelector('button');
        button.disabled = true;
        errorEl.hidden = true;
        try {
            const res = await fetch('/api/auth/login', {
                method: 'POST',
                headers: { ...(await csrfHeaders()), Accept: 'application/json' },
                body: new URLSearchParams({ email: form.email.value, password: form.password.value })
            });
            if (res.ok) {
                const next = store('dahaem.afterLogin');
                store('dahaem.afterLogin', null);
                location.hash = next && next !== '#/login' ? next : '#/shopping';
                return;
            }
            showLoginError(res.status === 401 ? 'Wrong email or password.' : `The server answered ${res.status}. Please try again.`);
        } catch (err) {
            showLoginError(err instanceof ApiError ? err.message : 'Could not reach the DAHÄM server. Check your connection and try again.');
        }
        button.disabled = false;
    });
}

async function logout() {
    try {
        await fetch('/api/auth/logout', { method: 'POST', headers: await csrfHeaders() });
    } catch (e) { /* offline: still forget the saved answers below */ }
    // The service worker's saved API answers belong to this login; don't leave them behind.
    if ('caches' in window) await caches.delete('dahaem-api').catch(() => {});
    location.hash = '#/login';
}

// ---- Router ----

const routes = {
    shopping: renderShopping,
    list: renderPickedList,
    'shopping-list': renderPersistedShoppingList,
    inventory: renderInventory,
    recipes: (id) => (id ? renderRecipe(id) : renderRecipes()),
    plans: renderPlans,
    savings: renderSavings,
    login: renderLogin
};

async function route() {
    const [, section = 'shopping', param] = location.hash.replace(/^#\/?/, '/').split('/');
    const render = routes[section] || routes.shopping;
    const onLogin = section === 'login';
    tabbarEl.hidden = onLogin;
    logoutEl.hidden = onLogin;
    // A saved list is still part of the shopping flow, so it keeps that tab highlighted.
    const activeTab = section === 'shopping-list' ? 'shopping' : (routes[section] ? section : 'shopping');
    document.querySelectorAll('.tabbar a').forEach(a => {
        if (a.dataset.tab === activeTab) a.setAttribute('aria-current', 'page');
        else a.removeAttribute('aria-current');
    });

    // Swap in a fresh view element so listeners from the previous screen go away with it.
    const fresh = view.cloneNode(false);
    view.replaceWith(fresh);
    view = fresh;

    staleSince = null;
    const token = ++routeToken;
    try {
        await render(param ? decodeURIComponent(param) : undefined);
    } catch (err) {
        if (err instanceof Superseded) return;
        if (err instanceof LoginRequired) {
            // Come back to this screen once logged in.
            if (token === routeToken) requireLogin();
            return;
        }
        if (token === routeToken) showError(err);
    }
    if (token === routeToken) window.scrollTo(0, 0);
}

// Driven by what the last request actually did: navigator.onLine only knows whether
// there is a network interface, not whether the server is reachable.
function setOffline(offline) {
    offlineEl.hidden = !offline;
}

// Each screen starts at the top; the browser's restore would land mid-list after a reload.
if ('scrollRestoration' in history) history.scrollRestoration = 'manual';
window.addEventListener('hashchange', route);
logoutEl.addEventListener('click', logout);
window.addEventListener('online', () => { if (!offlineEl.hidden) route(); });
route();
