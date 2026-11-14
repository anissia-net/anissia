<template>
  <section>
    <p v-if="loading" class="as-empty" role="status">이미지를 불러오는 중입니다.</p>
    <div v-if="loadError" class="as-box p-4"><p class="text-sm text-red-600" role="alert">{{loadError}}</p><button type="button" class="as-link text-sm mt-2" @click="load">다시 시도</button></div>
    <template v-if="item">
      <p v-if="imageOwner" class="as-desc mb-4">문서에 첨부된 이미지입니다. <router-link :to="wikiImageOwnerLocation(imageOwner)" class="as-link">소속 문서 보기</router-link></p>
      <p v-if="item.attachmentActive === false" class="as-desc mb-4">첨부가 취소된 이미지입니다. 원본 파일과 과거 이력은 보존됩니다.</p>
      <div class="flex flex-wrap items-center justify-between gap-3 mb-4"><h2 class="as-section-title">이미지 정보</h2><span class="as-meta">{{statusLabel}}</span></div>
      <a v-if="item.url" :href="imageUrl" target="_blank" rel="noopener noreferrer" class="block as-box p-4 mb-4"><img :src="imageUrl" :alt="item.description || '위키 이미지'" class="max-h-96 w-full object-contain"/></a>
      <div v-else class="as-box p-5 mb-4 as-desc">{{statusLabel}}<template v-if="item.status === 'DELETED'"> · 복구하면 이미지를 다시 사용할 수 있습니다.</template></div>
      <p v-if="item.error" class="text-sm text-red-600 mb-4" role="alert">{{item.error}}</p>
      <p v-if="item.replacementError" class="text-sm text-red-600 mb-4" role="alert">이미지 교체에 실패했습니다. {{item.replacementError}}</p>
      <p v-if="item.replacementPending" class="as-desc mb-4" role="status">새 이미지를 처리하고 있습니다. 처리가 끝나면 같은 주소의 이미지가 교체됩니다.</p>
      <p v-if="transitioning" class="as-desc mb-4" role="status">파일 처리가 끝나면 상태가 자동으로 갱신됩니다.</p>
      <div v-if="editing" data-save-scope="image-detail" class="space-y-3" role="group" aria-label="이미지 정보 수정">
        <Teleport to="body"><form :id="formId" hidden @submit.prevent.stop="saveMetadata"></form></Teleport>
        <fieldset :disabled="busy || item.replacementPending" class="space-y-3">
          <label v-if="access?.canReplaceImageFile === true" class="block"><span class="as-meta">교체할 이미지 (선택)</span><input :form="formId" type="file" accept="image/png,image/jpeg,image/webp,image/gif,image/avif,image/apng,image/bmp" class="block w-full text-sm mt-2" @change="pickReplacement"/></label>
          <p v-if="replacementFile" class="as-desc">파일을 교체하면 소속 문서와 해당 문서의 이력에 반영됩니다. 템플릿 이미지라면 그 템플릿을 포함한 문서에도 반영됩니다. 새 이미지의 설명과 출처를 함께 입력해 주세요.</p>
          <label class="block"><span class="as-meta">이미지 설명</span><textarea v-model="description" :form="formId" :required="!!replacementFile" class="as-input-text px-3 py-2 w-full mt-1" maxlength="500" rows="3"></textarea></label>
          <label class="block"><span class="as-meta">출처</span><input v-model="sourceUrl" :form="formId" :required="!!replacementFile" class="as-input-text px-3 py-2 w-full mt-1" type="url" maxlength="1024" placeholder="https://"/></label>
          <div class="flex justify-end gap-2"><button type="button" class="as-input-btn py-2" @click="cancelEdit">취소</button><button type="submit" :form="formId" data-save-button class="as-btn-primary py-2" :disabled="!canSaveImage">{{busy ? '저장 중…' : replacementFile ? '파일 교체 및 정보 저장' : '정보 저장'}}</button></div>
        </fieldset>
        <progress v-if="busy && replacementFile" :value="replacementProgress" max="100" class="w-full h-2 accent-brand" aria-label="교체 이미지 전송률"></progress>
      </div>
      <template v-else>
        <p class="text-sm whitespace-pre-wrap break-words mb-2">{{item.description || '등록된 설명이 없습니다.'}}</p>
        <a v-if="item.sourceUrl" :href="item.sourceUrl" target="_blank" rel="noopener noreferrer" class="as-link text-sm break-all">{{item.sourceUrl}}</a>
        <p class="as-meta mt-3">{{dimensions}}<template v-if="item.updatedBy"> · {{item.updatedBy}}</template></p><p class="as-meta mt-1">{{wikiDate(item.updatedAt || item.createdAt)}} · {{item.revision}}판</p>
        <div class="flex flex-wrap gap-3 items-center mt-4 text-sm">
          <button v-if="item.url" type="button" class="as-link" @click="copyUrl">주소 복사</button>
          <button v-if="imageOwner && canEditImage && item.status === 'READY'" type="button" class="as-input-btn py-2" :disabled="busy || item.replacementPending" @click="beginEdit">이미지 수정</button>
          <button v-if="access?.canContribute && item.status === 'READY' && item.attachmentActive !== false" type="button" class="as-input-btn py-2" :disabled="busy || !usage?.canDetach || usedInDraft" @click="detach">첨부 취소</button>
          <button v-if="access?.isOperator && item.status === 'READY'" type="button" class="as-input-btn py-2" :disabled="busy || item.replacementPending || !usage?.canDelete" @click="changeState('delete')">이미지 삭제</button>
          <router-link v-else-if="imageOwner && access?.canContribute && item.status === 'READY' && !item.replacementPending" :to="{path: '/wiki/requests/new', query: {kind: 'IMAGE_DELETE', imageId: item.id, ...wikiImageOwnerFields(imageOwner)}}" class="as-input-btn py-2">이미지 삭제 요청</router-link>
          <button v-if="access?.isOperator && item.status === 'DELETED'" type="button" class="as-btn-primary py-2" :disabled="busy" @click="changeState('restore')">이미지 복구</button>
        </div>
        <p v-if="access?.canContribute && item.status === 'READY' && item.attachmentActive !== false && usage" class="as-desc mt-3">{{usage.canDetach && !usedInDraft ? '첨부 취소는 이 문서의 이미지 목록에서만 제외합니다. 원본 파일과 과거 이력은 보존됩니다.' : '본문이나 썸네일에서 사용 중인 이미지는 사용을 해제하고 저장한 뒤 첨부를 취소할 수 있습니다.'}}</p>
        <p v-if="!access?.canContribute && item.status === 'READY'" class="as-desc mt-3">{{access?.message}}</p>
      </template>
      <p v-if="actionError" class="text-sm text-red-600 whitespace-pre-line mt-3" role="alert">{{actionError}}</p>
      <div v-if="conflicted" class="as-box p-3 mt-3 text-sm"><p>작성한 내용은 유지됩니다. 최신 정보를 불러오면 입력한 수정 내용이 바뀝니다.</p><button type="button" class="as-link mt-2" @click="reloadMetadata">최신 정보 불러오기</button></div>
      <div class="border-t border-line mt-5 pt-4">
        <h3 class="font-semibold mb-3">사용처</h3>
        <p v-if="usage" class="as-meta mb-3">현재 문서 {{usage.currentCount}}개 · 과거 이력 {{usage.historyCount}}개</p>
        <p v-if="usage && usage.currentCount + usage.historyCount > usage.totalElements" class="as-desc mb-3">일부 보존 이력은 운영자만 확인할 수 있습니다.</p>
        <p v-if="access?.isOperator && usage && !usage.canDelete && item.status === 'READY'" class="as-desc mb-3">현재 문서 또는 과거 이력에서 사용하는 이미지는 삭제할 수 없습니다.</p>
        <ul class="space-y-2 text-sm"><li v-for="(entry, index) in usage?.content" :key="`${entry.kind}:${entry.targetNo}:${entry.scope}:${entry.revision}:${index}`"><router-link :to="wikiLocation(entry)" class="as-link">{{entry.title}}</router-link><span class="as-meta ml-2">{{entry.kind === 'anime' ? '애니 위키' : '템플릿'}} · {{entry.scope === 'history' ? `${entry.revision}판 이력` : '현재 문서'}}</span></li></ul>
        <p v-if="usageLoading" class="as-desc" role="status">사용처를 불러오는 중입니다.</p><p v-else-if="usage && !usage.content.length" class="as-desc">{{usage.canDelete ? '현재 문서와 과거 이력에서 사용하지 않는 이미지입니다.' : '공개된 사용처가 없습니다.'}}</p>
        <p v-if="usageError" class="text-sm text-red-600" role="alert">{{usageError}} <button type="button" class="as-link" @click="loadUsage(false)">다시 시도</button></p>
        <button v-if="usage && usage.content.length < usage.totalElements && !usageLoading" type="button" class="as-input-btn mt-3 py-1.5" @click="loadUsage(true)">사용처 더 보기</button>
      </div>
    </template>
  </section>
