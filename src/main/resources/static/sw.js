// DAHÄM service worker.
// - App shell (HTML, CSS, JS, icons): cache first, so the app opens without a network.
// - GET /api/*: network first, falling back to the last good answer, so the shopping
//   list you opened at home is still there in the shop's dead zone. Fallback answers
//   carry an X-Dahaem-Cached header with the time they were saved. /api/auth/* is never
//   cached, and the app deletes the API cache on logout.
// Bump CACHE_VERSION whenever a shell file changes so clients pick up the new files.
const CACHE_VERSION = 'v5';
const SHELL_CACHE = `dahaem-shell-${CACHE_VERSION}`;
const API_CACHE = 'dahaem-api';

const SHELL_FILES = [
    './',
    'index.html',
    'app.css',
    'app.js',
    'manifest.webmanifest',
    'icons/icon.svg',
    'icons/icon-192.png',
    'icons/icon-512.png',
    'icons/icon-maskable-512.png',
    'icons/apple-touch-icon.png'
];

self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(SHELL_CACHE)
            .then(cache => cache.addAll(SHELL_FILES))
            .then(() => self.skipWaiting())
    );
});

self.addEventListener('activate', event => {
    event.waitUntil(
        caches.keys()
            .then(keys => Promise.all(keys
                .filter(key => key.startsWith('dahaem-shell-') && key !== SHELL_CACHE)
                .map(key => caches.delete(key))))
            .then(() => self.clients.claim())
    );
});

self.addEventListener('fetch', event => {
    const request = event.request;
    if (request.method !== 'GET') return;
    const url = new URL(request.url);
    if (url.origin !== self.location.origin) return;

    // Login state and CSRF tokens must always come from the server, never from a saved copy.
    if (url.pathname.startsWith('/api/auth/')) return;
    if (url.pathname.startsWith('/api/')) {
        event.respondWith(networkFirst(request));
    } else if (request.mode === 'navigate') {
        // Any page load is the single-page app; serve the cached shell when offline.
        event.respondWith(fetch(request).catch(() => caches.match('index.html', { cacheName: SHELL_CACHE })));
    } else {
        event.respondWith(caches.match(request, { cacheName: SHELL_CACHE }).then(hit => hit || fetch(request)));
    }
});

async function networkFirst(request) {
    const cache = await caches.open(API_CACHE);
    try {
        const response = await fetch(request);
        if (response.ok) {
            // Store a copy stamped with the time it was saved.
            const body = await response.clone().blob();
            const headers = new Headers(response.headers);
            headers.set('X-Dahaem-Cached', new Date().toISOString());
            await cache.put(request, new Response(body, { status: response.status, headers }));
        }
        return response;
    } catch (err) {
        const cached = await cache.match(request);
        if (cached) return cached;
        throw err;
    }
}
