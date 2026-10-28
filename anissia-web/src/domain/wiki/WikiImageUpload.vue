<template>
    <form v-if="access?.canContribute" class="as-box p-5 space-y-4" @submit.prevent.stop="startUpload">
      <p class="as-desc">이미지는 비율을 유지해 최대 512 × 512로 줄이고, 긴 쪽의 양 끝을 잘라 64 × 64 썸네일도 만듭니다. 움직이는 이미지의 썸네일은 첫 장면을 사용합니다.</p>
      <fieldset :disabled="!access?.canContribute || uploading || processing" class="space-y-3 disabled:opacity-60">
        <label class="block border border-dashed border-line-2 rounded-ctl p-5 text-center cursor-pointer" @dragover.prevent @drop.prevent="drop">
          <span class="block text-sm text-ink-2">{{file?.name || '이미지를 선택하거나 여기에 놓으세요.'}}</span>
          <input ref="fileInput" type="file" accept="image/png,image/jpeg,image/webp,image/gif,image/avif,image/apng,image/bmp" class="block w-full text-sm mt-3" @change="pick" aria-label="업로드할 이미지"/>
        </label>
        <label class="block"><span class="as-meta">이미지 설명</span><input v-model="description" class="as-input-text px-3 py-2 w-full mt-1" maxlength="500" placeholder="작품명과 이미지 설명"/></label>
        <label class="block"><span class="as-meta">출처</span><input v-model="sourceUrl" class="as-input-text px-3 py-2 w-full mt-1" type="url" maxlength="1024" placeholder="https://"/></label>
        <button type="submit" :disabled="!file" class="as-btn-primary py-2">업로드</button>
      </fieldset>
      <div v-if="uploading" role="status" class="space-y-2">
        <div class="flex justify-between text-sm"><span>파일 전송 {{progress}}%</span><button type="button" class="as-link" @click="cancelUpload">취소</button></div>
        <progress class="w-full h-2 accent-brand" :value="progress" max="100" aria-label="파일 전송률"></progress>
      </div>
      <div v-if="processing" class="as-desc" role="status">{{current?.status === 'WAITING' ? '이미지 압축 대기 중입니다.' : '이미지를 압축하고 있습니다.'}}</div>
      <p v-if="uploadError" class="text-sm text-red-600 whitespace-pre-line" role="alert">{{uploadError}} <button v-if="current && processing" type="button" class="as-link" @click="poll(current.id)">상태 다시 확인</button></p>
      <div v-if="current?.status === 'READY' && current.url" class="flex flex-wrap items-center gap-4 border-t border-line pt-4">
        <img :src="current.url" :alt="current.description || '업로드한 이미지'" class="w-24 h-24 object-contain"/>
        <div class="space-y-2 text-sm"><p>업로드가 완료되었습니다.</p><p class="as-meta">{{dimensions(current)}}</p><div class="flex gap-3"><button type="button" class="as-link" @click="copy(current.url)">주소 복사</button><router-link v-if="!selectable" :to="{path: '/wiki/images', query: {imageId: current.id}}" class="as-link">이미지 정보</router-link><button v-if="selectable" type="button" class="as-btn-primary py-1.5" @click="$emit('select', current)">본문에 넣기</button></div></div>
      </div>
    </form>
    <p v-else class="as-desc" role="status">{{access?.message || '업로드 권한을 확인하고 있습니다.'}}</p>
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue';
import {sessionStore} from '../session/sessionStore';
import toast from '../../common/toast';
import {wikiRemote, wikiErrorMessage, type WikiAccess, type WikiImage} from './wikiRemote';

defineProps<{selectable?: boolean}>();
const emit = defineEmits<{select: [image: WikiImage]; uploaded: [image: WikiImage]}>();
const session = sessionStore();
const access = ref<WikiAccess>();
const file = ref<File>(), fileInput = ref<HTMLInputElement>();
const description = ref(''), sourceUrl = ref('');
const current = ref<WikiImage>(), uploading = ref(false), progress = ref(0), uploadError = ref('');
const processing = computed(() => current.value?.status === 'WAITING' || current.value?.status === 'PROCESSING');
let active = true, accessGeneration = 0, uploadGeneration = 0;
let cancel: (() => void) | undefined;
let pollTimer: ReturnType<typeof setTimeout> | undefined;

function pick(event: Event) { file.value = (event.target as HTMLInputElement).files?.[0]; }
function drop(event: DragEvent) {
  if (!access.value?.canContribute || uploading.value || processing.value) return;
  file.value = event.dataTransfer?.files[0];
}
function dimensions(item: WikiImage) {
  return [item.width && item.height ? `${item.width} × ${item.height}` : '', item.bytes ? `${(item.bytes / 1024).toFixed(0)} KB` : '', item.animated ? '움직이는 이미지' : ''].filter(Boolean).join(' · ');
}
async function copy(url: string) {
  try { await navigator.clipboard.writeText(new URL(url, location.origin).href); toast.success('이미지 주소를 복사했습니다.'); }
  catch { toast.error('주소를 복사하지 못했습니다. 이미지를 열어 주소를 복사해 주세요.'); }
}
function cancelUpload() { ++uploadGeneration; cancel?.(); cancel = undefined; uploading.value = false; }
async function startUpload() {
  if (!file.value || !access.value?.canContribute || uploading.value || processing.value) return;
  const expected = ++uploadGeneration;
  clearTimeout(pollTimer); current.value = undefined; uploadError.value = ''; uploading.value = true; progress.value = 0;
  const transfer = wikiRemote.upload(file.value, description.value, sourceUrl.value, value => { if (expected === uploadGeneration) progress.value = value; });
  cancel = transfer.cancel;
  try {
    const item = await transfer.promise;
    if (!active || expected !== uploadGeneration) return;
    current.value = item;
    if (item.status === 'READY') completed();
    else if (item.status === 'FAILED') uploadError.value = item.error || '이미지 압축에 실패했습니다.';
    else void poll(item.id);
  } catch (error) {
    if (active && expected === uploadGeneration && !(error instanceof DOMException && error.name === 'AbortError')) uploadError.value = wikiErrorMessage(error);
  } finally { if (expected === uploadGeneration) { uploading.value = false; cancel = undefined; } }
}
function completed() {
  file.value = undefined;
  if (fileInput.value) fileInput.value.value = '';
  if (current.value) emit('uploaded', current.value);
}
async function poll(id: string) {
  clearTimeout(pollTimer);
  uploadError.value = '';
  const expected = uploadGeneration;
  try {
    const item = await wikiRemote.image(id);
    if (!active || expected !== uploadGeneration) return;
    current.value = item;
    if (item.status === 'READY') completed();
    else if (item.status === 'FAILED') uploadError.value = item.error || '이미지 압축에 실패했습니다.';
    else pollTimer = setTimeout(() => void poll(id), 2000);
  } catch (error) { if (active && expected === uploadGeneration) uploadError.value = wikiErrorMessage(error); }
}
watch([() => session.user.email, () => session.user.roles.join(',')], async () => {
  const expected = ++accessGeneration;
  cancelUpload(); clearTimeout(pollTimer); current.value = undefined; access.value = undefined;
  file.value = undefined; description.value = ''; sourceUrl.value = ''; uploadError.value = '';
  try { const value = await wikiRemote.access(); if (active && expected === accessGeneration) access.value = value; }
  catch (error) { if (active && expected === accessGeneration) access.value = {canContribute: false, isOperator: false, message: wikiErrorMessage(error)}; }
}, {immediate: true});
onBeforeUnmount(() => { active = false; cancelUpload(); clearTimeout(pollTimer); ++accessGeneration; });
</script>
