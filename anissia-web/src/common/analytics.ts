export function analyticsLocation(value: string, origin: string): string {
  try {
    const url = new URL(value, origin);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password) return '';
    const path = url.pathname.replace(/^\/(register|recover)\/.*$/i, '/$1');
    return url.origin + path;
  } catch { return ''; }
}

export function sensitiveAnalyticsLocation(value: string, origin: string): boolean {
  if (!value) return false;
  try {
    const url = new URL(value, origin);
    return !!url.search || /^\/(register|recover)\/.+/i.test(url.pathname);
  } catch { return true; }
}

export function createAnalytics(trackingId: string, defer: (task: () => void) => void) {
  const target = window as Window & Record<string, any>;
  const origin = window.location.origin;
  const disabledKey = `ga-disable-${trackingId}`;
  let disabled = !trackingId;
  let installed = false;
  let configured = false;
  let previous = document.referrer ? analyticsLocation(document.referrer, origin) : '';

  const native = () => typeof target.AnissiaMobile?.postMessage === 'function'
    || ['anissia', 'schedule'].includes(target.__anissiaMobile?.app)
    || /\bAndroid\b[^)]*;\s*wv\)/.test(window.navigator?.userAgent || '');
  function guard(value: string): boolean {
    // Do not re-enable delayed automatic events after a sensitive route visit.
    disabled ||= native() || sensitiveAnalyticsLocation(value, origin);
    target[disabledKey] = disabled;
    return disabled;
  }
  guard(window.location.href);
  guard(document.referrer);
  window.addEventListener('anissia-mobile-ready', () => guard(window.location.href));

  function send(...args: unknown[]) {
    if (guard(window.location.href)) return;
    target.dataLayer ||= [];
    target.gtag ||= function() { target.dataLayer.push(arguments); };
    target.gtag(...args);
  }

  function page(value: string, title: string) {
    if (guard(value)) return;
    const location = analyticsLocation(value, origin);
    if (!location) return;
    const fields = {page_path: new URL(location).pathname, page_location: location,
      page_referrer: previous, page_title: title};
    previous = location;
    send('set', fields);
    if (!configured) {
      configured = true;
      send('js', new Date());
      send('config', trackingId, {send_page_view: false, ...fields});
    }
    send('event', 'page_view', {send_to: trackingId, ...fields});
  }

  function install() {
    if (installed || guard(window.location.href)) return;
    installed = true;
    const load = () => defer(() => {
      if (guard(window.location.href)) return;
      const script = document.createElement('script');
      script.async = true;
      script.src = `https://www.googletagmanager.com/gtag/js?id=${encodeURIComponent(trackingId)}`;
      document.head.append(script);
    });
    if (document.readyState === 'complete') load();
    else window.addEventListener('load', load, {once: true});
  }

  return {beforeNavigation: guard, page, install};
}
