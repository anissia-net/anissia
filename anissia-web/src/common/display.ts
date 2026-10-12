import {onUnmounted, readonly, ref} from 'vue';

export const ZOOMS: readonly number[] = Array.from({length: 51}, (_, index) => 50 + index * 5);
const ZOOM_COOKIE = 'anissia-zoom';
const COOKIE_MAX_AGE = 60 * 60 * 24 * 365 * 10;

function normalizeZoom(value: unknown): number {
    const zoom = Number(value);
    // Keep existing preferences (including the former 67% step) near their saved size.
    return Number.isFinite(zoom) && zoom >= ZOOMS[0] && zoom <= ZOOMS[ZOOMS.length - 1]
        ? Math.round(zoom / 5) * 5 : 100;
}

export function useDisplayZoom() {
    const zoom = ref(100);
    let channel: BroadcastChannel | null = null;

    function setZoom(value: number) {
        zoom.value = normalizeZoom(value);
        document.documentElement.style.fontSize = zoom.value === 100 ? '' : `${zoom.value}%`;
    }

    function restoreZoom() {
        try {
            setZoom(normalizeZoom(document.cookie.match(/(?:^|; *)anissia-zoom=([^;]*)/)?.[1]));
        } catch {}
    }

    function changeZoom(direction: -1 | 0 | 1) {
        const index = ZOOMS.indexOf(zoom.value);
        setZoom(direction === 0 ? 100 : ZOOMS[Math.min(Math.max(index + direction, 0), ZOOMS.length - 1)]);
        try {
            const hostname = location.hostname;
            const domain = hostname === 'anissia.net' || hostname.endsWith('.anissia.net') ? '; domain=.anissia.net' : '';
            document.cookie = `${ZOOM_COOKIE}=${zoom.value}; path=/; max-age=${COOKIE_MAX_AGE}${domain}; samesite=lax`;
        } catch {}
        try { channel?.postMessage(zoom.value); } catch {}
    }

    restoreZoom();
    try {
        channel = new BroadcastChannel(ZOOM_COOKIE);
        channel.onmessage = restoreZoom;
    } catch {}

    const store = (window as unknown as {cookieStore?: EventTarget}).cookieStore;
    const syncCookie = (event: Event) => {
        const {changed = [], deleted = []} = event as Event & {changed?: {name: string}[]; deleted?: {name: string}[]};
        if ([...changed, ...deleted].some(cookie => cookie.name === ZOOM_COOKIE)) restoreZoom();
    };
    const syncVisible = () => { if (!document.hidden) restoreZoom(); };
    store?.addEventListener('change', syncCookie);
    document.addEventListener('visibilitychange', syncVisible);
    window.addEventListener('focus', syncVisible);
    window.addEventListener('pageshow', syncVisible);

    onUnmounted(() => {
        channel?.close();
        store?.removeEventListener('change', syncCookie);
        document.removeEventListener('visibilitychange', syncVisible);
        window.removeEventListener('focus', syncVisible);
        window.removeEventListener('pageshow', syncVisible);
        document.documentElement.style.fontSize = '';
    });

    return {zoom: readonly(zoom), changeZoom};
}
