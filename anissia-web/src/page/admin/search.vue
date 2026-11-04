<template>
  <div v-if="info" class="as-page search-admin">
    <header class="as-page-head">
      <h1 class="as-page-title">검색 관리</h1>
      <p class="as-desc">검색어의 형태소를 확인하고 사전 규칙을 관리합니다.</p>
    </header>

    <form role="search" class="flex gap-2" @submit.prevent="submitSearch">
      <div class="as-input-with-icon flex-1 min-w-0">
        <as-icon name="search"/>
        <input v-model="input" type="search" class="as-input-text py-3 pr-3" aria-label="검색어"
               placeholder="검색어를 입력하세요" maxlength="300" autocomplete="off"
               @input="scheduleSearch" @compositionstart="clearTimer" @compositionend="scheduleSearch"/>
      </div>
      <button class="as-input-btn" type="submit">검색</button>
    </form>

    <section v-if="query" class="as-box mt-3 p-4" aria-label="운영 검색어 분석" aria-live="polite">
      <h2 class="font-semibold mb-3">운영 검색어 분석</h2>
      <p v-if="analysisError" role="alert" class="text-sm text-danger">{{ analysisError }}</p>
      <search-analysis v-else-if="analysis" :analysis="analysis"/>
      <p v-else class="as-meta">형태소를 분석하고 있습니다.</p>
    </section>

    <section class="mt-8" aria-label="사전 초안">
      <div class="as-section-head">
        <h2 class="as-section-title">사전 초안</h2>
        <span v-if="state" class="as-meta pb-2">초안 {{ state.revision }}</span>
      </div>
      <nav v-choice class="as-segment" aria-label="사전 종류">
        <router-link v-for="(metadata, key) in categories" :key="key"
                     :to="{name: 'admin-search', params: {category: key}, query: input.trim() ? {q: input.trim()} : {}}"
                     :class="{'is-on': category === key}" :aria-current="category === key ? 'page' : undefined">
          {{ metadata.label }}
        </router-link>
      </nav>

      <div class="as-box p-4 mt-3">
        <div class="flex items-center justify-between gap-3">
          <h3 class="font-semibold">{{ info.label }} <span v-if="rules" class="as-meta">{{ rules.total }}건</span></h3>
          <button class="as-input-btn py-1.5" type="button" :disabled="locked" :aria-expanded="adding" @click="toggleAdd">
            {{ adding ? '닫기' : isUserDictionary ? '단어 추가' : '규칙 추가' }}
          </button>
        </div>
        <p v-if="isUserDictionary" class="as-desc mt-2">{{ info.hint }}</p>
        <form v-if="adding" class="grid gap-3 py-4" @submit.prevent="addRule">
          <div class="grid gap-3" :class="{'cq-md:grid-cols-2': info.value && !isUserDictionary}">
            <label class="grid gap-1 text-sm">{{ info.term }}
              <input ref="termInput" v-model="term" class="as-input-text p-2" maxlength="100" required autocomplete="off"/>
            </label>
            <label v-if="info.value && !isUserDictionary" class="grid gap-1 text-sm">{{ info.value }}
              <input v-model="value" class="as-input-text p-2" :maxlength="category === 'corrections' ? 100 : 500" required autocomplete="off"/>
            </label>
          </div>
          <div v-if="isUserDictionary" class="text-sm">
            <label class="inline-flex items-center gap-2">
              <input v-model="useSegmentation" type="checkbox"/> 복합어 분해 직접 지정
            </label>
            <label v-if="useSegmentation" class="grid gap-1 mt-2">{{ info.value }}
              <input v-model="value" class="as-input-text p-2" placeholder="공백으로 구분해 입력하세요" maxlength="500" required autocomplete="off"/>
            </label>
          </div>
          <p v-else class="as-meta">{{ info.hint }}</p>
          <button class="as-btn-primary py-2 justify-self-start" type="submit" :disabled="locked || !term.trim()">초안에 추가</button>
        </form>

        <p v-if="rulesError" class="py-4 text-sm text-danger" role="alert">{{ rulesError }}</p>
        <p v-else-if="!rules" class="py-4 as-meta">목록을 불러오고 있습니다.</p>
        <template v-else>
          <ul v-if="rules.items.length" class="mt-2">
            <li v-for="rule in rules.items" :key="rule.id" class="as-row flex items-center gap-3 py-3 text-sm">
              <div class="min-w-0 flex-1 break-words">
                <strong>{{ rule.term }}</strong>
                <p v-if="rule.value" class="text-ink-2 mt-1">{{ info.value }} · {{ rule.value }}</p>
              </div>
              <button type="button" class="as-input-btn py-1" :disabled="locked" :aria-label="`${rule.term} 삭제`" @click="removeRule(rule)">삭제</button>
            </li>
          </ul>
          <p v-else class="py-5 as-desc">{{ query ? '검색어와 일치하는 규칙이 없습니다.' : '등록된 규칙이 없습니다.' }}</p>
          <div v-if="rules.total > 30" class="flex items-center justify-center gap-3 mt-2">
            <button type="button" class="as-input-btn py-1" :disabled="page === 0" @click="page--">이전</button>
            <span class="as-meta">{{ page + 1 }} / {{ Math.ceil(rules.total / 30) }}</span>
            <button type="button" class="as-input-btn py-1" :disabled="(page + 1) * 30 >= rules.total" @click="page++">다음</button>
          </div>
        </template>
      </div>
    </section>

    <p v-if="stateError" class="mt-4 text-danger text-sm" role="alert">{{ stateError }}
      <button type="button" class="as-link ml-2" @click="refreshState">다시 불러오기</button>
    </p>
    <p v-if="actionError" class="mt-4 text-danger text-sm" role="alert">{{ actionError }}</p>
    <search-release v-if="state" :state="state" :locked="locked" :query="query"
                    @refresh="refreshState" @mutate="mutate"/>
  </div>
