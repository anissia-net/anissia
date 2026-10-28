<template>
  <div class="notification-inbox" :class="{'is-compact': compact}" :aria-busy="loading">
    <div v-if="error" class="notification-message" :class="{'as-box mb-4': !compact}" role="alert">
      <p class="text-danger text-sm">{{error}}</p>
      <button type="button" class="as-link text-sm mt-2" :disabled="loading" @click="emit('retry')">다시 불러오기</button>
    </div>
    <p v-if="loading && !loaded" class="notification-empty" :class="{'as-box': !compact}" role="status">알림을 불러오고 있습니다.</p>
    <div v-else-if="loaded && !items.length" class="notification-empty" :class="{'as-box': !compact}">
      <as-icon name="bell" class="notification-empty-icon"/>
      <p class="text-ink-2">아직 도착한 알림이 없습니다.</p>
      <p class="as-meta mt-1">작품에서 ‘알림 받기’를 켜면 새 자막 소식을 알려드립니다.</p>
    </div>
    <ul v-else class="notification-list" :class="{'grid gap-4 cq-md:grid-cols-2 cq-xl:grid-cols-3': !compact}">
      <li v-for="item in items" :key="item.id" class="min-w-0">
        <router-link :to="`/anime/${item.animeNo}`" class="notification-item" :class="{'as-card': !compact, 'is-unread': !item.readAt}"
                     :aria-disabled="busy(item) || undefined" :tabindex="busy(item) ? -1 : undefined"
                     @click.capture="visit(item, $event)" @auxclick.capture="visit(item, $event)">
          <span class="notification-dot" :class="{'is-unread': !item.readAt}" aria-hidden="true"></span>
          <span class="notification-copy">
            <span v-if="!item.readAt" class="sr-only">읽지 않음. </span>
            <span class="notification-subject">{{item.subject}}</span>
            <span class="notification-summary">{{item.episode === '0' ? '단편' : `${item.episode}화`}} 자막이 등록되었습니다.</span>
            <span class="notification-meta as-meta">
              <span>{{item.translatorName}}</span>
              <time :datetime="item.createdAt" :title="dateText(item.createdAt)">{{timeText(item.createdAt)}}</time>
              <span v-if="pending.has(item.id)" role="status">이동 중…</span>
            </span>
          </span>
          <as-icon name="chevron-right" class="notification-arrow"/>
        </router-link>
      </li>
    </ul>
  </div>
</template>

<script setup lang="ts">
import type {NotificationInboxItem} from './notificationInboxStore';

const props = withDefaults(defineProps<{
  items: NotificationInboxItem[];
  loading: boolean;
  loaded: boolean;
  error: string;
  pending: Set<number>;
  markingAll: boolean;
  compact?: boolean;
}>(), {compact: false});
const emit = defineEmits<{visit: [item: NotificationInboxItem]; retry: []}>();

function busy(item: NotificationInboxItem) {
  return props.markingAll || props.pending.has(item.id);
}

function visit(item: NotificationInboxItem, event: MouseEvent) {
  if (busy(item)) { event.preventDefault(); return; }
  if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
  event.preventDefault();
  emit('visit', item);
}

function dateText(value: string): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '' : date.toLocaleString('ko-KR');
}

function timeText(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '';
  const minutes = Math.max(0, Math.floor((Date.now() - date.getTime()) / 60000));
  if (minutes < 1) return '방금 전';
  if (minutes < 60) return `${minutes}분 전`;
  if (minutes < 1440) return `${Math.floor(minutes / 60)}시간 전`;
  if (minutes < 10080) return `${Math.floor(minutes / 1440)}일 전`;
  return date.toLocaleDateString('ko-KR');
}
</script>

<style scoped>
.notification-inbox { min-width: 0; }
.notification-message { padding: .875rem 1rem; }
.notification-empty { padding: 2rem 1.25rem; text-align: center; font-size: .8125rem; color: var(--as-ink-3); }
.notification-empty-icon { display: inline-block; margin-bottom: .625rem; font-size: 1.375rem; color: var(--as-ink-3); }
.notification-item { display: flex; align-items: center; gap: .75rem; min-width: 0; height: 100%; padding: 1.25rem; text-align: start; color: var(--as-ink); }
.notification-item[aria-disabled="true"] { cursor: wait; opacity: .6; }
.notification-item:focus-visible { outline: .125rem solid var(--as-brand); outline-offset: -.1875rem; border-radius: var(--radius-card); }
.notification-copy { display: flex; flex-direction: column; align-self: stretch; flex: 1; min-width: 0; }
.notification-subject { display: block; overflow-wrap: anywhere; font-size: var(--text-md); font-weight: 600; line-height: 1.7; transition: color .15s; }
.notification-summary { display: block; margin-top: .375rem; font-size: .8125rem; line-height: 1.6; color: var(--as-ink-2); }
.notification-meta { display: flex; flex-wrap: wrap; gap: .125rem .5rem; margin-top: auto; padding-top: 1rem; overflow-wrap: anywhere; }
.notification-meta > time { white-space: nowrap; }
.notification-dot { align-self: flex-start; flex: none; width: .375rem; height: .375rem; margin-top: .5rem; border-radius: 50%; }
.notification-dot.is-unread { background: var(--as-brand); }
.notification-arrow { flex: none; font-size: .75rem; color: var(--as-ink-3); }
.is-compact .notification-message { padding: .75rem; }
.is-compact .notification-empty { padding: 1.5rem .875rem; }
.is-compact .notification-list > li + li { border-top: .0625rem solid var(--as-line); }
.is-compact .notification-item { gap: .5rem; padding: .75rem; transition: background-color .15s; }
.is-compact .notification-item.is-unread { background: color-mix(in srgb, var(--as-brand-soft) 65%, transparent); }
.is-compact .notification-item:focus-visible { border-radius: var(--radius-ctl); }
.is-compact .notification-subject { font-size: .8125rem; line-height: 1.5; }
.is-compact .notification-summary { margin-top: .125rem; font-size: .75rem; }
.is-compact .notification-meta { margin-top: .25rem; padding-top: 0; }
@media (hover: hover) and (pointer: fine) {
  .notification-item:not([aria-disabled="true"]):hover .notification-subject { color: var(--as-brand); }
  .is-compact .notification-item:not([aria-disabled="true"]):hover { background: var(--as-muted); }
}
</style>
