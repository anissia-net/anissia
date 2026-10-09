// 2026 편성표 색상 — 고른 색(포인트/바탕/분위기)을 기준점으로 삼아 OKLCH 에서 나머지를 계산한다.

export interface Seed { accent: string, base: string, aura: string }
export interface Theme { light: Seed, dark: Seed, glow: number }
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

function css(lch: Lch, alpha = 1) {
  const [r, g, b] = toRgb(lch);
  return alpha >= 1 ? `rgb(${r} ${g} ${b})` : `rgb(${r} ${g} ${b} / ${+alpha.toFixed(3)})`;
}

const tone = (c: Lch, l: number, chroma = c.c, h = c.h): Lch => ({l, c: chroma, h});

function mixHue(a: number, b: number, t: number) {
  const d = ((b - a + 540) % 360) - 180;
  return (a + d * t + 360) % 360;
}

export function palette(seed: Seed, glowPercent: number, dark: boolean): Record<string, string> {
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

  const ink = tone(canvas, dark ? 0.94 : 0.22, dark ? neutral * 0.5 : neutral + 0.015);
  const ink2 = tone(ink, dark ? 0.8 : 0.4);
  const ink3 = tone(ink, dark ? 0.65 : 0.56);
  const lineInk = tone(ink, dark ? 0.98 : 0.28);

  const surface = dark
    ? tone(canvas, canvas.l + 0.09, Math.min(canvas.c * 0.8 + 0.008, 0.06), mixHue(canvas.h, accent.h, 0.15))
    : tone(canvas, 0.995, Math.min(canvas.c * 0.25, 0.008));

  const glow = clamp(glowPercent, 0, 100) / 50;
  const onAccent = accent.l > 0.68 ? tone(accent, 0.2, Math.min(accent.c * 0.3, 0.04)) : tone(accent, 0.99, 0.005);

  return {
    '--canvas': css(canvas),
    '--canvas-2': css(canvas2),
    '--surface': css(surface, dark ? 0.55 : 0.78),
    '--surface-hover': css(tone(surface, surface.l + (dark ? 0.04 : 0)), dark ? 0.72 : 0.96),
    '--surface-solid': css(dark ? tone(surface, surface.l + 0.02) : surface, dark ? 0.94 : 0.97),
    '--line': css(lineInk, dark ? 0.08 : 0.1),
    '--line-2': css(lineInk, dark ? 0.16 : 0.18),
    '--ink': css(ink),
    '--ink-2': css(ink2),
    '--ink-3': css(ink3),
    '--hi': css(accent),
    '--hi-2': css(accent2),
    '--hi-soft': css(accent, dark ? 0.14 : 0.1),
    '--hi-ring': css(accent, dark ? 0.32 : 0.26),
    '--on-hi': css(onAccent),
    '--gradient': `linear-gradient(120deg, ${css(grad1)} 0%, ${css(accent)} 55%, ${css(grad3)} 100%)`,
    '--wordmark': dark
      ? `linear-gradient(120deg, ${css(tone(ink, 0.97))} 0%, ${css(grad1)} 50%, ${css(accent)} 100%)`
      : `linear-gradient(120deg, ${css(ink)} 0%, ${css(accent)} 55%, ${css(grad1)} 100%)`,
    '--bg-1': css(accent, (dark ? 0.12 : 0.18) * glow),
    '--bg-2': css(aura, (dark ? 0.1 : 0.2) * glow),
    '--bg-3': css(between, (dark ? 0.07 : 0.12) * glow),
    '--edge': dark
      ? `linear-gradient(var(--edge-angle, 155deg), rgb(255 255 255 / .24) 0%, rgb(255 255 255 / .07) 28%, rgb(255 255 255 / .05) 66%, ${css(accent, 0.24)} 100%)`
      : `linear-gradient(var(--edge-angle, 155deg), ${css(accent, 0.28)} 0%, ${css(lineInk, 0.12)} 30%, ${css(lineInk, 0.13)} 68%, ${css(lineInk, 0.2)} 100%)`,
    '--edge-glow': css(dark ? grad1 : accent, dark ? 0.95 : 0.85),
    '--shadow-1': dark ? '0 1px 2px rgb(0 0 0 / .28)' : `0 1px 0 ${css(lineInk, 0.03)}, 0 4px 14px -10px ${css(lineInk, 0.18)}`,
    '--shadow-2': dark ? '0 10px 32px -14px rgb(0 0 0 / .6)' : `0 10px 30px -16px ${css(lineInk, 0.22)}`,
    '--shadow-3': dark ? '0 28px 70px -24px rgb(0 0 0 / .8)' : `0 24px 60px -22px ${css(lineInk, 0.32)}`,
    '--shadow-hi': `0 6px 18px -8px ${css(accent, dark ? 0.4 : 0.55)}`,
    '--backdrop': css(canvas, dark ? 0.5 : 0.35),
    '--scroll': css(lineInk, dark ? 0.16 : 0.2),
    '--scroll-hover': css(lineInk, dark ? 0.3 : 0.36),
    '--selection': css(accent, dark ? 0.26 : 0.18),
    '--scheme': dark ? 'dark' : 'light',
  };
}

