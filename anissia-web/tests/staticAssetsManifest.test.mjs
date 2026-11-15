import assert from 'node:assert/strict';
import {test} from 'node:test';
import vm from 'node:vm';
import config from '../vite.config.ts';

test('every emitted fingerprint enters the trusted manifest even after hosting header limits', () => {
  const settings = config({command: 'build', mode: 'production'});
  const plugin = settings.plugins.find(plugin => plugin.name === 'immutable-bundle-headers');
  const bundle = Object.fromEntries(Array.from({length: 150}, (_, index) =>
    [`assets/image-${String(index).padStart(8, '0')}.svg`, {type: 'asset', source: '<svg/>'}]));
  bundle['assets/index-Abcd1234.js'] = {type: 'chunk', code: 'main'};
  bundle['assets/style-Abcd1234.css'] = {type: 'asset', source: 'style'};
  bundle['assets/font-Abcd1234.woff2'] = {type: 'asset', source: 'font'};
  bundle['assets/mutable.svg'] = {type: 'asset', source: '<svg/>'};
  bundle['index.html'] = {type: 'asset', source: '<html/>'};
  const emitted = {};
  plugin.generateBundle.call({emitFile(asset) { emitted[asset.fileName] = asset.source; }}, {}, bundle);
  const scope = {};
  vm.runInNewContext(emitted['static-assets.js'], {self: scope});
  assert.equal(scope.AnissiaStaticAssets.length, 153);
  assert.ok(scope.AnissiaStaticAssets.includes('/assets/image-00000149.svg'));
  assert.ok(!scope.AnissiaStaticAssets.includes('/assets/mutable.svg'));
  assert.ok(!scope.AnissiaStaticAssets.includes('/index.html'));
  const rules = emitted._headers.split('\n').filter(line => line.startsWith('/'));
  assert.equal(rules.length, 100);
  for (const name of ['index-Abcd1234.js', 'style-Abcd1234.css', 'font-Abcd1234.woff2']) {
    assert.ok(rules.includes('/assets/' + name));
  }
  assert.match(emitted._headers, /\/static-assets\.js\n  Cache-Control: no-cache/);
  assert.ok(!rules.some(rule => rule.includes('*')));
});
