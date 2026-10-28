<template>
  <section :id="isTemplate ? undefined : 'wiki'" class="scroll-mt-5" :class="embedded ? 'wiki-embedded' : 'mt-6'" :aria-label="embedded ? '작품 내용' : '위키'">
    <div v-if="!embedded" class="flex flex-wrap items-center justify-between gap-3 mb-4">
      <h2 class="as-section-title">{{isTemplate ? (newTemplate ? '새 템플릿' : item?.title || '템플릿') : '위키'}}</h2>
      <div class="flex flex-wrap items-center gap-3 text-sm">
        <button v-if="!isTemplate" type="button" class="as-link" @click="copyLink">위키 주소 복사</button>
        <router-link v-if="canViewHistory" class="as-link" :to="historyLocation">변경 이력</router-link>
        <button v-if="canEdit" type="button" class="as-btn-primary py-1.5" @click="beginEdit">{{hasRevision ? '수정' : '작성'}}</button>
      </div>
    </div>
    <p v-if="loading" :class="embedded ? 'wiki-inline-state' : 'as-empty'" role="status">위키를 불러오는 중입니다.</p>
    <div v-else-if="loadError" :class="embedded ? 'wiki-inline-error' : 'as-box p-5'"><p class="text-sm text-red-600" role="alert">{{loadError}}</p><div class="flex gap-3 mt-2 text-sm"><button class="as-link" type="button" @click="load">다시 시도</button><router-link v-if="!newTemplate" class="as-link" :to="historyLocation">변경 이력</router-link></div></div>
    <template v-else-if="item">
      <p v-if="!embedded && !access?.canContribute" class="as-desc mb-4">{{access?.message}}</p>
      <router-link v-if="newTemplate && access && !session.user.isLogin" to="/login?path=%2Fwiki%2Ftemplates%3Fnew%3D1" class="as-input-btn inline-block py-2">로그인</router-link>
      <div v-if="item.deleted" class="as-box p-4 mb-4 flex flex-wrap items-center justify-between gap-3"><p class="as-desc">삭제된 템플릿입니다. 운영자만 이 내용과 이력을 볼 수 있습니다.</p><button v-if="access?.isOperator" type="button" class="as-btn-primary py-2" :disabled="saving" @click="recover">템플릿 복구</button></div>
      <div v-if="availableDraft && !editing && !item.deleted && access?.canContribute" class="as-box p-4 mb-4 flex flex-wrap items-center gap-3 text-sm"><span>이 탭에 저장한 초안이 있습니다. {{wikiDate(availableDraft.savedAt)}}</span><button type="button" class="as-link" @click="resumeDraft">이어서 작성</button><button type="button" class="as-link" @click="discardDraft">초안 버리기</button></div>
      <form v-if="editing" @submit.prevent="save" class="space-y-3">
        <fieldset :disabled="saving" :inert="saving || !!conflict" class="space-y-3">
          <label v-if="isTemplate" class="block"><span class="as-meta">템플릿 이름</span><input v-model="title" maxlength="100" class="as-input-text px-3 py-2 w-full mt-1" required/></label>
          <WikiEditor :key="editorVersion" ref="editor" v-model="body" v-model:templates="templates" :saved-body="baseline?.body" :additional-dirty="title !== baseline?.title || !!summary.trim()" :allow-templates="!isTemplate"/>
          <label class="block"><span class="as-meta">수정 요약</span><input v-model="summary" class="as-input-text px-3 py-2 w-full mt-1" maxlength="300" placeholder="어떤 내용을 바꾸었나요?"/></label>
          <div class="flex flex-wrap justify-end gap-2"><button type="button" class="as-input-btn py-2" @click="cancelEdit">취소</button><button type="submit" class="as-btn-primary py-2">{{saving ? '저장 중…' : '저장'}}</button></div>
        </fieldset>
        <p v-if="draftStatus" class="as-meta" role="status">{{draftStatus}}</p>
        <p v-if="saveError" class="text-sm text-red-600 whitespace-pre-line" role="alert">{{saveError}}</p>
      </form>
      <template v-else-if="!newTemplate || access?.canContribute">
        <p v-if="item.renderWarning" class="as-desc mb-3" role="status">{{item.renderWarning}}</p>
        <div v-if="item.revision > 0" :class="embedded ? 'wiki-content' : 'as-post overflow-hidden'"><WikiBodyView :body="item.body" :templates="item.templates"/></div>
        <div v-else-if="embedded" class="wiki-empty">
          <span class="wiki-empty-icon"><as-icon name="pen"/></span>
          <div><p>아직 작성된 위키가 없습니다.</p><p class="as-meta mt-1">작품 소개와 이야기가 이곳에 모입니다.</p></div>
        </div>
        <div v-else class="as-box p-6 as-desc">아직 작성된 위키가 없습니다.</div>
        <div v-if="!embedded && item.updatedAt" class="flex flex-wrap justify-between items-center gap-3 mt-3 as-meta"><span>{{wikiDate(item.updatedAt)}}<template v-if="item.updatedBy"> · {{item.updatedBy}}</template> · {{item.revision}}판</span><button v-if="isTemplate && access?.isOperator && item.revision > 0 && !item.deleted" type="button" class="as-link" :disabled="saving" @click="removeTemplate">템플릿 삭제</button></div>
        <WikiUsageList v-if="isTemplate && !newTemplate && !item.deleted" :key="item.revision" :endpoint="`${path}/usage`"/>
        <p v-if="saveError" class="text-sm text-red-600 mt-3" role="alert">{{saveError}}</p>
      </template>
    </template>

    <WikiMerge v-if="conflict && baseline" :base="baseline.body" :local="body" :incoming="conflict.body" :templates="mergeTemplates" :allow-templates="!isTemplate" @close="conflict = undefined" @resolved="mergeResolved"/>
  </section>
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue';
import {sessionStore} from '../session/sessionStore';
import toast from '../../common/toast';
import WikiEditor from './WikiEditor.vue';
import WikiBodyView from './WikiBodyView.vue';
import WikiMerge from './WikiMerge.vue';
import WikiUsageList from './WikiUsageList.vue';
import {wikiRemote, WikiError, wikiDate, wikiErrorMessage, wikiHistoryLocation, type WikiAccess, type WikiDocument, type WikiBody, type WikiTemplate} from './wikiRemote';
import {readWikiDraft, removeWikiDraft, wikiDraftKey, writeWikiDraft, type WikiDraft} from './wikiDraft';

