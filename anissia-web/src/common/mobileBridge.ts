export type MobileColorMode = 'light' | 'dark';
export interface MobileConfiguration {
  version: 1;
  app: 'anissia' | 'schedule';
  origin: string;
  colorMode: MobileColorMode;
  nativeNotifications?: boolean;
  notificationNonce?: string;
}
export interface MobileNotificationState {
  supported: boolean;
  configured: boolean;
  permission: NotificationPermission;
  enabled: boolean;
  clientId: string;
  token?: string;
  owner?: string;
  bindingId?: string;
  hasRefreshSecret?: boolean;
  refreshSecretVersion?: string;
}
export type MobileNotificationAction = 'status' | 'requestPermission' | 'bind' | 'revoke';
interface MobileNotificationReply {
  requestId: string;
  ok: boolean;
  state?: MobileNotificationState;
  error?: string;
}
export interface MobileNavigation {
  action: 'internal' | 'delegate' | 'block';
  url: string;
}

declare global {
  interface Window {
    __anissiaMobile?: MobileConfiguration;
    AnissiaMobile?: {postMessage(message: string): void};
    __anissiaMobileWebBridgeVersion?: number;
    __anissiaMobileApplyColorMode?: (mode: MobileColorMode) => void;
    __anissiaMobileBack?: () => boolean;
    __anissiaMobileNotificationReply?: (reply: MobileNotificationReply) => void;
  }
}

export function classifyMobileNavigation(value: string, config: Pick<MobileConfiguration, 'app' | 'origin'>, base = config.origin): MobileNavigation {
  let url: URL;
  try { url = new URL(value, base); } catch { return {action: 'block', url: value}; }
  const href = url.href;
  if (url.username || url.password || !['https:', 'http:', 'mailto:', 'tel:'].includes(url.protocol)) {
    return {action: 'block', url: href};
  }
  if (url.origin !== config.origin) return {action: 'delegate', url: href};
  const path = url.pathname.replace(/\/$/, '') || '/';
  if (url.searchParams.getAll('_anissia_browser').includes('1')
    && (path === '/schedule' || path.startsWith('/schedule/'))) {
    return {action: 'delegate', url: href};
  }
  const internal = config.app === 'schedule'
    ? path === '/schedule/2026' || path === '/schedule/2015'
    : !path.startsWith('/schedule/');
  return {action: internal ? 'internal' : 'delegate', url: href};
}

let applyingNativeColorMode = false;
const backHandlers = new Set<() => boolean>();
const notificationRequests = new Map<string, {
  resolve(state: MobileNotificationState): void;
  reject(error: Error): void;
  timer: ReturnType<typeof setTimeout>;
}>();

function configuration(): MobileConfiguration | null {
  if (typeof window === 'undefined' || window.top !== window) return null;
  const config = window.__anissiaMobile;
  if (!config || config.version !== 1 || !['anissia', 'schedule'].includes(config.app)
    || config.origin !== window.location.origin || !['light', 'dark'].includes(config.colorMode)
    || typeof window.AnissiaMobile?.postMessage !== 'function') return null;
  return config;
}

function post(message: Record<string, unknown>) {
  if (!configuration()) return;
  try { window.AnissiaMobile!.postMessage(JSON.stringify({version: 1, ...message})); } catch { }
}

export function nativeNotificationsSupported(): boolean {
  const config = configuration();
  return config?.app === 'anissia' && config.nativeNotifications === true
    && typeof config.notificationNonce === 'string' && config.notificationNonce.length >= 16;
}

export function requestMobileNotifications(action: MobileNotificationAction, payload: Record<string, string> = {}): Promise<MobileNotificationState> {
  if (!nativeNotificationsSupported()) return Promise.reject(new Error('이 앱에서는 기기 알림을 사용할 수 없습니다.'));
  const requestId = crypto.randomUUID();
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      notificationRequests.delete(requestId);
      reject(new Error('앱의 알림 설정 응답을 기다리다 중단했습니다. 다시 시도해 주세요.'));
    }, action === 'requestPermission' ? 60000 : 15000);
    notificationRequests.set(requestId, {resolve, reject, timer});
    try {
      window.AnissiaMobile!.postMessage(JSON.stringify({...payload, version: 1, type: 'notifications', requestId, action,
        nonce: configuration()!.notificationNonce}));
    } catch {
      clearTimeout(timer);
      notificationRequests.delete(requestId);
      reject(new Error('앱의 알림 설정에 연결하지 못했습니다. 다시 시도해 주세요.'));
    }
  });
}

