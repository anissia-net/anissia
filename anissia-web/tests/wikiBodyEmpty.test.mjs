import assert from 'node:assert/strict';
import {test} from 'node:test';
import {isWikiBodyEmpty} from '../src/domain/wiki/wikiBodyEmpty.ts';

const body = (...blocks) => ({schemaVersion: 1, blocks});

test('blank paragraphs and nested formatting are empty even in saved documents', () => {
  assert.equal(isWikiBodyEmpty(body({w: 'p', ch: []})), true);
  assert.equal(isWikiBodyEmpty(body({w: 'p', ch: [' \n\t', {w: 'b', ch: ['\u00a0']}, {w: 'br', ch: []}]})), true);
  assert.equal(isWikiBodyEmpty(body({w: 'p', ch: [{w: 'quote', ch: [{w: 'p', ch: []}]}]})), true);
  assert.equal(isWikiBodyEmpty(body({w: 'p', ch: ['작품 소개']})), false);
});

test('non-text content and template references keep a document visible', () => {
  for (const w of ['img', 'exTemplateInclude', 'youtube', 'table', 'hr', 'fn']) {
    assert.equal(isWikiBodyEmpty(body({w: 'p', ch: [{w, ch: []}]})), false, w);
  }
});

test('unknown or malformed content is not treated as an unwritten document', () => {
  assert.equal(isWikiBodyEmpty(body({w: 'futureWing', ch: []})), false);
  assert.equal(isWikiBodyEmpty(body({w: 'p', ch: 'unexpected'})), false);
  assert.equal(isWikiBodyEmpty(body(null)), false);
  assert.equal(isWikiBodyEmpty({schemaVersion: 2, blocks: []}), false);
});
