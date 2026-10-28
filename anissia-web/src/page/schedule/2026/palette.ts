// 2026 편성표 색상 — 고른 색(포인트/바탕/분위기)을 기준점으로 삼아 OKLCH 에서 나머지를 계산한다.

export interface Seed { accent: string, base: string, aura: string, glow: number }
export interface Theme { light: Seed, dark: Seed }
export interface Preset { name: string, theme: Theme }

interface Lch { l: number, c: number, h: number }

const clamp = (v: number, min: number, max: number) => Math.min(max, Math.max(min, v));

function toLinear(v: number) {
  v /= 255;
  return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
}

function fromLinear(v: number) {
  return v <= 0.0031308 ? v * 12.92 : 1.055 * Math.pow(v, 1 / 2.4) - 0.055;
}

function fromHex(hex: string): Lch {
  const n = parseInt(hex, 16);
  const r = toLinear((n >> 16) & 255), g = toLinear((n >> 8) & 255), b = toLinear(n & 255);
  const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
  const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
  const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
  const L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s;
  const A = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s;
  const B = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s;
  const c = Math.hypot(A, B);
  return {l: L, c, h: c < 1e-4 ? 0 : (Math.atan2(B, A) * 180 / Math.PI + 360) % 360};
}

function linearRgb({l: L, c, h}: Lch) {
  const a = c * Math.cos(h * Math.PI / 180), b = c * Math.sin(h * Math.PI / 180);
  const l = Math.pow(L + 0.3963377774 * a + 0.2158037573 * b, 3);
  const m = Math.pow(L - 0.1055613458 * a - 0.0638541728 * b, 3);
  const s = Math.pow(L - 0.0894841775 * a - 1.2914855480 * b, 3);
  return [
    4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s,
    -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s,
    -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s,
  ];
}

const inGamut = (rgb: number[]) => rgb.every(v => v >= -1e-4 && v <= 1 + 1e-4);

// 색역을 벗어나면 명도·색상은 두고 채도만 줄인다.
function toRgb(lch: Lch): number[] {
  const color = {...lch, l: clamp(lch.l, 0, 1), c: Math.max(lch.c, 0)};
  let rgb = linearRgb(color);
  if (!inGamut(rgb)) {
    let lo = 0, hi = color.c;
    for (let i = 0; i < 18; i++) {
      const mid = (lo + hi) / 2;
      if (inGamut(linearRgb({...color, c: mid}))) lo = mid; else hi = mid;
    }
    rgb = linearRgb({...color, c: lo});
  }
  return rgb.map(v => Math.round(clamp(fromLinear(clamp(v, 0, 1)), 0, 1) * 255));
}

const rgbCache = new WeakMap<Lch, string>();

function css(lch: Lch, alpha = 1) {
  let rgb = rgbCache.get(lch);
  if (rgb === undefined) {
    rgb = toRgb(lch).join(' ');
    rgbCache.set(lch, rgb);
  }
  return alpha >= 1 ? `rgb(${rgb})` : `rgb(${rgb} / ${+alpha.toFixed(3)})`;
}

const tone = (c: Lch, l: number, chroma = c.c, h = c.h): Lch => ({l, c: chroma, h});

function mixHue(a: number, b: number, t: number) {
  const d = ((b - a + 540) % 360) - 180;
  return (a + d * t + 360) % 360;
}