const props = defineProps<{animeNo?: number; templateNo?: number; newTemplate?: boolean; embedded?: boolean; editOnOpen?: boolean}>();
const emit = defineEmits<{created: [item: WikiDocument]; deleted: []; saved: [item: WikiDocument]}>();
const session = sessionStore();
const isTemplate = computed(() => props.newTemplate || !!props.templateNo);
const path = computed(() => props.newTemplate ? '/templates' : isTemplate.value ? `/templates/${props.templateNo}` : `/anime/${props.animeNo}`);
const historyLocation = computed(() => wikiHistoryLocation({kind: isTemplate.value ? 'template' : 'anime', targetNo: (isTemplate.value ? props.templateNo : props.animeNo)!}));
const item = ref<WikiDocument>(), baseline = ref<WikiDocument>(), access = ref<WikiAccess>();
const body = ref<WikiBody>(wikiRemote.emptyBody()), templates = ref<WikiTemplate[]>([]), title = ref(''), summary = ref('');
const loading = ref(false), editing = ref(false), saving = ref(false), loadError = ref(''), saveError = ref(''), editorVersion = ref(0);
const hasRevision = computed(() => !!item.value && item.value.revision > 0);
const canViewHistory = computed(() => hasRevision.value && !editing.value);
const canEdit = computed(() => !!item.value && !item.value.deleted && !editing.value && !!access.value?.canContribute);
defineExpose({hasRevision, canViewHistory, canEdit, beginEdit});
const editor = ref<InstanceType<typeof WikiEditor>>();
const conflict = ref<WikiDocument>();
const availableDraft = ref<WikiDraft>(), draftStatus = ref('');
const draftKey = computed(() => wikiDraftKey(session.user.email, path.value));
const mergeTemplates = computed(() => [...new Map([...templates.value, ...(conflict.value?.templates ?? [])].map(value => [value.templateNo, value])).values()]);
let active = true, generation = 0, accessGeneration = 0;
let draftTimer: ReturnType<typeof setTimeout> | undefined;
let editingDraftKey: string | undefined;
let openedForEditing = false;

watch([() => props.editOnOpen, item, access], () => {
  if (props.editOnOpen && !openedForEditing && canEdit.value) {
    openedForEditing = true;
    beginEdit();
  }
});

