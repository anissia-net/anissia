import assert from 'node:assert/strict';
import test from 'node:test';
import {versionWikiImages, wikiImageDisplayUrl} from '../src/domain/wiki/wikiImageVersion.ts';

test('display image versions preserve the canonical path, other query values and fragments', () => {
  const url = 'https://example.test/wiki/image.avif';
  assert.equal(wikiImageDisplayUrl(url, 4), `${url}?v=4`);
  assert.equal(wikiImageDisplayUrl(`${url}?size=64&v=2#image`, 5), `${url}?size=64&v=5#image`);
  for (const revision of [undefined, 0, -1, NaN, Infinity, 1.5]) assert.equal(wikiImageDisplayUrl(url, revision), url);
});

test('rendered image version updates keep canonical attributes and leave unlisted images unchanged', () => {
  const source = 'https://example.test/wiki/image.avif';
  const storedBody = {blocks: [{w: 'img', a: {src: source}, ch: []}]};
  const before = structuredClone(storedBody);
  const image = url => {
    const attributes = new Map([['src', url]]);
    return {getAttribute: key => attributes.get(key) ?? null, setAttribute: (key, value) => attributes.set(key, value), removeAttribute: key => attributes.delete(key)};
  };
  const known = image(storedBody.blocks[0].a.src), external = image('https://other.test/asset.png');
  const root = {querySelectorAll: () => [known, external]};
  versionWikiImages(root, {[source]: 7});
  assert.equal(known.getAttribute('src'), `${source}?v=7`);
  assert.equal(known.getAttribute('data-wiki-canonical-src'), source);
  assert.equal(external.getAttribute('src'), 'https://other.test/asset.png');
  versionWikiImages(root, {[source]: 8});
  assert.equal(known.getAttribute('src'), `${source}?v=8`);
  versionWikiImages(root);
  assert.equal(known.getAttribute('src'), source);
  assert.equal(known.getAttribute('data-wiki-canonical-src'), null);
  assert.deepEqual(storedBody, before);
});
