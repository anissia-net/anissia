<template>

  <div class="as-container pt-6">
    <nav v-choice class="as-segment admin-tabs">
      <router-link to="/admin" :class="{'is-on': url('/admin', true)}">
        <img src="./layout/tab-panel.svg" alt=""/><span>관리패널</span>
      </router-link>
      <router-link to="/admin/anime" :class="{'is-on': url('/admin/anime')}">
        <img src="./layout/tab-anime.svg" alt=""/><span>애니메이션</span>
      </router-link>
      <router-link to="/admin/schedule" :class="{'is-on': url('/admin/schedule')}">
        <img src="./layout/tab-schedule.svg" alt=""/><span>편성표</span>
      </router-link>
      <router-link to="/admin/caption" :class="{'is-on': url('/admin/caption')}">
        <img src="./layout/tab-caption.svg" alt=""/><span>자막</span>
      </router-link>
    </nav>
  </div>
  <div>
    <router-view />
  </div>

</template>

<script setup lang="ts">
import {onMounted} from "vue";
import anissia from "../../common/anissia";
import {prefetchRoute} from "../../common/router";
import {whenIdle} from "../../common/idle";

const url = anissia.url

// 관리 화면은 탭을 오가며 쓰므로 탭 페이지 코드를 한가할 때 미리 받아 둔다.
onMounted(() => whenIdle(() => ['/admin', '/admin/anime', '/admin/schedule', '/admin/caption'].forEach(prefetchRoute)));
</script>

<style>
@reference "../../common/tailwind.pcss";

.as-segment.admin-tabs {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));

  > a {
    @apply flex flex-col items-center justify-center gap-2 w-full aspect-square max-h-[5.75rem] p-0 text-xs font-semibold;
    img {
      @apply w-6 sm:w-8 transition-all duration-300;
      filter: grayscale(100%);
      opacity: .45;
    }
    span { @apply hidden sm:inline leading-none }
    &:hover img { opacity: .8; filter: grayscale(60%) }
    &.is-on {
      color: var(--as-brand);
      img { opacity: 1; filter: none }
    }
  }
}
</style>