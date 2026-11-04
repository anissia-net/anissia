// Run against a local Vite server with the same Playwright environment as navigation.browser.mjs.
import assert from 'node:assert/strict';

const {chromium} = await import(process.env.PLAYWRIGHT_MODULE || 'playwright');
const baseURL = process.env.TEST_BASE_URL || 'http://localhost:5173';
const browser = await chromium.launch({headless: true, ...(process.env.CHROME_PATH ? {executablePath: process.env.CHROME_PATH} : {})});
const user = Buffer.from('2\x1etester@example.test\x1eTester\x1eROOT').toString('base64url');
const dat = `${Math.floor(Date.now() / 1000) + 86400}.test.${user}.test.signature`;

async function setup(platform) {
  const context = await browser.newContext({serviceWorkers: 'block'});
  await context.addInitScript(value => Object.defineProperty(navigator, 'platform', {value}), platform);
  await context.addCookies([{name: 'dat', value: dat, url: baseURL}]);
  const writes = [];
  let template;
  await context.route('**/api/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname.slice('/api'.length);
    let data = [];
    if (path === '/wiki/access') data = {canContribute: true, isOperator: true};
    if (path === '/wiki/templates' && request.method() === 'POST') {
      const payload = request.postDataJSON();
      writes.push({method: 'POST', path, payload});
      template = {templateNo: 1, revision: 1, title: payload.title, body: payload.body, templates: []};
      data = template;
    }
    if (path === '/wiki/templates/1') data = template;
    if (path === '/wiki/templates/1/usage') data = {content: [], totalElements: 0};
    if (path === '/board/ticker/inquiry') data = {writePostRoles: ['ROOT'], writeTopicRoles: ['ROOT']};
    if (path === '/board/topic/inquiry/7') {
      const post = {topicNo: 7, postNo: 8, topic: 'Original topic', content: '<p>Original body</p>', root: true, name: 'Tester'};
      data = {topicNo: 7, topic: post.topic, posts: [post]};
    }
    if (path.startsWith('/board/') && ['PUT', 'POST'].includes(request.method())) {
      writes.push({method: request.method(), path, payload: request.postDataJSON()});
    }
    if (path === '/notification/inbox') data = {items: [], unreadCount: 0};
    await route.fulfill({json: {code: 'ok', data}});
  });
  const page = await context.newPage();
  page.on('pageerror', error => console.error(error));
  return {page, writes};
}

try {
  const wiki = await setup('Win32');
  await wiki.page.goto(baseURL + '/wiki/templates?new=1');
  const title = wiki.page.getByLabel('템플릿 이름');
  await wiki.page.getByLabel('수정 요약').press('Control+s');
  assert.ok(await title.evaluate(el => el === document.activeElement && el.validity.valueMissing));
  assert.deepEqual(wiki.writes, []);
  await title.fill('Shortcut template');
  const wikiBody = wiki.page.locator('.nabi-content[contenteditable="true"]').first();
  await wikiBody.click();
  await wiki.page.keyboard.type('Template body');
  await wiki.page.keyboard.press('Control+s');
  await wiki.page.waitForURL('**/wiki/templates?templateNo=1');
  assert.equal(wiki.writes.length, 1);
  assert.equal(wiki.writes[0].payload.title, 'Shortcut template');
  assert.match(JSON.stringify(wiki.writes[0].payload.body.blocks), /Template body/);

  const board = await setup('MacIntel');
  await board.page.goto(baseURL + '/inquiry/7');
  await board.page.locator('.as-post-editor').waitFor();
  const keydown = {key: 's', code: 'KeyS', metaKey: true, bubbles: true, cancelable: true};
  assert.equal(await board.page.evaluate(init => document.body.dispatchEvent(new KeyboardEvent('keydown', init)), keydown), true);
  assert.deepEqual(board.writes, []);
  await board.page.getByRole('button', {name: '게시글 수정'}).click();
  // The new reply is already open alongside the topic editor. Give both pending content.
  const reply = board.page.locator('.as-post-editor').last().locator('.nabi-content[contenteditable="true"]').first();
  await reply.click();
  await board.page.keyboard.type('Pending reply');
  const subject = board.page.getByPlaceholder('주제');
  await subject.fill('Updated topic');
  await board.page.locator('.as-post-editor').first().getByRole('button', {name: '전체화면', exact: true}).click();
  const fullscreen = board.page.locator('.nabi-fullscreen-host .nabi-content[contenteditable="true"]').first();
  await fullscreen.press('Meta+s');
  await board.page.getByRole('heading', {name: 'Updated topic'}).waitFor();
  assert.deepEqual(board.writes.map(w => [w.method, w.path]), [['PUT', '/board/topic/7']]);
  assert.equal(board.writes[0].payload.topic, 'Updated topic');
  assert.match(board.writes[0].payload.content, /Original body/);
  const repeat = await reply.evaluate((el, init) => {
    let clicks = 0;
    const count = () => { clicks++; };
    document.addEventListener('click', count, true);
    const handled = !el.dispatchEvent(new KeyboardEvent('keydown', {...init, repeat: true}));
    document.removeEventListener('click', count, true);
    return {handled, clicks};
  }, keydown);
  assert.deepEqual(repeat, {handled: true, clicks: 0});
  await Promise.all([
    board.page.waitForResponse(response => response.url().endsWith('/board/post/7') && response.request().method() === 'POST'),
    reply.press('Meta+s'),
  ]);
  assert.deepEqual(board.writes.map(w => [w.method, w.path]), [['PUT', '/board/topic/7'], ['POST', '/board/post/7']]);
  assert.match(board.writes[1].payload.content, /Pending reply/);
  console.log('PASS wiki template validation and Ctrl+S save, fullscreen topic and focused reply saves with Command+S');
} finally {
  await browser.close();
}
