import type {Router} from 'vue-router';

const SELECTOR = '.as-box, .as-card, .as-table, .as-post, .as-header-bar, .as-menu-panel, .as-glass';

/** 마우스가 올라간 상자의 테두리 빛이 커서를 따라가도록 `--edge-mx`·`--edge-my` 를 넣는다. */
function followPointer() {
  let target: HTMLElement | null = null;
  let x = 0;
  let y = 0;
  let frame = 0;

  const paint = () => {
    frame = 0;
    if (!target) return;
    const rect = target.getBoundingClientRect();
    target.style.setProperty('--edge-mx', `${x - rect.left}px`);
    target.style.setProperty('--edge-my', `${y - rect.top}px`);
  };

  document.addEventListener('pointermove', e => {
    target = (e.target as Element | null)?.closest?.(SELECTOR) as HTMLElement | null;
    x = e.clientX;
    y = e.clientY;
    if (target && !frame) frame = requestAnimationFrame(paint);
  }, {passive: true});
}

/** 이 각도(°)만큼 기울이면 빛이 끝까지 간다. */
const TILT_RANGE = 12;
/** 앞뒤 기준 각도는 고정하지 않고 들고 있는 자세를 이 시간 상수(ms)로 천천히 따라간다 — 눕혀 들어도 빛이 끝에 붙지 않는다. */
const HOLD_MS = 4000;
/** 이보다 작은 변화(°)는 잡음·손떨림으로 보고 버린다. */
const DEAD_ZONE = .25 / TILT_RANGE;
/** 이만큼(°) 움직이지 않고 IDLE_MS 가 지나면 쉰다 — 빛은 멈추고 센서는 느리게 보다가 MOVE 이상 움직이면 바로 깨어난다. */
const MOVE = 1.5;
const IDLE_MS = 3000;
/** 쉼이 이만큼 이어지면(책상 위 등) 센서를 끄고 PROBE_MS 마다 한 번만 읽는다. */
const DEEP_MS = 20000;
const PROBE_MS = 600;
/** 페이지에 들어오면(첫 로드·이동·탭 복귀) 이 동안은 쉬지 않는다. */
const GRACE_MS = 10000;
/** 충전 중이 아니고 이 이하면 자이로를 끈다(배터리 API 가 있는 브라우저만). */
const LOW_BATTERY = .2;
/** GravitySensor 주기 — 움직일 때 / 쉴 때. */
const FAST_HZ = 30;
const SLOW_HZ = 10;
const FRAME_MS = 1000 / 60;
/** 빛이 기울기를 따라가는 시간 상수(ms). */
const FOLLOW_MS = 40;

const clamp = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v));
const step = (v: number) => Math.round(v * 2) / 2;
const deg = (rad: number) => rad * 180 / Math.PI;

