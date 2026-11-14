import assert from 'node:assert/strict';
import test from 'node:test';
import {wikiImageOwner, wikiImageOwnerFields, wikiImageOwnerLocation} from '../src/domain/wiki/wikiImageOwner.ts';

test('image ownership requires a document kind and positive safe document number', () => {
  assert.deepEqual(wikiImageOwner('anime', '23'), {documentKind: 'anime', documentNo: 23});
  assert.deepEqual(wikiImageOwner('template', 12), {documentKind: 'template', documentNo: 12});
  for (const kind of [undefined, 'images', 'draft', ['anime']]) assert.equal(wikiImageOwner(kind, 12), undefined);
  for (const number of [undefined, null, '', 0, -1, 1.5, Infinity, 'NaN', ['12'], true, Number.MAX_SAFE_INTEGER + 1]) assert.equal(wikiImageOwner('anime', number), undefined);
});

test('owner fields and links distinguish anime documents from templates', () => {
  const anime = wikiImageOwner('anime', 23), template = wikiImageOwner('template', 12);
  assert.deepEqual(wikiImageOwnerFields(anime), {documentKind: 'anime', documentNo: '23'});
  assert.deepEqual(wikiImageOwnerFields(template), {documentKind: 'template', documentNo: '12'});
  assert.equal(wikiImageOwnerLocation(anime), '/anime/23');
  assert.equal(wikiImageOwnerLocation(template), '/wiki/templates?templateNo=12');
});
