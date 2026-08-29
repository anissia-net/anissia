<template>

 <div class="min-h-[225px]">
   <router-link v-for="node in list" :key="node.topicNo"
                :to="`/${props.ticker}?topicNo=${node.topicNo}`"
                class="group flex items-center gap-3 py-3 -mx-2 px-2 text-sm as-row">
     <span class="flex-1 min-w-0 flex items-baseline font-medium text-ink transition-colors group-hover:text-brand">
       <span class="min-w-0 truncate">{{node.topic}}</span>
       <sup v-if="node.postCount" class="post-count shrink-0">{{node.postCount}}</sup>
     </span>
     <span class="shrink-0 text-xs text-ink-3 tabular-nums">{{node.regDtText}}</span>
   </router-link>
 </div>

</template>

<script setup lang="ts">
import {RecentBoardData} from "./RecentBoardData";
import {computed, Ref} from "vue";
import {Topic} from "./Topic";

const props = defineProps({
  ticker: String,
  modelValue: RecentBoardData
});

const list = computed(() => (props.modelValue as any)[props.ticker!!]) as Ref<Topic[]>

</script>

<style scoped>
@reference "../../common/tailwind.pcss";

.post-count {
  @apply ml-1 text-[11px] font-semibold tabular-nums;
  color: var(--as-brand);
  vertical-align: baseline;
  position: relative;
  top: -.12em;
}
</style>
