// Local Vite + Playwright integration fixture. Search requests and state changes stay inside this browser.
import assert from 'node:assert/strict';
const {chromium} = await import(process.env.PLAYWRIGHT_MODULE || 'playwright');
const base = process.env.TEST_BASE_URL || 'http://localhost:5173';
const browser = await chromium.launch({headless: true, ...(process.env.CHROME_PATH && {executablePath: process.env.CHROME_PATH})});
const context = await browser.newContext({viewport: {width: 390, height: 844}, serviceWorkers: 'block'});
const errors = [];
let state = {revision: 2, active: 1, candidate: null, tested: false, busy: false, error: null,
  history: [{version: 1, actor: '관리자', createdAt: '2026-11-04T12:00:00+09:00'}]};
const entries = {'user-dictionary': [{id: 'existing', term: '초전자포', value: '초 전자포'}], synonyms: [], corrections: [], stopwords: []};
const analysis = text => ({input: text, corrected: text, raw: [text, '전자포'], tokens: [text]});
const preview = title => ({analysis: analysis('초전자포'), total: 1, results: [{id: '1', title}]});
await context.route('**/*', route => {
  if (new URL(route.request().url()).origin === new URL(base).origin) return route.continue();
  return route.abort();
});
await context.route('**/api/**', async route => {
  const req = route.request(), url = new URL(req.url()), path = url.pathname.replace(/^.*\/admin\/search/, '');
  let data = null;
  if (url.pathname.includes('/admin/search/')) {
    assert.ok(req.headers().dat);
    if (path === '/state') data = state;
    else if (path === '/analyze') data = analysis(url.searchParams.get('q'));
    else if (path.startsWith('/rules/')) {
      const [, , cat, id] = path.split('/');
      if (req.method() === 'GET') {
        const items = entries[cat].filter(row => row.term.includes(url.searchParams.get('q')));
        data = {items, total: items.length};
      } else if (req.method() === 'POST') {
        const {term, value, revision} = req.postDataJSON();
        assert.equal(revision, state.revision);
        entries[cat].push({id: 'added-' + state.revision, term, value});
        state.revision++;
        state.candidate = null;
      } else if (req.method() === 'DELETE') {
        assert.equal(Number(url.searchParams.get('revision')), state.revision);
        entries[cat] = entries[cat].filter(row => row.id !== id);
        state.revision++;
        state.candidate = null;
      }
    } else if (path === '/prepare') { assert.equal(req.postDataJSON().revision, state.revision); state.candidate = state.revision; state.tested = false; }
    else if (path === '/test') {
      assert.equal(req.postDataJSON().version, state.candidate);
      if (req.postDataJSON().q === '미등록검색') {
        const empty = {analysis: analysis('미등록검색'), results: [], total: 0};
        data = {active: empty, candidate: empty};
      } else {
        state.tested = true;
        data = {active: preview('기존 작품'), candidate: preview('변경된 작품')};
      }
    }
    else if (path === '/publish') { assert.equal(req.postDataJSON().version, state.candidate); assert.ok(state.tested); state.active = state.candidate; state.candidate = null; }
    else throw new Error('Unknown search request ' + path);
  } else if (url.pathname.includes('notification')) data = {};
  await route.fulfill({json: {code: 'ok', data}});
});
async function signIn(role) {
  const dat = `${Math.floor(Date.now() / 1000) + 86400}.test.${Buffer.from('2\x1etest@example.test\x1eTester\x1e' + role).toString('base64url')}.test.signature`;
  await context.addCookies([{name: 'dat', value: dat, url: base}]);
}
const page = await context.newPage();
page.on('dialog', dialog => dialog.accept());
page.on('pageerror', error => errors.push(error.message));
try {
  await signIn('ROOT');
  await page.goto(base + '/admin/search');
  await page.waitForURL('**/admin/search/user-dictionary');
  await page.getByText('초전자포', {exact: true}).waitFor();
  const categoryNav = page.getByRole('navigation', {name: '사전 종류'});
  assert.deepEqual((await categoryNav.getByRole('link').allTextContents()).slice(-2).map(label => label.trim()), ['불용어', '버전 이력']);
  const history = categoryNav.getByRole('link', {name: '버전 이력', exact: true});
  assert.equal(await page.getByRole('heading', {name: '검색 관리', exact: true}).count(), 0);
  assert.equal(await page.getByText('검색어의 형태소를 확인하고 사전 규칙을 관리합니다.', {exact: true}).count(), 0);
  await page.getByText('분해 단위 · 초 전자포', {exact: true}).waitFor();
  await page.getByRole('button', {name: '단어 추가'}).click();
  assert.equal(await page.getByRole('textbox', {name: '분해 단위'}).count(), 0);
  await page.getByLabel('등록할 단어').fill('이세계');
  await page.getByRole('button', {name: '초안에 추가'}).click();
  await page.getByRole('button', {name: '이세계 삭제'}).waitFor();
  assert.equal(entries['user-dictionary'].find(rule => rule.term === '이세계').value, '');
  await page.getByRole('button', {name: '단어 추가'}).click();
  await page.getByLabel('등록할 단어').fill('초능력자');
  await page.getByRole('checkbox', {name: '복합어 분해 직접 지정'}).check();
  await page.getByLabel('분해 단위', {exact: true}).fill('초능력 자');
  await page.getByRole('button', {name: '초안에 추가'}).click();
  await page.getByText('분해 단위 · 초능력 자', {exact: true}).waitFor();
  await page.getByPlaceholder('검색어 테스트', {exact: true}).fill('초전자포');
  await page.waitForFunction(() => new URL(location.href).searchParams.get('q') === '초전자포');
  await page.getByText('토크나이저 분해', {exact: true}).waitFor();
  assert.equal(await page.getByText('운영 검색어 분석', {exact: true}).count(), 0);
  await page.getByRole('link', {name: '동의어', exact: true}).click();
  await page.waitForURL('**/admin/search/synonyms?**');
  assert.equal(await page.getByRole('searchbox').inputValue(), '초전자포');
  await page.getByText('검색어와 일치하는 규칙이 없습니다.').waitFor();
  await page.getByRole('button', {name: '규칙 추가'}).click();
  assert.equal(await page.getByLabel('기준 표현').inputValue(), '초전자포');
  await page.getByLabel('동의 표현', {exact: true}).fill('레일건');
  await page.getByRole('button', {name: '초안에 추가'}).click();
  await page.getByRole('button', {name: '초전자포 삭제'}).waitFor();
  await page.getByRole('button', {name: '테스트 버전 준비'}).click();
  const publish = page.getByRole('button', {name: '운영에 직접 적용'});
  await publish.waitFor();
  assert.ok(await publish.isDisabled());
  await page.getByLabel('테스트 검색어').fill('미등록검색');
  await page.getByRole('button', {name: '비교 테스트'}).click();
  await page.getByText('두 버전 모두 결과가 없고 분석도 같습니다. 다른 검색어로 테스트해 주세요.').waitFor();
  assert.ok(await publish.isDisabled());
  await page.getByLabel('테스트 검색어').fill('초전자포');
  await page.getByRole('button', {name: '비교 테스트'}).click();
  await page.getByText('변경된 작품', {exact: true}).waitFor();
  await publish.click();
  await page.getByText('변경한 규칙이 없습니다.').waitFor();
  assert.ok(await page.getByRole('button', {name: '테스트 버전 준비'}).isDisabled());
  await page.getByRole('button', {name: '초전자포 삭제'}).click();
  await page.getByText('검색어와 일치하는 규칙이 없습니다.').waitFor();
  for (const [cat, label] of [['corrections', '교정어'], ['stopwords', '불용어']]) {
    await page.getByRole('link', {name: label, exact: true}).click();
    await page.waitForURL('**/admin/search/' + cat + '?**');
    await page.getByRole('heading', {name: label + ' 0건'}).waitFor();
  }
  await history.click();
  await page.getByText('버전 1', {exact: true}).waitFor();
  assert.equal(await page.getByRole('button', {name: '초안으로 복원'}).count(), 0);
  await page.goBack();
  await page.waitForURL('**/admin/search/stopwords?**');
  await page.goBack();
  await page.waitForURL('**/admin/search/corrections?**');
  assert.equal(await page.getByRole('searchbox').inputValue(), '초전자포');
  assert.ok(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), 'mobile content fits viewport');

  await signIn('TRANSLATOR');
  state.candidate = state.revision;
  state.tested = true;
  await page.reload();
  await page.getByRole('button', {name: '비교 테스트'}).waitFor();
  assert.equal(await page.getByRole('button', {name: '운영에 직접 적용'}).count(), 0);
  await history.click();
  await page.getByText('버전 1', {exact: true}).waitFor();
  assert.equal(await page.getByRole('button', {name: '초안으로 복원'}).count(), 0);
  assert.deepEqual(errors, []);
  console.log('PASS mobile admin search: query and category URLs, rule edits, analysis, comparison, publish, version history and permissions.');
} finally {
  await browser.close();
}