function notificationReply(reply: MobileNotificationReply) {
  if (!nativeNotificationsSupported() || !reply || typeof reply.requestId !== 'string') return;
  const pending = notificationRequests.get(reply.requestId);
  if (!pending) return;
  notificationRequests.delete(reply.requestId);
  clearTimeout(pending.timer);
  const state = reply.state;
  if (reply.ok && state && typeof state.supported === 'boolean' && typeof state.configured === 'boolean'
    && typeof state.enabled === 'boolean' && typeof state.clientId === 'string'
    && (state.hasRefreshSecret == null || typeof state.hasRefreshSecret === 'boolean')
    && (state.refreshSecretVersion == null || /^[a-f0-9]{64}$/.test(state.refreshSecretVersion))
    && ['default', 'granted', 'denied'].includes(state.permission)
    && ['token', 'owner', 'bindingId'].every(key => state[key] == null || typeof state[key] === 'string')) {
    pending.resolve({supported: state.supported, configured: state.configured, permission: state.permission,
      enabled: state.enabled, clientId: state.clientId, token: state.token, owner: state.owner,
      bindingId: state.bindingId, hasRefreshSecret: state.hasRefreshSecret === true, refreshSecretVersion: state.refreshSecretVersion});
  } else {
    pending.reject(new Error(typeof reply.error === 'string' ? reply.error : '앱 알림 설정을 확인하지 못했습니다.'));
  }
}

export function delegateMobileNavigation(value: string): boolean {
  const config = configuration();
  if (!config) return false;
  const destination = classifyMobileNavigation(value, config, window.location.href);
  if (destination.action === 'internal') return false;
  if (destination.action === 'delegate') post({type: 'navigate', url: destination.url});
  return true;
}

export function notifyMobileColorMode(mode: MobileColorMode) {
  const config = configuration();
  if (!config || applyingNativeColorMode || !['light', 'dark'].includes(mode)) return;
  config.colorMode = mode;
  post({type: 'colorMode', mode});
}

function applyNativeColorMode(mode: MobileColorMode) {
  const config = configuration();
  if (!config || !['light', 'dark'].includes(mode)) return;
  config.colorMode = mode;
  const apply = (window as Window & {colorMode?: (mode: MobileColorMode) => void}).colorMode;
  if (typeof apply !== 'function') return;
  applyingNativeColorMode = true;
  try { apply(mode); } finally { applyingNativeColorMode = false; }
}

export function mobilePageReady() {
  const config = configuration();
  if (!config) return;
  applyNativeColorMode(config.colorMode);
  post({type: 'ready'});
}

export function registerMobileBackHandler(handler: () => boolean): () => void {
  backHandlers.add(handler);
  return () => { backHandlers.delete(handler); };
}

export function installMobileBridge(navigateInternal: (path: string) => void): () => void {
  const originalOpen = window.open;
  const originalApply = window.__anissiaMobileApplyColorMode;
  const originalBack = window.__anissiaMobileBack;
  const originalVersion = window.__anissiaMobileWebBridgeVersion;
  const originalNotificationReply = window.__anissiaMobileNotificationReply;
  const navigate = (url: string) => {
    const destination = new URL(url);
    navigateInternal(destination.pathname + destination.search + destination.hash);
  };
  const click = (event: MouseEvent) => {
    if (event.defaultPrevented || (event.type === 'click' ? event.button !== 0 : event.button !== 1) || !configuration()) return;
    const link = (event.target as Element | null)?.closest?.<HTMLAnchorElement>('a[href]');
    if (!link || link.hasAttribute('download')) return;
    if (delegateMobileNavigation(link.href)) {
      event.preventDefault();
      event.stopImmediatePropagation();
    } else if ((link.target && link.target.toLowerCase() !== '_self') || event.type === 'auxclick' || event.metaKey || event.ctrlKey || event.shiftKey) {
      event.preventDefault();
      event.stopImmediatePropagation();
      navigate(link.href);
    }
  };
  const open: typeof window.open = (url, target, features) => {
    if (!configuration()) return originalOpen.call(window, url, target, features);
    const value = url == null || url === '' ? 'about:blank' : String(url);
    if (!delegateMobileNavigation(value)) navigate(new URL(value, window.location.href).href);
    return null;
  };
  const back = () => {
    if (!configuration()) return false;
    for (const handler of [...backHandlers].reverse()) if (handler()) return true;
    return false;
  };

  window.__anissiaMobileWebBridgeVersion = 1;
  window.__anissiaMobileApplyColorMode = applyNativeColorMode;
  window.__anissiaMobileBack = back;
  window.__anissiaMobileNotificationReply = notificationReply;
  window.open = open;
  document.addEventListener('click', click, true);
  document.addEventListener('auxclick', click, true);
  window.addEventListener('anissia-mobile-ready', mobilePageReady);
  mobilePageReady();

  return () => {
    document.removeEventListener('click', click, true);
    document.removeEventListener('auxclick', click, true);
    window.removeEventListener('anissia-mobile-ready', mobilePageReady);
    if (window.open === open) window.open = originalOpen;
    if (window.__anissiaMobileApplyColorMode === applyNativeColorMode) window.__anissiaMobileApplyColorMode = originalApply;
    if (window.__anissiaMobileBack === back) window.__anissiaMobileBack = originalBack;
    if (window.__anissiaMobileNotificationReply === notificationReply) window.__anissiaMobileNotificationReply = originalNotificationReply;
    for (const request of notificationRequests.values()) {
      clearTimeout(request.timer);
      request.reject(new Error('앱 연결이 종료되었습니다.'));
    }
    notificationRequests.clear();
    window.__anissiaMobileWebBridgeVersion = originalVersion;
  };
}
