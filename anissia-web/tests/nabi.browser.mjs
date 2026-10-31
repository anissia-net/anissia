// Run against Vite: PLAYWRIGHT_MODULE=/path/to/playwright/index.mjs node tests/nabi.browser.mjs
import assert from 'node:assert/strict';
import {fileURLToPath} from 'node:url';
const {chromium} = await import(process.env.PLAYWRIGHT_MODULE || 'playwright');
const browser = await chromium.launch({headless: true, ...(process.env.CHROME_PATH ? {executablePath: process.env.CHROME_PATH} : {})});
const page = await browser.newPage({viewport: {width: 1280, height: 1000}});
const errors = [], requests = [];
// The app's HTML plugin replaces every entry script; serve this isolated fixture unchanged.
await page.route('**/tests/nabi.fixture.html', route => route.fulfill({path: fileURLToPath(new URL('./nabi.fixture.html', import.meta.url)), contentType: 'text/html'}));
page.on('pageerror', error => { errors.push(error.message); console.error(error.message); });
await page.route('**/link/title?**', async route => {
  requests.push(route.request().url());
  await route.fulfill({json: {code: 'ok', data: {title: '조회한 실제 제목 <안전한 텍스트>'}}});
});
try {
  await page.goto((process.env.TEST_BASE_URL || 'http://localhost:5186') + '/tests/nabi.fixture.html');
  await page.waitForFunction(() => !!window.fixture);
  const template = page.locator('#editor [data-template-editor]');
  const handle = page.locator('.as-wiki-template-move').first();
  const button = page.locator('#diff [aria-pressed]');
  const arrow = page.locator('#diff .nabi-diff-merge-action').first();
  const states = [];
  for (const theme of ['light', 'dark']) {
    await page.evaluate(theme => document.documentElement.className = theme, theme);
    await template.click();
    await handle.waitFor({state: 'visible'});
    const grip = await handle.evaluate(el => ({color: getComputedStyle(el).color, background: getComputedStyle(el).backgroundColor}));
    const active = await button.evaluate(el => ({background: getComputedStyle(el).backgroundColor, color: getComputedStyle(el).color}));
    const plain = await arrow.evaluate(el => getComputedStyle(el).backgroundColor);
    assert.equal(plain, 'rgba(0, 0, 0, 0)');
    await arrow.hover();
    await page.waitForTimeout(190);
    const hover = await arrow.evaluate(el => getComputedStyle(el).backgroundColor);
    assert.notEqual(hover, plain);
    // Evaluate synchronously to sample animation frames before they finish.
    const animation = await button.evaluate(async el => {
      el.click(); await Promise.resolve();
      return {pressed: el.getAttribute('aria-pressed'), animations: document.querySelector('#diff').getAnimations({subtree: true}).filter(a => a.effect?.getTiming().duration === 240).length};
    });
    assert.equal(animation.pressed, 'false');
    assert.ok(animation.animations > 0, 'expanding actually animates document blocks');
    await page.waitForTimeout(270);
    assert.notEqual(await button.evaluate(el => getComputedStyle(el).backgroundColor), active.background);
    await button.click(); await page.waitForTimeout(270);
    states.push({theme, grip, active, hover});
    if (process.env.SCREENSHOT_DIR) await page.screenshot({path: `${process.env.SCREENSHOT_DIR}/nabi-${theme}.png`, fullPage: true});
  }
  assert.notEqual(states[0].grip.color, states[1].grip.color);
  assert.notEqual(states[0].active.background, states[1].active.background);
  console.log('PASS themed handles, fold states and movement, transparent/hover arrows', states);

  await template.click(); await handle.focus(); await page.keyboard.press('Alt+ArrowDown');
  assert.equal(await page.evaluate(() => window.fixture.nabi.getJson()[2].ch[0].w), 'exTemplateInclude');
  await page.evaluate(() => window.fixture.nabi.undo());
  assert.equal(await page.evaluate(() => window.fixture.nabi.getJson()[1].ch[0].w), 'exTemplateInclude');
  await template.click();
  const grip = await handle.boundingBox();
  const destination = await page.locator('#editor .nabi-content > [data-key]').last().boundingBox();
  await page.mouse.move(grip.x + grip.width / 2, grip.y + grip.height / 2); await page.mouse.down();
  await page.mouse.move(destination.x + 80, destination.y + destination.height - 2, {steps: 12});
  assert.equal(await page.locator('.nabi-move-preview').count(), 1);
  assert.equal(await page.locator('.as-wiki-template-drop:visible').count(), 1);
  await page.mouse.up();
  assert.equal(await page.evaluate(() => window.fixture.nabi.getJson().at(-1).ch[0].w), 'exTemplateInclude');
  await page.evaluate(() => window.fixture.nabi.undo());
  assert.equal(await page.evaluate(() => window.fixture.nabi.getJson()[1].ch[0].w), 'exTemplateInclude');
  console.log('PASS keyboard + pointer template movement and undo');

  await page.evaluate(() => {
    const {nabi, surface} = window.fixture;
    nabi.setJson([{w: 'p', ch: []}]); surface.focus();
    nabi.select({anchor: {path: [0], offset: 0}, focus: {path: [0], offset: 0}});
    const clipboardData = new DataTransfer(); clipboardData.setData('text/plain', location.origin + '/anime/123');
    surface.dispatchEvent(new ClipboardEvent('paste', {clipboardData, bubbles: true, cancelable: true}));
  });
  await page.waitForFunction(() => document.querySelector('#editor a')?.textContent === '조회한 실제 제목 <안전한 텍스트>');
  assert.equal(requests.length, 1);
  assert.equal(await page.locator('#editor a').getAttribute('href'), new URL('/anime/123', page.url()).href);
  assert.equal(await page.locator('#editor a *').count(), 0);
  console.log('PASS real paste callback, title-only lookup, original href and escaped label');

  await page.emulateMedia({reducedMotion: 'reduce'});
  assert.equal(await button.evaluate(async el => {
    el.click(); await Promise.resolve();
    return document.querySelector('#diff').getAnimations({subtree: true}).filter(a => a.effect?.getTiming().duration === 240).length;
  }), 0);
  assert.deepEqual(errors, []);
} finally { await browser.close(); }
