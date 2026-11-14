import assert from 'node:assert/strict';

const {chromium} = await import(process.env.PLAYWRIGHT_MODULE || 'playwright');
const baseURL = process.env.TEST_BASE_URL || 'http://localhost:5173';
const browser = await chromium.launch({headless: true, ...(process.env.CHROME_PATH ? {executablePath: process.env.CHROME_PATH} : {})});
const now = new Date().toISOString();
const imageId = '0123456789abcdef0123456789abcdef';
const pixel = Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=', 'base64');
const canonicalImage = 'https://example.test/test-image.png';
const body = {schemaVersion: 1, blocks: [{w: 'p', ch: ['등록할 내용']}, {w: 'img', a: {src: canonicalImage}, ch: []}]};
const template = {templateNo: 12, revision: 3, title: '시리즈 틀', body, templates: [], updatedAt: now, imageVersions: {[canonicalImage]: 7}};
const readyImage = {id: imageId, documentKind: 'template', documentNo: 12, status: 'READY', revision: 3, description: '기존 이미지', sourceUrl: 'https://example.com/old', url: '/test-image.png', createdAt: now, updatedAt: now, updatedBy: '기여자', replacementPending: false};
const requestBase = {id: 31, kind: 'TEMPLATE_CREATE', status: 'PENDING', title: '새 시리즈 틀', reason: '작품 시리즈를 묶습니다.', createdBy: '요청자', createdAt: now, processedBy: null, processedAt: null, processingReason: '', targetNo: null, imageId: null, body, description: '', sourceUrl: '', imagePreviewUrl: null, image: null, resultTargetNo: null, resultImageId: null, canCancel: true, canProcess: false, canApprove: false};