/** 기울기(beta·gamma, deviceorientation 과 같은 기준)를 읽어 주는 센서. slow 면 낮은 주기로. */
interface TiltSource {
  start(fn: (beta: number, gamma: number) => void, slow?: boolean): void;
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
 * 안드로이드 크롬 — deviceorientation 은 60Hz 고정이라 GravitySensor 로 주기를 고른다(움직일 때 FAST_HZ, 쉴 때 SLOW_HZ).
 * 중력 벡터(기기 좌표)에서 beta·gamma 를 구한다. 실패하면 deviceorientation 으로.
 */
function gravitySource(fallback: TiltSource): TiltSource {
  const Gravity = (window as any).GravitySensor;
  if (!Gravity) return fallback;
  const sensors = new Map<number, any>();
  let fn: ((beta: number, gamma: number) => void) | undefined;
  let current: any;
  let failed = false;

  const create = (hz: number) => {
    let sensor = sensors.get(hz);
    if (sensor) return sensor;
    sensor = new Gravity({frequency: hz});
    sensor.addEventListener('reading', () => {
      const {x, y, z} = sensor;
      if (x == null || !fn || current != sensor) return;
      // 중력 = (−cosβ·sinγ, sinβ, cosβ·cosγ), γ ∈ [−90°, 90°]
      const s = z < 0 ? -1 : 1;
      fn(deg(Math.atan2(y, s * Math.hypot(x, z))), deg(Math.atan2(-x * s, z * s)));
    });
    sensor.addEventListener('error', () => {
      failed = true;
      sensor.stop();
      current = undefined;
      if (fn) fallback.start(fn);
    });
    sensors.set(hz, sensor);
    return sensor;
  };

  try {
    create(FAST_HZ);
  } catch {
    return fallback;
  }
  return {
    start(f, slow) {
      fn = f;
      if (!failed) {
        try {
          const next = create(slow ? SLOW_HZ : FAST_HZ);
          if (current != next) current?.stop();
          current = next;
          current.start();
          return;
        } catch {
          failed = true;
        }
      }
      fallback.start(f);
    },
    stop() {
      fn = undefined;
      current?.stop();
      current = undefined;
      fallback.stop();
    },
  };
}

/** 페이지 이동 때 부른다 — followMotion 이 켜져 있을 때만 값이 생긴다. */
let enterPage = () => {};

/**
 * 마우스가 없는 기기 — 빛은 상자마다 가운데에서 번지고, 기기를 조금만 기울여도(자이로) 빛과 반사각이 크게 움직인다.
 * 위치·각도(`--edge-mx/--edge-my`(%)·`--edge-deg`)는 화면 근처 상자에만 넣는다 — html 에 쓰면 바뀔 때마다 문서 전체 스타일을 다시 계산한다.
 * 상태: run(센서 빠르게) → 가만히 IDLE_MS → still(빛 멈춤, 센서 느리게, 움직이면 즉시 run) → DEEP_MS → deep(센서 끔, PROBE_MS 마다 확인).
 * 탭이 숨거나 화면에 상자가 없으면 off, 동작 줄이기·배터리 부족이면 자이로 끔.
 */
function followMotion() {
  const root = document.documentElement;
  root.classList.add('as-edge-scroll');

  const Orientation = (window as any).DeviceOrientationEvent;
  const useGyro = (!!Orientation || 'GravitySensor' in window) && !matchMedia('(prefers-reduced-motion: reduce)').matches;
  const source = gravitySource(orientationSource());
  let lowBattery = false;

  const visible = new Set<HTMLElement>();
  const observed = new Set<HTMLElement>();
  const active = () => !document.hidden && visible.size > 0;

  let tiltX = 0;
  let tiltY = 0;
  let curX = 0;
  let curY = 0;
  let frame = 0;
  let last = 0;
  let deg = '';
  let mx = '';
  let my = '';

  const put = (el: HTMLElement) => {
    if (!deg) return;
    el.style.setProperty('--edge-deg', deg);
    el.style.setProperty('--edge-mx', mx);
    el.style.setProperty('--edge-my', my);
  };

  const request = () => {
    if (!frame && active()) frame = requestAnimationFrame(paint);
  };

  function paint(now: number) {
    frame = 0;
    const dt = now - last;
    if (dt < FRAME_MS - 2) return request();
    last = now;

    const k = 1 - Math.exp(-Math.min(dt, 100) / FOLLOW_MS);
    curX = Math.abs(tiltX - curX) < .002 ? tiltX : curX + (tiltX - curX) * k;
    curY = Math.abs(tiltY - curY) < .002 ? tiltY : curY + (tiltY - curY) * k;

    const max = root.scrollHeight - innerHeight;
    const depth = max > 0 ? clamp(scrollY / max, 0, 1) : 0;
    const angle = `${step(155 + curX * 60 + (depth - .5) * 20)}deg`;
    const x = `${step(50 + 75 * curX)}%`;
    const y = `${step(50 + 90 * curY)}%`;
    if (angle != deg || x != mx || y != my) {
      deg = angle;
      mx = x;
      my = y;
      visible.forEach(put);
    }

    if (curX != tiltX || curY != tiltY) request();
  }

  /** 화면 기준 기울기(°) — [좌우, 앞뒤]. */
  const toScreen = (beta: number, gamma: number): [number, number] => {
    const angle = screen.orientation?.angle ?? 0;
    return [
      angle == 90 ? beta : angle == 270 ? -beta : gamma,
      angle == 90 ? -gamma : angle == 270 ? gamma : beta,
    ];
  };

  let mode: 'off' | 'run' | 'still' | 'deep' = 'off';
  let hold = NaN;
  let lastReading = 0;
  let anchorX = 0;
  let anchorY = 0;
  let stillSince = 0;
  let graceUntil = Infinity;
  let probeTimer: ReturnType<typeof setTimeout> | undefined;

  const moved = (x: number, y: number) => Math.abs(x - anchorX) > MOVE || Math.abs(y - anchorY) > MOVE;

  const onReading = (beta: number, gamma: number) => {
    const now = performance.now();
    const [x, y] = toScreen(beta, gamma);
    if (moved(x, y)) {
      anchorX = x;
      anchorY = y;
      stillSince = now;
      if (mode != 'run') run();
    } else if (mode == 'still') {
      if (now - stillSince > DEEP_MS) deep();
      return;
    }

    hold = isNaN(hold) ? y : hold + (y - hold) * (1 - Math.exp(-Math.min(now - lastReading, 200) / HOLD_MS));
    lastReading = now;
    const tx = clamp(x / TILT_RANGE, -1, 1);
    const ty = clamp((y - hold) / TILT_RANGE, -1, 1);
    if (Math.abs(tx - tiltX) > DEAD_ZONE || Math.abs(ty - tiltY) > DEAD_ZONE) {
      tiltX = tx;
      tiltY = ty;
      request();
    }
    if (now - stillSince > IDLE_MS && now > graceUntil) still();
  };

  const onProbe = (beta: number, gamma: number) => {
    source.stop();
    clearTimeout(probeTimer);
    if (moved(...toScreen(beta, gamma))) run(); else probe();
  };

  function probe() {
    probeTimer = setTimeout(() => {
      source.start(onProbe, true);
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
    stillSince = lastReading = performance.now();
    source.start(onReading);
  }

  function still() {
    mode = 'still';
    source.start(onReading, true);
  }

  function deep() {
    halt();
    mode = 'deep';
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
    if (mode == 'still' || mode == 'deep') run();
  };

  const grace = () => {
    graceUntil = performance.now() + GRACE_MS;
    wake();
  };
  enterPage = grace;

  if (useGyro) {
    // iOS 는 권한 전에는 이벤트가 오지 않는다. 권한은 사용자 동작(탭) 안에서만 물을 수 있어서, 스크롤 끝의 touchend 처럼
    // 동작으로 인정되지 않으면 거절된다 — 답을 받을 때까지 다음 탭에 다시 묻는다. 허용되면 리스너를 새로 붙인다.
    if (typeof Orientation?.requestPermission === 'function') {
      let asking = false;
      const ask = () => {
        if (asking) return;
        asking = true;
        Orientation.requestPermission().then((state: string) => {
          removeEventListener('touchend', ask);
          removeEventListener('click', ask);
          if (state == 'granted') {
            grace();
            if (mode != 'off') run();
          }
        }).catch(() => {}).finally(() => asking = false);
      };
      addEventListener('touchend', ask);
      addEventListener('click', ask);
    }
    addEventListener('touchstart', wake, {passive: true});
    screen.orientation?.addEventListener('change', () => {
      hold = NaN;
      wake();
    });
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

  // 위아래 1/4 화면 앞서 잡아, 스크롤로 들어오는 상자가 옛 값으로 그려지지 않게 한다.
  const io = new IntersectionObserver(entries => {
    for (const entry of entries) {
      const el = entry.target as HTMLElement;
      if (entry.isIntersecting) {
        visible.add(el);
        put(el);
      } else {
        visible.delete(el);
      }
    }
    sync();
    request();
  }, {rootMargin: '25% 0px'});

  // 새 상자는 처음 그려질 때부터 지금 값을 갖는다.
  const adopt = (el: HTMLElement) => {
    if (observed.has(el)) return;
    observed.add(el);
    io.observe(el);
    put(el);
  };
  const adoptIn = (node: Node) => {
    if (!(node instanceof HTMLElement)) return;
    if (node.matches(SELECTOR)) adopt(node);
    node.querySelectorAll<HTMLElement>(SELECTOR).forEach(adopt);
  };

  let sweepTimer: ReturnType<typeof setTimeout> | undefined;
  const sweep = () => {
    sweepTimer = undefined;
    for (const el of observed) {
      if (el.isConnected) continue;
      io.unobserve(el);
      observed.delete(el);
      visible.delete(el);
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
  new MutationObserver(records => {
    for (const record of records) record.addedNodes.forEach(adoptIn);
    sweepTimer ??= setTimeout(sweep, 300);
  }).observe(document.body, {childList: true, subtree: true});
  adoptIn(document.body);
}

export function installEdgeLight(router: Router) {
  if (matchMedia('(hover: hover)').matches) {
    followPointer();
  } else {
    followMotion();
    router.afterEach(() => enterPage());
  }
}
