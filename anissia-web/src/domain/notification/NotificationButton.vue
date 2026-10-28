<template>
  <button type="button" class="notification-button" :class="{'is-enabled': enabled, 'is-icon-only': iconOnly}"
          :aria-pressed="enabled" :aria-label="`${subject || '작품'} ${enabled ? '알림 끄기' : '알림 받기'}`"
          :title="iconOnly ? (enabled ? '알림 끄기' : '알림 받기') : undefined"
          :disabled="busy || notifications.loading || notifications.pending.has(animeNo)" @click.stop="toggle">
    <as-icon name="bell"/><span v-if="!iconOnly">{{ enabled ? '알림 끄기' : '알림 받기' }}</span>
  </button>
</template>

<script setup lang="ts">
import {computed, ref} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {sessionStore} from '../session/sessionStore';
import sessionService from '../session/remote/sessionService';
import Session from '../session/Session';
import {ensurePushSubscription, requestBrowserPushPermission} from '../push/pushSubscription';
import {notificationStore} from './notificationStore';
import toast from '../../common/toast';

const props = defineProps<{animeNo: number, subject?: string, iconOnly?: boolean}>();
const session = sessionStore();
const notifications = notificationStore();
const router = useRouter();
const route = useRoute();
const busy = ref(false);
const enabled = computed(() => notifications.isEnabled(props.animeNo));

async function toggle() {
  if (!session.user.isLogin) {
    await router.push(`/login?path=${encodeURIComponent(route.fullPath)}`);
    return;
  }
  if (busy.value) return;
  busy.value = true;
  const animeNo = props.animeNo;
  const account = session.user.email;
  const dat = sessionService.getDat();
  const isCurrent = () => {
    const credential = Session.assign(sessionService.getDat());
    return session.user.isLogin && session.user.email === account && credential.isLogin && credential.email === account;
  };
  try {
    if (!isCurrent()) throw new Error('로그인 정보가 변경되었습니다. 페이지를 새로고침해 주세요.');
    // Start the native prompt in this click, before the work save can yield.
    // Handle rejection immediately even if saving the work fails first.
    const permission = !enabled.value
      ? requestBrowserPushPermission().then(value => ({value, error: null}), error => ({value: null, error}))
      : null;
    await notifications.toggle(animeNo);
    if (!permission || !isCurrent() || !notifications.isEnabled(animeNo)) return;
    const result = await permission;
    if (!isCurrent() || !notifications.isEnabled(animeNo)) return;
    if (result.error) throw result.error;
    if (result.value === 'denied') {
      toast.error('브라우저 알림이 차단되어 있습니다. 사이트 설정에서 알림을 허용해 주세요.');
    } else if (result.value === 'granted') {
      await ensurePushSubscription(account, dat, isCurrent);
    }
  }
  catch (error) { toast.error(error instanceof Error ? error.message : '알림 설정을 변경하지 못했습니다.'); }
  finally { busy.value = false; }
}
</script>

<style scoped>
.notification-button { display: inline-flex; align-items: center; justify-content: center; gap: .4em; padding: .4em .7em; border: 1px solid currentColor; border-radius: .65rem; font-size: .75rem; line-height: 1.5; color: inherit; opacity: .75; cursor: pointer; }
.notification-button.is-enabled { opacity: 1; color: var(--as-brand); background: var(--as-brand-soft); }
.notification-button.is-icon-only { min-width: 2rem; height: calc(1.6em + .75rem); padding: .375rem .625rem; border: 0; border-radius: .25rem; background: var(--as-muted); color: var(--as-ink-3); opacity: 1; transition: color .15s, background-color .15s, box-shadow .15s; }
.notification-button.is-icon-only:hover:not(:disabled) { color: var(--as-ink-2); background: var(--as-line); }
.notification-button.is-icon-only.is-enabled { color: var(--as-brand); background: var(--as-brand-soft); box-shadow: inset 0 0 0 1px color-mix(in oklab, var(--as-brand) 30%, transparent); }
.notification-button:disabled { opacity: .4; cursor: wait; }
.notification-button:focus-visible { outline: 2px solid currentColor; outline-offset: 3px; }
</style>
