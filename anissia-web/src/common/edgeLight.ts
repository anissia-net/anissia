import type {Router} from 'vue-router';

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

/** 폰을 손에 들고 볼 때 화면이 바닥과 이루는 평균 각도 (45°~90° 사이). */
const HOLD_ANGLE = 60;
/** 이 각도(°)만큼 기울이면 빛이 끝까지 간다. */
const TILT_RANGE = 12;
/** 이보다 작은 변화(°)는 잡음·손떨림으로 보고 버린다. */
const DEAD_ZONE = .25 / TILT_RANGE;
/** 이만큼(°) 움직이지 않고 IDLE_MS 가 지나면 센서를 쉬게 하고, 쉬는 동안 PROBE_MS 마다 한 번씩만 읽어 본다. */
const MOVE = 1.5 / TILT_RANGE;
const IDLE_MS = 3000;
const PROBE_MS = 2000;
/** 페이지에 들어오면(첫 로드·이동·탭 복귀) 이 동안은 쉬지 않는다. */
const GRACE_MS = 10000;
/** 충전 중이 아니고 이 이하면 자이로를 끈다(배터리 API 가 있는 브라우저만). */
const LOW_BATTERY = .2;
const SENSOR_HZ = 15;
const FRAME_MS = 1000 / 30;

const clamp = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v));
const step = (v: number) => Math.round(v * 2) / 2;
const deg = (rad: number) => rad * 180 / Math.PI;

/** 기울기(beta·gamma, deviceorientation 과 같은 기준)를 읽어 주는 센서. */
interface TiltSource {
  start(fn: (beta: number, gamma: number) => void): void;
  stop(): void;
}

function orientationSource(): TiltSource {
  let handler: ((e: DeviceOrientationEvent) => void) | undefined;
  const stop = () => {
    if (handler) removeEventListener('deviceorientation', handler);
    handler = undefined;
  };
  return {
    start(fn) {
      stop();
      handler = e => {
        if (e.beta != null && e.gamma != null) fn(e.beta, e.gamma);
      };
      addEventListener('deviceorientation', handler);
    },
    stop,
  };
}

/**
 * 안드로이드 크롬 — deviceorientation 은 60Hz 고정이라 GravitySensor 로 주기를 낮춘다.
 * 중력 벡터(기기 좌표)에서 beta·gamma 를 구한다. 실패하면 deviceorientation 으로.
 */
function gravitySource(fallback: TiltSource): TiltSource {
  const Gravity = (window as any).GravitySensor;
  if (!Gravity) return fallback;
  let sensor: any;
  try {
    sensor = new Gravity({frequency: SENSOR_HZ});
  } catch {
    return fallback;
  }
  let fn: ((beta: number, gamma: number) => void) | undefined;
  let failed = false;
  sensor.addEventListener('reading', () => {
    const {x, y, z} = sensor;
    if (x == null || !fn) return;
    // 중력 = (−cosβ·sinγ, sinβ, cosβ·cosγ), γ ∈ [−90°, 90°]
    const s = z < 0 ? -1 : 1;
    fn(deg(Math.atan2(y, s * Math.hypot(x, z))), deg(Math.atan2(-x * s, z * s)));
  });
  sensor.addEventListener('error', () => {
    failed = true;
    sensor.stop();
    if (fn) fallback.start(fn);
  });
  return {
    start(f) {
      fn = f;
      if (failed) fallback.start(f); else sensor.start();
    },
    stop() {
      fn = undefined;
      if (!failed) sensor.stop();
      fallback.stop();
    },
  };
}

/** 페이지 이동 때 부른다 — followMotion 이 켜져 있을 때만 값이 생긴다. */
let enterPage = () => {};

/**
 * 마우스가 없는 기기 — 빛은 상자마다 가운데에서 번지고, 기기를 조금만 기울여도(자이로) 빛과 반사각이 크게 움직인다.
 * 위치는 html 의 `--edge-x/--edge-y`(%) 하나로 모든 상자에 같이 적용한다.
 * 발열 대책: 30fps 상한, 값이 같으면 쓰지 않음, 센서 15Hz(안드로이드), 오래 가만히 있으면 센서 쉼(들어온 뒤 10초는 제외),
 * 탭이 숨거나 화면에 상자가 없으면 정지, 동작 줄이기·배터리 부족이면 자이로 끔.
 */
