import assert from 'node:assert/strict';
import test from 'node:test';
import {animeThumbnailImageUrl} from '../src/domain/anime/animeThumbnailImage.ts';

test('legacy cropped wiki thumbnails use the complete image and retain cache revisions', () => {
  const path = 'https://file.anissia.net/file/wiki/ac9f436bc1f06e408720d58fe277fd7d';
  for (const suffix of ['', '?v=3', '?v=3#image', '#image']) {
    assert.equal(animeThumbnailImageUrl(`${path}.thumb.avif${suffix}`), `${path}.avif${suffix}`);
  }
});

test('other image URLs are not rewritten', () => {
  for (const source of [
    'https://example.test/poster.png',
    '/wiki/unknown.thumb.avif',
    '/wiki/ac9f436bc1f06e408720d58fe277fd7d.avif',
    '/wiki/ac9f436bc1f06e408720d58fe277fd7d.thumb.avif.backup',
    'https://example.test/image?source=/wiki/ac9f436bc1f06e408720d58fe277fd7d.thumb.avif',
  ]) assert.equal(animeThumbnailImageUrl(source), source);
});
