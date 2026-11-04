<template>
  <dl class="grid gap-3 text-sm">
    <div v-if="analysis.input !== analysis.corrected">
      <dt class="as-meta mb-1">검색 조건 분리·교정 후 분석할 텍스트</dt>
      <dd>{{ analysis.corrected || '없음' }}</dd>
    </div>
    <div v-for="row in rows" :key="row.label">
      <dt class="as-meta mb-1.5">{{ row.label }}</dt>
      <dd class="flex flex-wrap items-center gap-1.5">
        <span v-for="(token, index) in row.tokens" :key="index" class="as-tag-xs">{{ token }}</span>
        <span v-if="!row.tokens.length" class="text-ink-3">토큰 없음</span>
      </dd>
    </div>
  </dl>
</template>

<script setup lang="ts">
import {computed} from 'vue';
import type {Analysis} from './adminSearch';

const props = defineProps<{analysis: Analysis}>();
const rows = computed(() => [
  {label: '토크나이저 분해', tokens: props.analysis.raw},
  {label: '검색에 사용하는 최종 토큰', tokens: props.analysis.tokens},
]);
</script>
