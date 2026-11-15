<template>
  <section v-if="supported" class="as-box native-notification-settings" aria-label="이 기기의 앱 알림">
    <div class="native-notification-description">
      <h2 class="font-semibold text-sm">이 기기의 앱 알림</h2>
      <p class="as-desc mt-1" role="status">{{description}}</p>
      <p v-if="error" class="text-danger text-sm mt-2" role="alert">{{error}}</p>
    </div>
    <button type="button" :class="active ? 'as-input-btn' : 'as-btn-primary'"
            class="px-4 py-2.5 shrink-0" :disabled="busy || loading || (state && !state.configured)"
            :aria-pressed="active" @click="toggle">{{busy ? '변경 중…' : active ? '앱 알림 끄기' : '앱 알림 받기'}}</button>
  </section>
</template>

<script setup lang="ts">
import {computed, onMounted, onUnmounted, ref} from 'vue';
import {sessionStore} from '../session/sessionStore';
import sessionService from '../session/remote/sessionService';
import Session from '../session/Session';
import {clearNativePush, enableNativePush, nativePushStatus, nativePushSupported} from '../push/nativePushDevice';
import type {MobileNotificationState} from '../../common/mobileBridge';

const session = sessionStore();
const supported = ref(nativePushSupported());
const state = ref<MobileNotificationState | null>(null);
const busy = ref(false);
const loading = ref(false);
const error = ref('');
let revision = 0;
const active = computed(() => !!state.value?.enabled && state.value.permission === 'granted'
  && state.value.owner === session.user.email && !!state.value.bindingId);
const description = computed(() => {
  if (loading.value && !state.value) return '앱 알림 설정을 확인하고 있습니다.';
  if (state.value && !state.value.configured) return '이 버전에서는 앱 알림을 연결할 수 없습니다.';
  if (state.value?.permission === 'denied') return 'Android 설정에서 애니시아의 알림 권한을 허용해 주세요.';
  return active.value ? '선택한 작품의 새 자막 소식을 앱을 닫아도 알려드립니다.'
    : '알림을 켜면 선택한 작품의 새 자막 소식을 Android 알림으로 받습니다.';
});

async function refresh() {
  supported.value = nativePushSupported();
  if (!supported.value || busy.value) return;
  const current = ++revision;
  loading.value = true;
  try {
    const value = await nativePushStatus();
    if (current === revision) { state.value = value; error.value = ''; }
  } catch (cause) {
    if (current === revision) error.value = cause instanceof Error ? cause.message : '앱 알림 설정을 확인하지 못했습니다.';
  } finally { if (current === revision) loading.value = false; }
}

async function toggle() {
  if (busy.value || !session.user.isLogin) return;
  const owner = session.user.email;
  const dat = sessionService.getDat();
  const isCurrent = () => {
    const cookie = Session.assign(sessionService.getDat());
    return session.user.isLogin && session.user.email === owner && cookie.isLogin && cookie.email === owner;
  };
  if (!isCurrent()) return;
  busy.value = true;
  error.value = '';
  revision++;
  try {
    if (active.value) {
      await clearNativePush(dat);
      if (isCurrent()) state.value = await nativePushStatus();
    } else {
      const result = await enableNativePush(owner, dat, isCurrent);
      if (isCurrent()) state.value = result;
    }
  } catch (cause) {
    if (isCurrent()) error.value = cause instanceof Error ? cause.message : '앱 알림을 변경하지 못했습니다.';
  } finally { busy.value = false; }
}

onMounted(() => {
  void refresh();
  window.addEventListener('focus', refresh);
  window.addEventListener('anissia-mobile-ready', refresh);
  window.addEventListener('anissia-mobile-notifications-changed', refresh);
  window.addEventListener('anissia-mobile-resumed', refresh);
});
onUnmounted(() => {
  revision++;
  window.removeEventListener('focus', refresh);
  window.removeEventListener('anissia-mobile-ready', refresh);
  window.removeEventListener('anissia-mobile-notifications-changed', refresh);
  window.removeEventListener('anissia-mobile-resumed', refresh);
});
</script>

<style scoped>
.native-notification-settings { display: flex; align-items: center; gap: 1rem; padding: 1rem; margin-bottom: 1rem; }
.native-notification-description { flex: 1; min-width: 0; }
@container site (width < 30rem) {
  .native-notification-settings { align-items: stretch; flex-direction: column; }
}
</style>