function followMotion() {
  const root = document.documentElement;
  root.classList.add('as-edge-scroll');

  const Orientation = (window as any).DeviceOrientationEvent;
  const useGyro = (!!Orientation || 'GravitySensor' in window) && !matchMedia('(prefers-reduced-motion: reduce)').matches;
  const source = gravitySource(orientationSource());
  let lowBattery = false;

  const visible = new Set<Element>();
  const observed = new Set<Element>();
  const active = () => !document.hidden && visible.size > 0;

  let tiltX = 0;
  let tiltY = 0;
  let curX = 0;
  let curY = 0;
  let frame = 0;
  let last = 0;
  let written = '';

  const request = () => {
    if (!frame && active()) frame = requestAnimationFrame(paint);
  };

  function paint(now: number) {
    frame = 0;
    const dt = now - last;
    if (dt < FRAME_MS - 2) return request();
    last = now;

    const k = 1 - Math.exp(-Math.min(dt, 100) / 60);
    curX = Math.abs(tiltX - curX) < .002 ? tiltX : curX + (tiltX - curX) * k;
    curY = Math.abs(tiltY - curY) < .002 ? tiltY : curY + (tiltY - curY) * k;

    const max = root.scrollHeight - innerHeight;
    const depth = max > 0 ? clamp(scrollY / max, 0, 1) : 0;
    const angle = step(155 + curX * 60 + (depth - .5) * 20);
    const x = step(50 + 75 * curX);
    const y = step(50 + 90 * curY);
    const key = `${angle} ${x} ${y}`;
    if (key != written) {
      written = key;
      root.style.setProperty('--edge-angle', `${angle}deg`);
      root.style.setProperty('--edge-x', `${x}%`);
      root.style.setProperty('--edge-y', `${y}%`);
    }

    if (curX != tiltX || curY != tiltY) request();
  }

  const toTilt = (beta: number, gamma: number): [number, number] => {
    const angle = screen.orientation?.angle ?? 0;
    const x = angle == 90 ? beta : angle == 270 ? -beta : gamma;
    const y = angle == 90 ? -gamma : angle == 270 ? gamma : beta;
    // 기준 자세: 좌우는 수평, 앞뒤는 손에 들고 볼 때의 평균 각도.
    return [clamp(x / TILT_RANGE, -1, 1), clamp((y - HOLD_ANGLE) / TILT_RANGE, -1, 1)];
  };

  // off: 정지, run: 센서 켜짐, sleep: 센서 쉼(가끔 probe)
  let mode: 'off' | 'run' | 'sleep' = 'off';
  let anchorX = 0;
  let anchorY = 0;
  let stillSince = 0;
  let graceUntil = Infinity;
  let probeTimer: ReturnType<typeof setTimeout> | undefined;

  const moved = (x: number, y: number) => Math.abs(x - anchorX) > MOVE || Math.abs(y - anchorY) > MOVE;

  const onReading = (beta: number, gamma: number) => {
    const [x, y] = toTilt(beta, gamma);
    if (Math.abs(x - tiltX) > DEAD_ZONE || Math.abs(y - tiltY) > DEAD_ZONE) {
      tiltX = x;
      tiltY = y;
      request();
    }
    const now = performance.now();
    if (moved(x, y)) {
      anchorX = x;
      anchorY = y;
      stillSince = now;
    } else if (now - stillSince > IDLE_MS && now > graceUntil) {
      sleep();
    }
  };

  const onProbe = (beta: number, gamma: number) => {
    source.stop();
    clearTimeout(probeTimer);
    if (moved(...toTilt(beta, gamma))) run(); else probe();
  };

  function probe() {
    probeTimer = setTimeout(() => {
      source.start(onProbe);
      // 값이 안 오면(권한 전 등) 끄고 다음 차례로.
      probeTimer = setTimeout(() => {
        source.stop();
        probe();
      }, 1000);
    }, PROBE_MS);
  }

  function halt() {
    source.stop();
    clearTimeout(probeTimer);
  }

  function run() {
    halt();
    mode = 'run';
    stillSince = performance.now();
    source.start(onReading);
  }

  function sleep() {
    halt();
    mode = 'sleep';
    probe();
  }

  const sync = () => {
    const allowed = useGyro && !lowBattery;
    if (!allowed) tiltX = tiltY = 0;
    if (!allowed || !active()) {
      if (mode != 'off') halt();
      mode = 'off';
    } else if (mode == 'off') {
      run();
    }
  };

  const wake = () => {
    if (mode == 'sleep') run();
  };

  const grace = () => {
    graceUntil = performance.now() + GRACE_MS;
    wake();
  };
  enterPage = grace;

  if (useGyro) {
    // iOS 는 권한 전에는 이벤트가 오지 않는다 — 권한은 사용자 동작 안에서만 물을 수 있다.
    if (typeof Orientation?.requestPermission === 'function') {
      addEventListener('touchend', () => Orientation.requestPermission().catch(() => {}), {once: true});
    }
    addEventListener('touchstart', wake, {passive: true});
    screen.orientation?.addEventListener('change', wake);
    if (document.readyState == 'complete') grace(); else addEventListener('load', grace, {once: true});

    (navigator as any).getBattery?.().then((battery: any) => {
      const check = () => {
        lowBattery = !battery.charging && battery.level <= LOW_BATTERY;
        sync();
        request();
      };
      battery.addEventListener('levelchange', check);
      battery.addEventListener('chargingchange', check);
      check();
    }).catch(() => {});
  }

  const io = new IntersectionObserver(entries => {
    for (const entry of entries) {
      if (entry.isIntersecting) visible.add(entry.target); else visible.delete(entry.target);
    }
    sync();
    request();
  });

  let scanTimer: ReturnType<typeof setTimeout> | undefined;
  const scan = () => {
    scanTimer = undefined;
    for (const el of observed) {
      if (el.isConnected) continue;
      io.unobserve(el);
      observed.delete(el);
      visible.delete(el);
    }
    for (const el of document.querySelectorAll(SELECTOR)) {
      if (observed.has(el)) continue;
      observed.add(el);
      io.observe(el);
    }
    sync();
  };

  addEventListener('scroll', () => {
    wake();
    request();
  }, {passive: true});
  addEventListener('resize', request, {passive: true});
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden && useGyro) grace();
    sync();
    request();
  });
  new MutationObserver(() => {
    scanTimer ??= setTimeout(scan, 300);
  }).observe(document.body, {childList: true, subtree: true});
  scan();
}

export function installEdgeLight(router: Router) {
  if (matchMedia('(hover: hover)').matches) {
    followPointer();
  } else {
    followMotion();
    router.afterEach(() => enterPage());
  }
}
