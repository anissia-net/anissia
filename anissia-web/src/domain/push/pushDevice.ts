// Kept independent of the session/ajax modules so logout can revoke this device
// with the old credentials, without a circular session -> ajax -> session import.
export interface PushBinding { clientId: string; owner: string }
export interface PushConfig { enabled: boolean; publicKey: string }
const api = import.meta.env.VITE_API_PATH;
const database = 'anissia-push';

export function pushSupported(): boolean {
  return window.isSecureContext && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
}

export function needsHomeScreen(): boolean {
  const ios = /iPad|iPhone|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  return ios && !matchMedia('(display-mode: standalone)').matches && !(navigator as Navigator & {standalone?: boolean}).standalone;
}

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(database, 1);
    request.onupgradeneeded = () => request.result.createObjectStore('settings');
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(new Error('알림 설정을 저장할 수 없습니다. 브라우저 저장소 설정을 확인해 주세요.'));
    request.onblocked = () => reject(new Error('다른 애니시아 탭을 닫고 다시 시도해 주세요.'));
  });
}

export async function readBinding(): Promise<PushBinding | null> {
  const db = await openDatabase();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction('settings', 'readonly');
    const request = transaction.objectStore('settings').get('binding');
    transaction.oncomplete = () => { db.close(); resolve(request.result || null); };
    transaction.onerror = () => { db.close(); reject(new Error('브라우저 알림 설정을 읽지 못했습니다.')); };
    transaction.onabort = () => { db.close(); reject(new Error('브라우저 알림 설정 읽기가 중단되었습니다.')); };
  });
}

export async function clearBindingIf(clientId: string, shouldClear: () => boolean = () => true): Promise<boolean> {
  const db = await openDatabase();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction('settings', 'readwrite');
    const store = transaction.objectStore('settings');
    const read = store.get('binding');
    let removed = false;
    read.onsuccess = () => {
      if (read.result?.clientId === clientId && shouldClear()) { store.delete('binding'); removed = true; }
    };
    transaction.oncomplete = () => { db.close(); resolve(removed); };
    transaction.onerror = () => { db.close(); reject(new Error('이전 알림 설정을 해제하지 못했습니다.')); };
    transaction.onabort = () => { db.close(); reject(new Error('이전 알림 설정 해제가 중단되었습니다.')); };
  });
}

export async function syncPushOwner(owner: string, isCurrent: () => boolean): Promise<void> {
  if (!('serviceWorker' in navigator) || !await navigator.serviceWorker.getRegistration('/')) return;
  const binding = await readBinding();
  if (isCurrent() && binding && binding.owner !== owner) await clearPushDevice('', binding.clientId, isCurrent);
}

export async function writeBinding(binding: PushBinding | null): Promise<void> {
  const db = await openDatabase();
  return new Promise((resolve, reject) => {
    const transaction = db.transaction('settings', 'readwrite');
    const store = transaction.objectStore('settings');
    if (binding) store.put(binding, 'binding');
    else store.delete('binding');
    transaction.oncomplete = () => { db.close(); resolve(); };
    transaction.onerror = () => { db.close(); reject(new Error('브라우저 알림 설정을 저장하지 못했습니다.')); };
    transaction.onabort = () => { db.close(); reject(new Error('브라우저 알림 설정 저장이 중단되었습니다.')); };
  });
}

export async function pushRequest<T>(path: string, dat: string, body?: unknown, method = 'POST'): Promise<T> {
  const controller = new AbortController();
  const timeout = window.setTimeout(() => controller.abort(), 10000);
  try {
    const response = await fetch(`${api}/push/${path}`, {
      method, cache: 'no-store', signal: controller.signal,
      headers: {...(dat ? {dat} : {}), ...(body ? {'Content-Type': 'application/json'} : {})},
      body: body ? JSON.stringify(body) : undefined,
    });
    if (!response.ok) throw new Error('알림 서버에 연결하지 못했습니다. 잠시 후 다시 시도해 주세요.');
    const result = await response.json();
    if (result.code !== 'ok') throw new Error(result.message || '알림 설정을 변경하지 못했습니다.');
    return result.data as T;
  } finally { clearTimeout(timeout); }
}

export function applicationKey(value: string): Uint8Array<ArrayBuffer> {
  const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
  const binary = atob(normalized + '='.repeat((4 - normalized.length % 4) % 4));
  return Uint8Array.from(binary, char => char.charCodeAt(0));
}

// Revoke locally first: even an already queued push cannot disclose the old
// account's titles after logout. Server removal and browser unsubscribe then
// run independently so either can succeed while the other is unavailable.
export async function clearPushDevice(dat: string, expectedClientId?: string, shouldClear: () => boolean = () => true): Promise<void> {
  if (!('serviceWorker' in navigator)) return;
  let localFailure: unknown;
  let binding: PushBinding | null = null;
  try { binding = await readBinding(); } catch (error) { localFailure = error; }
  if (expectedClientId && binding?.clientId !== expectedClientId) return;
  if (!shouldClear()) return;
  const registration = await navigator.serviceWorker.getRegistration('/');
  const subscription = await registration?.pushManager?.getSubscription();
  if (!shouldClear()) return;
  // Capture the subscription before the conditional deletion. A new account
  // can replace it concurrently; only this captured old endpoint is revoked.
  if (binding) {
    try {
      const removed = await clearBindingIf(binding.clientId, shouldClear);
      if (!removed) return;
    } catch (error) { localFailure = error; }
  }
  const notices = await registration?.getNotifications() || [];
  notices.filter(notice => !binding || notice.tag.startsWith(`${binding.clientId}:`)).forEach(notice => notice.close());
  if (!subscription) {
    if (localFailure) throw localFailure;
    return;
  }
  const results = await Promise.allSettled([
    subscription.unsubscribe().then(success => { if (!success) throw new Error('구독 해제 실패'); }),
    dat ? pushRequest('subscription', dat, {endpoint: subscription.endpoint}, 'DELETE') : Promise.reject(new Error('로그인 필요')),
  ]);
  if (localFailure) throw new Error('브라우저에 저장된 알림 설정을 지우지 못했습니다. 브라우저의 애니시아 알림 권한을 차단해 주세요.');
  if (results.every(result => result.status === 'rejected')) {
    throw new Error('이 기기의 작품 알림은 차단했습니다. 연결이 복구되면 알림 해제를 다시 눌러 주세요.');
  }
}
