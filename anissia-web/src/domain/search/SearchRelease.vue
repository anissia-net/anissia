<template>
  <section class="mt-8" aria-labelledby="search-release-title">
    <div class="as-section-head">
      <h2 id="search-release-title" class="as-section-title">테스트 및 적용</h2>
      <span class="as-meta pb-2">운영 {{ state.active }}</span>
    </div>
    <div class="as-box p-4">
      <div class="flex flex-wrap justify-between items-start gap-4">
        <div>
          <h3 class="font-semibold">간접 적용</h3>
          <p class="as-desc mt-1">초안의 사전으로 테스트용 검색 인덱스를 준비합니다.</p>
        </div>
        <button type="button" class="as-input-btn py-2" :disabled="locked || state.candidate === state.revision || state.active === state.revision" @click="prepare">테스트 버전 준비</button>
      </div>
      <p v-if="state.busy" role="status" class="as-meta mt-3">검색 인덱스를 갱신하고 있습니다.</p>
      <p v-if="state.error" role="alert" class="text-sm text-danger mt-3">{{ state.error }}</p>
      <div v-if="state.candidate !== null" class="mt-5">
        <h3 class="font-semibold">운영 {{ state.active }} · 테스트 {{ state.candidate }}</h3>
        <p class="as-desc mt-1">같은 검색어로 두 버전의 분석 결과와 상위 검색 결과를 비교합니다.</p>
        <form class="flex flex-wrap items-end gap-2 mt-3" @submit.prevent="test">
          <label class="grid flex-1 min-w-[9rem] gap-1 as-meta">테스트 검색어
            <input v-model="testQuery" class="as-input-text p-2.5 text-sm" maxlength="300" required/>
          </label>
          <label class="grid gap-1 as-meta">검색 대상
            <select v-model="kind" class="as-input-text p-2.5 text-sm">
              <option v-for="(label, key) in searchKinds" :key="key" :value="key">{{ label }}</option>
            </select>
          </label>
          <button type="submit" class="as-input-btn py-2.5" :disabled="locked || testing || !testQuery.trim()">
            {{ testing ? '비교 중…' : '비교 테스트' }}
          </button>
        </form>

        <p v-if="testError" role="alert" class="text-danger text-sm mt-3">{{ testError }}</p>
        <div v-if="comparison" class="grid cq-md:grid-cols-2 gap-4 my-4">
          <div v-for="(label, key) in sides" :key="key" class="rounded-ctl p-3 bg-brand-soft min-w-0">
            <h4 class="font-semibold mb-3">{{ label }} 버전</h4>
            <search-analysis :analysis="comparison[key].analysis"/>
            <p class="mt-4 text-sm font-semibold">검색 결과 <span class="as-meta">{{ comparison[key].total }}건</span></p>
            <ol v-if="comparison[key].results.length" class="list-decimal pl-6 mt-2 text-sm break-words">
              <li v-for="item in comparison[key].results" :key="item.id" class="py-0.5">{{ item.title || item.id }}</li>
            </ol>
            <p v-else class="as-meta mt-1">검색 결과가 없습니다.</p>
          </div>
        </div>
        <div class="flex flex-wrap items-center gap-3 mt-4">
          <button v-if="session.user.isRoot" type="button" class="as-btn-primary py-2"
                  :disabled="locked || testing || !state.tested" @click="publish">운영에 직접 적용</button>
          <span v-if="state.tested" class="as-meta">비교 테스트 완료</span>
          <span v-else-if="emptyComparison" class="as-desc" role="status">두 버전 모두 결과가 없고 분석도 같습니다. 다른 검색어로 테스트해 주세요.</span>
          <span v-else class="as-meta">비교 테스트 후 운영에 적용할 수 있습니다.</span>
        </div>
      </div>
      <p v-else-if="!state.busy" class="as-meta mt-3">{{ state.active === state.revision ? '변경한 규칙이 없습니다.' : '준비된 테스트 버전이 없습니다.' }}</p>
    </div>

    <details v-if="state.history.length" class="as-box p-4 mt-3">
      <summary class="cursor-pointer font-semibold">버전 이력</summary>
      <ul class="mt-2">
        <li v-for="entry in state.history" :key="entry.version" class="as-row flex flex-wrap items-center gap-2 py-2 text-sm">
          <span class="font-semibold">버전 {{ entry.version }}</span>
          <span v-if="entry.version === state.active" class="as-tag-xs">운영</span>
          <span class="as-meta flex-1">{{ entry.actor }} · {{ formatDate(entry.createdAt) }}</span>
          <button v-if="session.user.isRoot && entry.version !== state.revision" type="button" class="as-input-btn py-1"
                  :disabled="locked" @click="restore(entry.version)">초안으로 복원</button>
        </li>
      </ul>
    </details>
  </section>
</template>

<script setup lang="ts">
import {computed, ref, watch} from 'vue';
import {searchAdmin, searchKinds, type Comparison, type SearchKind, type SearchState} from './adminSearch';
import {sessionStore} from '../session/sessionStore';
import SearchAnalysis from './SearchAnalysis.vue';

const props = defineProps<{state: SearchState; locked: boolean; query: string}>();
const emit = defineEmits<{
  refresh: [];
  mutate: [action: () => Promise<unknown>, success: string];
}>();
const session = sessionStore();
const sides = {active: '운영', candidate: '테스트'} as const;
const testQuery = ref(props.query);
const kind = ref<SearchKind>('anime');
const comparison = ref<Comparison | null>(null), testing = ref(false), testError = ref('');
const emptyComparison = computed(() => {
  const result = comparison.value;
  return result && !result.active.total && !result.candidate.total &&
    JSON.stringify(result.active.analysis) === JSON.stringify(result.candidate.analysis);
});
let testRequest = 0;
watch(() => props.query, value => { testQuery.value = value; });
watch([testQuery, kind, () => props.state.active, () => props.state.candidate, () => props.state.revision], () => {
  ++testRequest;
  testing.value = false;
  comparison.value = null;
  testError.value = '';
});

function prepare() {
  const revision = props.state.revision;
  emit('mutate', () => searchAdmin.prepare(revision), '테스트 버전 준비를 요청했습니다.');
}
async function test() {
  const version = props.state.candidate;
  if (version === null || testing.value || props.locked) return;
  const request = ++testRequest;
  comparison.value = null;
  testError.value = '';
  testing.value = true;
  try {
    const result = await searchAdmin.test(testQuery.value.trim(), kind.value, version);
    if (request === testRequest) comparison.value = result;
  } catch (error) {
    if (request === testRequest) testError.value = error instanceof Error ? error.message : '검색 결과를 비교하지 못했습니다.';
  } finally {
    if (request === testRequest) testing.value = false;
    emit('refresh');
  }
}
function publish() {
  const version = props.state.candidate;
  if (version === null || !confirm(`테스트한 버전 ${version}을 운영 검색에 적용할까요?`)) return;
  emit('mutate', () => searchAdmin.publish(version), '운영 적용을 요청했습니다.');
}
function restore(version: number) {
  if (!confirm(`현재 초안을 버전 ${version}의 사전으로 교체할까요? 테스트를 거친 뒤 운영에 적용할 수 있습니다.`)) return;
  const revision = props.state.revision;
  emit('mutate', () => searchAdmin.restore(version, revision), '해당 버전을 초안으로 복원했습니다.');
}
function formatDate(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString('ko-KR');
}
</script>
