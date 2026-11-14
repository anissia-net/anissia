import 'dart:convert';

import 'navigation_policy.dart';

String mobileBridgeScript({
  required MobileAppKind app,
  required Uri origin,
  required String colorMode,
}) {
  final config = jsonEncode({
    'version': 1,
    'app': app.name,
    'origin': origin.origin,
    'colorMode': colorMode,
  });
  return '''
(() => {
  const config = $config;
  if (window !== window.top || location.origin !== config.origin) return;
  window.__anissiaMobile = config;
  const send = message => window.AnissiaMobile?.postMessage(JSON.stringify({version: 1, ...message}));
  if (config.app === 'schedule') {
    const styleId = 'anissia-mobile-schedule-inset';
    let style = document.getElementById(styleId);
    if (!style) {
      style = document.createElement('style');
      style.id = styleId;
      document.head.appendChild(style);
    }
    style.textContent = '.sc2026-host:not(.embedded) > #sc2026 > .wrap, .sc2015-host:not(.embedded) #sc2015 .main { padding-bottom: 88px !important; }';
  }
  const isOfficial = () => window.__anissiaMobileWebBridgeVersion === 1;
  const shouldDelegate = input => {
    let url;
    try { url = new URL(input, location.href); } catch { return true; }
    if (!['https:', 'http:'].includes(url.protocol) || url.username || url.password) return true;
    if (url.origin !== config.origin) return true;
    const path = url.pathname.length > 1 ? url.pathname.replace(/\\/\$/, '') : url.pathname;
    if ((path === '/schedule' || path.startsWith('/schedule/')) &&
        url.searchParams.getAll('_anissia_browser').includes('1')) return true;
    return config.app === 'schedule'
      ? !['/schedule/2026', '/schedule/2015'].includes(path)
      : path === '/schedule' || path.startsWith('/schedule/');
  };
  const delegate = input => {
    let url;
    try { url = new URL(input, location.href).href; } catch { return; }
    send({type: 'navigate', url});
  };
  const installRouterGuard = () => {
    if (isOfficial() || window.__anissiaMobileFallbackRouter) return true;
    const router = document.getElementById('app')?.__vue_app__?.config?.globalProperties?.\$router;
    if (typeof router?.beforeEach !== 'function') return false;
    window.__anissiaMobileFallbackRouter = router.beforeEach(to => {
      if (isOfficial() || !shouldDelegate(to.fullPath)) return true;
      delegate(to.fullPath);
      return false;
    });
    return true;
  };
  if (!isOfficial() && !window.__anissiaMobileFallbackInstalled) {
    window.__anissiaMobileFallbackInstalled = true;
    document.addEventListener('click', event => {
      if (isOfficial() || event.defaultPrevented || event.button !== 0) return;
      const link = event.target instanceof Element ? event.target.closest('a[href]') : null;
      if (!link) return;
      if (shouldDelegate(link.href)) {
        event.preventDefault(); event.stopImmediatePropagation(); delegate(link.href);
      } else if (link.target && link.target !== '_self') {
        event.preventDefault(); event.stopImmediatePropagation(); location.assign(link.href);
      }
    }, true);
    const originalOpen = window.open;
    window.open = function(url, ...args) {
      if (isOfficial()) return originalOpen.call(this, url, ...args);
      if (!url) return null;
      if (shouldDelegate(url)) delegate(url); else location.assign(new URL(url, location.href).href);
      return null;
    };
    for (const method of ['pushState', 'replaceState']) {
      const original = history[method];
      history[method] = function(state, unused, url) {
        if (!isOfficial() && url && shouldDelegate(url)) {
          delegate(url);
          return;
        }
        return original.call(this, state, unused, url);
      };
    }
    const originalSetItem = Storage.prototype.setItem;
    Storage.prototype.setItem = function(key, value) {
      originalSetItem.call(this, key, value);
      if (!isOfficial() && !window.__anissiaMobileApplyingMode && this === localStorage &&
          ['schedule2015ColorMode', 'schedule2026ColorMode'].includes(key) &&
          ['light', 'dark'].includes(value)) send({type: 'colorMode', mode: value});
    };
    window.__anissiaMobileBack = () => {
      if (!document.querySelector('#sc2015 .popup, #sc2026 .popup')) return false;
      window.dispatchEvent(new KeyboardEvent('keydown', {key: 'Escape', bubbles: true}));
      return true;
    };
    window.__anissiaMobileApplyColorMode = mode => {
      window.__anissiaMobileApplyingMode = true;
      try { window.colorMode?.(mode); }
      finally { window.__anissiaMobileApplyingMode = false; }
    };
  }
  window.dispatchEvent(new Event('anissia-mobile-ready'));
  let attempts = 0;
  const apply = () => {
    const routerReady = installRouterGuard();
    const modeReady = config.app !== 'schedule' || typeof window.colorMode === 'function';
    if (routerReady && modeReady) {
      window.__anissiaMobileApplyColorMode?.(config.colorMode);
      send({type: 'ready'});
    } else if (++attempts < 100) {
      setTimeout(apply, 50);
    } else {
      send({type: 'bridgeError'});
    }
  };
  apply();
})();
''';
}
