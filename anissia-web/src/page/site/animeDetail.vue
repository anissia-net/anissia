<template>
  <div class="as-page">
    <p v-if="loading" class="as-empty" role="status">애니메이션을 불러오는 중입니다.</p>
    <div v-else-if="error" class="as-empty" role="alert">{{error}} <button class="as-link" @click="load">다시 시도</button></div>
    <div v-if="anime" class="mb-12">
      <article v-if="anime.animeNo != 0" class="as-box anime-detail" aria-labelledby="anime-title">

        <div class="p-6 cq-md:p-8">
          <div class="flex flex-wrap items-center gap-x-3 gap-y-2">
            <span class="status-pill" :class="{'is-on': anime.status == 'ON'}">
              <span class="dot"></span>
              <span v-if="anime.status == 'ON' && anime.pureWeek">방영중 · 매주 ({{anime.weekText}}) {{anime.timeText}}</span>
              <span v-else-if="anime.status == 'ON' && !anime.pureWeek">{{anime.statusText}} ({{anime.weekText}})</span>
              <span v-else>{{anime.statusText}}</span>
            </span>
            <NotificationButton :anime-no="anime.animeNo" :subject="anime.subject" icon-only/>
            <span v-if="anime.period" class="as-meta">{{anime.period}}</span>
          </div>

          <div class="flex items-start gap-4 mt-4">
            <img v-if="anime.thumbnail" :src="anime.thumbnail" alt="" width="64" height="64" decoding="async" class="w-16 h-16 shrink-0 rounded-ctl object-cover"/>
            <div class="min-w-0">
              <h1 id="anime-title" class="as-page-title">{{anime.subject}}</h1>
              <div v-if="anime.originalSubject" class="as-desc mt-1.5" lang="ja">{{anime.originalSubject}}</div>
            </div>
          </div>

          <div class="mt-5 flex flex-wrap items-center gap-1.5">
            <router-link class="as-tag-xs" v-for="tag in anime.genres.split(/,/g)" :key="tag" :to="`/anime?q=%23${encodeURIComponent(tag)}`">{{tag}}</router-link>
            <span v-if="anime.website || anime.x" class="w-[0.0625rem] h-4 mx-1 bg-line-2"></span>
            <a v-if="anime.website" :href="anime.website" target="_blank" class="as-tag-xs" :title="anime.website"><as-icon name="globe" class="mr-1.5 opacity-70"/>공식 사이트</a>
            <a v-if="anime.x" :href="anime.x" target="_blank" class="as-tag-xs as-icon-link" :aria-label="`${anime.subject} X 계정 (새 창)`" :title="`${anime.subject} X 계정 (새 창)`"><as-icon name="x-logo" class="opacity-70"/></a>
            <div v-if="wikiSection?.canViewHistory || wikiSection?.canEdit" class="anime-document-actions">
              <span aria-hidden="true">|</span>
              <router-link v-if="wikiSection?.canViewHistory" class="as-link" :to="wikiHistoryLocation({kind: 'anime', targetNo: anime.animeNo})">변경 이력</router-link>
              <span v-if="wikiSection?.canViewHistory && wikiSection?.canEdit" aria-hidden="true">|</span>
              <button v-if="wikiSection?.canEdit" type="button" class="as-link" @click="wikiSection?.beginEdit()">{{wikiSection.hasRevision ? '수정' : '작성'}}</button>
            </div>
          </div>
        </div>

        <section v-if="anime.captions.length" class="anime-captions" aria-label="자막">
          <ul class="caption-list">
            <li v-for="caption in anime.captions" :key="caption.name" class="caption-row" :class="{'is-pending': !caption.website}">
              <router-link :to="`/anime?q=%40${encodeURIComponent(caption.name)}`" class="caption-provider" :aria-label="`${caption.name} 제작자의 애니메이션 검색`">
                {{caption.name}}
              </router-link>
              <component :is="caption.website ? 'a' : 'div'" class="caption-content"
                         :href="caption.website || undefined" :target="caption.website ? '_blank' : undefined"
                         :rel="caption.website ? 'noopener noreferrer' : undefined"
                         :aria-label="caption.website ? `${caption.name} · ${caption.episodeText} 자막 보기 (새 창)` : undefined">
                <span>{{caption.episodeText}}</span>
                <as-icon v-if="caption.website" name="external"/>
              </component>
              <time v-if="caption.updDt" class="caption-updated" :datetime="caption.updDt" :title="caption.updDt">{{caption.updDtText}}</time>
            </li>
          </ul>
        </section>

        <WikiSection :key="anime.animeNo" ref="wikiSection" :anime-no="anime.animeNo" embedded @saved="refreshThumbnail"/>

      </article>
      <div v-else class="as-empty">
        존재하지 않거나 삭제된 애니메이션 입니다.
      </div>

    </div>

  </div>
