import assert from 'node:assert/strict';
import {test} from 'node:test';
import {legacyWikiHistoryLocation, wikiHistoryLocation, wikiHistoryTarget, wikiLocation} from '../src/domain/wiki/wikiLocation.ts';

test('current document links and history links have separate destinations', () => {
  const anime = {kind: 'anime', targetNo: 3527, revision: 3};
  const template = {kind: 'template', targetNo: 12, revision: 2};
  assert.equal(wikiLocation(anime), '/anime/3527');
  assert.equal(wikiLocation(template), '/wiki/templates?templateNo=12');
  assert.equal(wikiLocation({...anime, scope: 'history'}), '/wiki/history?animeNo=3527&revision=3');
  assert.equal(wikiLocation({...template, scope: 'history'}), '/wiki/history?templateNo=12&revision=2');
  assert.equal(wikiHistoryLocation({kind: 'anime', targetNo: 3527}), '/wiki/history?animeNo=3527');
});

test('old revision links keep their document and selected revision', () => {
  assert.equal(legacyWikiHistoryLocation('/anime', {animeNo: '3527', wikiRevision: '3'}), '/wiki/history?animeNo=3527&revision=3');
  assert.equal(legacyWikiHistoryLocation('/wiki/templates', {templateNo: '12', wikiRevision: '2'}), '/wiki/history?templateNo=12&revision=2');
  assert.equal(legacyWikiHistoryLocation('/anime', {animeNo: '3527'}), undefined);
  assert.equal(legacyWikiHistoryLocation('/wiki/images', {animeNo: '3527', wikiRevision: '3'}), undefined);
});

test('history targets reject ambiguous, repeated, missing and malformed identifiers', () => {
  assert.deepEqual(wikiHistoryTarget({animeNo: '3527'}), {kind: 'anime', targetNo: 3527});
  assert.deepEqual(wikiHistoryTarget({templateNo: '12'}), {kind: 'template', targetNo: 12});
  assert.equal(wikiHistoryTarget({animeNo: '3527', templateNo: '12'}), undefined);
  for (const animeNo of [undefined, null, '', '0', '-1', '1.5', 'NaN', '1e3', '9007199254740992', ['1', '2']]) {
    assert.equal(wikiHistoryTarget({animeNo}), undefined);
  }
  assert.equal(legacyWikiHistoryLocation('/anime', {animeNo: '3527', wikiRevision: ['1', '2']}), undefined);
});
