import assert from 'node:assert/strict';
import {test} from 'node:test';
import {createHash} from 'node:crypto';
import {NativePushCoordinator} from '../src/domain/push/nativePushCoordinator.ts';

const rotationSecret = 's'.repeat(43);
const secretVersion = createHash('sha256').update(rotationSecret).digest('hex');

function deferred() {
  let resolve, reject;
  const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
  return {promise, resolve, reject};
}

function environment(overrides = {}) {
  const calls = [];
  let state = {supported: true, configured: true, permission: 'granted', enabled: true, clientId: 'client-a', token: 'token-a', owner: '', bindingId: '', hasRefreshSecret: false};
  const transport = {
    async native(action, payload) {
      calls.push({action, payload});
      if (overrides.native) {
        const result = overrides.native(action, payload);
        if (result !== undefined) return result;
      }
      if (action === 'revoke') {
        const previous = {...state};
        state = {...state, enabled: false, clientId: 'new-client', owner: '', bindingId: '', hasRefreshSecret: false, refreshSecretVersion: undefined};
        return previous;
      }
      if (action === 'bind') {
        assert.equal(payload.clientId, state.clientId);
        assert.equal(payload.token, state.token);
        const {refreshSecret, ...binding} = payload;
        state = {...state, ...binding, hasRefreshSecret: !!refreshSecret || state.hasRefreshSecret};
      }
      return {...state};
    },
    async request(path, dat, body, method = 'POST') {
      calls.push({path, dat, body, method});
      if (overrides.request) {
        const result = overrides.request(path, dat, body, method);
        if (result !== undefined) return result;
      }
      if (path === 'native/config') return {enabled: true};
      if (method === 'DELETE') return null;
      if (path.endsWith('/status')) return {subscribed: false};
      return {subscribed: true, bindingId: 'server-binding', refreshSecret: rotationSecret, refreshSecretVersion: secretVersion};
    },
  };
  return {calls, coordinator: new NativePushCoordinator(transport), setState(value) { state = {...state, ...value}; }};
}

test('native registration uses authenticated HTTP then commits only its server binding to native', async () => {
  const env = environment();
  const state = await env.coordinator.enable('account-a', 'dat-a', () => true);
  assert.equal(state.owner, 'account-a');
  assert.equal(state.bindingId, 'server-binding');
  const bind = env.calls.find(call => call.action === 'bind');
  assert.deepEqual(bind.payload, {clientId: 'client-a', token: 'token-a', owner: 'account-a', bindingId: 'server-binding', refreshSecret: rotationSecret, refreshSecretVersion: secretVersion});
  assert.equal(state.hasRefreshSecret, true);
  assert.equal('refreshSecret' in state, false);
  assert.equal(JSON.stringify(env.calls.filter(call => call.action)).includes('dat-a'), false);
  assert.equal(env.calls.find(call => call.path === 'native/subscription').dat, 'dat-a');
});

test('silent sync does not opt into notification permission or registration', async () => {
  for (const disabled of [{enabled: false}, {permission: 'default'}, {permission: 'denied'}, {configured: false}]) {
    const env = environment();
    env.setState(disabled);
    await env.coordinator.sync('account-a', 'dat-a', () => true);
    assert.deepEqual(env.calls.map(call => call.action), ['status']);
  }
});

test('account changes clear native binding locally without registering the new owner', async () => {
  const env = environment();
  env.setState({owner: 'account-a', bindingId: 'binding-a', hasRefreshSecret: true});
  const state = await env.coordinator.sync('account-b', 'dat-b', () => true);
  assert.equal(state.enabled, false);
  assert.equal(state.hasRefreshSecret, false);
  assert.equal(env.calls.some(call => call.path), false);
  assert.equal(env.calls.some(call => call.action === 'requestPermission'), false);
});

test('logout revokes immediately while an old registration is still in flight', async () => {
  const registered = deferred();
  const started = deferred();
  const env = environment({request(path, dat, body, method) {
    if (path === 'native/subscription' && method === 'POST') { started.resolve(); return registered.promise; }
  }});
  const request = env.coordinator.sync('account-a', 'dat-a', () => true);
  await started.promise;
  await env.coordinator.revoke('dat-a');
  assert.equal(env.calls.at(-1).action, 'revoke');
  registered.resolve({subscribed: true, bindingId: 'old-server-binding', refreshSecret: rotationSecret, refreshSecretVersion: secretVersion});
  await request;
  assert.equal(env.calls.some(call => call.action === 'bind'), false);
  assert.deepEqual(env.calls.at(-1), {path: 'native/subscription', dat: 'dat-a', method: 'DELETE', body: {
    token: 'token-a', clientId: 'client-a', bindingId: 'old-server-binding',
  }});
});

test('logout remains locally revoked when the server cannot remove the old device', async () => {
  const env = environment({request(path, dat, body, method) { if (method === 'DELETE') throw Error('offline'); }});
  env.setState({owner: 'account-a', bindingId: 'binding-a'});
  await env.coordinator.revoke('dat-a');
  assert.equal(env.calls[0].action, 'revoke');
  assert.equal(env.calls[1].method, 'DELETE');
});

test('a rotated native token rejects the old bind and removes only the captured server registration', async () => {
  const env = environment({native(action) { if (action === 'bind') throw Error('stale token'); }});
  await assert.rejects(env.coordinator.sync('account-a', 'dat-a', () => true), /stale token/);
  assert.deepEqual(env.calls.at(-1).body, {token: 'token-a', clientId: 'client-a', bindingId: 'server-binding'});
  assert.equal(env.calls.at(-1).method, 'DELETE');
});