</template>

<script setup lang="ts">
import {computed, nextTick, onBeforeUnmount, onMounted, ref, watch} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import {categories, searchAdmin, type Analysis, type Category, type RuleList, type SearchRule, type SearchState} from '../../domain/search/adminSearch';
import SearchAnalysis from '../../domain/search/SearchAnalysis.vue';
import SearchRelease from '../../domain/search/SearchRelease.vue';
import toast from '../../common/toast';

const route = useRoute(), router = useRouter();
const category = computed(() => route.params.category as Category);
const isUserDictionary = computed(() => category.value === 'user-dictionary');
const info = computed(() => categories[category.value]);
const query = computed(() => typeof route.query.q === 'string' ? route.query.q.trim() : '');
const input = ref(query.value);
const page = ref(0);
const state = ref<SearchState | null>(null);
const rules = ref<RuleList | null>(null);
const analysis = ref<Analysis | null>(null);
const stateError = ref(''), rulesError = ref(''), analysisError = ref(''), actionError = ref('');
const pending = ref(false), adding = ref(false);
const term = ref(''), value = ref(''), termInput = ref<HTMLInputElement>();
const useSegmentation = ref(false);
let formRevision = 0;
const locked = computed(() => !state.value || !!stateError.value || pending.value || state.value.busy);
let searchTimer: ReturnType<typeof setTimeout>;
let pollTimer: ReturnType<typeof setTimeout>;
let stateRequest = 0, rulesRequest = 0, analysisRequest = 0;
let alive = true;

function message(error: unknown) {
  return error instanceof Error ? error.message : '요청을 완료하지 못했습니다.';
}

function clearTimer() { clearTimeout(searchTimer); }
function scheduleSearch(event: Event) {
  clearTimer();
  if ((event as InputEvent).isComposing) return;
  searchTimer = setTimeout(() => navigateSearch(true), 350);
}
function navigateSearch(replace: boolean) {
  const q = input.value.trim();
  if (q === query.value) return;
  const location = {path: route.path, query: q ? {q} : {}};
  if (replace) void router.replace(location);
  else void router.push(location);
}
function submitSearch() {
  clearTimer();
  navigateSearch(false);
}

