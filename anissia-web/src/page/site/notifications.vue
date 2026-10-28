<template>
  <div class="as-page">
    <div class="as-page-head">
      <h1 class="as-page-title">알림</h1>
      <p class="as-desc">새로 도착한 자막 소식과 작품별 알림을 확인하세요.</p>
    </div>

    <div v-if="!session.user.isLogin" class="as-box notifications-empty">
      <as-icon name="bell" class="notifications-empty-icon"/>
      <p class="as-desc">로그인하면 자막 알림을 확인하고 작품별 알림을 설정할 수 있습니다.</p>
      <router-link to="/login?path=%2Fnotifications" class="as-btn-primary inline-flex mt-4 py-2.5">로그인하고 알림 받기</router-link>
    </div>
    <template v-else>
      <div class="notifications-toolbar">
        <div v-choice class="as-segment notifications-tabs" role="tablist" aria-label="알림 보기">
          <button v-for="option in tabs" :key="option.value" :id="`notification-tab-${option.value}`" type="button" role="tab"
                  :class="{'is-on': activeTab === option.value}" :aria-selected="activeTab === option.value"
                  :aria-controls="`notification-panel-${option.value}`" :tabindex="activeTab === option.value ? 0 : -1"
                  @click="selectTab(option.value)" @keydown="onTabKeydown($event)">{{option.label}}</button>
        </div>
        <button v-if="activeTab === 'inbox'" type="button" class="as-link notifications-read-all"
                :disabled="!inbox.unreadCount || inbox.markingAll || inbox.pending.size > 0"
                @click="inbox.markAllRead()">{{inbox.markingAll ? '처리 중…' : '모두 읽음'}}</button>
      </div>

      <section v-show="activeTab === 'inbox'" id="notification-panel-inbox" role="tabpanel" aria-labelledby="notification-tab-inbox" tabindex="0">
        <NotificationInboxList :items="inbox.items" :loading="inbox.loading" :loaded="inbox.loaded" :error="inbox.error"
                               :pending="inbox.pending" :marking-all="inbox.markingAll" @visit="visit" @retry="inbox.load(true)"/>
      </section>

      <section v-show="activeTab === 'settings'" id="notification-panel-settings" role="tabpanel" aria-labelledby="notification-tab-settings" tabindex="0">
        <p class="as-desc notifications-settings-description">작품의 ‘알림 받기’를 누르면 새 자막 소식을 알려드립니다.</p>
        <div v-if="notifications.error" class="as-box notifications-error" role="alert">
          <p class="text-danger text-sm">{{notifications.error}}</p>
          <button type="button" class="as-link text-sm mt-2" :disabled="notifications.loading" @click="notifications.load(true)">다시 불러오기</button>
        </div>
        <p v-if="notifications.loading && !notifications.loaded" class="as-box notifications-empty as-desc" role="status">알림 설정을 불러오고 있습니다.</p>
        <div v-else-if="notifications.loaded && !notifications.items.length" class="as-box notifications-empty">
          <as-icon name="bell" class="notifications-empty-icon"/>
          <p class="as-desc">아직 알림 설정한 작품이 없습니다.</p>
          <router-link to="/anime" class="as-link inline-block text-sm mt-3">작품을 찾아 알림 받기</router-link>
        </div>
        <ul v-else class="grid gap-4 cq-md:grid-cols-2 cq-xl:grid-cols-3" :aria-busy="notifications.loading">
          <li v-for="item in sorted" :key="item.animeNo" class="as-card p-5 min-w-0">
            <article class="flex h-full flex-col">
              <div class="notifications-work-heading">
                <div class="min-w-0">
                  <router-link :to="`/anime/${item.animeNo}`" class="notifications-work-title">{{item.subject}}</router-link>
                  <p v-if="item.originalSubject" class="notifications-original" lang="ja">{{item.originalSubject}}</p>
                </div>
                <NotificationButton :anime-no="item.animeNo" :subject="item.subject" class="shrink-0"/>
              </div>
              <div class="notifications-work-meta as-meta">
                <span>{{item.status === 'END' ? '완결' : item.status === 'OFF' ? '결방' : '최신 자막'}}</span>
                <span :class="{'text-brand': item.notify}">알림 {{item.notify ? '켜짐' : '꺼짐'}}</span>
              </div>
              <ul v-if="item.captions.length" class="notifications-captions">
                <li v-for="caption in item.captions" :key="caption.name">
                  <a v-if="caption.website" :href="caption.website" target="_blank" rel="noopener noreferrer" class="notifications-caption">
                    <span class="as-tag-xs shrink-0">{{caption.episodeText}}</span>
                    <span class="notifications-translator">{{caption.name}}</span>
                    <span class="as-meta notifications-caption-time">{{caption.updDtText}}</span>
                  </a>
                  <div v-else class="notifications-caption text-ink-3"><span class="as-tag-xs shrink-0">준비중</span><span>{{caption.name}}</span></div>
                </li>
              </ul>
              <p v-else class="as-meta mt-2">아직 등록된 자막 제작자가 없습니다.</p>
            </article>
          </li>
        </ul>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {sessionStore} from '../../domain/session/sessionStore';
