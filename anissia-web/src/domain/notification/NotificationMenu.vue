<template>
  <div ref="root" class="notification-menu" @focusout="onFocusOut">
    <button ref="trigger" type="button" class="nav-icon notification-trigger" :class="{'is-on': open || inbox.unreadCount > 0}"
            :aria-label="inbox.unreadCount ? `알림, 읽지 않은 알림 ${inbox.unreadCount}개` : '알림'"
            :aria-expanded="open" :aria-controls="panelId" title="알림" @click="toggle">
      <span class="notification-icon">
        <as-icon name="bell"/>
        <span v-if="inbox.unreadCount > 0" class="notification-badge" aria-hidden="true">{{inbox.unreadCount > 99 ? '99+' : inbox.unreadCount}}</span>
      </span>
    </button>

    <section v-if="open" :id="panelId" class="notification-panel as-menu-panel" :aria-labelledby="`${panelId}-title`">
      <div class="notification-heading">
        <h2 :id="`${panelId}-title`" class="notification-title">
          <router-link to="/notifications" @click="close()">알림 <as-icon name="chevron-right"/></router-link>
          <span v-if="inbox.unreadCount" class="notification-unread" :aria-label="`읽지 않은 알림 ${inbox.unreadCount}개`">{{inbox.unreadCount}}</span>
        </h2>
        <div class="notification-heading-actions">
          <button type="button" class="as-link text-xs disabled:opacity-40" :disabled="!inbox.unreadCount || inbox.markingAll || inbox.pending.size > 0" @click="inbox.markAllRead()">{{inbox.markingAll ? '처리 중…' : '모두 읽음'}}</button>
          <button type="button" class="nav-icon notification-close" aria-label="알림 목록 닫기" @click="close(true)"><as-icon name="x"/></button>
        </div>
      </div>
      <div class="notification-scroll">
        <NotificationInboxList :items="recentItems" :loading="inbox.loading" :loaded="inbox.loaded" :error="inbox.error"
                               :pending="inbox.pending" :marking-all="inbox.markingAll" compact @visit="visit" @retry="inbox.load(true)"/>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import {computed, onMounted, onUnmounted, ref, useId, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {notificationInboxStore, type NotificationInboxItem} from './notificationInboxStore';
import NotificationInboxList from './NotificationInboxList.vue';

const emit = defineEmits<{open: []}>();
const inbox = notificationInboxStore();
const router = useRouter();
const route = useRoute();
const panelId = `notification-inbox-${useId()}`;
const root = ref<HTMLElement | null>(null);
const trigger = ref<HTMLButtonElement | null>(null);
const open = ref(false);
const recentItems = computed(() => inbox.items.slice(0, 5));
let timer: ReturnType<typeof setInterval> | undefined;

function close(restoreFocus = false) {
  open.value = false;
  if (restoreFocus) trigger.value?.focus();
}

function toggle() {
  open.value = !open.value;
  if (open.value) { emit('open'); void inbox.load(true); }
}

async function visit(item: NotificationInboxItem) {
  if (!await inbox.markRead(item.id)) return;
  close();
  await router.push(`/anime/${item.animeNo}`);
}

function onPointerDown(event: PointerEvent) {
  if (event.target instanceof Node && !root.value?.contains(event.target)) close();
}

function onKeyDown(event: KeyboardEvent) {
  if (open.value && event.key === 'Escape') { event.preventDefault(); close(true); }
}

function onFocusOut(event: FocusEvent) {
  if (event.relatedTarget instanceof Node && !root.value?.contains(event.relatedTarget)) close();
}

function refreshWhenVisible() {
  if (document.visibilityState === 'visible') void inbox.load(true);
}

watch(() => route.fullPath, () => close());

onMounted(() => {
  void inbox.load();
  timer = setInterval(refreshWhenVisible, 60000);
  document.addEventListener('pointerdown', onPointerDown);
  document.addEventListener('keydown', onKeyDown);
  document.addEventListener('visibilitychange', refreshWhenVisible);
  window.addEventListener('focus', refreshWhenVisible);
});

onUnmounted(() => {
  clearInterval(timer);
  document.removeEventListener('pointerdown', onPointerDown);
  document.removeEventListener('keydown', onKeyDown);
  document.removeEventListener('visibilitychange', refreshWhenVisible);
  window.removeEventListener('focus', refreshWhenVisible);
});
</script>

<style scoped>
.notification-menu { position: static; }
.notification-trigger { position: relative; }
.notification-icon { position: relative; display: inline-flex; align-items: center; justify-content: center; width: 1.25em; height: 1em; }
.notification-badge { position: absolute; top: -.375rem; left: calc(100% - .5rem); display: grid; place-items: center; min-width: .875rem; height: .875rem; padding-inline: .1875rem; border-radius: var(--radius-pill); background: var(--as-danger); color: var(--as-canvas); font-size: .625rem; line-height: 1; font-weight: 700; box-shadow: 0 0 0 .09375rem var(--as-canvas); }
.notification-panel.as-menu-panel { position: absolute; z-index: 50; top: calc(100% + .5rem); right: 0; width: min(24rem, calc(100vw - 2rem)); max-width: calc(100vw - 2rem); max-height: calc(100dvh - 5rem); color: var(--as-ink); }
.notification-heading { display: flex; align-items: center; justify-content: space-between; gap: .75rem; padding: .5rem .5rem .5rem .875rem; border-bottom: .0625rem solid var(--as-line); }
.notification-title { display: flex; align-items: center; gap: .375rem; min-width: 0; font-size: .875rem; font-weight: 600; }
.notification-title > a { display: inline-flex; align-items: center; gap: .375rem; min-height: 2rem; color: var(--as-ink); }
.notification-title > a > i { font-size: .625rem; color: var(--as-ink-3); }
.notification-unread { color: var(--as-brand); font-size: .75rem; font-variant-numeric: tabular-nums; }
.notification-heading-actions { display: flex; align-items: center; gap: .375rem; flex-shrink: 0; }
.notification-close { width: 2rem; height: 2rem; font-size: .8125rem; }
.notification-scroll { max-height: min(28rem, calc(100dvh - 8rem)); overflow-y: auto; overscroll-behavior: contain; }
@media (hover: hover) and (pointer: fine) {
  .notification-title > a:hover { color: var(--as-brand); }
}
</style>
