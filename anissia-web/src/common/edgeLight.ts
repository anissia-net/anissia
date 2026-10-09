const SELECTOR = '.as-box, .as-card, .as-table, .as-post, .as-header-bar, .as-menu-panel, .as-glass';

/** 마우스가 올라간 상자의 테두리 빛이 커서를 따라가도록 `--mx`·`--my` 를 넣는다. */
function followPointer() {
  let target: HTMLElement | null = null;
  let x = 0;
  let y = 0;
  let frame = 0;

  const paint = () => {
    frame = 0;
    if (!target) return;
    const rect = target.getBoundingClientRect();
    target.style.setProperty('--mx', `${x - rect.left}px`);
    target.style.setProperty('--my', `${y - rect.top}px`);
  };

  document.addEventListener('pointermove', e => {
    target = (e.target as Element | null)?.closest?.(SELECTOR) as HTMLElement | null;
    x = e.clientX;
    y = e.clientY;
    if (target && !frame) frame = requestAnimationFrame(paint);
  }, {passive: true});
}

const clamp = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v));

/**
 * 마우스가 없는 기기 — 화면 위 광원이 스크롤 깊이를 따라 위아래로 오가고,
 * 기기를 기울이면(자이로) 광원과 테두리 반사각이 함께 기운다.
 */
function followMotion() {
  const root = document.documentElement;
  root.classList.add('as-edge-scroll');

  let tiltX = 0;
  let tiltY = 0;
  let curX = 0;
  let curY = 0;
  let base: {x: number, y: number} | null = null;
  let frame = 0;

  const request = () => {
    if (!frame) frame = requestAnimationFrame(paint);
  };

  function paint() {
    frame = 0;
    curX += (tiltX - curX) * .18;
    curY += (tiltY - curY) * .18;

    const max = root.scrollHeight - innerHeight;
    const depth = max > 0 ? clamp(scrollY / max, 0, 1) : 0;
    const lx = innerWidth * clamp(.72 + .35 * curX, .05, .95);
    const ly = innerHeight * clamp(.14 + .72 * depth + .3 * curY, .02, .98);
    root.style.setProperty('--edge-angle', `${155 + curX * 45 + (depth - .5) * 40}deg`);

    for (const el of document.querySelectorAll<HTMLElement>(SELECTOR)) {
      const rect = el.getBoundingClientRect();
      if (rect.bottom < 0 || rect.top > innerHeight) continue;
      el.style.setProperty('--mx', `${lx - rect.left}px`);
      el.style.setProperty('--my', `${ly - rect.top}px`);
    }

    if (Math.abs(tiltX - curX) > .003 || Math.abs(tiltY - curY) > .003) request();
  }

  const onOrientation = (e: DeviceOrientationEvent) => {
    if (e.beta == null || e.gamma == null) return;
    const angle = screen.orientation?.angle ?? 0;
    const x = angle == 90 ? e.beta : angle == 270 ? -e.beta : e.gamma;
    const y = angle == 90 ? -e.gamma : angle == 270 ? e.gamma : e.beta;
    base ??= {x, y};
    // 기울인 채 들고 있으면 그 자세가 천천히 새 기준이 된다.
    base.x += (x - base.x) * .004;
    base.y += (y - base.y) * .004;
    tiltX = clamp((x - base.x) / 25, -1, 1);
    tiltY = clamp((y - base.y) / 25, -1, 1);
    request();
  };

  const Orientation = (window as any).DeviceOrientationEvent;
  if (Orientation) {
    addEventListener('deviceorientation', onOrientation);
    // iOS 는 권한 전에는 이벤트가 오지 않는다 — 권한은 사용자 동작 안에서만 물을 수 있다.
    if (typeof Orientation.requestPermission === 'function') {
      addEventListener('touchend', () => Orientation.requestPermission().catch(() => {}), {once: true});
    }
  }

  addEventListener('scroll', request, {passive: true});
  addEventListener('resize', request, {passive: true});
  new MutationObserver(request).observe(document.body, {childList: true, subtree: true});
  request();
}

export function installEdgeLight() {
  if (matchMedia('(hover: hover)').matches) {
    followPointer();
  } else {
    followMotion();
  }
}
