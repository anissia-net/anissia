<template>
  <div>
    <div v-choice class="as-segment mb-2">
      <button type="button" :class="{'is-on': period === 'week'}" @click="load('week')">주간</button>
      <button type="button" :class="{'is-on': period === 'quarter'}" @click="load('quarter')">분기</button>
      <button type="button" :class="{'is-on': period === 'year'}" @click="load('year')">연간</button>
    </div>

    <div class="px-2">
      <router-link v-for="node in list" :key="node.animeNo"
                   :to="`/anime?animeNo=${node.animeNo}`"
                   class="group flex items-center gap-3 py-2.5 -mx-2 px-2 as-row anissia-home-reduce-10">
        <span class="rank-no" :class="rankClass(node.rank)">{{node.rank}}</span>
        <span class="flex-1 min-w-0 truncate text-sm font-medium text-ink transition-colors group-hover:text-brand">{{node.subject}}</span>
        <span v-if="node.isDiff" class="shrink-0 text-[11px] font-semibold tabular-nums"
              :class="node.isDiffUp ? 'text-rose-600 dark:text-rose-400' : 'text-sky-600 dark:text-sky-400'">
          {{ node.isDiffUp ? '▲' : '▼' }} {{node.isDiffAbs}}
        </span>
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import {Ref, ref} from "vue";
import animeRemote from "../anime/remote/animeRemote";
import {AnimeRankItem} from "./AnimeRankItem";

const period = ref();
const list = ref([]) as Ref<AnimeRankItem[]>;

function load(_period: string) {
  period.value = _period;
  animeRemote.getRank(_period).then(data => list.value = data);
}

function rankClass(rank: number) {
  return rank <= 3 ? 'is-top' : '';
}

load("week");
</script>

<style scoped>
@reference "../../common/tailwind.pcss";

.rank-no {
  @apply shrink-0 w-6 h-6 inline-flex items-center justify-center rounded text-[11px] font-bold tabular-nums;
  background: var(--as-muted);
  color: var(--as-ink-3);
  &.is-top {
    background: var(--as-gradient);
    color: var(--as-on-brand);
  }
}
</style>