export const DEFAULT_THEME: Theme = {
  light: {accent: '2c4c8c', base: 'f3f5f9', aura: '7896cd'},
  dark: {accent: 'e3aa9a', base: '0b0b10', aura: 'c3788a'},
  glow: 50,
};

const HEX = /^[0-9a-f]{6}$/i;

// 라이트(포인트·바탕·분위기) + 다크(포인트·바탕·분위기) + 번짐 세기(2자리 16진수, 0~100)
export function encodeTheme(t: Theme): string {
  const glow = Math.round(clamp(Number(t.glow) || 0, 0, 100)).toString(16).padStart(2, '0');
  return [t.light.accent, t.light.base, t.light.aura, t.dark.accent, t.dark.base, t.dark.aura]
    .map(e => e.toLowerCase()).join('') + glow;
}

export function decodeTheme(code: string | null | undefined): Theme {
  const m = (code || '').replace(/^#/, '').match(/^([0-9a-f]{36})([0-9a-f]{2})?$/i);
  if (!m) return DEFAULT_THEME;
  const c = m[1].match(/.{6}/g)!;
  const glow = m[2] ? parseInt(m[2], 16) : DEFAULT_THEME.glow;
  return {
    light: {accent: c[0], base: c[1], aura: c[2]},
    dark: {accent: c[3], base: c[4], aura: c[5]},
    glow: clamp(glow, 0, 100),
  };
}

export const isHex = (v: string) => HEX.test(v);

export const PRESETS: Preset[] = [
  {name: '애니시아', theme: DEFAULT_THEME},
  {name: '벚꽃', theme: {light: {accent: 'c64f78', base: 'fbf4f6', aura: 'f0a6c0'}, dark: {accent: 'f2a0bc', base: '120d10', aura: '8a5cc8'}, glow: 55}},
  {name: '민트', theme: {light: {accent: '0f7468', base: 'f1f7f5', aura: '7ccab8'}, dark: {accent: '72d8c0', base: '090f0e', aura: '3b80c4'}, glow: 50}},
  {name: '라벤더', theme: {light: {accent: '6a4fba', base: 'f5f3fa', aura: 'b49cdc'}, dark: {accent: 'bba5f6', base: '0e0c14', aura: 'd17fae'}, glow: 55}},
  {name: '노을', theme: {light: {accent: 'b9512b', base: 'faf5f1', aura: 'f0b27a'}, dark: {accent: 'f3a266', base: '120d0b', aura: 'cf5f7a'}, glow: 55}},
  {name: '바다', theme: {light: {accent: '1d68b0', base: 'f0f5fb', aura: '6cc3e0'}, dark: {accent: '7fc5f3', base: '080d14', aura: '5a6ed6'}, glow: 50}},
  {name: '숲', theme: {light: {accent: '3d6b2e', base: 'f4f6f0', aura: 'b7cf8a'}, dark: {accent: 'a9d58a', base: '0b0e09', aura: 'c9b45e'}, glow: 45}},
  {name: '모노', theme: {light: {accent: '27272a', base: 'f4f4f5', aura: 'a1a1aa'}, dark: {accent: 'e4e4e7', base: '0a0a0b', aura: '71717a'}, glow: 30}},
];
