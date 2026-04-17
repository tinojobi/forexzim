/*
 * ZimRate Service Worker
 *
 * Cache strategy:
 *   Static assets (CSS, images, fonts) — cache-first
 *   HTML pages                         — network-first, cached fallback when offline
 *   /api/rates/* responses             — network-first, cached fallback when offline
 *
 * Bump VERSION when deploying changes — old caches are cleaned up automatically.
 */

const VERSION       = '%%SW_VERSION%%';
const STATIC_CACHE  = `zimrate-static-${VERSION}`;
const PAGES_CACHE   = `zimrate-pages-${VERSION}`;
const API_CACHE     = `zimrate-api-${VERSION}`;
const ALL_CACHES    = [STATIC_CACHE, PAGES_CACHE, API_CACHE];

const PRECACHE_STATIC = [
    '/css/styles.css',
    '/favicon.svg',
    '/logo.svg',
    '/manifest.json',
];

const PRECACHE_PAGES = ['/'];

// ── Install ──────────────────────────────────────────────────────────────────

self.addEventListener('install', function (event) {
    event.waitUntil(
        Promise.all([
            caches.open(STATIC_CACHE).then(function (c) { return c.addAll(PRECACHE_STATIC); }),
            caches.open(PAGES_CACHE).then(function (c)  { return c.addAll(PRECACHE_PAGES);  }),
        ]).then(function () { return self.skipWaiting(); })
    );
});

// ── Activate — purge old caches ──────────────────────────────────────────────

self.addEventListener('activate', function (event) {
    event.waitUntil(
        caches.keys().then(function (keys) {
            return Promise.all(
                keys.filter(function (k) { return ALL_CACHES.indexOf(k) === -1; })
                    .map(function (k) { return caches.delete(k); })
            );
        }).then(function () { return self.clients.claim(); })
    );
});

// ── Fetch ────────────────────────────────────────────────────────────────────

self.addEventListener('fetch', function (event) {
    var req = event.request;
    if (req.method !== 'GET') return;

    var url = new URL(req.url);

    // Only handle same-origin requests
    if (url.origin !== self.location.origin) return;

    // API rate data — network-first, cache fallback
    if (url.pathname.startsWith('/api/rates')) {
        event.respondWith(networkFirst(req, API_CACHE));
        return;
    }

    // Static assets — cache-first
    if (isStaticAsset(url.pathname)) {
        event.respondWith(cacheFirst(req, STATIC_CACHE));
        return;
    }

    // HTML pages — network-first, cache fallback
    var accept = req.headers.get('accept') || '';
    if (accept.indexOf('text/html') !== -1) {
        event.respondWith(networkFirst(req, PAGES_CACHE));
        return;
    }
});

// ── Strategies ───────────────────────────────────────────────────────────────

function cacheFirst(req, cacheName) {
    return caches.match(req).then(function (cached) {
        if (cached) return cached;
        return fetch(req).then(function (fresh) {
            if (fresh && fresh.ok) {
                caches.open(cacheName).then(function (c) { c.put(req, fresh.clone()); });
            }
            return fresh;
        });
    });
}

function networkFirst(req, cacheName) {
    return fetch(req).then(function (fresh) {
        if (fresh && fresh.ok) {
            caches.open(cacheName).then(function (c) { c.put(req, fresh.clone()); });
        }
        return fresh;
    }).catch(function () {
        return caches.match(req).then(function (cached) {
            if (cached) return cached;
            // For HTML requests fall back to the cached homepage
            var accept = req.headers.get('accept') || '';
            if (accept.indexOf('text/html') !== -1) {
                return caches.match('/');
            }
            return new Response(JSON.stringify({ error: 'offline' }), {
                status: 503,
                headers: { 'Content-Type': 'application/json' }
            });
        });
    });
}

function isStaticAsset(pathname) {
    return pathname.startsWith('/css/')
        || pathname.startsWith('/js/')
        || pathname.endsWith('.svg')
        || pathname.endsWith('.png')
        || pathname.endsWith('.jpg')
        || pathname.endsWith('.ico')
        || pathname.endsWith('.webp')
        || pathname === '/manifest.json';
}