test('a denied permission does not create a remote subscription', async () => {
  const env = environment();
  env.setState({permission: 'denied', enabled: false});
  assert.equal((await env.coordinator.enable('account-a', 'dat-a', () => true)).permission, 'denied');
  assert.equal(env.calls.some(call => call.path), false);
});

test('existing native credentials are retained without requesting or sending a new secret', async () => {
  const env = environment({request(path) {
    if (path.endsWith('/status')) return {subscribed: true, bindingId: 'binding-a', refreshSecretVersion: secretVersion};
  }});
  env.setState({owner: 'account-a', bindingId: 'binding-a', hasRefreshSecret: true, refreshSecretVersion: secretVersion});
  const state = await env.coordinator.sync('account-a', 'dat-a', () => true);
  assert.equal(state.hasRefreshSecret, true);
  assert.equal(env.calls.some(call => call.path === 'native/subscription'), false);
  assert.deepEqual(env.calls.find(call => call.action === 'bind').payload,
    {clientId: 'client-a', token: 'token-a', owner: 'account-a', bindingId: 'binding-a', refreshSecretVersion: secretVersion});
});

test('missing credentials or a different binding require an authenticated credential refresh', async () => {
  for (const oldBinding of [{bindingId: 'binding-a', hasRefreshSecret: false}, {bindingId: 'other-binding', hasRefreshSecret: true}]) {
    const env = environment({request(path) {
      if (path.endsWith('/status')) return {subscribed: true, bindingId: 'binding-a', refreshSecretVersion: secretVersion};
    }});
    env.setState({owner: 'account-a', ...oldBinding});
    const state = await env.coordinator.sync('account-a', 'dat-a', () => true);
    assert.equal(state.hasRefreshSecret, true);
    assert.equal(env.calls.find(call => call.path === 'native/subscription').dat, 'dat-a');
    assert.equal(env.calls.find(call => call.action === 'bind').payload.refreshSecret, rotationSecret);
  }
});

test('an incomplete credential response never creates a native binding', async () => {
  for (const refreshSecret of [undefined, '', 'short-secret']) {
    const env = environment({request(path, dat, body, method) {
      if (path === 'native/subscription' && method === 'POST') return {subscribed: true, bindingId: 'server-binding', refreshSecret, refreshSecretVersion: secretVersion};
    }});
    await assert.rejects(env.coordinator.sync('account-a', 'dat-a', () => true), /연결/);
    assert.equal(env.calls.some(call => call.action === 'bind'), false);
    assert.equal(env.calls.at(-1).method, 'DELETE');
    assert.equal(JSON.stringify(env.calls.at(-1)).includes('refreshSecret'), false);
  }
});

test('a credential response for an account that changed while waiting cannot bind to the new account', async () => {
  const subscribed = deferred(), started = deferred();
  let owner = 'account-a';
  const env = environment({request(path, dat, body, method) {
    if (path === 'native/subscription' && method === 'POST') { started.resolve(); return subscribed.promise; }
  }});
  const registration = env.coordinator.sync('account-a', 'dat-a', () => owner === 'account-a');
  await started.promise;
  owner = 'account-b';
  subscribed.resolve({subscribed: true, bindingId: 'old-binding', refreshSecret: rotationSecret, refreshSecretVersion: secretVersion});
  await registration;
  assert.equal(env.calls.some(call => call.action === 'bind'), false);
  assert.deepEqual(env.calls.at(-1), {path: 'native/subscription', dat: 'dat-a', method: 'DELETE', body: {
    token: 'token-a', clientId: 'client-a', bindingId: 'old-binding',
  }});
});

test('a lost rotated-secret response is repaired even when the native binding and presence flag still match', async () => {
  const env = environment({request(path) {
    if (path.endsWith('/status')) return {subscribed: true, bindingId: 'server-binding', refreshSecretVersion: secretVersion};
  }});
  env.setState({owner: 'account-a', bindingId: 'server-binding', hasRefreshSecret: true, refreshSecretVersion: 'b'.repeat(64)});
  const state = await env.coordinator.sync('account-a', 'dat-a', () => true);
  assert.equal(env.calls.some(call => call.path === 'native/subscription' && call.method === 'POST'), true);
  assert.equal(env.calls.find(call => call.action === 'bind').payload.refreshSecret, rotationSecret);
  assert.equal(state.refreshSecretVersion, secretVersion);
});

test('a native credential change between status and bind rejects the stale version and cleans up its registration', async () => {
  const env = environment({
    native(action) { if (action === 'bind') throw Error('stale credential version'); },
    request(path) {
      if (path.endsWith('/status')) return {subscribed: true, bindingId: 'binding-a', refreshSecretVersion: secretVersion};
    },
  });
  env.setState({owner: 'account-a', bindingId: 'binding-a', hasRefreshSecret: true, refreshSecretVersion: secretVersion});
  await assert.rejects(env.coordinator.sync('account-a', 'dat-a', () => true), /stale credential version/);
  assert.equal(env.calls.find(call => call.action === 'bind').payload.refreshSecretVersion, secretVersion);
  assert.equal(env.calls.at(-1).method, 'DELETE');
  assert.equal('refreshSecret' in env.calls.at(-1).body, false);
});