</template>
<script setup lang="ts">
import './wiki.pcss';
import {computed, onBeforeUnmount, ref, useId, watch} from 'vue';
import {onBeforeRouteLeave, onBeforeRouteUpdate} from 'vue-router';
import {sessionStore} from '../session/sessionStore';
import toast from '../../common/toast';
import {WikiError, wikiDate, wikiErrorMessage, wikiFileRequest, wikiLocation, wikiRemote, type WikiAccess, type WikiImage, type WikiImageUsagePage} from './wikiRemote';
import {wikiImageDisplayUrl} from './wikiImageVersion';
import {wikiImageOwner, wikiImageOwnerFields, wikiImageOwnerLocation, type WikiImageOwner} from './wikiImageOwner';
const props = defineProps<{imageId: string; owner: WikiImageOwner; usedInDraft?: boolean}>();
const formId = `wiki-image-edit-${useId()}`;
const emit = defineEmits<{changed: [image: WikiImage]}>();
const session = sessionStore();
const item = ref<WikiImage>(), access = ref<WikiAccess>(), usage = ref<WikiImageUsagePage>();
const imageOwner = computed(() => wikiImageOwner(item.value?.documentKind, item.value?.documentNo));
const loading = ref(false), loadError = ref(''), editing = ref(false), busy = ref(false), actionError = ref(''), conflicted = ref(false);
const description = ref(''), sourceUrl = ref(''), usageLoading = ref(false), usageError = ref('');
const replacementFile = ref<File>(), replacementProgress = ref(0);
const canEditImage = computed(() => item.value?.attachmentActive !== false && (access.value?.canContribute === true || access.value?.canReplaceImageFile === true));
const canSaveImage = computed(() => item.value?.attachmentActive !== false && (replacementFile.value ? access.value?.canReplaceImageFile === true : access.value?.canContribute === true));
let cancelReplacement: (() => void) | undefined;
let active = true, generation = 0, usageGeneration = 0, usagePage = 0, editRevision = 0;
let timer: ReturnType<typeof setTimeout> | undefined;
const transitioning = computed(() => !!item.value && (!!item.value.replacementPending || ['WAITING', 'PROCESSING', 'DELETING', 'RESTORING', 'APPROVING'].includes(item.value.status)));
const statusLabel = computed(() => ({WAITING: '압축 대기', PROCESSING: '압축 중', READY: '사용 가능', FAILED: '압축 실패', DELETING: '삭제 처리 중', DELETED: '삭제됨', RESTORING: '복구 처리 중', REVIEW_READY: '검토 대기', APPROVING: '등록 처리 중', CANCELLED: '취소됨'}[item.value?.status ?? 'READY']));
const imageUrl = computed(() => item.value?.url ? wikiImageDisplayUrl(item.value.url, item.value.revision) : undefined);
const dimensions = computed(() => [item.value?.width && item.value?.height ? `${item.value.width} × ${item.value.height}` : '', item.value?.bytes ? `${(item.value.bytes / 1024).toFixed(0)} KB` : '', item.value?.animated ? '움직이는 이미지' : ''].filter(Boolean).join(' · '));
function dirty() { return editing.value && (!!replacementFile.value || description.value !== item.value?.description || sourceUrl.value !== item.value?.sourceUrl); }
function confirmLeave() { return !busy.value && (!dirty() || window.confirm('저장하지 않은 이미지 정보가 있습니다. 나가시겠습니까?')); }
function beforeUnload(event: BeforeUnloadEvent) { if (dirty()) { event.preventDefault(); event.returnValue = ''; } }
defineExpose({confirmLeave, dirty});
onBeforeRouteLeave(confirmLeave); onBeforeRouteUpdate(confirmLeave);
function pickReplacement(event: Event) { if (access.value?.canReplaceImageFile === true) replacementFile.value = (event.target as HTMLInputElement).files?.[0]; }
function beginEdit() { if (!item.value || item.value.replacementPending || !canEditImage.value) return; replacementFile.value = undefined; editRevision = item.value.revision; description.value = item.value.description; sourceUrl.value = item.value.sourceUrl; editing.value = true; actionError.value = ''; conflicted.value = false; }
function cancelEdit() { if (dirty() && !window.confirm('이미지 정보 수정을 취소하시겠습니까?')) return; editing.value = false; actionError.value = ''; conflicted.value = false; }
async function reloadMetadata() { if (!confirmLeave()) return; await load(); if (item.value && item.value.status === 'READY') beginEdit(); }
async function copyUrl() { try { await navigator.clipboard.writeText(new URL(item.value!.url!, location.origin).href); toast.success('이미지 주소를 복사했습니다.'); } catch { toast.error('이미지 주소를 복사하지 못했습니다.'); } }
async function load() {
  clearTimeout(timer); cancelReplacement?.(); replacementFile.value = undefined; const expected = ++generation;
  ++usageGeneration; loading.value = true; loadError.value = ''; item.value = undefined; usage.value = undefined; access.value = undefined; editing.value = false; busy.value = false; actionError.value = ''; conflicted.value = false;
  try {
    const [image, permission] = await Promise.all([wikiRemote.image(props.imageId), wikiRemote.access()]);
    if (!active || expected !== generation) return;
    if (image.documentKind !== props.owner.documentKind || image.documentNo !== props.owner.documentNo) throw new Error('이 문서에 속한 이미지가 아닙니다.');
    item.value = image; access.value = permission; emit('changed', image); void loadUsage(false); schedulePoll(expected);
  } catch (error) { if (active && expected === generation) loadError.value = wikiErrorMessage(error); }
  finally { if (expected === generation) loading.value = false; }
}
function schedulePoll(expected: number) { clearTimeout(timer); if (transitioning.value) timer = setTimeout(() => void poll(expected), 1800); }
async function poll(expected: number) {
  try {
    const value = await wikiRemote.image(props.imageId);
    if (!active || expected !== generation) return;
    item.value = value; emit('changed', value); schedulePoll(expected);
    if (!transitioning.value) void loadUsage(false);
  } catch (error) { if (active && expected === generation) actionError.value = `${wikiErrorMessage(error)} 새로 열어 처리 결과를 확인해 주세요.`; }
}
async function saveMetadata() {
  if (!item.value || busy.value || item.value.replacementPending || !canSaveImage.value) return;
  if (replacementFile.value && (!description.value.trim() || !sourceUrl.value.trim())) { actionError.value = '이미지를 교체할 때는 설명과 출처를 모두 입력해 주세요.'; return; }
  const expected = generation; busy.value = true; actionError.value = ''; conflicted.value = false;
  try {
    const transfer = replacementFile.value ? wikiFileRequest<WikiImage>(`/images/${props.imageId}/file`, 'PUT', replacementFile.value, {baseRevision: String(editRevision), description: description.value.trim(), sourceUrl: sourceUrl.value.trim()}, value => { if (active && expected === generation) replacementProgress.value = value; }) : undefined;
    cancelReplacement = transfer?.cancel;
    const value = transfer ? await transfer.promise : await wikiRemote.request<WikiImage>(`/images/${props.imageId}`, 'PUT', {baseRevision: editRevision, description: description.value.trim(), sourceUrl: sourceUrl.value.trim()});
    if (active && expected === generation) { item.value = value; editing.value = false; replacementFile.value = undefined; emit('changed', value); schedulePoll(expected); toast.success(transfer ? '이미지 교체를 시작했습니다.' : '이미지 정보를 저장했습니다.'); }
  } catch (error) { if (active && expected === generation) { actionError.value = wikiErrorMessage(error); conflicted.value = error instanceof WikiError && error.status === 409; } }
  finally { if (expected === generation) { busy.value = false; cancelReplacement = undefined; } }
}
async function changeState(action: 'delete' | 'restore') {
  if (!item.value || busy.value || item.value.replacementPending || !access.value?.isOperator || !window.confirm(action === 'delete' ? '이 이미지를 삭제하시겠습니까? 운영자가 나중에 복구할 수 있습니다.' : '이 이미지를 복구하시겠습니까?')) return;
  const expected = generation; busy.value = true; actionError.value = '';
  try {
    const value = await wikiRemote.request<WikiImage>(`/images/${props.imageId}${action === 'restore' ? '/restore' : ''}`, action === 'restore' ? 'POST' : 'DELETE', {baseRevision: item.value.revision});
    if (active && expected === generation) { item.value = value; emit('changed', value); schedulePoll(expected); if (!transitioning.value) void loadUsage(false); }
  } catch (error) { if (active && expected === generation) actionError.value = wikiErrorMessage(error); }
  finally { if (expected === generation) busy.value = false; }
}
async function detach() {
  if (!item.value || busy.value || !access.value?.canContribute || !usage.value?.canDetach || props.usedInDraft || !window.confirm('이 문서의 이미지 목록에서 첨부를 취소하시겠습니까? 원본 파일과 과거 이력은 보존됩니다.')) return;
  const expected = generation; busy.value = true; actionError.value = '';
  try {
    const value = await wikiRemote.detachImage(props.imageId, item.value.revision);
    if (active && expected === generation) { item.value = value; emit('changed', value); void loadUsage(false); toast.success('이미지 첨부를 취소했습니다.'); }
  } catch (error) { if (active && expected === generation) actionError.value = wikiErrorMessage(error); }
  finally { if (expected === generation) busy.value = false; }
}
async function loadUsage(append: boolean) {
  if (append && usageLoading.value) return;
  const expected = ++usageGeneration, next = append ? usagePage + 1 : 0;
  usageLoading.value = true; usageError.value = '';
  try {
    const value = await wikiRemote.request<WikiImageUsagePage>(`/images/${props.imageId}/usage?page=${next}`);
    if (active && expected === usageGeneration) { usage.value = {...value, content: append ? [...(usage.value?.content ?? []), ...value.content] : value.content}; usagePage = next; }
  } catch (error) { if (active && expected === usageGeneration) usageError.value = wikiErrorMessage(error); }
  finally { if (expected === usageGeneration) usageLoading.value = false; }
}
watch([() => props.imageId, () => props.owner.documentKind, () => props.owner.documentNo, () => session.user.email, () => session.user.roles.join(',')], load, {immediate: true});
window.addEventListener('beforeunload', beforeUnload);
onBeforeUnmount(() => { window.removeEventListener('beforeunload', beforeUnload); active = false; ++generation; ++usageGeneration; clearTimeout(timer); cancelReplacement?.(); });
</script>