async function copyLink() {
  try { await navigator.clipboard.writeText(`${location.origin}/anime/${props.animeNo}`); toast.success('위키 주소를 복사했습니다.'); }
  catch { toast.error('주소를 복사하지 못했습니다.'); }
}
function persistDraft() {
  clearTimeout(draftTimer);
  if (!editing.value || !baseline.value) return;
  const changed = title.value !== baseline.value.title || !!summary.value.trim() || JSON.stringify(body.value) !== JSON.stringify(baseline.value.body);
  if (!changed) { removeWikiDraft(editingDraftKey); draftStatus.value = ''; return; }
  const draft: WikiDraft = {version: 1, baseline: baseline.value, body: body.value, templates: templates.value, title: title.value, summary: summary.value, savedAt: new Date().toISOString()};
  const saved = writeWikiDraft(editingDraftKey, draft);
  draftStatus.value = saved ? '작성 중인 내용은 이 탭에 임시 저장됩니다.' : '임시 저장을 사용할 수 없습니다. 저장 전에는 이 탭을 닫지 마세요.';
}
function discardDraft() { removeWikiDraft(draftKey.value); availableDraft.value = undefined; }
function resumeDraft() {
  const draft = availableDraft.value;
  if (!draft || item.value?.deleted || !access.value?.canContribute) return;
  baseline.value = draft.baseline; body.value = draft.body; templates.value = draft.templates; title.value = draft.title; summary.value = draft.summary;
  editingDraftKey = draftKey.value; availableDraft.value = undefined; saveError.value = ''; editing.value = true; ++editorVersion.value;
}