async function setup({operator = false, eligible = true, request = requestBase, owned = true, capabilities = {}, omitCapabilities = false} = {}) {
  const context = await browser.newContext({serviceWorkers: 'block'});
  await context.route('**/*', route => new URL(route.request().url()).origin === new URL(baseURL).origin ? route.continue() : route.abort());
  const user = Buffer.from(`2\x1etester@example.test\x1eTester\x1e${operator ? 'TRANSLATOR' : ''}`).toString('base64url');
  const dat = `${Math.floor(Date.now() / 1000) + 86400}.test.${user}.test.signature`;
  await context.addCookies([{name: 'dat', value: dat, url: baseURL}]);
  const writes = [], pageErrors = [], imageLists = [];
  let current = structuredClone(request), image = structuredClone({...readyImage, ...(!owned ? {documentKind: null, documentNo: null} : {})}), replacementReads = 0, previewAuthenticated = false;
  await context.route('**/test-image.png*', route => route.fulfill({contentType: 'image/png', body: pixel}));
  await context.route('**/api/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname.slice('/api'.length), method = request.method();
    let data = {};
    if (path === '/wiki/access') data = {canContribute: eligible, isOperator: operator, ...(!omitCapabilities ? {canUploadImage: operator && eligible, canRequestImageUpload: eligible, canReplaceImageFile: eligible, ...capabilities} : {}), message: eligible ? '' : '위키 참여는 가입 후 15일이 지나면 가능합니다.'};
    else if (path === '/wiki/templates') data = {content: [template], totalElements: 1, page: 0, size: 30};
    else if (path === '/wiki/templates/12') {
      if (method === 'PUT') { writes.push({path, method, payload: request.postDataJSON()}); data = {...template, ...request.postDataJSON(), revision: template.revision + 1}; }
      else data = template;
    }
    else if (path === '/wiki/anime/1') data = {...template, templateNo: undefined, animeNo: 1};
    else if (path === '/anime/animeNo/1') data = {animeNo: 1, subject: '시험 작품', genres: '', captions: [], status: 'END', week: '8'};
    else if (path.endsWith('/usage')) data = {content: [], totalElements: 0, currentCount: 0, historyCount: 0, canDelete: true};
    else if (path === '/wiki/images' && method === 'POST') {
      writes.push({path, method, payload: request.postData()}); data = image;
    } else if (path === '/wiki/images') {
      imageLists.push(Object.fromEntries(url.searchParams));
      data = {content: [image], totalElements: 1, page: 0, size: 30};
    } else if (path === `/wiki/images/${imageId}/attach`) {
      writes.push({path, method, payload: request.postDataJSON()});
      data = {...image, id: '11111111111111111111111111111111', sourceImageId: imageId, url: '/test-image.png?copy=1'};
    }
    else if (path === `/wiki/images/${imageId}/file`) {
      writes.push({path, method, payload: request.postData()});
      image = {...image, replacementPending: true}; data = image;
    } else if (path === `/wiki/images/${imageId}`) {
      if (image.replacementPending && ++replacementReads > 1) image = {...image, revision: 4, description: '교체 이미지', sourceUrl: 'https://example.com/new', replacementPending: false};
      data = image;
    } else if (path === '/wiki/requests' && method === 'POST') {
      const payload = request.postDataJSON(); writes.push({path, method, payload});
      current = {...current, ...payload}; data = current;
    } else if (path === '/wiki/requests/images' && method === 'POST') {
      writes.push({path, method, payload: request.postData()});
      current = {...requestBase, kind: 'IMAGE_CREATE', title: '등록 이미지', body: null, description: '등록 이미지', sourceUrl: 'https://example.com/source', image: {...readyImage, status: 'REVIEW_READY', url: null}, imagePreviewUrl: '/wiki/requests/31/image'}; data = current;
    } else if (path === '/wiki/requests') data = {content: [current], totalElements: 1, page: 0, size: 30};
    else if (path === '/wiki/requests/31/image') {
      previewAuthenticated = request.headers().dat === dat;
      await route.fulfill({contentType: 'image/png', body: pixel}); return;
    } else if (path === '/wiki/requests/31/approve' || path === '/wiki/requests/31/reject' || path === '/wiki/requests/31/cancel') {
      const payload = request.postDataJSON(); writes.push({path, method, payload});
      const status = path.endsWith('/approve') ? 'APPROVED' : path.endsWith('/reject') ? 'REJECTED' : 'CANCELLED';
      current = {...current, status, processingReason: payload.reason, processedBy: '처리자', processedAt: now, canProcess: false, canCancel: false, canApprove: false, resultTargetNo: status === 'APPROVED' ? 13 : null}; data = current;
    } else if (path === '/wiki/requests/31') data = current;
    else if (path === '/notification/inbox') data = {items: [], unreadCount: 0};
    await route.fulfill({contentType: 'application/json', body: JSON.stringify({code: 'ok', data})});
  });
  const page = await context.newPage();
  page.on('pageerror', error => pageErrors.push(error.message));
  page.on('dialog', dialog => dialog.accept());
  return {page, context, writes, pageErrors, imageLists, previewAuthenticated: () => previewAuthenticated};
}

