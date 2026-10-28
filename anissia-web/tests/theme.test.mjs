import assert from 'node:assert/strict';
import {test} from 'node:test';
import {nextTick, watch} from 'vue';
import {DEFAULT_THEME, PRESETS, decodeTheme, encodeTheme, palette} from '../src/page/schedule/2026/palette.ts';

test('previously shared Anissia iframe codes use the current palette', () => {
    const legacy = '2c6b9efafafa74a3c9e3aa9a0b0b109673780032';
    assert.deepEqual(decodeTheme(legacy), DEFAULT_THEME);
    assert.deepEqual(decodeTheme(`#${legacy.toUpperCase()}`), DEFAULT_THEME);
    assert.equal(decodeTheme(`${legacy.slice(0, 36)}2332`).light.glow, 35);
});

test('custom themes and every current preset survive iframe code round trips', () => {
    for (const {theme} of PRESETS) {
        assert.deepEqual(decodeTheme(encodeTheme(theme)), theme);
    }
    const custom = {
        light: {accent: '2c6b9f', base: 'fafafa', aura: '74a3c9', glow: 0},
        dark: {accent: 'aabbcc', base: '112233', aura: '667788', glow: 73},
    };
    assert.deepEqual(decodeTheme(encodeTheme(custom)), custom);
    assert.deepEqual(decodeTheme('invalid'), DEFAULT_THEME);
});

test('Anissia ambient colors follow site tokens in both modes and respect glow', () => {
    for (const dark of [false, true]) {
        const seed = DEFAULT_THEME[dark ? 'dark' : 'light'];
        const standard = palette(seed, dark);
        const noGlow = palette({...seed, glow: 0}, dark);
        const strongerGlow = palette({...seed, glow: 100}, dark);
        for (let i = 1; i <= 3; i++) {
            assert.match(standard[`--bg-${i}`], new RegExp(`var\\(--as-bg-${i}\\)`));
            assert.match(noGlow[`--bg-${i}`], /alpha \* 0/);
            assert.match(strongerGlow[`--bg-${i}`], /alpha \* 2/);
        }
        for (const key of ['--hi-ring', '--backdrop', '--scroll', '--scroll-hover']) {
            assert.match(standard[key], /var\(--as-/);
        }
        const custom = palette(PRESETS[1].theme[dark ? 'dark' : 'light'], dark);
        assert.doesNotMatch(custom['--canvas'], /--as-/);
        assert.doesNotMatch(custom['--bg-1'], /--as-/);
    }
});

test('theme changes remain observable after page-style subscriptions are recreated', async t => {
    const values = new Map([['theme', 'dark']]);
    const classes = new Set(['light', 'unrelated']);
    const mocks = {
        localStorage: {getItem: key => values.get(key) ?? null, setItem: (key, value) => values.set(key, value)},
        matchMedia: () => ({matches: false}),
        document: {documentElement: {classList: {
            toggle: (name, force) => force ? classes.add(name) : classes.delete(name),
        }}},
    };
    for (const [key, value] of Object.entries(mocks)) {
        const original = Object.getOwnPropertyDescriptor(globalThis, key);
        Object.defineProperty(globalThis, key, {configurable: true, value});
        t.after(() => original ? Object.defineProperty(globalThis, key, original) : delete globalThis[key]);
    }
    const {default: theme} = await import('../src/common/theme.ts');
    assert.equal(theme.value, 'dark');
    assert.deepEqual([...classes].sort(), ['dark', 'unrelated']);

    const firstPage = [];
    const stopFirst = watch(() => theme.value, mode => firstPage.push(mode));
    theme.toggle();
    await nextTick();
    assert.deepEqual(firstPage, ['light']);
    stopFirst();

    theme.value = 'dark';
    const returningPage = [];
    const stopReturning = watch(() => theme.value, mode => returningPage.push(mode), {immediate: true});
    t.after(stopReturning);
    theme.toggle();
    await nextTick();
    assert.deepEqual(returningPage, ['dark', 'light']);
    assert.equal(values.get('theme'), 'light');

    // Reapplying the stored mode must repair classes even if its value is unchanged.
    classes.delete('light');
    classes.add('dark');
    theme.apply();
    assert.deepEqual([...classes].sort(), ['light', 'unrelated']);
});
