import assert from 'node:assert/strict';
import {test} from 'node:test';
import {createNoteLinkResolver, noteLinkTarget} from '../src/common/noteLink.ts';

const origin = 'https://anissia.net';
test('only public detail patterns and their legacy links have title targets', () => {
  for (const [path, target] of [
    ['/anime/123?q=test#ref', {kind: 'anime', id: '123'}],
    ['/notice/42', {kind: 'notice', id: '42'}],
    ['/inquiry?topicNo=18', {kind: 'inquiry', id: '18'}],
    ['/anime?animeNo=15', {kind: 'anime', id: '15'}],
  ]) assert.deepEqual(noteLinkTarget(new URL(path, origin)), target);
  for (const path of ['/anime', '/anime/0', '/anime/01', '/anime/new', '/admin/anime/1', '/notice/1/edit',
    '/anime?animeNo=1&wikiRevision=2', '/anime?animeNo=1&type=templates', '/wiki/templates?templateNo=1',
    '/anime?animeNo=1&animeNo=2', '/anime/9007199254740992']) assert.equal(noteLinkTarget(new URL(path, origin)), undefined, path);
});

test('same-origin enforcement, deduplication, canonical cache keys and expiry', async () => {
  let now = 0;
  const requests = [];
  const resolve = createNoteLinkResolver({origin, api: '/api', now: () => now, fetcher: async (url, options) => {
    requests.push({url, options}); return Response.json({code: 'ok', data: {title: '실제 제목'}});
  }});
  for (const [url, same] of [['https://other.test/anime/1', true], [origin + '/anime/1', false],
    ['https://user:pass@anissia.net/anime/1', true], [origin + '/wiki/templates?templateNo=1', true]]) {
    assert.equal(await resolve(url, same, ''), null);
  }
  assert.equal(requests.length, 0);
  assert.deepEqual(await Promise.all([resolve(origin + '/anime/1', true, ''), resolve(origin + '/anime?animeNo=1#ref', true, '')]), ['실제 제목', '실제 제목']);
  assert.equal(requests.length, 1);
  assert.equal(requests[0].url, '/api/link/title?kind=anime&id=1');
  assert.equal(requests[0].options.credentials, 'omit');
  now = 60_001;
  assert.equal(await resolve(origin + '/anime/1', true, ''), '실제 제목');
  assert.equal(requests.length, 2);
});

test('missing titles, server failures and timeout leave the URL label intact', async () => {
  for (const response of [Response.json({code: 'ok', data: {title: null}}), Response.json({code: 'fail'}), new Response('', {status: 500})]) {
    const resolve = createNoteLinkResolver({origin, api: '/api', fetcher: async () => response});
    assert.equal(await resolve(origin + '/notice/1', true, ''), null);
  }
  let calls = 0;
  const resolve = createNoteLinkResolver({origin, api: '/api', timeoutMs: 5, fetcher: async (_url, {signal}) => {
    calls++;
    return new Promise((_resolve, reject) => signal.addEventListener('abort', () => reject(signal.reason), {once: true}));
  }});
  assert.equal(await resolve(origin + '/notice/1', true, ''), null);
  assert.equal(await resolve(origin + '/notice/1', true, ''), null);
  assert.equal(calls, 2, 'failed lookups can be retried');
});
