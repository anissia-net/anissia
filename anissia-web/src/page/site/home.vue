<template>

  <div class="as-page">

    <div v-if="mode == 'PC'" class="flex gap-8 items-start">

      <div class="flex-1 min-w-0 as-sections">

        <div class="grid gap-8 grid-cols-2">
          <section>
            <div class="section-head">
              <router-link to="/notice" class="as-section-title">공지사항</router-link>
              <router-link to="/notice" class="more-link">더보기 <i class="fa-solid fa-chevron-right"></i></router-link>
            </div>
            <div class="as-box px-4">
              <RecentBoard ticker="notice" v-model="recentBoardData"/>
            </div>
          </section>
          <section>
            <div class="section-head">
              <router-link to="/inquiry" class="as-section-title">문의 게시판</router-link>
              <router-link to="/inquiry" class="more-link">더보기 <i class="fa-solid fa-chevron-right"></i></router-link>
            </div>
            <div class="as-box px-4">
              <RecentBoard ticker="inquiry" v-model="recentBoardData"/>
            </div>
          </section>
        </div>

        <section>
          <div class="section-head">
            <router-link to="/caption/recent" class="as-section-title">최근 자막</router-link>
            <router-link to="/caption/recent" class="more-link">더보기 <i class="fa-solid fa-chevron-right"></i></router-link>
          </div>
          <RecentCaption />
        </section>

        <section>
          <div class="section-head">
            <div class="as-section-title">운영기록</div>
          </div>
          <div class="as-box px-4">
            <active-panel mode="public" />
          </div>
        </section>

      </div>

      <aside class="w-[300px] shrink-0">
        <div class="section-head">
          <div class="as-section-title">애니 랭킹</div>
        </div>
        <div class="as-box p-2">
          <Rank/>
        </div>
      </aside>

    </div>

    <div v-else-if="mode == 'MOB'" class="anissia-home-mode-mob as-sections">

      <section>
        <div class="section-head">
          <router-link to="/caption/recent" class="as-section-title">최근 자막</router-link>
        </div>
        <RecentCaption />
        <router-link to="/caption/recent" class="as-input-btn w-full py-3 mt-4">더보기</router-link>
      </section>

      <section>
        <div class="section-head">
          <div class="as-section-title">애니 랭킹</div>
        </div>
        <div class="as-box p-2">
          <Rank/>
        </div>
      </section>

      <section>
        <div class="section-head">
          <router-link to="/notice" class="as-section-title">공지사항</router-link>
          <router-link to="/notice" class="more-link">더보기 <i class="fa-solid fa-chevron-right"></i></router-link>
        </div>
        <div class="as-box px-4">
          <RecentBoard ticker="notice" v-model="recentBoardData"/>
        </div>
      </section>

      <section>
        <div class="section-head">
          <router-link to="/inquiry" class="as-section-title">문의 게시판</router-link>
          <router-link to="/inquiry" class="more-link">더보기 <i class="fa-solid fa-chevron-right"></i></router-link>
        </div>
        <div class="as-box px-4">
          <RecentBoard ticker="inquiry" v-model="recentBoardData"/>
        </div>
      </section>

      <section>
        <div class="section-head">
          <div class="as-section-title">운영기록</div>
        </div>
        <div class="as-box px-4">
          <active-panel mode="public" />
        </div>
      </section>

    </div>

  </div>

</template>

<script setup lang="ts">

import {onUnmounted, ref} from "vue";
import Rank from "../../domain/rank/AnimeRank.vue";
import RecentCaption from "../../domain/anime/RecentCaption.vue";
import ActivePanel from "../../domain/activePanel/ActivePanel.vue";
import RecentBoard from "../../domain/board/RecentBoard.vue";
import boardRemote from "../../domain/board/remote/boardRemote";
import {RecentBoardData} from "../../domain/board/RecentBoardData";
import Result from "../../common/Result";

const mode = ref("");
const recentBoardData = ref(new RecentBoardData());

function applyResponsive() {
  const m = window.matchMedia("(min-width: 1024px)").matches ? "PC" : "MOB";
  if (mode.value != m) {
    mode.value = m;
  }
}

function resize(event: Event) {
  applyResponsive();
}

boardRemote.getRecentHome().then(data => recentBoardData.value = data);
applyResponsive();
addEventListener("resize", resize, true);

onUnmounted(() => {
  removeEventListener("resize", resize, true);
});

</script>

<style scoped>
@reference "../../common/tailwind.pcss";

.section-head {
  @apply flex items-center justify-between mb-3 gap-4;
}

.more-link {
  @apply inline-flex items-center gap-1.5 text-xs font-medium transition-colors duration-200;
  color: var(--as-ink-3);
  i { @apply text-[9px] }
  &:hover { color: var(--as-brand) }
}

:global(.anissia-home-mode-mob .anissia-home-reduce-6:nth-child( n + 7 )) { display: none }
:global(.anissia-home-mode-mob .anissia-home-reduce-10:nth-child( n + 11 )) { display: none }
</style>
