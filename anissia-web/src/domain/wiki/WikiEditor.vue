<template>
  <div class="as-post as-post-editor">
    <div ref="root" class="nabi">
      <div ref="chrome" class="nabi-toolbar">
        <div class="nabi-toolbar-row"><span ref="viewTools"></span><div ref="toolbar"></div></div>
        <div ref="context" class="nabi-context" hidden></div>
      </div>
      <div ref="surface" class="nabi-content" contenteditable="true"></div>
    </div>
    <Teleport to="body">
    <dialog ref="imageDialog" class="as-wiki-dialog as-box" @click="backdrop($event, imageDialog)" @close="imagePicker = false">
      <div class="flex items-center justify-between gap-3 mb-5"><h2 class="as-section-title">이미지 선택</h2><button type="button" class="as-input-btn px-3 py-1" @click="imageDialog?.close()">닫기</button></div>
      <WikiImageLibrary v-if="imagePicker" selectable @select="insertImage"/>
    </dialog>
    <dialog ref="templateDialog" class="as-wiki-dialog as-box" @click="backdrop($event, templateDialog)">
      <div class="flex items-center justify-between gap-3 mb-5"><h2 class="as-section-title">템플릿 선택</h2><button type="button" class="as-input-btn px-3 py-1" @click="templateDialog?.close()">닫기</button></div>
      <form class="flex gap-2" @submit.prevent.stop="findTemplates(false)"><input v-model="templateQuery" class="as-input-text px-3 py-2 flex-1 min-w-0" placeholder="템플릿 검색" aria-label="템플릿 검색"/><button class="as-input-btn px-3" type="submit">검색</button></form>
      <p v-if="templateLoading" class="as-empty" role="status">템플릿을 불러오는 중입니다.</p>
      <p v-if="templateError" class="text-sm text-red-600 my-4" role="alert">{{templateError}}</p>
      <ul class="mt-4 divide-y divide-line"><li v-for="item in templateResults" :key="item.templateNo" class="py-3"><button type="button" class="as-link text-left" @click="insertTemplate(item)">{{item.title}}</button></li></ul>
      <p v-if="!templateLoading && !templateResults.length && !templateError" class="as-empty">템플릿이 없습니다.</p>
      <button v-if="templateHasMore && !templateLoading" type="button" class="as-input-btn py-1.5 mt-3 mr-3" @click="findTemplates(true)">템플릿 더 보기</button>
      <router-link to="/wiki/templates" target="_blank" class="as-link text-sm">템플릿 관리 열기</router-link>
    </dialog>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import 'nabi-note/nabi.css';
import '../../common/nabi.pcss';
import './wiki.pcss';
import {nextTick, onBeforeUnmount, onMounted, ref} from 'vue';
import {onBeforeRouteLeave, onBeforeRouteUpdate} from 'vue-router';
import {createNabiWith, mountSurface, mountToolbar, mountContextToolbar, mountHints, mountViewTools, mountPickedMark, mountSticky, watchSettle, type Nabi} from 'nabi-note';
import {attachViewer} from 'nabi-note/viewer';
import WikiImageLibrary from './WikiImageLibrary.vue';
import {normalizeWikiBlocks, wikiWings} from './wikiNote';
import {wikiRemote, wikiErrorMessage, type WikiBody, type WikiTemplate, type WikiImage, type WikiDocument} from './wikiRemote';

