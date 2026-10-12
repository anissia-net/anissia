<template>
  <div class="as-page">
    <div class="home-grid">
      <div class="home-main">
        <section class="home-captions">
          <div class="section-head">
            <router-link to="/caption/recent" class="as-section-title">최근 자막</router-link>
            <router-link to="/caption/recent" class="more-link home-caption-more">더보기 <as-icon name="chevron-right"/></router-link>
          </div>
          <RecentCaption />
          <router-link to="/caption/recent" class="as-input-btn home-caption-more-mobile w-full py-3 mt-4">더보기</router-link>
        </section>

        <section class="home-notice">
          <div class="section-head">
            <router-link to="/notice" class="as-section-title">공지사항</router-link>
            <router-link to="/notice" class="more-link">더보기 <as-icon name="chevron-right"/></router-link>
          </div>
          <div class="as-box px-4 py-2.5">
            <RecentBoard ticker="notice" v-model="recentBoardData"/>
          </div>
        </section>

        <section class="home-inquiry">
          <div class="section-head">
            <router-link to="/inquiry" class="as-section-title">문의 게시판</router-link>
            <router-link to="/inquiry" class="more-link">더보기 <as-icon name="chevron-right"/></router-link>
          </div>
          <div class="as-box px-4 py-2.5">
            <RecentBoard ticker="inquiry" v-model="recentBoardData"/>
          </div>
        </section>

        <section class="home-activity">
          <div class="section-head">
            <div class="as-section-title">운영기록</div>
          </div>
          <div class="as-box px-4 py-3">
            <active-panel mode="public" />
          </div>
        </section>
      </div>

      <section class="home-rank">
        <div class="section-head">
          <div class="as-section-title">애니 랭킹</div>
        </div>
        <div class="as-box p-2">
          <Rank/>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import {ref} from "vue";
import Rank from "../../domain/rank/AnimeRank.vue";
import RecentCaption from "../../domain/anime/RecentCaption.vue";
import ActivePanel from "../../domain/activePanel/ActivePanel.vue";
import RecentBoard from "../../domain/board/RecentBoard.vue";
import boardRemote from "../../domain/board/remote/boardRemote";
import {RecentBoardData} from "../../domain/board/RecentBoardData";

const recentBoardData = ref(new RecentBoardData());
boardRemote.getRecentHome().then(data => recentBoardData.value = data);
</script>
<style scoped>
@reference "../../common/tailwind.pcss";

.section-head {
  @apply flex items-end justify-between mb-3 gap-4;
  box-shadow: inset 0 -0.0625rem 0 var(--as-line-2);
}

.more-link {
  @apply inline-flex items-center gap-1.5 pb-2 text-xs font-medium transition-colors duration-200;
  color: var(--as-ink-3);
  i { @apply text-[0.5625rem] }
  &:hover { color: var(--as-brand) }
}

.home-grid {
  display: grid;
  gap: var(--spacing-section);
  grid-template-columns: minmax(0, 1fr);
  grid-template-areas: "captions" "rank" "notice" "inquiry" "activity";
}

.home-main { display: contents }
.home-captions { grid-area: captions }
.home-rank { grid-area: rank }
.home-notice { grid-area: notice }
.home-inquiry { grid-area: inquiry }
.home-activity { grid-area: activity }
.home-caption-more { display: none }

@container site (width >= 64rem) {
  .home-caption-more { display: inline-flex }
  .home-caption-more-mobile { display: none }
  .home-grid {
    gap: var(--spacing-section) 2rem;
    grid-template-columns: minmax(0, 1fr) 18.75rem;
    grid-template-areas: "main rank";
    align-items: start;
  }
  .home-main {
    display: grid;
    grid-area: main;
    gap: var(--spacing-section) 2rem;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    grid-template-areas: "notice inquiry" "captions captions" "activity activity";
  }
}

@container site (width < 64rem) {
  .home-grid :deep(.anissia-home-reduce-6:nth-child(n + 7)),
  .home-grid :deep(.anissia-home-reduce-10:nth-child(n + 11)) { display: none }
}
</style>
