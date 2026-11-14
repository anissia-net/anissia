import assert from 'node:assert/strict';
import test from 'node:test';
import {wikiImageUploadMode} from '../src/domain/wiki/wikiImageAccess.ts';

test('image file permissions are not inferred from document or operator roles', () => {
  for (const access of [undefined, {}, {canContribute: true}, {isOperator: true}, {canContribute: true, isOperator: true}, {canUploadImage: false, canRequestImageUpload: false}, {canUploadImage: 'true', canRequestImageUpload: 1}]) {
    assert.equal(wikiImageUploadMode(access), undefined);
  }
});

test('direct upload and approved-request paths follow their own explicit capabilities', () => {
  assert.equal(wikiImageUploadMode({canUploadImage: true}), 'direct');
  assert.equal(wikiImageUploadMode({canRequestImageUpload: true}), 'request');
  assert.equal(wikiImageUploadMode({canUploadImage: false, canRequestImageUpload: true, isOperator: true}), 'request');
  assert.equal(wikiImageUploadMode({canUploadImage: true, canRequestImageUpload: false, canContribute: false, isOperator: false}), 'direct');
  assert.equal(wikiImageUploadMode({canUploadImage: true, canRequestImageUpload: true}), 'direct');
  assert.equal(wikiImageUploadMode({canReplaceImageFile: true}), undefined);
});