// 라이트는 사이트 라이트 테마와 같은 결 — 불투명 흰 면, 고른 가는 테, 옅은 그림자, 선택은 옅은 면 + 안쪽 테.
export function palette(seed: Seed, dark: boolean): Record<string, string> {
  const base = fromHex(seed.base);

  const canvas = tone(base, dark ? clamp(base.l, 0.1, 0.3) : clamp(base.l, 0.9, 0.995), Math.min(base.c, 0.08));
  const canvas2 = tone(canvas, canvas.l + (dark ? 0.035 : -0.03));
  const neutral = Math.min(canvas.c, 0.035);

  const rawAccent = fromHex(seed.accent);
  const accent = tone(rawAccent, dark ? clamp(rawAccent.l, 0.72, 0.9) : clamp(rawAccent.l, 0.36, 0.56));
  const accent2 = tone(accent, accent.l + (dark ? 0.07 : -0.07));
  const grad1 = tone(accent, accent.l + (dark ? 0.08 : 0.13), accent.c * 0.85, accent.h - 12);
  const grad3 = tone(accent, accent.l - (dark ? 0.13 : 0.11), accent.c * 1.05, accent.h + 10);

  const rawAura = fromHex(seed.aura);
  const aura = tone(rawAura, dark ? clamp(rawAura.l, 0.5, 0.78) : clamp(rawAura.l, 0.62, 0.86));
  const between = tone(aura, (accent.l + aura.l) / 2, (accent.c + aura.c) / 2, mixHue(accent.h, aura.h, 0.5));

  const ink = tone(canvas, dark ? 0.94 : 0.25, dark ? neutral * 0.5 : neutral + 0.015);
  const ink2 = tone(ink, dark ? 0.8 : 0.43);
  const ink3 = tone(ink, dark ? 0.65 : 0.56);
  const lineInk = tone(ink, dark ? 0.98 : 0.28);

  const surface = dark
    ? tone(canvas, canvas.l + 0.09, Math.min(canvas.c * 0.8 + 0.008, 0.06), mixHue(canvas.h, accent.h, 0.15))
    : tone(canvas, Math.min(canvas.l + 0.03, 1), Math.min(canvas.c * 0.25, 0.006));

  const glow = clamp(seed.glow, 0, 100) / 50;
  const onAccent = accent.l > 0.68 ? tone(accent, 0.2, Math.min(accent.c * 0.3, 0.04)) : tone(accent, 0.99, 0.005);

  const colors: Record<string, string> = {
    '--canvas': css(canvas),
    '--canvas-2': css(canvas2),
    '--surface': css(surface, dark ? 0.55 : 1),
    '--surface-hover': dark ? css(tone(surface, surface.l + 0.04), 0.72) : css(surface),
    '--surface-solid': css(dark ? tone(surface, surface.l + 0.02) : surface, dark ? 0.94 : 0.9),
    '--line': css(lineInk, dark ? 0.08 : 0.085),
    '--line-2': css(lineInk, dark ? 0.16 : 0.15),
    '--ink': css(ink),
    '--ink-2': css(ink2),
    '--ink-3': css(ink3),
    '--hi': css(accent),
    '--hi-2': css(accent2),
    '--hi-soft': css(accent, dark ? 0.14 : 0.08),
    '--hi-ring': css(accent, dark ? 0.32 : 0.26),
    '--on-hi': css(onAccent),
    '--gradient': dark
      ? `linear-gradient(120deg, ${css(grad1)} 0%, ${css(accent)} 55%, ${css(grad3)} 100%)`
      : `linear-gradient(180deg, ${css(tone(accent, accent.l + 0.12, accent.c * 0.85))} 0%, ${css(tone(accent, accent.l + 0.04))} 55%, ${css(tone(accent, accent.l - 0.01))} 100%)`,
    '--pill': dark ? 'var(--gradient)' : css(accent, 0.1),
    '--pill-ink': css(dark ? onAccent : accent2),
    '--pill-shadow': dark
      ? `0 6px 18px -8px ${css(accent, 0.4)}`
      : `inset 0 0 0 1px ${css(accent, 0.3)}, 0 1px 3px -1px ${css(lineInk, 0.1)}`,
    '--wordmark': dark
      ? `linear-gradient(120deg, ${css(tone(ink, 0.97))} 0%, ${css(grad1)} 50%, ${css(accent)} 100%)`
      : `linear-gradient(120deg, ${css(tone(accent, 0.3, accent.c * 0.6))} 0%, ${css(accent)} 55%, ${css(tone(accent, accent.l + 0.2, accent.c * 0.7))} 100%)`,
    '--bg-1': css(accent, (dark ? 0.12 : 0.18) * glow),
    '--bg-2': css(aura, (dark ? 0.1 : 0.2) * glow),
    '--bg-3': css(between, (dark ? 0.07 : 0.12) * glow),
    '--edge': dark
      ? `linear-gradient(var(--edge-angle, 155deg), rgb(255 255 255 / .24) 0%, rgb(255 255 255 / .07) 28%, rgb(255 255 255 / .05) 66%, ${css(accent, 0.24)} 100%)`
      : `linear-gradient(var(--edge-angle, 155deg), ${css(lineInk, 0.11)} 0%, ${css(lineInk, 0.085)} 30%, ${css(lineInk, 0.085)} 70%, ${css(accent, 0.16)} 100%)`,
    '--edge-glow': css(dark ? grad1 : accent, dark ? 0.95 : 0.7),
    '--shadow-1': dark ? '0 1px 2px rgb(0 0 0 / .28)' : `0 1px 2px ${css(lineInk, 0.04)}`,
    '--shadow-2': dark ? '0 10px 32px -14px rgb(0 0 0 / .6)' : `0 1px 2px ${css(lineInk, 0.04)}, 0 8px 20px -12px ${css(lineInk, 0.12)}`,
    '--shadow-3': dark ? '0 28px 70px -24px rgb(0 0 0 / .8)' : `0 24px 48px -24px ${css(lineInk, 0.22)}, 0 2px 6px -2px ${css(lineInk, 0.06)}`,
    '--backdrop': css(canvas, dark ? 0.5 : 0.35),
    '--scroll': css(lineInk, dark ? 0.16 : 0.2),
    '--scroll-hover': css(lineInk, dark ? 0.3 : 0.36),
    '--selection': css(accent, dark ? 0.26 : 0.16),
    '--scheme': dark ? 'dark' : 'light',
  };

  // 기본 애니시아 색 조합은 사이트 토큰을 공유한다. 편성표의 모드에 직접
  // 선언하므로 사이트와 미리보기의 라이트/다크 모드가 달라도 올바르게 표시된다.
  const site = dark ? DEFAULT_THEME.dark : DEFAULT_THEME.light;
  if (seed.accent.toLowerCase() === site.accent && seed.base.toLowerCase() === site.base && seed.aura.toLowerCase() === site.aura) {
    Object.assign(colors, {
      '--canvas': 'var(--as-canvas)',
      '--canvas-2': 'var(--as-canvas-2)',
      '--surface': 'var(--as-surface)',
      '--surface-hover': dark ? 'var(--as-glass-strong)' : 'var(--as-surface)',
      '--surface-solid': 'var(--as-glass-strong)',
      '--line': 'var(--as-line)',
      '--line-2': 'var(--as-line-2)',
      '--ink': 'var(--as-ink)',
      '--ink-2': 'var(--as-ink-2)',
      '--ink-3': 'var(--as-ink-3)',
      '--hi': 'var(--as-brand)',
      '--hi-2': 'var(--as-brand-2)',
      '--hi-soft': 'var(--as-brand-soft)',
      '--hi-ring': `color-mix(in srgb, var(--as-brand) ${dark ? 32 : 26}%, transparent)`,
      '--on-hi': 'var(--as-on-brand)',
      '--gradient': 'var(--as-gradient)',
      '--pill': dark ? 'var(--as-gradient)' : 'var(--as-choice-bg)',
      '--pill-ink': dark ? 'var(--as-on-brand)' : 'var(--as-choice-ink)',
      '--pill-shadow': dark ? 'var(--as-shadow-brand)' : 'var(--as-choice-shadow)',
      '--wordmark': 'var(--as-wordmark-gradient)',
      '--edge': 'linear-gradient(var(--edge-angle, 155deg), var(--as-edge-stops))',
      '--edge-glow': 'var(--as-edge-glow)',
      '--shadow-1': 'var(--as-shadow-1)',
      '--shadow-2': 'var(--as-shadow-2)',
      '--shadow-3': 'var(--as-shadow-3)',
      '--backdrop': `color-mix(in srgb, var(--as-canvas) ${dark ? 50 : 35}%, transparent)`,
      '--scroll': `color-mix(in srgb, var(${dark ? '--as-ink' : '--as-brand'}) ${dark ? 16 : 24}%, transparent)`,
      '--scroll-hover': `color-mix(in srgb, var(${dark ? '--as-ink' : '--as-brand'}) ${dark ? 30 : 40}%, transparent)`,
      '--selection': dark ? 'rgba(227, 170, 154, .24)' : '#ebc7d4',
    });
    // 두 모드 모두 50이 사이트의 기본 번짐 세기다.
    for (let i = 1; i <= 3; i++) {
      colors[`--bg-${i}`] = `rgb(from var(--as-bg-${i}) r g b / calc(alpha * ${glow}))`;
    }
  }
  return colors;
}

