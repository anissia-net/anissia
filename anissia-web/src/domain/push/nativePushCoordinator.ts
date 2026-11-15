import type {MobileNotificationAction, MobileNotificationState} from '../../common/mobileBridge';

interface NativeTransport {
  native(action: MobileNotificationAction, payload?: Record<string, string>): Promise<MobileNotificationState>;
  request<T>(path: string, dat: string, body?: unknown, method?: string): Promise<T>;
}
interface Subscription {subscribed: boolean; bindingId?: string; refreshSecret?: string; refreshSecretVersion?: string}

export class NativePushCoordinator {
  private revision = 0;
  private tail: Promise<void> = Promise.resolve();
  private readonly transport: NativeTransport;

  constructor(transport: NativeTransport) { this.transport = transport; }

  private queue<T>(operation: () => Promise<T>): Promise<T> {
    const request = this.tail.then(operation, operation);
    this.tail = request.then(() => {}, () => {});
    return request;
  }

  enable(owner: string, dat: string, isCurrent: () => boolean): Promise<MobileNotificationState> {
    const revision = this.revision;
    if (!owner || !isCurrent()) return Promise.reject(new Error('로그인 정보를 확인한 뒤 다시 시도해 주세요.'));
    const permission = this.transport.native('requestPermission').then(state => ({state, error: null}), error => ({state: null, error}));
    return this.queue(async () => {
      const result = await permission;
      if (result.error) throw result.error;
      const state = result.state!;
      if (revision !== this.revision || !isCurrent()) return state;
      if (!state.supported || !state.configured) throw new Error('현재 앱 알림을 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.');
      if (state.permission !== 'granted') return state;
      return this.register(owner, dat, isCurrent, revision);
    });
  }

  sync(owner: string, dat: string, isCurrent: () => boolean): Promise<MobileNotificationState | null> {
    const revision = this.revision;
    return this.queue(async () => {
      if (revision !== this.revision || !isCurrent()) return null;
      return this.register(owner, dat, isCurrent, revision);
    });
  }

  private async register(owner: string, dat: string, isCurrent: () => boolean, revision: number): Promise<MobileNotificationState> {
    const current = () => revision === this.revision && isCurrent();
    let state = await this.transport.native('status');
    if (!current()) return state;
    if (!owner || (state.owner && state.owner !== owner)) {
      if (state.enabled || state.owner) {
        await this.revoke('');
        state = await this.transport.native('status');
      }
      return state;
    }
    if (!state.supported || !state.configured || !state.enabled || state.permission !== 'granted') return state;
    if (!state.token || !state.clientId) throw new Error('앱 알림 연결 정보를 준비하고 있습니다. 잠시 후 다시 시도해 주세요.');
    const config = await this.transport.request<{enabled: boolean}>('native/config', '', undefined, 'GET');
    if (!current()) return state;
    if (!config.enabled) throw new Error('현재 앱 알림을 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.');

    const identity = {token: state.token, clientId: state.clientId};
    let subscription = await this.transport.request<Subscription>('native/subscription/status', dat, identity);
    if (!current()) return state;
    let refreshSecret: string | undefined;
    const renewCredential = !subscription.subscribed || !state.hasRefreshSecret || state.bindingId !== subscription.bindingId || state.owner !== owner
      || !subscription.refreshSecretVersion || state.refreshSecretVersion !== subscription.refreshSecretVersion;
    if (renewCredential) {
      subscription = await this.transport.request<Subscription>('native/subscription', dat, identity);
      refreshSecret = subscription.refreshSecret;
    }
    if (!subscription.subscribed || !subscription.bindingId) throw new Error('앱 알림을 등록하지 못했습니다. 다시 시도해 주세요.');
    if (!current()) {
      await this.remove(dat, {...identity, bindingId: subscription.bindingId});
      return state;
    }
    if (!/^[a-f0-9]{64}$/.test(subscription.refreshSecretVersion || '')
      || (renewCredential && (typeof refreshSecret !== 'string' || !/^[A-Za-z0-9_-]{43}$/.test(refreshSecret)))) {
      await this.remove(dat, {...identity, bindingId: subscription.bindingId});
      throw new Error('앱 알림 연결 정보를 갱신하지 못했습니다. 잠시 후 다시 시도해 주세요.');
    }
    try {
      return await this.transport.native('bind', {clientId: state.clientId, token: state.token, owner, bindingId: subscription.bindingId,
        refreshSecretVersion: subscription.refreshSecretVersion,
        ...(refreshSecret ? {refreshSecret} : {})});
    } catch (error) {
      await this.remove(dat, {...identity, bindingId: subscription.bindingId});
      if (current()) throw error;
      return state;
    }
  }

  async revoke(dat: string): Promise<void> {
    this.revision++;
    const previous = await this.transport.native('revoke');
    if (dat && previous.token && previous.clientId && previous.bindingId) {
      await this.remove(dat, {token: previous.token, clientId: previous.clientId, bindingId: previous.bindingId});
    }
  }

  private async remove(dat: string, identity: {token: string; clientId: string; bindingId: string}): Promise<void> {
    await this.transport.request('native/subscription', dat, identity, 'DELETE').catch(() => {});
  }
}
