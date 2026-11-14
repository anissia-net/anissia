export type MobileColorMode = 'light' | 'dark';
export interface MobileConfiguration {
  version: 1;
  app: 'anissia' | 'schedule';
  origin: string;
  colorMode: MobileColorMode;
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
    : path !== '/schedule' && !path.startsWith('/schedule/');
  return {action: internal ? 'internal' : 'delegate', url: href};
}

let applyingNativeColorMode = false;
const backHandlers = new Set<() => boolean>();

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
    window.__anissiaMobileWebBridgeVersion = originalVersion;
  };
}
