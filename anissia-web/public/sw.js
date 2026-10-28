// Notification-only service worker. Intentionally no fetch handler or cache.
self.addEventListener('install', event => event.waitUntil(self.skipWaiting()));
self.addEventListener('activate', event => event.waitUntil(self.clients.claim()));

function readBinding() {
  return new Promise(resolve => {
    const request = indexedDB.open('anissia-push', 1);
    request.onupgradeneeded = () => request.result.createObjectStore('settings');
    request.onerror = () => resolve(null);
    request.onblocked = () => resolve(null);
    request.onsuccess = () => {
      const db = request.result;
      const transaction = db.transaction('settings', 'readonly');
      const read = transaction.objectStore('settings').get('binding');
      transaction.oncomplete = () => { db.close(); resolve(read.result || null); };
      transaction.onerror = () => { db.close(); resolve(null); };
      transaction.onabort = () => { db.close(); resolve(null); };
    };
  });
}

function targetUrl(value) {
  try {
    const url = new URL(value, self.location.origin);
    if (url.origin === self.location.origin) {
      const animeNo = /^\/anime\/([1-9]\d*)$/.exec(url.pathname)?.[1]
        || (url.pathname === '/anime' ? url.searchParams.get('animeNo') : null);
      if (/^[1-9]\d*$/.test(animeNo || '') && Number.isSafeInteger(Number(animeNo))) {
        return `${self.location.origin}/anime/${animeNo}`;
      }
    }
  } catch (_) { /* Invalid payloads open the notifications page. */ }
  return `${self.location.origin}/notifications`;
}

self.addEventListener('push', event => {
  event.waitUntil((async () => {
    let payload = {};
    try { payload = event.data?.json() || {}; } catch (_) { /* Use a generic notice. */ }
    const binding = await readBinding();
    const allowed = typeof payload.clientId === 'string' && binding?.clientId === payload.clientId;
    // A push already in flight may arrive after logout/account switching.
    // Never show the previous account's titles, but still honor userVisibleOnly.
    await self.registration.showNotification(allowed && typeof payload.title === 'string' ? payload.title.slice(0, 200) : '애니시아', {
      body: allowed && typeof payload.body === 'string' ? payload.body.slice(0, 500) : '알림 설정이 변경되었습니다. 사이트에서 설정을 확인해 주세요.',
      icon: '/favicon.png',
      badge: '/push-icon.svg',
      tag: allowed && typeof payload.tag === 'string' ? `${payload.clientId}:${payload.tag.slice(0, 150)}` : 'anissia-push-settings',
      data: {url: allowed ? targetUrl(payload.url) : `${self.location.origin}/notifications`},
    });
  })());
});

self.addEventListener('notificationclick', event => {
  event.notification.close();
  event.waitUntil((async () => {
    const url = targetUrl(event.notification.data?.url);
    const windows = await self.clients.matchAll({type: 'window', includeUncontrolled: true});
    const existing = windows.find(client => client.url === url);
    if (existing) await existing.focus();
    else await self.clients.openWindow(url);
  })());
});
