import {computed, ref, watch} from 'vue';
import {defineStore} from 'pinia';
import {sessionStore} from '../session/sessionStore';
import sessionService from '../session/remote/sessionService';
import Session from '../session/Session';
import AnimeCaption from '../anime/AnimeCaption';
import ajax from '../../common/ajax';

export interface NotificationItem {
  animeNo: number;
  subject: string;
  originalSubject: string;
  status: string;
  week: string;
  time: string;
  notify: boolean;
  captions: AnimeCaption[];
}

const api = import.meta.env.VITE_API_PATH;
const maxNotificationWorks = 200;

export const notificationStore = defineStore('notifications', () => {
  const session = sessionStore();
  const owner = computed(() => session.user.isLogin ? session.user.email : '');
  const items = ref<NotificationItem[]>([]);
  const loading = ref(false);
  const loaded = ref(false);
  const error = ref('');
  const pending = ref(new Set<number>());
  let revision = 0;
  let request: Promise<void> | null = null;

  function isCurrentOwner(account: string): boolean {
    const credential = Session.assign(sessionService.getDat());
    return !!account && account === owner.value && credential.isLogin && credential.email === account;
  }

  function isEnabled(animeNo: number) { return items.value.some(item => item.animeNo === animeNo && item.notify); }

  async function load(force = false): Promise<void> {
    if (!owner.value) return;
    if (request && !force) return request;
    if (loaded.value && !force) return;
    const account = owner.value;
    if (!isCurrentOwner(account)) return;
    const current = ++revision;
    loading.value = true;
    error.value = '';
    const operation = (async () => {
      try {
        const response = await ajax.fetch(`${api}/notification`, null, {cache: 'no-store'});
        if (!response?.success) throw new Error(response?.message || '알림 목록을 불러오지 못했습니다.');
        if (current !== revision || !isCurrentOwner(account)) return;
        items.value = (response.data as NotificationItem[]).map(item => ({
          ...item, captions: (item.captions || []).map(AnimeCaption.assign),
        }));
        loaded.value = true;
      } catch (cause) {
        if (current !== revision || !isCurrentOwner(account)) return;
        error.value = cause instanceof Error ? cause.message : '알림 목록을 불러오지 못했습니다.';
      } finally {
        if (current === revision) { loading.value = false; request = null; }
      }
    })();
    request = operation;
    return operation;
  }

  async function change(animeNo: number, notify: boolean | null) {
    if (!owner.value || pending.value.has(animeNo)) return;
    const account = owner.value;
    if (!isCurrentOwner(account)) throw new Error('로그인 정보가 변경되었습니다. 페이지를 새로고침해 주세요.');
    if (notify !== null && loaded.value && !items.value.some(item => item.animeNo === animeNo) && items.value.length >= maxNotificationWorks) {
      throw new Error(`알림을 받을 작품은 최대 ${maxNotificationWorks}개까지 추가할 수 있습니다.`);
    }
    pending.value.add(animeNo);
    try {
      const result = notify === null
        ? await ajax.delete(`${api}/notification/${animeNo}`)
        : await ajax.put(`${api}/notification/${animeNo}`, {notify});
      if (!result?.success) throw new Error(result?.message || '알림 설정을 변경하지 못했습니다.');
      if (!isCurrentOwner(account)) return;
      if (notify === null) items.value = items.value.filter(item => item.animeNo !== animeNo);
      else {
        const item = items.value.find(item => item.animeNo === animeNo);
        if (item) item.notify = notify;
      }
      await load(true);
    } finally {
      if (account === owner.value) pending.value.delete(animeNo);
    }
  }

  async function toggle(animeNo: number) {
    const account = owner.value;
    await load();
    if (!isCurrentOwner(account)) return;
    if (!loaded.value) throw new Error(error.value || '알림 목록을 먼저 불러와 주세요.');
    return change(animeNo, isEnabled(animeNo) ? null : true);
  }

  watch(owner, () => {
    revision++;
    request = null;
    items.value = [];
    pending.value = new Set();
    loading.value = false;
    loaded.value = false;
    error.value = '';
    void load();
  }, {immediate: true});

  return {items, loading, loaded, error, pending, isEnabled, load, change, toggle};
});
