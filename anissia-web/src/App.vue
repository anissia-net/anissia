<template>
  <router-view/>
</template>

<script setup lang="ts">
import theme from "./common/theme";
import sessionService from "./domain/session/remote/sessionService";
import {useRouter} from "vue-router";
import {sessionStore} from "./domain/session/sessionStore";
import {onBeforeMount, onUnmounted, watch} from "vue";
import {syncPushOwner} from './domain/push/pushDevice';
import Session from './domain/session/Session';
import toast from './common/toast';

const router = useRouter();
const session = sessionStore();

theme.apply()

function syncNotifications() {
  const owner = session.user.isLogin ? session.user.email : '';
  return syncPushOwner(owner, () => {
    const cookie = Session.assign(sessionService.getDat());
    return owner === (cookie.isLogin ? cookie.email : '');
  }).catch(error => toast.error(error instanceof Error ? error.message : '브라우저 알림 설정을 확인해 주세요.'));
}

watch(() => session.user.email, () => {
  // An expired DAT may still be followed by a successful automatic login.
  // The completed sync/logout handles the signed-out case.
  if (session.user.isLogin) void syncNotifications();
});

let syncing: Promise<void> | null = null;
function sync() {
  if (syncing) return syncing;
  syncing = sessionService.sync().then(async () => {
    await syncNotifications();
    sessionService.amendPathBySession(location.pathname, router);
  }).catch(() => {}).finally(() => { syncing = null; });
  return syncing;
}

function reload() {
  location.reload();
}

window['exReload'] = () => { reload(); };

let timer: ReturnType<typeof setInterval>;
const onFocus = () => { void sync(); };
onBeforeMount(() => {
  timer = setInterval(onFocus, 60000);
  window.addEventListener('focus', onFocus);
  void sync();
});
onUnmounted(() => { clearInterval(timer); window.removeEventListener('focus', onFocus); });
</script>
