<template>
  <div class="as-page">
    <header class="flex flex-wrap items-start justify-between gap-4 mb-6">
      <div class="min-w-0">
        <h1 class="as-page-title">변경 이력</h1>
        <p v-if="item" class="as-desc mt-2 break-words">{{item.title}}</p>
      </div>
      <router-link v-if="target" :to="documentLocation" class="as-link text-sm py-2">{{target.kind === 'anime' ? '작품으로' : '템플릿으로'}}</router-link>
    </header>

    <p v-if="!target" class="as-empty" role="alert">변경 이력을 볼 작품이나 템플릿을 선택해 주세요.</p>
    <template v-else>
      <p v-if="documentError" class="text-sm text-red-600 mb-4" role="alert">{{documentError}} <button type="button" class="as-link" @click="loadDocument">다시 시도</button></p>
      <p v-if="item?.deleted" class="as-desc mb-4">삭제된 템플릿입니다.</p>
      <div class="as-box p-4 cq-md:p-6">
        <div v-if="history.length" class="overflow-auto">
          <table class="history-table w-full text-sm text-left">
            <caption class="sr-only">{{item?.title}} 변경 이력</caption>
            <thead><tr><th scope="col">비교</th><th scope="col">판</th><th scope="col">수정 요약</th><th scope="col">수정자 · 시각</th><th scope="col">보기</th></tr></thead>
            <tbody>
              <tr v-for="record in history" :key="record.revision">
                <td><input v-model="compareVersions" :value="record.revision" type="checkbox" :disabled="saving || (compareVersions.length >= 2 && !compareVersions.includes(record.revision))" :aria-label="`${record.revision}판 비교 선택`"/></td>
                <td class="whitespace-nowrap">{{record.revision}}<span v-if="record.revision === item?.revision" class="as-meta ml-2">현재</span></td>
                <td class="min-w-40 break-words">{{record.summary || '수정'}}</td>
                <td class="as-meta min-w-36">{{record.updatedBy}}<br/><time :datetime="record.updatedAt">{{wikiDate(record.updatedAt)}}</time></td>
                <td><router-link :to="revisionLocation(record.revision)" class="as-link whitespace-nowrap" :aria-label="`${record.revision}판 보기`" :aria-current="selectedRevision?.revision === record.revision ? 'page' : undefined">보기</router-link></td>
              </tr>
            </tbody>
          </table>
        </div>
        <p v-if="historyLoading" class="as-empty" role="status">이력을 불러오는 중입니다.</p>
        <p v-else-if="!history.length && !historyError" class="as-empty">아직 변경 이력이 없습니다.</p>
        <p v-if="historyError" class="text-sm text-red-600 mt-3" role="alert">{{historyError}} <button type="button" class="as-link" @click="loadHistory(retryAppend)">다시 시도</button></p>
        <div v-if="history.length" class="flex flex-wrap gap-3 mt-4">
          <button type="button" class="as-input-btn py-2" :disabled="compareVersions.length !== 2 || detailLoading || saving" @click="compare">선택한 두 판 비교</button>
          <button v-if="historyHasMore" type="button" class="as-input-btn py-2" :disabled="historyLoading" @click="loadHistory(true)">이력 더 보기</button>
        </div>
      </div>

      <section v-if="hasDetail" class="as-box p-4 cq-md:p-6 mt-6" aria-label="선택한 변경 이력">
        <p v-if="detailLoading" class="as-empty" role="status">내용을 불러오는 중입니다.</p>
        <p v-if="detailError" class="text-sm text-red-600" role="alert">{{detailError}} <button type="button" class="as-link" @click="loadDetails">다시 시도</button></p>
        <template v-if="comparison">
          <h2 class="as-section-title mb-4">{{comparison.before.revision}}판 → {{comparison.after.revision}}판 비교</h2>
          <WikiRevisionDiff :key="`${endpoint}:${comparison.before.revision}:${comparison.after.revision}`" :before="comparison.before" :after="comparison.after"/>
        </template>
        <template v-if="selectedRevision">
          <div class="flex flex-wrap items-center justify-between gap-3 mb-4">
            <h2 class="as-section-title">{{selectedRevision.revision}}판</h2>
            <button v-if="item && access?.canContribute && !item.deleted && selectedRevision.revision !== item.revision" type="button" class="as-input-btn py-2" :disabled="saving" @click="restore">{{saving ? '되돌리는 중…' : '이 판으로 되돌리기'}}</button>
          </div>
          <p v-if="restoreError" class="text-sm text-red-600 mb-4" role="alert">{{restoreError}}</p>
          <WikiBodyView :body="selectedRevision.body" :templates="selectedRevision.templates"/>
        </template>
      </section>
    </template>
  </div>
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import toast from '../../common/toast';
import {sessionStore} from '../../domain/session/sessionStore';
import WikiBodyView from '../../domain/wiki/WikiBodyView.vue';
import WikiRevisionDiff from '../../domain/wiki/WikiRevisionDiff.vue';
import {wikiHistoryLocation, wikiHistoryTarget, wikiLocation, wikiPositiveInteger} from '../../domain/wiki/wikiLocation';
import {wikiRemote, wikiDate, wikiErrorMessage, type WikiAccess, type WikiDocument, type WikiHistory} from '../../domain/wiki/wikiRemote';