const props = defineProps<{modelValue: WikiBody; savedBody?: WikiBody; additionalDirty?: boolean; templates?: WikiTemplate[]; allowTemplates?: boolean}>();
const emit = defineEmits<{'update:modelValue': [body: WikiBody]; 'update:templates': [templates: WikiTemplate[]]}>();
const root = ref<HTMLElement>(), chrome = ref<HTMLElement>(), toolbar = ref<HTMLElement>(), context = ref<HTMLElement>(), surface = ref<HTMLElement>(), viewTools = ref<HTMLElement>();
const imageDialog = ref<HTMLDialogElement>(), templateDialog = ref<HTMLDialogElement>();
const imagePicker = ref(false), templateQuery = ref(''), templateLoading = ref(false), templateError = ref('');
const templateResults = ref<WikiDocument[]>([]);
const templateHasMore = ref(false);
let knownTemplates: WikiTemplate[] = [...(props.templates ?? [])];
let nabi: Nabi | undefined;
let cleanBody = JSON.stringify(props.savedBody?.blocks ?? props.modelValue.blocks);
let saved = false;
let unmounts: (() => void)[] = [];
let active = true, templateGeneration = 0, templatePage = 0, submittedTemplateQuery = '';
function dirty() { return !saved && (!!props.additionalDirty || (!!nabi && JSON.stringify(nabi.getJson()) !== cleanBody)); }
function confirmLeave() { return !dirty() || window.confirm('저장하지 않은 위키 내용이 있습니다. 나가시겠습니까?'); }
function markSaved() { cleanBody = JSON.stringify(nabi?.getJson() ?? props.modelValue.blocks); saved = true; }
defineExpose({markSaved, dirty});
onBeforeRouteLeave(confirmLeave);
onBeforeRouteUpdate(confirmLeave);
function beforeUnload(event: BeforeUnloadEvent) { if (dirty()) { event.preventDefault(); event.returnValue = ''; } }
function backdrop(event: MouseEvent, dialog?: HTMLDialogElement) { if (event.target === dialog) { const rect = dialog.getBoundingClientRect(); if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close(); } }
async function openImages() { imagePicker.value = true; await nextTick(); imageDialog.value?.showModal(); }
async function openTemplates() { templateDialog.value?.showModal(); await findTemplates(); }
async function findTemplates(append = false) {
  if (append && templateLoading.value) return;
  const expected = ++templateGeneration;
  const page = append ? templatePage + 1 : 0;
  if (!append) { submittedTemplateQuery = templateQuery.value.trim(); templateResults.value = []; }
  templateLoading.value = true; templateError.value = '';
  try {
    const result = await wikiRemote.templates(page, submittedTemplateQuery);
    if (active && expected === templateGeneration) { templateResults.value = append ? [...templateResults.value, ...result.content] : result.content; templatePage = page; templateHasMore.value = templateResults.value.length < result.totalElements; }
  }
  catch (error) { if (active && expected === templateGeneration) templateError.value = wikiErrorMessage(error); }
  finally { if (expected === templateGeneration) templateLoading.value = false; }
}
function insertImage(item: WikiImage) {
  if (!item.url || item.status !== 'READY') return;
  imageDialog.value?.close();
  nabi?.applyCommand('insertImage', {src: item.url}, 'pointer');
  surface.value?.focus();
}
async function insertTemplate(item: WikiDocument) {
  if (!item.templateNo) return;
  try {
    const full = await wikiRemote.request<WikiDocument>(`/templates/${item.templateNo}`);
    if (!active || !full.templateNo) return;
    const record = {templateNo: full.templateNo, title: full.title, body: full.body, revision: full.revision};
    knownTemplates = [...knownTemplates.filter(value => value.templateNo !== record.templateNo), record];
    emit('update:templates', knownTemplates);
    templateDialog.value?.close();
    nabi?.applyCommand('insertWikiTemplate', {templateNo: record.templateNo}, 'pointer');
    surface.value?.focus();
  } catch (error) { templateError.value = wikiErrorMessage(error); }
}
onMounted(() => {
  const built = createNabiWith(wikiWings(() => knownTemplates, props.allowTemplates !== false), {locale: 'ko', allowLocalUrls: false, ask: {message: text => window.alert(text), confirm: text => window.confirm(text)}});
  nabi = built.nabi;
  nabi.setJson(props.modelValue.blocks);
  cleanBody = JSON.stringify(props.savedBody ? normalizeWikiBlocks(props.savedBody, knownTemplates, props.allowTemplates !== false) : nabi.getJson());
  const registry = built.registry, content = surface.value!;
  const editor = mountSurface({nabi, registry, root: content, locale: 'ko', placeholder: props.allowTemplates === false ? '공유할 템플릿 내용을 작성해 주세요.' : '이 작품의 위키를 함께 작성해 주세요.'});
  const settle = watchSettle(document, {surface: content});
  const shared = {nabi, registry, surface: content, settle, locale: 'ko'};
  const tools = mountToolbar({...shared, root: toolbar.value!, onHost: wing => { if (wing === 'img') void openImages(); if (wing === 'exTemplateInclude') void openTemplates(); }});
  const contextual = mountContextToolbar({...shared, root: context.value!});
  const hints = mountHints({toolbar: tools, context: contextual, root: chrome.value!, surface: content});
  const views = mountViewTools({nabi, root: root.value!, surface: content, container: viewTools.value!, locale: 'ko', onBody: body => { const viewer = attachViewer(body, {locale: 'ko'}); return () => viewer.unmount(); }});
  const sticky = mountSticky({nabi, root: root.value!, surface: content, chrome: chrome.value!, settle});
  const picked = mountPickedMark({nabi, surface: content});
  const changed = nabi.onChange(() => emit('update:modelValue', {schemaVersion: 1, blocks: nabi!.getJson()}));
  unmounts = [changed, () => picked.unmount(), () => sticky.unmount(), () => views.unmount(), () => hints.unmount(), () => contextual.unmount(), () => tools.unmount(), () => settle.unmount(), () => editor.unmount()];
  window.addEventListener('beforeunload', beforeUnload);
});
onBeforeUnmount(() => { active = false; ++templateGeneration; unmounts.forEach(off => off()); window.removeEventListener('beforeunload', beforeUnload); });
</script>
