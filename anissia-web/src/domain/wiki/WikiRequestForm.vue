<template>
  <section>
    <p v-if="!access" class="as-empty" role="status">{{error || '요청 권한을 확인하고 있습니다.'}}</p>
    <p v-else-if="!access.canContribute && access.canRequestImageUpload !== true" class="as-box p-5 as-desc">{{access.message || '요청을 제출할 권한이 없습니다.'}}</p>
    <form v-else data-save-scope="page" @submit.prevent="submit" class="space-y-5">
      <fieldset :disabled="saving" :inert="saving" class="space-y-5">
        <label class="block"><span class="as-meta">요청 종류</span><select v-model="kind" class="as-input-text px-3 py-2 w-full mt-1" @change="changeKind"><option v-for="(label, value) in wikiRequestKinds" :key="value" :value="value" :disabled="value === 'IMAGE_CREATE' ? access.canRequestImageUpload !== true : !access.canContribute">{{label}}</option></select></label>
        <p v-if="!canSubmitKind" class="as-desc" role="status">이 요청을 제출할 권한이 없습니다.</p>
        <template v-else-if="kind === 'TEMPLATE_CREATE'">
          <label class="block"><span class="as-meta">템플릿 이름</span><input v-model="title" required maxlength="100" class="as-input-text px-3 py-2 w-full mt-1"/></label>
          <WikiEditor ref="editor" v-model="body" :saved-body="emptyBody" :additional-dirty="!!title.trim() || !!reason.trim()" :allow-templates="false"/>
        </template>
        <div v-else-if="kind === 'TEMPLATE_DELETE'" class="space-y-3">
          <div v-if="targetTemplate" class="as-box p-4"><p class="font-semibold break-words">{{targetTemplate.title}}</p><p class="as-meta mt-2">{{targetTemplate.revision}}판 · {{wikiDate(targetTemplate.updatedAt)}}</p><div class="flex gap-3 mt-3 text-sm"><router-link :to="`/wiki/templates?templateNo=${targetTemplate.templateNo}`" target="_blank" class="as-link">대상 보기 (새 창)</router-link><button type="button" class="as-link" @click="targetTemplate = undefined; searchTemplates()">다른 템플릿 선택</button></div></div>
          <template v-else>
            <div class="flex gap-2"><input v-model="templateQuery" class="as-input-text px-3 py-2 flex-1 min-w-0" aria-label="삭제 요청할 템플릿 검색" placeholder="삭제 요청할 템플릿 검색" @keydown.enter.prevent="searchTemplates()"/><button type="button" class="as-input-btn px-4" @click="searchTemplates()">검색</button></div>
            <ul class="as-box divide-y divide-line"><li v-for="template in templateResults" :key="template.templateNo" class="p-4"><button type="button" class="as-link text-left" @click="selectTemplate(template.templateNo!)">{{template.title}}</button></li></ul>
            <p v-if="!targetLoading && !templateResults.length" class="as-desc">선택할 템플릿이 없습니다.</p>
            <button v-if="templatesHasMore" type="button" class="as-input-btn py-2" :disabled="targetLoading" @click="searchTemplates(true)">템플릿 더 보기</button>
          </template>
        </div>
        <template v-else-if="kind === 'IMAGE_CREATE'">
          <p v-if="imageOwner" class="as-desc"><router-link :to="wikiImageOwnerLocation(imageOwner)" target="_blank" class="as-link">대상 문서</router-link>에 이미지를 등록하도록 요청합니다. 운영진이 승인하면 해당 문서에서 사용할 수 있습니다.</p>
          <p v-else class="as-desc" role="status">이미지 등록 요청은 이미지를 넣을 위키 문서나 템플릿의 편집 화면에서 시작해 주세요.</p>
          <label class="block as-box p-5"><span class="as-meta">등록할 이미지</span><input type="file" required accept="image/png,image/jpeg,image/webp,image/gif,image/avif,image/apng,image/bmp" class="block w-full text-sm mt-3" @change="pickFile"/></label>
          <img v-if="filePreview" :src="filePreview" alt="등록 요청할 이미지 미리보기" class="max-h-72 w-full object-contain"/>
          <label class="block"><span class="as-meta">이미지 설명</span><textarea v-model="description" required maxlength="500" rows="3" class="as-input-text px-3 py-2 w-full mt-1"></textarea></label>
          <label class="block"><span class="as-meta">출처</span><input v-model="sourceUrl" required type="url" maxlength="1024" placeholder="https://" class="as-input-text px-3 py-2 w-full mt-1"/></label>
        </template>
        <div v-else-if="kind === 'IMAGE_DELETE'" class="space-y-3">
          <div v-if="targetImage" class="as-box p-4"><img v-if="targetImage.url" :src="wikiImageDisplayUrl(targetImage.url, targetImage.revision)" :alt="targetImage.description || '삭제 요청 대상'" class="max-h-60 w-full object-contain mb-4"/><p class="font-semibold break-words">{{targetImage.description || '위키 이미지'}}</p><p class="as-meta mt-2">{{targetImage.revision}}판</p><div class="flex gap-3 mt-3 text-sm"><router-link v-if="wikiImageLocation(targetImage)" :to="wikiImageLocation(targetImage)!" target="_blank" class="as-link">대상 보기 (새 창)</router-link><button type="button" class="as-link" @click="targetImage = undefined">다른 이미지 선택</button></div></div>
          <WikiImageLibrary v-else-if="imageOwner" selectable :owner="imageOwner" @select="selectImage"/>
          <p v-else class="as-desc">삭제할 이미지는 해당 문서의 이미지 정보 화면에서 선택해 주세요.</p>
        </div>
        <p v-if="targetLoading" class="as-desc" role="status">대상을 불러오는 중입니다.</p>
        <label class="block"><span class="as-meta">요청 사유</span><textarea v-model="reason" required maxlength="2000" rows="4" class="as-input-text px-3 py-2 w-full mt-1" placeholder="등록하거나 삭제해야 하는 이유를 적어 주세요."></textarea></label>
        <div class="flex justify-end gap-2"><router-link to="/wiki/requests" class="as-input-btn py-2">취소</router-link><button type="submit" data-save-button class="as-btn-primary py-2" :disabled="!canSubmitKind || targetLoading || (kind === 'IMAGE_CREATE' && !imageOwner)">{{saving ? '제출 중…' : '요청 제출'}}</button></div>
      </fieldset>
      <div v-if="saving && kind === 'IMAGE_CREATE'" class="space-y-2" role="status"><p class="as-desc">이미지 전송 {{progress}}%</p><progress :value="progress" max="100" class="w-full h-2 accent-brand" aria-label="이미지 전송률"></progress></div>
      <p v-if="error" class="text-sm text-red-600 whitespace-pre-line" role="alert">{{error}}</p>
    </form>
  </section>
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, ref, watch} from 'vue';
import {onBeforeRouteLeave, useRoute, useRouter} from 'vue-router';
import {sessionStore} from '../session/sessionStore';
import toast from '../../common/toast';
import WikiEditor from './WikiEditor.vue';
import WikiImageLibrary from './WikiImageLibrary.vue';
import {wikiDate, wikiErrorMessage, wikiRemote, type WikiAccess, type WikiDocument, type WikiImage} from './wikiRemote';
import {wikiImageDisplayUrl} from './wikiImageVersion';
import {wikiImageOwner, wikiImageOwnerLocation, wikiImageLocation} from './wikiImageOwner';
import {wikiRequestKinds, wikiRequestRemote, type WikiRequestKind, type WikiRequestInput} from './wikiRequestRemote';
const route = useRoute(), router = useRouter(), session = sessionStore();
const imageOwner = computed(() => wikiImageOwner(route.query.documentKind, route.query.documentNo));
const initialKind = typeof route.query.kind === 'string' && Object.hasOwn(wikiRequestKinds, route.query.kind) ? route.query.kind as WikiRequestKind : 'TEMPLATE_CREATE';
const kind = ref<WikiRequestKind>(initialKind), access = ref<WikiAccess>(), editor = ref<InstanceType<typeof WikiEditor>>();
const canSubmitKind = computed(() => kind.value === 'IMAGE_CREATE' ? access.value?.canRequestImageUpload === true : access.value?.canContribute === true);
const emptyBody = wikiRemote.emptyBody(), body = ref(wikiRemote.emptyBody());
const title = ref(''), reason = ref(''), description = ref(''), sourceUrl = ref(''), error = ref('');
const file = ref<File>(), filePreview = ref(''), saving = ref(false), progress = ref(0);
const targetTemplate = ref<WikiDocument>(), targetImage = ref<WikiImage>(), targetLoading = ref(false);
const templateQuery = ref(''), templateResults = ref<WikiDocument[]>([]), templatesHasMore = ref(false);
let active = true, generation = 0, targetGeneration = 0, templatePage = 0, saved = false, cancelUpload: (() => void) | undefined;
function dirty() { return !saved && kind.value !== 'TEMPLATE_CREATE' && (!!file.value || !!reason.value.trim() || !!description.value.trim() || !!sourceUrl.value.trim()); }
function confirmLeave() { return !saving.value && (!dirty() || window.confirm('작성 중인 요청을 남기고 나가시겠습니까?')); }
function beforeUnload(event: BeforeUnloadEvent) { if (saving.value || dirty()) { event.preventDefault(); event.returnValue = ''; } }
onBeforeRouteLeave(confirmLeave);
function clearFile() { if (filePreview.value) URL.revokeObjectURL(filePreview.value); filePreview.value = ''; file.value = undefined; }
function pickFile(event: Event) { clearFile(); file.value = (event.target as HTMLInputElement).files?.[0]; if (file.value) filePreview.value = URL.createObjectURL(file.value); }
function changeKind() { ++targetGeneration; targetLoading.value = false; targetTemplate.value = undefined; targetImage.value = undefined; templateResults.value = []; templatesHasMore.value = false; error.value = ''; clearFile(); if (kind.value === 'TEMPLATE_DELETE') void searchTemplates(); }
async function searchTemplates(append = false) {
  const expected = ++targetGeneration, next = append ? templatePage + 1 : 0;
  targetLoading.value = true; error.value = '';
  try { const result = await wikiRemote.templates(next, templateQuery.value.trim()); if (active && expected === targetGeneration) { templateResults.value = append ? [...templateResults.value, ...result.content] : result.content; templatePage = next; templatesHasMore.value = templateResults.value.length < result.totalElements; } }
  catch (cause) { if (active && expected === targetGeneration) error.value = wikiErrorMessage(cause); }
  finally { if (expected === targetGeneration) targetLoading.value = false; }
}
async function selectTemplate(id: number) {
  const expected = ++targetGeneration; targetLoading.value = true; error.value = '';
  try { const value = await wikiRemote.request<WikiDocument>(`/templates/${id}`); if (active && expected === targetGeneration) { if (value.deleted) throw new Error('이미 삭제된 템플릿입니다.'); targetTemplate.value = value; } }
  catch (cause) { if (active && expected === targetGeneration) error.value = wikiErrorMessage(cause); }
  finally { if (expected === targetGeneration) targetLoading.value = false; }
}
async function selectImage(image: WikiImage | string) {
  const expected = ++targetGeneration; targetLoading.value = true; error.value = '';
  try { const value = await wikiRemote.image(typeof image === 'string' ? image : image.id); if (active && expected === targetGeneration) { if (value.status !== 'READY') throw new Error('사용 가능한 이미지를 선택해 주세요.'); targetImage.value = value; } }
  catch (cause) { if (active && expected === targetGeneration) error.value = wikiErrorMessage(cause); }
  finally { if (expected === targetGeneration) targetLoading.value = false; }
}
async function submit() {
  if (saving.value || targetLoading.value || !canSubmitKind.value) return;
  error.value = '';
  if (!reason.value.trim()) { error.value = '요청 사유를 입력해 주세요.'; return; }
  if (kind.value === 'TEMPLATE_CREATE' && !title.value.trim()) { error.value = '템플릿 이름을 입력해 주세요.'; return; }
  if (kind.value === 'TEMPLATE_DELETE' && !targetTemplate.value) { error.value = '삭제 요청할 템플릿을 선택해 주세요.'; return; }
  if (kind.value === 'IMAGE_DELETE' && !targetImage.value) { error.value = '삭제 요청할 이미지를 선택해 주세요.'; return; }
  if (kind.value === 'IMAGE_CREATE' && !imageOwner.value) { error.value = '이미지를 넣을 문서의 편집 화면에서 등록을 요청해 주세요.'; return; }
  if (kind.value === 'IMAGE_CREATE' && (!file.value || !description.value.trim() || !sourceUrl.value.trim())) { error.value = '이미지 파일과 설명, 출처를 입력해 주세요.'; return; }
  const expected = generation; saving.value = true; progress.value = 0;
  try {
    const input: WikiRequestInput = {kind: kind.value, reason: reason.value.trim()};
    if (kind.value === 'TEMPLATE_CREATE') { input.title = title.value.trim(); input.body = body.value; }
    if (kind.value === 'TEMPLATE_DELETE') { input.targetNo = targetTemplate.value!.templateNo; input.baseRevision = targetTemplate.value!.revision; }
    if (kind.value === 'IMAGE_DELETE') { input.imageId = targetImage.value!.id; input.baseRevision = targetImage.value!.revision; }
    const transfer = kind.value === 'IMAGE_CREATE' ? wikiRequestRemote.upload(file.value!, description.value.trim(), sourceUrl.value.trim(), reason.value.trim(), imageOwner.value!, value => { if (active && expected === generation) progress.value = value; }) : undefined;
    cancelUpload = transfer?.cancel;
    const result = transfer ? await transfer.promise : await wikiRequestRemote.create(input);
    if (!active || expected !== generation) return;
    saved = true; editor.value?.markSaved(); saving.value = false; toast.success('요청을 제출했습니다.');
    await router.replace(`/wiki/requests/${result.id}`);
  } catch (cause) { if (active && expected === generation) error.value = wikiErrorMessage(cause); }
  finally { if (expected === generation) { saving.value = false; cancelUpload = undefined; } }
}
watch([() => session.user.email, () => session.user.roles.join(',')], async () => {
  const expected = ++generation; ++targetGeneration; cancelUpload?.(); saving.value = false; access.value = undefined; error.value = '';
  try {
    const value = await wikiRemote.access();
    if (!active || expected !== generation) return;
    access.value = value;
    if (value.canContribute && kind.value === 'TEMPLATE_DELETE') {
      const id = Number(route.query.templateNo); if (Number.isSafeInteger(id) && id > 0) void selectTemplate(id); else void searchTemplates();
    }
    if (value.canContribute && kind.value === 'IMAGE_DELETE' && typeof route.query.imageId === 'string' && /^[a-f0-9]{32}$/.test(route.query.imageId)) void selectImage(route.query.imageId);
  } catch (cause) { if (active && expected === generation) error.value = wikiErrorMessage(cause); }
}, {immediate: true});
window.addEventListener('beforeunload', beforeUnload);
onBeforeUnmount(() => { active = false; ++generation; ++targetGeneration; cancelUpload?.(); clearFile(); window.removeEventListener('beforeunload', beforeUnload); });
</script>