const route = useRoute(), router = useRouter(), session = sessionStore();
const target = computed(() => wikiHistoryTarget(route.query));
const endpoint = computed(() => target.value ? `/${target.value.kind === 'anime' ? 'anime' : 'templates'}/${target.value.targetNo}` : '');
const documentLocation = computed(() => target.value ? wikiLocation(target.value) : '/anime');
const hasDetail = computed(() => route.query.revision !== undefined || route.query.from !== undefined || route.query.to !== undefined);
const item = ref<WikiDocument>(), access = ref<WikiAccess>(), documentError = ref('');
const history = ref<WikiHistory[]>([]), historyLoading = ref(false), historyError = ref(''), historyHasMore = ref(false);
const compareVersions = ref<number[]>([]), selectedRevision = ref<WikiDocument>();
const comparison = ref<{before: WikiDocument; after: WikiDocument}>();
const detailLoading = ref(false), detailError = ref(''), saving = ref(false), restoreError = ref('');
let active = true, generation = 0, documentGeneration = 0, historyGeneration = 0, detailGeneration = 0, historyPage = 0, retryAppend = false;

function revisionLocation(revision: number) {
  return wikiHistoryLocation({...target.value!, revision});
}

async function loadDocument() {
  if (!endpoint.value) return;
  const expected = ++documentGeneration, path = endpoint.value;
  documentError.value = '';
  const [documentResult, accessResult] = await Promise.allSettled([
    wikiRemote.request<WikiDocument>(path), wikiRemote.access(),
  ]);
  if (!active || expected !== documentGeneration) return;
  if (documentResult.status === 'fulfilled') item.value = documentResult.value;
  else documentError.value = wikiErrorMessage(documentResult.reason);
  access.value = accessResult.status === 'fulfilled' ? accessResult.value : undefined;
}

async function loadHistory(append: boolean) {
  if (!endpoint.value || (append && (historyLoading.value || !historyHasMore.value))) return;
  const expected = ++historyGeneration, path = endpoint.value, page = append ? historyPage + 1 : 0;
  historyLoading.value = true; historyError.value = ''; retryAppend = append;
  try {
    const records = await wikiRemote.request<WikiHistory[]>(`${path}/history?page=${page}`);
    if (!active || expected !== historyGeneration) return;
    history.value = append ? [...history.value, ...records] : records;
    historyPage = page; historyHasMore.value = records.length === 50;
  } catch (error) { if (active && expected === historyGeneration) historyError.value = wikiErrorMessage(error); }
  finally { if (active && expected === historyGeneration) historyLoading.value = false; }
}