import {notificationStore, type NotificationItem} from '../../domain/notification/notificationStore';
import {notificationInboxStore, type NotificationInboxItem} from '../../domain/notification/notificationInboxStore';
import NotificationButton from '../../domain/notification/NotificationButton.vue';
import NotificationInboxList from '../../domain/notification/NotificationInboxList.vue';

const session = sessionStore();
const notifications = notificationStore();
const inbox = notificationInboxStore();
const route = useRoute();
const router = useRouter();
const tabs = [{value: 'inbox', label: '알림 목록'}, {value: 'settings', label: '알림 설정'}] as const;
type NotificationTab = typeof tabs[number]['value'];
const activeTab = computed<NotificationTab>(() => route.query.tab === 'settings' ? 'settings' : 'inbox');

function selectTab(value: NotificationTab) {
  const {tab, ...query} = route.query;
  return router.push({path: '/notifications', query: value === 'settings' ? {...query, tab: value} : query});
}

async function onTabKeydown(event: KeyboardEvent) {
  let next: NotificationTab;
  if (event.key === 'Home') next = 'inbox';
  else if (event.key === 'End') next = 'settings';
  else if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') next = activeTab.value === 'inbox' ? 'settings' : 'inbox';
  else return;
  event.preventDefault();
  const tablist = (event.currentTarget as HTMLElement).parentElement;
  await selectTab(next);
  await nextTick();
  tablist?.querySelector<HTMLButtonElement>(`#notification-tab-${next}`)?.focus();
}

async function visit(item: NotificationInboxItem) {
  if (await inbox.markRead(item.id)) await router.push(`/anime/${item.animeNo}`);
}

function latest(item: NotificationItem): string {
  return item.captions.filter(caption => caption.website).reduce((value, caption) => caption.updDt > value ? caption.updDt : value, '');
}
const sorted = computed(() => [...notifications.items].sort((a, b) => latest(b).localeCompare(latest(a)) || a.subject.localeCompare(b.subject, 'ko')));
watch(activeTab, value => { void (value === 'settings' ? notifications.load() : inbox.load()); }, {immediate: true});
</script>

<style scoped>
.notifications-toolbar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: .75rem; margin-bottom: 1rem; }
.notifications-tabs { width: min(100%, 20rem); }
.notifications-read-all { padding: .375rem .25rem; font-size: .75rem; }
.notifications-read-all:disabled { opacity: .4; cursor: default; }
.notifications-settings-description { margin-bottom: 1rem; }
.notifications-error { margin-bottom: 1rem; padding: 1rem; }
.notifications-empty { padding: 2.5rem 1rem; text-align: center; }
.notifications-empty-icon { display: inline-block; margin-bottom: .75rem; color: var(--as-ink-3); font-size: 1.5rem; }
.notifications-work-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: .75rem; }
.notifications-work-title { color: var(--as-ink); font-size: var(--text-md); font-weight: 600; line-height: 1.7; overflow-wrap: anywhere; transition: color .15s; }
.notifications-original { margin-top: .375rem; color: var(--as-ink-3); font-size: .75rem; overflow-wrap: anywhere; }
.notifications-work-meta { display: flex; flex-wrap: wrap; gap: .25rem .75rem; margin-top: auto; padding-top: 1rem; }
.notifications-captions { margin-top: .5rem; }
.notifications-caption { display: flex; align-items: center; flex-wrap: wrap; gap: .375rem .625rem; padding-block: .375rem; font-size: .8125rem; }
.notifications-translator { min-width: 0; color: var(--as-brand); font-weight: 500; overflow-wrap: anywhere; }
.notifications-caption-time { margin-inline-start: auto; white-space: nowrap; }
@container site (width < 30rem) {
  .notifications-tabs { width: auto; flex: 1; }
}
@media (hover: hover) and (pointer: fine) {
  .notifications-work-title:hover { color: var(--as-brand); }
}
</style>
