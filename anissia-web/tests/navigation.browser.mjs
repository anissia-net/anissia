// Run against a local Vite server with Playwright installed (or PLAYWRIGHT_MODULE set).
// Every API call is mocked, including administrator saves and deletes.
import assert from 'node:assert/strict';
const {chromium} = await import(process.env.PLAYWRIGHT_MODULE || 'playwright');
const baseURL = process.env.TEST_BASE_URL || 'http://localhost:5173';
const browser = await chromium.launch({headless: true, ...(process.env.CHROME_PATH ? {executablePath: process.env.CHROME_PATH} : {})});
const context = await browser.newContext({viewport: {width: 1280, height: 800}, serviceWorkers: 'block'});
const requests = [], errors = [];
const saved = new Map();
const anime = id => ({animeNo: id, subject: `작품 ${id}`, originalSubject: `Original ${id}`, genres: '판타지,모험', week: '2', time: '12:00', status: 'ON', captions: [], ...saved.get(id)});
const topic = id => ({topicNo: id, topic: `게시글 ${id}`, name: 'Writer', postCount: 1, regTime: Date.now()});
const paged = (page, make) => ({content: Array.from({length: 30}, (_, i) => make(page * 30 + i + 1)), number: page, totalElements: 300, totalPages: 10, empty: false, first: page === 0, last: page === 9, size: 30});
await context.route('**/api/**', async route => {
  const request = route.request(), url = new URL(request.url()), path = url.pathname;
  requests.push(`${request.method()} ${path}${url.search}`);
  let data = [];
  if (/\/anime\/list\/\d+$/.test(path)) data = paged(Number(path.split('/').at(-1)), anime);
  else if (/\/anime\/animeNo\/\d+$/.test(path)) data = anime(Number(path.split('/').at(-1)));
  else if (/\/anime\/\d+$/.test(path) && request.method() === 'PUT') saved.set(Number(path.split('/').at(-1)), request.postDataJSON());
  else if (path.endsWith('/anime/genres')) data = ['판타지', '모험'];
  else if (path.endsWith('/anime/delist')) data = {...paged(0, id => ({...anime(id), agendaNo: id})), last: true};
  else if (/\/anime\/schedule\//.test(path)) data = Array.from({length: 60}, (_, i) => anime(i + 1));
  else if (/\/anime\/caption\/myList\//.test(path)) data = paged(Number(path.split('/').at(-1)), id => ({animeNo: id, subject: `자막 ${id}`, episode: '1', updDt: '2026-10-28T12:00', website: ''}));
  else if (/\/board\/list\//.test(path)) data = paged(Number(path.split('/').at(-1)), topic);
  else if (/\/board\/ticker\//.test(path)) data = {writePostRoles: ['ROOT'], writeTopicRoles: ['ROOT']};
  else if (/\/board\/topic\//.test(path)) {
    const id = Number(path.split('/').at(-1));
    data = {...topic(id), posts: [{postNo: id, topicNo: id, topic: `게시글 ${id}`, root: true, name: 'Writer', regTime: Date.now(), content: '<p>본문</p>'}]};
  } else if (path.endsWith('/wiki/access')) data = {canContribute: false, isOperator: false};
  else if (/\/wiki\/anime\/\d+$/.test(path)) data = {revision: 0, title: '작품', body: {schemaVersion: 1, blocks: []}, templates: []};
  else if (path.endsWith('/notification/inbox')) data = {items: [], unreadCount: 0};
  await route.fulfill({json: {code: 'ok', data}});
});
const page = await context.newPage();
page.on('pageerror', error => errors.push(error.message));
page.on('dialog', dialog => dialog.accept());
const settle = () => page.waitForTimeout(300);
const listRequests = () => requests.filter(url => /\/(anime\/list|board\/list)\//.test(url)).length;

async function roundTrip(path, cards, mutate = false) {
  await page.goto(baseURL + path);
  await page.locator(cards).first().waitFor();
  await settle();
  await page.evaluate(() => window.scrollTo(0, document.documentElement.scrollHeight));
  await page.waitForFunction(selector => document.querySelectorAll(selector).length >= 60, cards);
  const card = page.locator(cards).nth(35);
  const link = path.includes('anime') ? card.locator('a').first() : card;
  await link.scrollIntoViewIfNeeded();
  await settle();
  const before = {count: await page.locator(cards).count(), scroll: await page.evaluate(() => scrollY), requests: listRequests()};
  const detail = await link.getAttribute('href');
  await link.click();
  await page.waitForURL(baseURL + detail);
  await settle();
  assert.equal(await page.locator(cards).count(), 0, 'detail renders without the list');
  assert.equal(await page.getByText('목록으로', {exact: true}).count(), 0);
  assert.equal(await page.evaluate(() => scrollY), 0);
  if (mutate) {
    await page.locator('input[name="subject"]').fill('수정한 작품');
    await page.getByRole('button', {name: '저장', exact: true}).click();
    await page.waitForFunction(() => document.querySelector('input[name="subject"]')?.value === '수정한 작품');
    await settle();
  }
  await page.evaluate(() => window.scrollTo(0, 10000));
  await settle();
  assert.equal(listRequests(), before.requests, 'hidden lists must not load pages');
  await page.goBack();
  await settle();
  assert.equal(await page.locator(cards).count(), before.count);
  assert.equal(listRequests(), before.requests, 'returning must not refetch loaded pages');
  assert.ok(Math.abs(await page.evaluate(() => scrollY) - before.scroll) < 3, `scroll restored for ${path}`);
  if (mutate) assert.match(await page.locator(cards).nth(35).innerText(), /수정한 작품/);
  await page.goForward();
  await settle();
  assert.equal(page.url(), baseURL + detail);
  await page.goBack();
  await settle();
  assert.equal(listRequests(), before.requests);
  console.log(`PASS ${path}: ${before.count} cached items, back/forward scroll, ${mutate ? 'saved item patched' : 'no refetch'}`);
}

try {
  await roundTrip('/anime?q=test', '.anime-list .as-card');
  // Searching creates a separate cached list; browser back restores the earlier query and pages.
  const count = await page.locator('.anime-list .as-card').count();
  const before = listRequests();
  await page.locator('#default-search').fill('second');
  await page.getByRole('button', {name: '검색', exact: true}).click();
  await page.waitForURL('**/anime?q=second');
  await settle();
  await page.goBack();
  await settle();
  assert.equal(await page.locator('.anime-list .as-card').count(), count);
  assert.equal(listRequests(), before + 1);
  console.log('PASS separate search caches');
  await roundTrip('/inquiry', 'main a.as-card');
  await roundTrip('/notice', 'main a.as-card');

  for (const [old, current] of [
    ['/anime?animeNo=123&q=test#ref', '/anime/123?q=test#ref'],
    ['/inquiry?topicNo=281', '/inquiry/281'],
    ['/notice?topicNo=141', '/notice/141'],
    ['/anime?animeNo=123&wikiRevision=2', '/wiki/history?animeNo=123&revision=2'],
  ]) {
    await page.goto(baseURL + old);
    await page.waitForURL(baseURL + current);
  }
  console.log('PASS old links and wiki history redirect precedence');

  const dat = `${Math.floor(Date.now() / 1000) + 86400}.test.${Buffer.from('2\x1etest@example.test\x1eTester\x1eROOT').toString('base64url')}.test.signature`;
  await context.addCookies([{name: 'dat', value: dat, url: baseURL}]);
  await roundTrip('/admin/anime?q=test', 'main .as-card', true);
  await page.goto(baseURL + '/admin/anime?animeNo=0');
  await page.waitForURL(baseURL + '/admin/anime/new');
  await page.locator('input[name="subject"]').waitFor();
  assert.equal(await page.locator('#default-search').count(), 0);
  console.log('PASS admin creation path');

  await page.goto(baseURL + '/admin/schedule');
  await page.getByText('火', {exact: true}).click();
  const scheduleLink = page.locator('a[href="/admin/anime/36"]');
  await scheduleLink.scrollIntoViewIfNeeded();
  const scheduleScroll = await page.evaluate(() => scrollY);
  const scheduleRequests = requests.filter(value => value.includes('/anime/schedule/')).length;
  await scheduleLink.click();
  await page.waitForURL('**/admin/anime/36');
  await page.goBack();
  await settle();
  assert.match(await page.locator('.as-segment .is-on').last().innerText(), /火/);
  assert.equal(requests.filter(value => value.includes('/anime/schedule/')).length, scheduleRequests);
  assert.ok(Math.abs(await page.evaluate(() => scrollY) - scheduleScroll) < 3);
  console.log('PASS admin schedule day and scroll retained');

  await page.goto(baseURL + '/admin/caption');
  await page.locator('input[name="episode"]').first().fill('9');
  await page.locator('a[href="/admin/caption?state=0"]').click();
  await page.waitForURL('**/admin/caption?state=0');
  await settle();
  await page.goBack();
  await settle();
  assert.equal(await page.locator('input[name="episode"]').first().inputValue(), '9');
  const captions = requests.filter(value => value.includes('/caption/myList/')).length;
  await page.locator('.admin-tabs a[href="/admin/anime"]').click();
  await settle();
  await page.evaluate(() => window.scrollTo(0, 10000));
  await settle();
  assert.equal(requests.filter(value => value.includes('/caption/myList/')).length, captions);
  console.log('PASS admin caption state cached and hidden pagination paused');
  assert.deepEqual(errors, []);
} finally {
  await browser.close();
}