</template>

<script setup lang="ts">
import {onUnmounted, ref} from 'vue';
import {useRoute} from 'vue-router';
import Anime from '../../domain/anime/Anime';
import NotificationButton from '../../domain/notification/NotificationButton.vue';
import WikiSection from '../../domain/wiki/WikiSection.vue';
import {wikiHistoryLocation} from '../../domain/wiki/wikiLocation';
import animeRemote from '../../domain/anime/remote/animeRemote';
import {animeUpdates} from '../../domain/anime/animeUpdates';

const animeNo = Number(useRoute().params.animeNo);
const anime = ref<Anime | null>(null);
const wikiSection = ref<InstanceType<typeof WikiSection>>();
const loading = ref(false), error = ref('');
let active = true;

async function load() {
  loading.value = true;
  error.value = '';
  try {
    const value = await animeRemote.getAnime(animeNo);
    if (active) anime.value = value;
  } catch {
    if (active) error.value = '애니메이션을 불러오지 못했습니다.';
  } finally { if (active) loading.value = false; }
}

async function refreshThumbnail() {
  try {
    const fresh = await animeRemote.getAnime(animeNo);
    animeUpdates.publish(animeNo, {thumbnail: fresh.thumbnail});
    if (active && anime.value) anime.value.thumbnail = fresh.thumbnail;
  } catch {}
}

void load();
onUnmounted(() => { active = false; });
</script>

<style scoped>
@reference "../../common/tailwind.pcss";

.anime-detail {
  overflow: clip;
}

.anime-document-actions {
  display: inline-flex;
  align-items: center;
  gap: .625rem;
  margin-left: .375rem;
  font-size: .75rem;
  white-space: nowrap;

  > span { color: var(--as-line-2) }
  > :is(a, button) { padding: .25rem 0 }
}

.anime-captions {
  padding: .75rem 1.5rem;
  font-size: .8125rem;
}

.caption-list {
  display: flex;
  flex-wrap: wrap;
  gap: .5rem;
  min-width: 0;
}

.caption-row {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: .25rem .625rem;
  min-width: 0;
  max-width: 100%;
  padding: .375rem .75rem;
  border: .0625rem solid var(--as-line-2);
  border-radius: var(--radius-ctl);
  background: var(--as-muted);
  overflow-wrap: anywhere;
}

.caption-provider {
  padding: .25rem 0;
  color: var(--as-ink-2);
  font-weight: 600;

  &:hover, &:focus-visible { color: var(--as-brand) }
}

.caption-content {
  display: inline-flex;
  align-items: center;
  gap: .25rem;
  padding: .25rem 0;
  color: var(--as-brand);
  font-weight: 600;
  font-variant-numeric: tabular-nums;

  > .as-icon { font-size: .625rem }
  &:is(a):hover, &:is(a):focus-visible { text-decoration: underline }
}

.caption-updated {
  color: var(--as-ink-3);
  font-size: .75rem;
  font-variant-numeric: tabular-nums;
  white-space: nowrap;
}

.is-pending .caption-content { color: var(--as-ink-3); font-weight: 400 }

@container site (width >= 48rem) {
  .anime-captions { padding-inline: 2rem }
}

.status-pill {
  @apply inline-flex items-center gap-2 px-3 py-1.5 text-xs font-semibold;
  border-radius: 0.25rem;
  background: var(--as-muted);
  color: var(--as-ink-2);
  .dot {
    width: 0.375rem;
    height: 0.375rem;
    border-radius: 62.4375rem;
    background: var(--as-ink-3);
  }
  &.is-on {
    background: color-mix(in oklab, var(--as-success) 13%, transparent);
    color: var(--as-success);
    .dot { background: var(--as-success) }
  }
}

</style>
