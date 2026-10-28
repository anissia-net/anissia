import {applicationKey, clearBindingIf, clearPushDevice, needsHomeScreen, pushRequest, pushSupported, readBinding, writeBinding, type PushConfig} from './pushDevice';
import {requestPushPermission} from './notificationPermission';

let pending: {owner: string; request: Promise<void>} | null = null;

// Call from the work button before awaiting its save or any server request.
// An async wrapper also turns a synchronous browser error into a rejection.
export async function requestBrowserPushPermission(): Promise<NotificationPermission | null> {
  if (!pushSupported() || needsHomeScreen()) return null;
  return requestPushPermission();
}

export function ensurePushSubscription(owner: string, dat: string, isCurrent: () => boolean): Promise<void> {
  if (!isCurrent()) return Promise.resolve();
  if (pending) {
    if (pending.owner === owner) return pending.request;
    return pending.request.catch(() => {}).then(() => ensurePushSubscription(owner, dat, isCurrent));
  }
  const request = subscribe(owner, dat, isCurrent);
  pending = {owner, request};
  const completed = () => { if (pending?.request === request) pending = null; };
  void request.then(completed, completed);
  return request;
}

async function subscribe(owner: string, dat: string, isCurrent: () => boolean): Promise<void> {
  if (Notification.permission !== 'granted') return;
  const config = await pushRequest<PushConfig>('config', '', undefined, 'GET');
  if (!isCurrent()) return;
  if (!config.enabled) throw new Error('브라우저 알림 발송이 활성화되어 있지 않습니다. 새 자막 소식은 사이트 알림 목록에서 확인해 주세요.');
  const key = applicationKey(config.publicKey);

  const registration = await navigator.serviceWorker.register('/sw.js', {scope: '/', updateViaCache: 'none'});
  await navigator.serviceWorker.ready;
  if (!isCurrent()) return;
  let subscription = await registration.pushManager.getSubscription();
  const binding = await readBinding();
  if (!isCurrent()) return;
  let clientId = '';
  const previousKey = subscription?.options.applicationServerKey;
  const sameKey = previousKey && previousKey.byteLength === key.byteLength
    && new Uint8Array(previousKey).every((byte, index) => byte === key[index]);
  if (subscription && binding?.owner === owner && sameKey) {
    const status = await pushRequest<{subscribed: boolean}>('subscription/status', dat, {endpoint: subscription.endpoint, clientId: binding.clientId});
    if (!isCurrent() || status.subscribed) return;
    clientId = binding.clientId;
  } else if (subscription) {
    // Revoke the previous device locally without sending another owner's
    // endpoint to the current account's unsubscribe API.
    await clearPushDevice(binding?.owner === owner ? dat : '', binding?.clientId, isCurrent);
    subscription = null;
  }
  if (!isCurrent()) return;

  let created: PushSubscription | null = null;
  let completed = false;
  try {
    if (!subscription) {
      created = await registration.pushManager.subscribe({userVisibleOnly: true, applicationServerKey: key});
      subscription = created;
      clientId = crypto.randomUUID();
    }
    if (!isCurrent()) return;
    await pushRequest('subscription', dat, {...subscription.toJSON(), clientId});
    if (!isCurrent()) return;
    await writeBinding({clientId, owner});
    if (!isCurrent()) return;
    completed = true;
  } finally {
    // Never tear down a reused subscription when a refresh request fails.
    if (!completed && created) {
      if (clientId) await clearBindingIf(clientId).catch(() => {});
      await created.unsubscribe().catch(() => {});
    }
  }
}
