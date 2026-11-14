import assert from 'node:assert/strict';
import test from 'node:test';
import {wikiImageOwner, wikiImageOwnerFields, wikiImageOwnerLocation, wikiImageLocation} from '../src/domain/wiki/wikiImageOwner.ts';

test('image ownership requires a document kind and positive safe document number', () => {
  assert.deepEqual(wikiImageOwner('anime', '23'), {documentKind: 'anime', documentNo: 23});
  assert.deepEqual(wikiImageOwner('template', 12), {documentKind: 'template', documentNo: 12});
  for (const kind of [undefined, 'images', 'draft', ['anime']]) assert.equal(wikiImageOwner(kind, 12), undefined);
  for (const number of [undefined, null, '', 0, -1, 1.5, Infinity, 'NaN', ['12'], true, Number.MAX_SAFE_INTEGER + 1]) assert.equal(wikiImageOwner('anime', number), undefined);
});

test('image links open details in the owning document without opening an editor', () => {
  assert.equal(wikiImageLocation({id: 'image1', documentKind: 'anime', documentNo: 23}), '/anime/23?imageId=image1#wiki-images');
  assert.equal(wikiImageLocation({id: 'image2', documentKind: 'template', documentNo: 12}), '/wiki/templates?templateNo=12&imageId=image2#wiki-images');
  assert.equal(wikiImageLocation({id: 'image3'}), undefined);
  assert.equal(wikiImageLocation({id: 'image4', documentKind: 'anime', documentNo: 0}), undefined);
});

test('owner fields and links distinguish anime documents from templates', () => {
  const anime = wikiImageOwner('anime', 23), template = wikiImageOwner('template', 12);
  assert.deepEqual(wikiImageOwnerFields(anime), {documentKind: 'anime', documentNo: '23'});
  assert.deepEqual(wikiImageOwnerFields(template), {documentKind: 'template', documentNo: '12'});
  assert.equal(wikiImageOwnerLocation(anime), '/anime/23');
  assert.equal(wikiImageOwnerLocation(template), '/wiki/templates?templateNo=12');
});