async function refreshState() {
  if (!alive || route.name !== 'admin-search') return;
  const request = ++stateRequest;
  clearTimeout(pollTimer);
  try {
    const result = await searchAdmin.state();
    if (!alive || request !== stateRequest) return;
    if (state.value && state.value.revision !== result.revision && !pending.value) void loadRules();
    state.value = result;
    stateError.value = '';
    if (result.busy) pollTimer = setTimeout(refreshState, 1500);
  } catch (error) {
    if (alive && request === stateRequest) stateError.value = message(error);
  }
}
async function loadRules() {
  const request = ++rulesRequest;
  rules.value = null;
  rulesError.value = '';
  if (!info.value || !alive) return;
  try {
    const result = await searchAdmin.rules(category.value, query.value, page.value);
    if (!alive || request !== rulesRequest) return;
    if (!result.items.length && page.value > 0) page.value = Math.max(0, Math.ceil(result.total / 30) - 1);
    else rules.value = result;
  } catch (error) {
    if (alive && request === rulesRequest) rulesError.value = message(error);
  }
}
async function loadAnalysis() {
  const request = ++analysisRequest;
  analysis.value = null;
  analysisError.value = '';
  if (!query.value || !info.value || !alive) return;
  try {
    const result = await searchAdmin.analyze(query.value);
    if (alive && request === analysisRequest) analysis.value = result;
  } catch (error) {
    if (alive && request === analysisRequest) analysisError.value = message(error);
  }
}

function toggleAdd() {
  adding.value = !adding.value;
  if (adding.value) {
    formRevision = state.value!.revision;
    term.value = input.value.trim();
    value.value = '';
    useSegmentation.value = false;
    void nextTick(() => termInput.value?.focus());
  }
}

async function mutate(action: () => Promise<unknown>, success: string, done?: () => void) {
  if (locked.value) return;
  pending.value = true;
  actionError.value = '';
  try {
    await action();
    if (!alive) return;
    done?.();
    toast.success(success);
  } catch (error) {
    if (alive) actionError.value = message(error);
  } finally {
    if (alive) {
      await Promise.all([refreshState(), loadRules()]);
      if (adding.value && state.value) formRevision = state.value.revision;
      pending.value = false;
    }
  }
}

function addRule() {
  if (!state.value) return;
  const selected = category.value, revision = formRevision;
  const ruleValue = isUserDictionary.value && !useSegmentation.value ? '' : value.value.trim();
  void mutate(() => searchAdmin.add(selected, term.value.trim(), ruleValue, revision), '초안에 추가했습니다.', () => { adding.value = false; });
}
function removeRule(rule: SearchRule) {
  if (!state.value || !confirm(`초안에서 ‘${rule.term}’ 규칙을 삭제할까요?`)) return;
  const selected = category.value, revision = state.value.revision;
  void mutate(() => searchAdmin.remove(selected, rule.id, revision), '초안에서 규칙을 삭제했습니다.');
}

watch([category, query], () => {
  clearTimer();
  input.value = query.value;
  adding.value = false;
  page.value = 0;
}, {flush: 'sync'});
watch([category, query, page], loadRules, {immediate: true});
watch([query, () => state.value?.active], loadAnalysis, {immediate: true});
onMounted(() => {
  void refreshState();
  window.addEventListener('focus', refreshState);
});
onBeforeUnmount(() => {
  alive = false;
  clearTimer();
  clearTimeout(pollTimer);
  window.removeEventListener('focus', refreshState);
});
</script>

<style scoped>
.search-admin :disabled { opacity: .5; cursor: default; }
.search-admin .as-tag-xs { overflow-wrap: anywhere; }
</style>
