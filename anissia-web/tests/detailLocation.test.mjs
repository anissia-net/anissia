import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {test} from 'node:test';
import {runInNewContext} from 'node:vm';
import {legacyDetailLocation} from '../src/common/detailLocation.ts';

test('shared detail URLs redirect without dropping search context or mutating the query', () => {
  for (const path of ['/anime', '/admin/anime']) {
    const query = {animeNo: '123', q: '#판타지', state: 'list'};
    assert.deepEqual(legacyDetailLocation(path, query), {path: `${path}/123`, query: {q: '#판타지', state: 'list'}});
    assert.equal(query.animeNo, '123');
  }
  for (const path of ['/notice', '/inquiry']) {
    assert.deepEqual(legacyDetailLocation(path, {topicNo: '281'}), {path: `${path}/281`, query: {}});
    assert.deepEqual(legacyDetailLocation(path, {topicNo: '0'}), {path: `${path}/new`, query: {}});
  }
  assert.deepEqual(legacyDetailLocation('/admin/anime', {animeNo: '0'}), {path: '/admin/anime/new', query: {}});
});

test('list routes, unrelated routes and malformed identifiers are left alone', () => {
  for (const animeNo of [undefined, null, '', '0', '-1', '01', '1.5', '1e3', '9007199254740992', ['1', '2']]) {
    assert.equal(legacyDetailLocation('/anime', {animeNo}), undefined);
  }
  assert.equal(legacyDetailLocation('/wiki/history', {animeNo: '123'}), undefined);
  assert.equal(legacyDetailLocation('/anime', {q: 'test'}), undefined);
});

test('push notifications accept current and old links, while keeping their same-origin boundary', () => {
  const worker = {location: {origin: 'https://anissia.net'}, addEventListener() {}};
  const context = {self: worker, URL};
  runInNewContext(readFileSync(new URL('../public/sw.js', import.meta.url), 'utf8'), context);
  for (const value of ['/anime/123', '/anime?animeNo=123', 'https://anissia.net/anime/123?q=test']) {
    assert.equal(context.targetUrl(value), 'https://anissia.net/anime/123');
  }
  for (const value of ['/anime/0', '/anime/-1', '/anime/new', '/anime/9007199254740992', '/anime?animeNo=0', 'https://example.org/anime/123', '//example.org/anime/123', undefined]) {
    assert.equal(context.targetUrl(value), 'https://anissia.net/notifications');
  }
});