async function loadDetails() {
  const expected = ++detailGeneration, path = endpoint.value;
  selectedRevision.value = undefined; comparison.value = undefined; detailError.value = ''; restoreError.value = ''; detailLoading.value = false;
  if (!path || !hasDetail.value) return;
  const revision = wikiPositiveInteger(route.query.revision);
  const from = wikiPositiveInteger(route.query.from), to = wikiPositiveInteger(route.query.to);
  const comparing = route.query.from !== undefined || route.query.to !== undefined;
  if (comparing ? (!from || !to || from === to || route.query.revision !== undefined) : !revision) {
    detailError.value = '올바른 판 번호를 선택해 주세요.';
    return;
  }
  detailLoading.value = true;
  try {
    if (comparing) {
      const versions = [from!, to!].sort((a, b) => a - b);
      compareVersions.value = versions;
      const [before, after] = await Promise.all(versions.map(value => wikiRemote.request<WikiDocument>(`${path}/history/${value}`)));
      if (active && expected === detailGeneration) comparison.value = {before: before!, after: after!};
    } else {
      const value = await wikiRemote.request<WikiDocument>(`${path}/history/${revision}`);
      if (active && expected === detailGeneration) selectedRevision.value = value;
    }
  } catch (error) { if (active && expected === detailGeneration) detailError.value = wikiErrorMessage(error); }
  finally { if (active && expected === detailGeneration) detailLoading.value = false; }
}

function compare() {
  if (!target.value || compareVersions.value.length !== 2 || saving.value) return;
  const [from, to] = [...compareVersions.value].sort((a, b) => a - b);
  void router.push(`${wikiHistoryLocation(target.value)}&from=${from}&to=${to}`);
}

async function restore() {
  if (!item.value || !selectedRevision.value || !access.value?.canContribute || item.value.deleted || saving.value) return;
  const revision = selectedRevision.value.revision;
  if (revision === item.value.revision || !window.confirm(`${revision}판 내용으로 되돌리시겠습니까? 새 변경 이력이 남습니다.`)) return;
  const expected = generation, path = endpoint.value, destination = documentLocation.value;
  saving.value = true; restoreError.value = '';
  try {
    await wikiRemote.request<WikiDocument>(`${path}/restore/${revision}`, 'POST', {baseRevision: item.value.revision, summary: `${revision}판으로 되돌림`});
    if (!active || expected !== generation) return;
    toast.success('선택한 판으로 되돌렸습니다.');
    await router.push(destination);
  } catch (error) { if (active && expected === generation) restoreError.value = wikiErrorMessage(error); }
  finally { if (active && expected === generation) saving.value = false; }
}

watch([endpoint, () => session.user.email, () => session.user.roles.join(','), () => route.query.revision, () => route.query.from, () => route.query.to], (_, previous) => {
  if (!previous?.length || endpoint.value !== previous[0] || session.user.email !== previous[1] || session.user.roles.join(',') !== previous[2]) {
    ++generation; ++documentGeneration; ++historyGeneration;
    item.value = undefined; access.value = undefined; documentError.value = ''; history.value = []; historyError.value = ''; historyHasMore.value = false; historyLoading.value = false;
    compareVersions.value = []; historyPage = 0; saving.value = false;
    void loadDocument(); void loadHistory(false);
  }
  void loadDetails();
}, {immediate: true});

onBeforeUnmount(() => { active = false; ++generation; ++documentGeneration; ++historyGeneration; ++detailGeneration; });
</script>

<style scoped>
.history-table :is(th, td) {
  padding: .75rem .5rem;
  border-bottom: .0625rem solid var(--as-line);
}
.history-table th { white-space: nowrap; }
.history-table td { vertical-align: top; }
</style>