export const DEFAULT_THEME: Theme = {
  light: {accent: '905574', base: 'f5efe6', aura: 'ae97be', glow: 50},
  dark: {accent: 'e3aa9a', base: '0b0b10', aura: '967378', glow: 50},
};

const HEX = /^[0-9a-f]{6}$/i;

const glowHex = (v: number) => Math.round(clamp(Number(v) || 0, 0, 100)).toString(16).padStart(2, '0');

// 라이트(포인트·바탕·분위기) + 다크(포인트·바탕·분위기) + 번짐 세기 라이트·다크(각 2자리 16진수, 0~100)
export function encodeTheme(t: Theme): string {
  return [t.light.accent, t.light.base, t.light.aura, t.dark.accent, t.dark.base, t.dark.aura]
    .map(e => e.toLowerCase()).join('') + glowHex(t.light.glow) + glowHex(t.dark.glow);
}

export function decodeTheme(code: string | null | undefined): Theme {
  const m = (code || '').replace(/^#/, '').match(/^([0-9a-f]{36})([0-9a-f]{2})([0-9a-f]{2})$/i);
  if (!m) return DEFAULT_THEME;
  const c = m[1].toLowerCase().match(/.{6}/g)!;
  const lightGlow = clamp(parseInt(m[2], 16), 0, 100);
  // 이미 배포된 이전 애니시아 iframe 코드도 현재 프리셋으로 읽는다.
  // 사용자가 다른 색을 고른 테마와 별도로 지정한 번짐 세기는 유지한다.
  const legacyAnissia = c[0] === '2c6b9e' && c[1] === 'fafafa' && c[2] === '74a3c9';
  return {
    light: legacyAnissia
      ? {...DEFAULT_THEME.light, glow: lightGlow === 0 ? DEFAULT_THEME.light.glow : lightGlow}
      : {accent: c[0], base: c[1], aura: c[2], glow: lightGlow},
    dark: {accent: c[3], base: c[4], aura: c[5], glow: clamp(parseInt(m[3], 16), 0, 100)},
  };
}

export const isHex = (v: string) => HEX.test(v);

export const PRESETS: Preset[] = [
  {name: '애니시아', theme: DEFAULT_THEME},
  {name: '벚꽃', theme: {light: {accent: 'c64f78', base: 'fbf4f6', aura: 'f0a6c0', glow: 55}, dark: {accent: 'f2a0bc', base: '120d10', aura: '8a5cc8', glow: 55}}},
  {name: '민트', theme: {light: {accent: '0f7468', base: 'f1f7f5', aura: '7ccab8', glow: 50}, dark: {accent: '72d8c0', base: '090f0e', aura: '3b80c4', glow: 50}}},
  {name: '라벤더', theme: {light: {accent: '6a4fba', base: 'f5f3fa', aura: 'b49cdc', glow: 55}, dark: {accent: 'bba5f6', base: '0e0c14', aura: 'd17fae', glow: 55}}},
  {name: '노을', theme: {light: {accent: 'b9512b', base: 'faf5f1', aura: 'f0b27a', glow: 55}, dark: {accent: 'f3a266', base: '120d0b', aura: 'cf5f7a', glow: 55}}},
  {name: '바다', theme: {light: {accent: '1d68b0', base: 'f0f5fb', aura: '6cc3e0', glow: 50}, dark: {accent: '7fc5f3', base: '080d14', aura: '5a6ed6', glow: 50}}},
  {name: '숲', theme: {light: {accent: '3d6b2e', base: 'f4f6f0', aura: 'b7cf8a', glow: 45}, dark: {accent: 'a9d58a', base: '0b0e09', aura: 'c9b45e', glow: 45}}},
  {name: '모노', theme: {light: {accent: '27272a', base: 'f4f4f5', aura: 'a1a1aa', glow: 30}, dark: {accent: 'e4e4e7', base: '0a0a0b', aura: '71717a', glow: 30}}},
];