async function load() {
  const expected = ++generation, endpoint = path.value;
  loading.value = true; loadError.value = ''; item.value = undefined; editing.value = false; conflict.value = undefined;
  try {
    const value = props.newTemplate ? {title: '', body: wikiRemote.emptyBody(), revision: 0, templates: []} : await wikiRemote.request<WikiDocument>(endpoint);
    if (!active || expected !== generation) return;
    item.value = value;
    availableDraft.value = readWikiDraft(draftKey.value);
    if (props.newTemplate && access.value?.canContribute && !availableDraft.value) beginEdit();
  } catch (error) { if (active && expected === generation) loadError.value = wikiErrorMessage(error); }
  finally { if (expected === generation) loading.value = false; }
}
function beginEdit() {
  if (!item.value || item.value.deleted || !access.value?.canContribute) return;
  if (availableDraft.value) { resumeDraft(); return; }
  editingDraftKey = draftKey.value;
  baseline.value = JSON.parse(JSON.stringify(item.value));
  body.value = JSON.parse(JSON.stringify(item.value.body)); templates.value = [...(item.value.templates ?? [])]; title.value = item.value.title; summary.value = ''; saveError.value = ''; editing.value = true; ++editorVersion.value;
}
function cancelEdit() {
  if (editor.value?.dirty() && !window.confirm('작성 중인 내용을 취소하시겠습니까?')) return;
  clearTimeout(draftTimer); removeWikiDraft(editingDraftKey); availableDraft.value = undefined; draftStatus.value = '';
  editing.value = false; saveError.value = '';
}
async function save() {
  if (!baseline.value || saving.value || !access.value?.canContribute) return;
  const endpoint = path.value, expected = generation;
  saving.value = true; saveError.value = '';
  try {
    const value = await wikiRemote.request<WikiDocument>(endpoint, props.newTemplate ? 'POST' : 'PUT', {baseRevision: baseline.value.revision, body: body.value, summary: summary.value.trim(), ...(isTemplate.value ? {title: title.value.trim()} : {})});
    if (!active || expected !== generation) return;
    clearTimeout(draftTimer); removeWikiDraft(editingDraftKey); availableDraft.value = undefined; draftStatus.value = '';
    editor.value?.markSaved(); item.value = value; editing.value = false; toast.success('위키를 저장했습니다.');
    if (props.newTemplate) emit('created', value); else emit('saved', value);
  } catch (error) {
    if (!active || expected !== generation) return;
    saveError.value = wikiErrorMessage(error);
    if (error instanceof WikiError && error.status === 409 && !props.newTemplate) {
      try {
        const latest = await wikiRemote.request<WikiDocument>(endpoint);
        if (active && expected === generation) conflict.value = latest;
      } catch { saveError.value += '\n작성 중인 내용은 유지됩니다. 최신판을 불러오지 못했습니다.'; }
    }
  } finally { if (expected === generation) saving.value = false; }
}
function mergeResolved(value: WikiBody) {
  if (!conflict.value || !baseline.value) return;
  if (isTemplate.value && title.value !== baseline.value.title && conflict.value.title !== baseline.value.title && title.value !== conflict.value.title) {
    if (!window.confirm(`템플릿 이름도 변경되었습니다. 현재 이름: ${conflict.value.title}\n내가 입력한 이름 '${title.value}'을 사용하시겠습니까?`)) title.value = conflict.value.title;
  } else if (title.value === baseline.value.title) title.value = conflict.value.title;
  body.value = value; templates.value = mergeTemplates.value; baseline.value = conflict.value; item.value = conflict.value; conflict.value = undefined; saveError.value = '변경 내용을 합쳤습니다. 확인 후 저장해 주세요.'; ++editorVersion.value;
}
async function removeTemplate() {
  if (!isTemplate.value || !item.value || saving.value || !window.confirm('이 템플릿을 삭제하시겠습니까?')) return;
  saving.value = true; saveError.value = '';
  const expected = generation;
  try {
    await wikiRemote.request(path.value, 'DELETE', {baseRevision: item.value.revision});
    if (active && expected === generation) { emit('deleted'); toast.success('템플릿을 삭제했습니다.'); }
  } catch (error) { if (active && expected === generation) saveError.value = wikiErrorMessage(error); }
  finally { if (expected === generation) saving.value = false; }
}
async function recover() {
  if (!item.value || !access.value?.isOperator || saving.value || !window.confirm('이 템플릿을 복구하시겠습니까?')) return;
  const expected = generation;
  saving.value = true; saveError.value = '';
  try {
    const value = await wikiRemote.request<WikiDocument>(`${path.value}/recover`, 'POST', {baseRevision: item.value.revision});
    if (active && expected === generation) { item.value = value; toast.success('템플릿을 복구했습니다.'); emit('saved', value); }
  } catch (error) { if (active && expected === generation) saveError.value = wikiErrorMessage(error); }
  finally { if (expected === generation) saving.value = false; }
}
watch([() => session.user.email, () => session.user.roles.join(',')], async (_, previous) => {
  if (previous?.length && editing.value) { persistDraft(); editing.value = false; }
  const expected = ++accessGeneration;
  ++generation; saving.value = false; conflict.value = undefined;
  access.value = undefined;
  if (previous?.length) void load();
  else if (loading.value) void load();
  availableDraft.value = readWikiDraft(draftKey.value);
  try { const value = await wikiRemote.access(); if (active && expected === accessGeneration) { access.value = value; if (props.newTemplate && item.value && !editing.value && value.canContribute && !availableDraft.value) beginEdit(); } }
  catch (error) { if (active && expected === accessGeneration) access.value = {canContribute: false, isOperator: false, message: wikiErrorMessage(error)}; }
}, {immediate: true});
watch(path, load, {immediate: true});
watch([body, title, summary, templates], () => { if (editing.value) { clearTimeout(draftTimer); draftTimer = setTimeout(persistDraft, 350); } }, {deep: true});
window.addEventListener('beforeunload', persistDraft);
onBeforeUnmount(() => { persistDraft(); clearTimeout(draftTimer); window.removeEventListener('beforeunload', persistDraft); active = false; ++generation; ++accessGeneration; });
</script>

<style scoped>
.wiki-embedded {
  padding: 1.5rem;
}
.wiki-content {
  min-width: 0;
  overflow-wrap: anywhere;
}
.wiki-content > :deep(.as-post-body) {
  padding: 0;
  border-radius: 0;
}
.wiki-inline-state {
  padding: 1.25rem 0;
  color: var(--as-ink-3);
  font-size: 0.875rem;
}
.wiki-inline-error {
  padding: 1rem;
  border-radius: var(--radius-ctl);
  background: var(--as-muted);
}
.wiki-empty {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.25rem;
  border: 0.0625rem dashed var(--as-line-2);
  border-radius: var(--radius-ctl);
  color: var(--as-ink-2);
  font-size: 0.875rem;
}
.wiki-empty-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 2.5rem;
  height: 2.5rem;
  border-radius: var(--radius-ctl);
  background: var(--as-muted);
  color: var(--as-ink-3);
  font-size: 1rem;
}
@container site (width >= 48rem) {
  .wiki-embedded { padding: 2rem; }
}
</style>
