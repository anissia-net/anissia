import {computed, ref, watch} from 'vue';
import {defineStore} from 'pinia';
import {sessionStore} from '../session/sessionStore';
import sessionService from '../session/remote/sessionService';
import Session from '../session/Session';

export interface NotificationInboxItem {
  id: number;
  animeNo: number;
  subject: string;
  episode: string;
  translatorName: string;
  createdAt: string;
  readAt: string | null;
}

interface NotificationInbox {
  items: NotificationInboxItem[];
  unreadCount: number;
}

const api = import.meta.env.VITE_API_PATH;

// Inbox polling must not show the application's full-page loading/error overlay.
async function inboxRequest<T>(path: string, method: 'GET' | 'PUT', dat: string): Promise<T | null> {
  const response = await fetch(`${api}/notification/inbox${path}`, {
    method,
    cache: 'no-store',
    headers: {'Content-Type': 'application/json', dat},
  });
  if (response.headers.get('Dat-Error') === 'INVALID' && dat && sessionService.getDat() === dat) {
    void sessionService.logout();
    throw new Error('로그인이 만료되었습니다. 다시 로그인해 주세요.');
  }
  if (!response.ok) throw new Error('알림 서버에 연결하지 못했습니다. 다시 시도해 주세요.');
  const result = await response.json() as {code: string, message?: string, data?: T};
  if (result.code !== 'ok') throw new Error(result.message || '알림 요청을 처리하지 못했습니다.');
  return result.data ?? null;
}

export const notificationInboxStore = defineStore('notificationInbox', () => {
  const session = sessionStore();
  const owner = computed(() => session.user.isLogin ? session.user.email : '');
  const items = ref<NotificationInboxItem[]>([]);
  const unreadCount = ref(0);
  const loaded = ref(false);
  const loading = ref(false);
  const error = ref('');
  const pending = ref(new Set<number>());
  const markingAll = ref(false);
  let revision = 0;
  let request: Promise<void> | null = null;

  function isCurrentOwner(account: string): boolean {
    const credential = Session.assign(sessionService.getDat());
    return !!account && account === owner.value && credential.isLogin && credential.email === account;
  }

  async function load(force = false): Promise<void> {
    const account = owner.value;
    if (!isCurrentOwner(account)) return;
    if (request) return request;
    if (loaded.value && !force) return;
    const dat = sessionService.getDat();
    const current = ++revision;
    loading.value = true;
    error.value = '';
    const operation = (async () => {
      try {
        const inbox = await inboxRequest<NotificationInbox>('', 'GET', dat);
        if (!inbox) throw new Error('알림을 불러오지 못했습니다.');
        if (current !== revision || !isCurrentOwner(account)) return;
        items.value = inbox.items;
        unreadCount.value = inbox.unreadCount;
        loaded.value = true;
      } catch (cause) {
        if (current === revision && isCurrentOwner(account)) {
          error.value = cause instanceof Error ? cause.message : '알림을 불러오지 못했습니다.';
        }
      } finally {
        if (current === revision) { loading.value = false; request = null; }
      }
    })();
    request = operation;
    return operation;
  }

  async function markRead(id: number): Promise<boolean> {
    const account = owner.value;
    if (!isCurrentOwner(account) || markingAll.value || pending.value.has(id)) return false;
    const item = items.value.find(item => item.id === id);
    if (!item) return false;
    if (item.readAt) return true;
    pending.value.add(id);
    error.value = '';
    const dat = sessionService.getDat();
    try {
      await inboxRequest(`/${id}/read`, 'PUT', dat);
      if (!isCurrentOwner(account)) return false;
      // A list request started before this write must not restore the unread state.
      revision++;
      request = null;
      loading.value = false;
      const currentItem = items.value.find(item => item.id === id);
      if (currentItem && !currentItem.readAt) {
        currentItem.readAt = new Date().toISOString();
        unreadCount.value = Math.max(0, unreadCount.value - 1);
      }
      void load(true);
      return true;
    } catch (cause) {
      if (isCurrentOwner(account)) {
        error.value = cause instanceof Error ? cause.message : '알림을 읽음 처리하지 못했습니다. 다시 눌러 주세요.';
      }
      return false;
    } finally {
      if (isCurrentOwner(account)) pending.value.delete(id);
    }
  }

  async function markAllRead(): Promise<void> {
    const account = owner.value;
    if (!isCurrentOwner(account) || markingAll.value || pending.value.size || !unreadCount.value) return;
    markingAll.value = true;
    error.value = '';
    const dat = sessionService.getDat();
    try {
      await inboxRequest('/read', 'PUT', dat);
      if (!isCurrentOwner(account)) return;
      revision++;
      request = null;
      loading.value = false;
      const readAt = new Date().toISOString();
      items.value.forEach(item => { item.readAt ||= readAt; });
      unreadCount.value = 0;
      void load(true);
    } catch (cause) {
      if (isCurrentOwner(account)) {
        error.value = cause instanceof Error ? cause.message : '알림을 모두 읽음 처리하지 못했습니다.';
      }
    } finally {
      if (isCurrentOwner(account)) markingAll.value = false;
    }
  }

  watch(owner, () => {
    revision++;
    request = null;
    items.value = [];
    unreadCount.value = 0;
    loaded.value = false;
    loading.value = false;
    error.value = '';
    pending.value = new Set();
    markingAll.value = false;
    void load();
  }, {immediate: true});

  return {items, unreadCount, loaded, loading, error, pending, markingAll, load, markRead, markAllRead};
});
