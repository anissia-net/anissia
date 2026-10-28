<template>
  <div>
    <div class="flex items-center justify-between gap-4 mb-5">
      <h2 v-if="props.title" class="as-section-title">{{props.title}}</h2>
      <span v-else></span>
      <router-link v-if="boardInfo.canWriteTopic(user)" :to="`/${props.ticker}/new`" @pointerenter="preloadEditor" @focus="preloadEditor" class="as-btn-primary py-2">
        <as-icon name="pen" class="mr-1.5 text-[1rem]"/> 글쓰기
      </router-link>
    </div>

    <div class="grid gap-4 cq-md:grid-cols-2 cq-xl:grid-cols-3">
      <router-link v-for="node in list.content" :key="node.topicNo" :to="`/${props.ticker}/${node.topicNo}`" class="as-card group p-5 flex flex-col">
        <div class="text-md font-semibold text-ink group-hover:text-brand transition-colors line-clamp-2">
          {{node.topic}}
        </div>
        <div class="mt-auto pt-4 flex items-center gap-4 as-meta">
          <span class="inline-flex items-center gap-1.5 truncate"><as-icon name="user" class="opacity-60"/>{{node.name}}</span>
          <span class="inline-flex items-center gap-1.5"><as-icon name="chat" class="opacity-60"/>{{node.postCount}}</span>
          <span class="inline-flex items-center gap-1.5 ml-auto shrink-0"><as-icon name="clock" class="opacity-60"/>{{node.regDtText}}</span>
        </div>
      </router-link>
    </div>

  </div>

</template>

<script setup lang="ts">
import {computed, nextTick, onUnmounted, ref} from 'vue';
import PageData from '../../common/PageData';
import {useListScroll} from '../../common/useListScroll';
import {sessionStore} from '../session/sessionStore';
import boardRemote from './remote/boardRemote';
import {BoardInfo} from './BoardInfo';
import {Topic} from './Topic';
import {topicUpdates} from './topicUpdates';

const props = defineProps<{ticker: string; title?: string}>();
const boardInfo = ref(new BoardInfo());
const list = ref(PageData.empty<Topic>().notLoaded());
const user = computed(() => sessionStore().user);
let page = 0, active = true;
const sl = useListScroll(() => { page++; loadList(); });

function preloadEditor() { void import('./PostEditor.vue').catch(() => {}); }

function loadList() {
  const first = page === 0;
  sl.watch(false);
  boardRemote.getList(props.ticker, page).then(data => {
    if (!active) return;
    list.value = first ? data : list.value.merge(data);
    nextTick(() => sl.watch(data.next));
  });
}

topicUpdates.subscribe((id, changes) => {
  const item = list.value.content.find(item => item.topicNo === id);
  if (!item) return;
  if (changes) Object.assign(item, changes);
  else {
    list.value.content = list.value.content.filter(item => item.topicNo !== id);
    list.value.totalElements--;
  }
});

boardRemote.getTicker(props.ticker).then(info => { if (active) boardInfo.value = info; });
loadList();
onUnmounted(() => { active = false; });
</script>
