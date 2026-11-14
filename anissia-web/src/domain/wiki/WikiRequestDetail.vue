<template>
  <section>
    <p v-if="loading" class="as-empty" role="status">요청을 불러오는 중입니다.</p>
    <p v-if="error" class="text-sm text-red-600 mb-4" role="alert">{{error}} <button type="button" class="as-link" @click="load">다시 시도</button></p>
    <article v-if="item" class="as-box p-5 cq-md:p-7">
      <div class="flex flex-wrap justify-between items-start gap-3"><h2 class="as-section-title break-words">{{item.title}}</h2><span class="as-tag-xs">{{wikiRequestStatuses[item.status]}}</span></div>
      <p class="as-meta mt-3">{{wikiRequestKinds[item.kind]}} · {{item.createdBy}} · {{wikiDate(item.createdAt)}}</p>
      <div class="mt-6"><h3 class="font-semibold text-sm mb-2">요청 사유</h3><p class="text-sm whitespace-pre-wrap break-words">{{item.reason}}</p></div>
      <div v-if="item.body" class="mt-6"><h3 class="font-semibold text-sm mb-3">등록할 템플릿</h3><WikiBodyView :body="item.body" :floating-outline="false"/></div>
      <div v-if="item.kind === 'IMAGE_CREATE'" class="mt-6 space-y-3">
        <h3 class="font-semibold text-sm">등록할 이미지</h3>
        <p v-if="imageOwner" class="text-sm"><router-link :to="wikiImageOwnerLocation(imageOwner)" class="as-link">이미지가 속한 {{imageOwner.documentKind === 'anime' ? '위키 문서' : '템플릿'}} 보기</router-link></p>
        <img v-if="preview || item.image?.url" :src="preview || (item.image?.url ? wikiImageDisplayUrl(item.image.url, item.image.revision) : undefined)" :alt="item.description || '요청 이미지'" class="max-h-96 w-full object-contain rounded-ctl bg-canvas p-3"/>
        <p v-else-if="imageProcessing" class="as-desc" role="status">이미지를 준비하고 있습니다. 처리 상태가 자동으로 갱신됩니다.</p>
        <p v-if="previewError || item.image?.error" class="text-sm text-red-600" role="alert">{{item.image?.error || previewError}} <button v-if="!item.image?.error" type="button" class="as-link" @click="loadPreview">다시 시도</button></p>
        <p class="text-sm whitespace-pre-wrap break-words">{{item.description}}</p>
        <a v-if="item.sourceUrl" :href="item.sourceUrl" class="as-link text-sm break-all" target="_blank" rel="noopener noreferrer">{{item.sourceUrl}}</a>
      </div>
      <router-link v-if="targetLocation" :to="targetLocation" class="as-link text-sm inline-block mt-5">{{item.kind.endsWith('DELETE') ? '삭제 요청 대상 보기' : item.kind === 'IMAGE_CREATE' ? '등록된 이미지 보기' : '등록된 문서 보기'}}</router-link>
      <div v-if="item.status !== 'PENDING'" class="border-t border-line mt-6 pt-5 space-y-2">
        <p class="as-meta">{{item.processedBy || item.createdBy}} · {{wikiDate(item.processedAt || undefined)}}</p>
        <h3 v-if="item.processingReason" class="font-semibold text-sm">처리 사유</h3><p class="text-sm whitespace-pre-wrap break-words">{{item.processingReason}}</p>
      </div>
      <form v-if="item.canProcess && item.status === 'PENDING'" class="border-t border-line mt-6 pt-5 space-y-3" @submit.prevent="process('approve')">
        <label class="block"><span class="as-meta">처리 사유</span><textarea v-model="processingReason" required maxlength="2000" rows="3" class="as-input-text w-full px-3 py-2 mt-1" :disabled="busy" placeholder="승인 또는 반려 사유를 적어 주세요."></textarea></label>
        <div class="flex flex-wrap justify-end gap-2"><button type="button" class="as-input-btn py-2" :disabled="busy || !processingReason.trim()" @click="process('reject')">반려</button><button type="submit" class="as-btn-primary py-2" :disabled="busy || !processingReason.trim() || !item.canApprove">{{busy ? '처리 중…' : '승인하고 처리'}}</button></div>
      </form>
      <div v-if="item.canCancel && item.status === 'PENDING'" class="mt-5 text-right"><button type="button" class="as-link text-sm" :disabled="busy" @click="process('cancel')">요청 취소</button></div>
      <p v-if="actionError" class="text-sm text-red-600 whitespace-pre-line mt-3" role="alert">{{actionError}}</p>
    </article>
  </section>
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue';
import {sessionStore} from '../session/sessionStore';
import toast from '../../common/toast';
import WikiBodyView from './WikiBodyView.vue';
import {wikiDate, wikiErrorMessage} from './wikiRemote';
import {wikiImageDisplayUrl} from './wikiImageVersion';
import {wikiImageOwner, wikiImageOwnerLocation, wikiImageLocation} from './wikiImageOwner';
import {wikiRequestKinds, wikiRequestStatuses, wikiRequestRemote, wikiRequestResult, type WikiRequest} from './wikiRequestRemote';
const props = defineProps<{requestId: number}>();
const session = sessionStore();
const item = ref<WikiRequest>(), loading = ref(false), error = ref(''), busy = ref(false), actionError = ref(''), processingReason = ref('');
const preview = ref(''), previewError = ref('');
let active = true, generation = 0, previewGeneration = 0, timer: ReturnType<typeof setTimeout> | undefined;
let previewAbort: AbortController | undefined;
const imageProcessing = computed(() => !!item.value?.image && ['WAITING', 'PROCESSING', 'APPROVING'].includes(item.value.image.status));
const imageOwner = computed(() => wikiImageOwner(item.value?.image?.documentKind, item.value?.image?.documentNo));
const targetLocation = computed(() => {
  if (!item.value) return;
  const result = wikiRequestResult(item.value);
  if (result) return result;
  if (item.value.kind === 'TEMPLATE_DELETE' && item.value.targetNo) return `/wiki/templates?templateNo=${item.value.targetNo}`;
  if (item.value.kind === 'IMAGE_DELETE' && item.value.imageId) return item.value.image ? wikiImageLocation(item.value.image) : `/wiki/images?imageId=${encodeURIComponent(item.value.imageId)}`;
});
function clearPreview() { ++previewGeneration; previewAbort?.abort(); if (preview.value) URL.revokeObjectURL(preview.value); preview.value = ''; previewError.value = ''; }
async function loadPreview() {
  if (!item.value?.imagePreviewUrl || preview.value) return;
  const expected = ++previewGeneration; previewAbort?.abort(); previewAbort = new AbortController(); previewError.value = '';
  try {
    const url = await wikiRequestRemote.preview(props.requestId, previewAbort.signal);
    if (active && expected === previewGeneration) preview.value = url; else URL.revokeObjectURL(url);
  } catch (cause) { if (active && expected === previewGeneration && !(cause instanceof DOMException && cause.name === 'AbortError')) previewError.value = wikiErrorMessage(cause); }
}
function schedulePoll() { clearTimeout(timer); if (imageProcessing.value) timer = setTimeout(() => void refresh(), 2000); }
async function refresh() {
  const expected = generation;
  try { const value = await wikiRequestRemote.get(props.requestId); if (active && expected === generation) { item.value = value; void loadPreview(); schedulePoll(); } }
  catch (cause) { if (active && expected === generation) actionError.value = wikiErrorMessage(cause); }
}
async function load() {
  const expected = ++generation; clearTimeout(timer); clearPreview(); item.value = undefined; loading.value = true; busy.value = false; error.value = ''; actionError.value = ''; processingReason.value = '';
  try { const value = await wikiRequestRemote.get(props.requestId); if (active && expected === generation) { item.value = value; void loadPreview(); schedulePoll(); } }
  catch (cause) { if (active && expected === generation) error.value = wikiErrorMessage(cause); }
  finally { if (expected === generation) loading.value = false; }
}
async function process(action: 'approve' | 'reject' | 'cancel') {
  if (!item.value || busy.value || item.value.status !== 'PENDING') return;
  if (action !== 'cancel' && (!item.value.canProcess || !processingReason.value.trim())) return;
  if (action === 'approve' && !item.value.canApprove) return;
  if (action === 'cancel' && (!item.value.canCancel || !window.confirm('이 요청을 취소하시겠습니까?'))) return;
  if (action === 'approve' && item.value.kind.endsWith('DELETE') && !window.confirm('요청을 승인하고 대상을 삭제하시겠습니까?')) return;
  const expected = ++generation; busy.value = true; actionError.value = ''; clearTimeout(timer);
  try { const value = await wikiRequestRemote.process(props.requestId, action, processingReason.value.trim()); if (active && expected === generation) { item.value = value; processingReason.value = ''; schedulePoll(); toast.success('요청을 처리했습니다.'); } }
  catch (cause) { if (active && expected === generation) actionError.value = wikiErrorMessage(cause); }
  finally { if (expected === generation) { busy.value = false; schedulePoll(); } }
}
watch([() => props.requestId, () => session.user.email, () => session.user.roles.join(',')], load, {immediate: true});
onBeforeUnmount(() => { active = false; ++generation; clearTimeout(timer); clearPreview(); });
</script>