try {
  const member = await setup();
  await member.page.goto(baseURL + '/wiki/templates');
  await member.page.getByRole('link', {name: '템플릿 등록 요청', exact: true}).waitFor();
  await member.page.locator(`img[src="${canonicalImage}?v=7"]`).waitFor();
  assert.equal(template.body.blocks[1].a.src, canonicalImage);
  assert.equal(await member.page.getByRole('link', {name: '새 템플릿', exact: true}).count(), 0);
  await member.page.getByRole('link', {name: '템플릿 등록 요청', exact: true}).click();
  await member.page.getByLabel('템플릿 이름').fill('새 시리즈 틀');
  await member.page.locator('.nabi-content[contenteditable="true"]').fill('등록 요청 본문');
  await member.page.getByLabel('요청 사유').fill('시리즈 탐색을 돕습니다.');
  await member.page.getByRole('button', {name: '요청 제출', exact: true}).click();
  await member.page.waitForURL('**/wiki/requests/31');
  assert.equal(member.writes[0].payload.kind, 'TEMPLATE_CREATE');
  assert.match(JSON.stringify(member.writes[0].payload.body), /등록 요청 본문/);
  assert.equal(await member.page.getByRole('button', {name: '승인하고 처리', exact: true}).count(), 0);
  await member.page.getByRole('button', {name: '요청 취소', exact: true}).click();
  await member.page.getByText('취소', {exact: true}).waitFor();
  assert.ok(member.writes.some(write => write.path.endsWith('/cancel')));
  assert.deepEqual(member.pageErrors, []);
  await member.context.close();

  const young = await setup({eligible: false});
  await young.page.goto(baseURL + '/wiki/requests/new');
  await young.page.getByText('위키 참여는 가입 후 15일이 지나면 가능합니다.', {exact: true}).waitFor();
  assert.equal(await young.page.getByRole('button', {name: '요청 제출', exact: true}).count(), 0);
  await young.context.close();

  const operator = await setup({operator: true, request: {...requestBase, canProcess: true, canApprove: true}});
  await operator.page.goto(baseURL + '/wiki/requests/31');
  const approve = operator.page.getByRole('button', {name: '승인하고 처리', exact: true});
  await approve.waitFor(); assert.equal(await approve.isDisabled(), true);
  await operator.page.getByLabel('처리 사유').fill('내용과 출처를 확인했습니다.');
  await approve.click();
  await operator.page.getByRole('link', {name: '등록된 문서 보기', exact: true}).waitFor();
  assert.equal(operator.writes[0].payload.reason, '내용과 출처를 확인했습니다.');
  assert.deepEqual(operator.pageErrors, []);
  await operator.context.close();

  const upload = await setup();
  await upload.page.goto(baseURL + '/wiki/requests/new?kind=IMAGE_CREATE&documentKind=template&documentNo=12');
  await upload.page.getByLabel('등록할 이미지', {exact: true}).setInputFiles({name: 'test.png', mimeType: 'image/png', buffer: pixel});
  await upload.page.getByLabel('이미지 설명', {exact: true}).fill('등록 이미지');
  await upload.page.getByLabel('출처', {exact: true}).fill('https://example.com/source');
  await upload.page.getByLabel('요청 사유').fill('작품 대표 이미지입니다.');
  await upload.page.getByRole('button', {name: '요청 제출', exact: true}).click();
  await upload.page.waitForURL('**/wiki/requests/31');
  await upload.page.locator('img[src^="blob:"]').waitFor();
  assert.equal(await upload.page.getByRole('link', {name: '이미지가 속한 템플릿 보기', exact: true}).getAttribute('href'), '/wiki/templates?templateNo=12');
  assert.equal(upload.previewAuthenticated(), true);
  for (const field of ['file', 'description', 'sourceUrl', 'reason', 'documentKind', 'documentNo']) assert.match(upload.writes[0].payload, new RegExp(`name="${field}"`));
  assert.deepEqual(upload.pageErrors, []);
  await upload.context.close();

  const replacement = await setup();
  await replacement.page.goto(baseURL + `/wiki/images?imageId=${imageId}`);
  await replacement.page.getByRole('button', {name: '이미지 수정', exact: true}).click();
  await replacement.page.getByLabel('교체할 이미지 (선택)', {exact: true}).setInputFiles({name: 'replacement.png', mimeType: 'image/png', buffer: pixel});
  await replacement.page.getByLabel('이미지 설명', {exact: true}).fill('교체 이미지');
  await replacement.page.getByLabel('출처', {exact: true}).fill('https://example.com/new');
  await replacement.page.getByRole('button', {name: '파일 교체 및 정보 저장', exact: true}).click();
  await replacement.page.getByText('새 이미지를 처리하고 있습니다.', {exact: false}).waitFor();
  assert.equal(await replacement.page.getByRole('button', {name: '이미지 수정', exact: true}).isDisabled(), true);
  await replacement.page.locator('img[src="/test-image.png?v=4"]').waitFor();
  for (const field of ['file', 'baseRevision', 'description', 'sourceUrl']) assert.match(replacement.writes[0].payload, new RegExp(`name="${field}"`));
  assert.deepEqual(replacement.pageErrors, []);
  await replacement.context.close();
  const scoped = await setup({operator: true});
  await scoped.page.goto(baseURL + '/wiki/templates?templateNo=12&edit=1');
  const scopedDialog = scoped.page.locator('.wiki-editor-images');
  await scopedDialog.locator('article img').waitFor();
  assert.equal(await scoped.page.locator('.wiki-editor-images').count(), 1);
  assert.equal(await scoped.page.locator('dialog[open]').count(), 0);
  const initialListRequests = scoped.imageLists.length;
  const editingSurface = scoped.page.locator('.nabi-content[contenteditable="true"]');
  await editingSurface.fill('앞');
  await editingSurface.press('Enter');
  await editingSurface.pressSequentially('뒤');
  await editingSurface.locator('p').first().click();
  assert.equal(await editingSurface.evaluate(element => { const selection = document.getSelection(); const range = document.createRange(); range.selectNodeContents(element); range.setEnd(selection.focusNode, selection.focusOffset); return range.toString(); }), '앞');
  await scoped.page.locator('button[data-wing="img"]').click();
  assert.equal(scoped.imageLists.length, initialListRequests);
  await scopedDialog.getByRole('button', {name: '이미지 업로드', exact: true}).click();
  await scopedDialog.getByLabel('업로드할 이미지', {exact: true}).setInputFiles({name: 'owned.png', mimeType: 'image/png', buffer: pixel});
  await scopedDialog.getByLabel('이미지 설명', {exact: true}).fill('문서 첨부');
  await scopedDialog.getByLabel('출처', {exact: true}).fill('https://example.com/owned');
  await scopedDialog.getByRole('button', {name: '업로드', exact: true}).click();
  await scopedDialog.getByText('업로드가 완료되었습니다.', {exact: true}).waitFor();
  assert.equal(scoped.imageLists[0].documentKind, 'template');
  assert.equal(scoped.imageLists[0].documentNo, '12');
  assert.match(scoped.writes[0].payload, /name="documentKind"\r\n\r\ntemplate/);
  assert.match(scoped.writes[0].payload, /name="documentNo"\r\n\r\n12/);
  assert.equal(await scoped.page.locator('form form').count(), 0);
  assert.equal(scoped.writes.filter(write => write.path === '/wiki/templates/12').length, 0);
  await scopedDialog.getByRole('button', {name: '본문에 넣기', exact: true}).click();
  await editingSurface.locator('img[src="/test-image.png"]').waitFor();
  assert.equal(await editingSurface.evaluate(element => { const range = document.createRange(); range.selectNodeContents(element); range.setEndBefore(element.querySelector('img')); return range.toString(); }), '앞');
  assert.equal(await editingSurface.evaluate(element => { const range = document.createRange(); range.selectNodeContents(element); range.setStartAfter(element.querySelector('img')); return range.toString(); }), '뒤');
  await scopedDialog.getByRole('button', {name: '보관함에서 가져오기', exact: true}).click();
  await scopedDialog.getByText('이 문서로 가져오기', {exact: true}).click();
  await scoped.page.locator('.nabi-content[contenteditable="true"] img[src="/test-image.png?copy=1"]').waitFor();
  assert.deepEqual(scoped.writes.find(write => write.path.endsWith('/attach')).payload, {documentKind: 'template', documentNo: 12});
  await scoped.page.locator('button[data-wing="img"]').click();
  await scoped.page.locator('.wiki-editor-images').getByRole('button', {name: '이미지 정보', exact: true}).click();
  await scoped.page.locator('.wiki-editor-images').getByRole('button', {name: '이미지 수정', exact: true}).click();
  await scoped.page.locator('.wiki-editor-images').getByLabel('이미지 설명', {exact: true}).fill('저장 전 변경');
  scoped.page.removeAllListeners('dialog');
  scoped.page.once('dialog', dialog => dialog.dismiss());
  await scoped.page.locator('.wiki-editor-images').getByRole('button', {name: '이미지 목록으로', exact: true}).click();
  assert.equal(await scoped.page.locator('.wiki-editor-images').getByRole('group', {name: '이미지 정보 수정', exact: true}).count(), 1);
  assert.deepEqual(scoped.pageErrors, []);
  await scoped.context.close();

  const inline = await setup();
  await inline.page.goto(baseURL + '/wiki/templates?templateNo=12&edit=1');
  assert.equal(await inline.page.getByRole('link', {name: '이미지 보관함', exact: true}).count(), 0);
  await inline.page.setViewportSize({width: 390, height: 844});
  const inlineDialog = inline.page.locator('.wiki-editor-images');
  await inlineDialog.locator('article img').waitFor();
  await inlineDialog.getByRole('button', {name: '이미지 등록 요청', exact: true}).click();
  await inlineDialog.getByLabel('업로드할 이미지', {exact: true}).setInputFiles({name: 'requested.png', mimeType: 'image/png', buffer: pixel});
  await inlineDialog.getByLabel('이미지 설명', {exact: true}).fill('승인할 첨부');
  await inlineDialog.getByLabel('출처', {exact: true}).fill('https://example.com/requested');
  await inlineDialog.getByLabel('등록 요청 사유', {exact: true}).fill('이 문서에 필요합니다.');
  await inlineDialog.getByRole('group', {name: '이미지 파일 등록', exact: true}).getByRole('button', {name: '이미지 등록 요청', exact: true}).click();
  await inlineDialog.getByRole('link', {name: '처리 상태 보기 (새 창)', exact: true}).waitFor();
  assert.equal(await inlineDialog.evaluate(element => element.scrollWidth <= element.clientWidth + 1), true);
  assert.equal(inline.writes[0].path, '/wiki/requests/images');
  assert.match(inline.writes[0].payload, /name="documentKind"\r\n\r\ntemplate/);
  assert.match(inline.writes[0].payload, /name="documentNo"\r\n\r\n12/);
  assert.deepEqual(inline.pageErrors, []);
  await inline.context.close();

  const fresh = await setup({operator: true});
  await fresh.page.goto(baseURL + '/wiki/templates?new=1');
  await fresh.page.getByText('템플릿을 먼저 저장하거나 등록 요청이 승인된 뒤, 템플릿 편집 화면에서 이미지를 올릴 수 있습니다.', {exact: true}).waitFor();
  assert.equal(fresh.imageLists.length, 0);
  assert.deepEqual(fresh.pageErrors, []);
  await fresh.context.close();
  const archived = await setup({operator: true, owned: false});
  await archived.page.goto(baseURL + `/wiki/images?imageId=${imageId}`);
  await archived.page.getByText('기존 이미지 보관함에 있는 이미지입니다.', {exact: false}).waitFor();
  assert.equal(await archived.page.getByRole('button', {name: '이미지 수정', exact: true}).count(), 0);
  await archived.page.getByRole('button', {name: '이미지 삭제', exact: true}).waitFor();
  assert.deepEqual(archived.pageErrors, []);
  await archived.context.close();

  const missingCapabilities = await setup({operator: true, omitCapabilities: true});
  await missingCapabilities.page.goto(baseURL + '/wiki/templates?templateNo=12&edit=1');
  const restrictedDialog = missingCapabilities.page.locator('.wiki-editor-images');
  await restrictedDialog.getByRole('button', {name: '새로고침', exact: true}).waitFor();
  assert.equal(await restrictedDialog.getByRole('button', {name: '이미지 업로드', exact: true}).count(), 0);
  assert.equal(await restrictedDialog.getByRole('button', {name: '이미지 등록 요청', exact: true}).count(), 0);
  assert.equal(await restrictedDialog.getByRole('button', {name: '보관함에서 가져오기', exact: true}).count(), 0);
  await restrictedDialog.getByRole('button', {name: '이미지 정보', exact: true}).click();
  await restrictedDialog.getByRole('button', {name: '이미지 수정', exact: true}).click();
  assert.equal(await restrictedDialog.getByLabel('교체할 이미지 (선택)', {exact: true}).count(), 0);
  await restrictedDialog.getByLabel('이미지 설명', {exact: true}).fill('설명은 별도 수정 가능');
  assert.equal(await restrictedDialog.getByRole('button', {name: '정보 저장', exact: true}).isEnabled(), true);
  assert.deepEqual(missingCapabilities.writes, []);
  assert.deepEqual(missingCapabilities.pageErrors, []);
  await missingCapabilities.context.close();

  const deniedFiles = await setup({capabilities: {canUploadImage: false, canRequestImageUpload: false, canReplaceImageFile: false}});
  await deniedFiles.page.goto(baseURL + '/wiki/requests/new?kind=IMAGE_CREATE&documentKind=template&documentNo=12');
  await deniedFiles.page.getByText('이 요청을 제출할 권한이 없습니다.', {exact: true}).waitFor();
  assert.equal(await deniedFiles.page.locator('input[type="file"]').count(), 0);
  assert.equal(await deniedFiles.page.getByRole('button', {name: '요청 제출', exact: true}).isDisabled(), true);
  assert.deepEqual(deniedFiles.writes, []);
  assert.deepEqual(deniedFiles.pageErrors, []);
  await deniedFiles.context.close();

  const requestOnly = await setup({operator: true, capabilities: {canUploadImage: false, canRequestImageUpload: true, canReplaceImageFile: false}});
  await requestOnly.page.goto(baseURL + '/wiki/templates?templateNo=12&edit=1');
  await requestOnly.page.locator('.wiki-editor-images').getByRole('button', {name: '이미지 등록 요청', exact: true}).waitFor();
  assert.equal(await requestOnly.page.locator('.wiki-editor-images').getByRole('button', {name: '이미지 업로드', exact: true}).count(), 0);
  assert.equal(await requestOnly.page.locator('.wiki-editor-images').getByRole('button', {name: '보관함에서 가져오기', exact: true}).count(), 0);
  assert.deepEqual(requestOnly.pageErrors, []);
  await requestOnly.context.close();

  const animeEditor = await setup({operator: true});
  await animeEditor.page.goto(baseURL + '/anime/1');
  await animeEditor.page.locator('.anime-document-actions button').click();
  const animePanel = animeEditor.page.locator('.wiki-editor-images');
  await animePanel.locator('article img').waitFor();
  assert.equal(await animeEditor.page.locator('dialog[open]').count(), 0);
  assert.equal(animeEditor.imageLists[0].documentKind, 'anime');
  assert.equal(animeEditor.imageLists[0].documentNo, '1');
  assert.equal(await animePanel.isVisible(), true);
  assert.deepEqual(animeEditor.pageErrors, []);
  await animeEditor.context.close();

  const independentSave = await setup({operator: true});
  await independentSave.page.goto(baseURL + '/wiki/templates?templateNo=12&edit=1');
  const savePanel = independentSave.page.locator('.wiki-editor-images');
  await savePanel.getByRole('button', {name: '이미지 업로드', exact: true}).click();
  assert.equal(await savePanel.getByLabel('이미지 설명', {exact: true}).evaluate(input => input.validity.valueMissing), true);
  const documentForm = independentSave.page.locator('form[data-save-scope="page"]');
  assert.equal(await documentForm.evaluate(form => form.checkValidity()), true);
  await independentSave.page.getByLabel('수정 요약', {exact: true}).fill('문서만 저장');
  await documentForm.getByRole('button', {name: '저장', exact: true}).click();
  await independentSave.page.locator('.wiki-editor-images').waitFor({state: 'detached'});
  assert.deepEqual(independentSave.writes.map(write => [write.method, write.path]), [['PUT', '/wiki/templates/12']]);
  assert.deepEqual(independentSave.pageErrors, []);
  await independentSave.context.close();
  console.log('PASS explicit image file capabilities, missing/false flags denied, metadata editing preserved, scoped image upload, archive copy, inline approval request, new-template save-first flow, member permissions, template request/cancel, 15-day restriction, operator approval, authenticated image request preview, and image replacement polling');
} finally { await browser.close(); }
