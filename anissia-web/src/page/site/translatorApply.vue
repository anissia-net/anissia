<template>

  <div class="as-page">
    <div v-if="view" class="mb-12">
      <div v-if="view.applyNo > 0">
        <h1 class="as-page-title mb-6">자막 제작자 심사</h1>

        <table class="as-table as-table-kv">
          <tbody>
          <tr>
            <th>신청일자</th>
            <td class="break-all">{{view.regDtFullText}}</td>
          </tr>
          <tr>
            <th>심사번호</th>
            <td class="break-all">{{view.applyNo}}</td>
          </tr>
          <tr>
            <th>상태</th>
            <td class="break-all">{{view.resultText}}</td>
          </tr>
          <tr>
            <th>신청인</th>
            <td class="break-all">{{view.name}}</td>
          </tr>
          <tr>
            <th>블로그주소</th>
            <td class="break-all">
              <a v-if="view.website.startsWith('http')" :href="view.website" target="_blank" class="as-link">{{view.website}}</a>
              <span v-else>-</span>
            </td>
          </tr>
          </tbody>
        </table>

        <h2 class="as-section-title mt-10 mb-3">심사이력</h2>
        <div v-if="view.result == 'ACT' && user.isLogin && (user.isAdmin || user.name == view.name)" class="mb-3">
          <div v-if="user.isAdmin" v-choice class="vote-bar">
            <div title="수리" @click="point = '1'" class="vote-btn text-green-600" :class="point == '1' ? 'is-on' : ''"><i class="fa-solid fa-circle-check"></i></div>
            <div title="반려" @click="point = '-1'" class="vote-btn text-red-600" :class="point == '-1' ? 'is-on' : ''"><i class="fa-solid fa-circle-xmark"></i></div>
            <div title="의견" @click="point = '0'" class="vote-btn text-purple-500" :class="point == '0' ? 'is-on' : ''"><i class="fa-solid fa-comments"></i></div>
            <div class="flex-1">
              <input type="text" v-model="comment" @keyup.enter="doComment" name="comment" placeholder="의견" class="as-input-none w-full px-3 py-2.5 text-sm">
            </div>
          </div>
          <div v-else>
            <input type="text" v-model="comment" @keyup.enter="doComment" name="comment" placeholder="의견" class="as-input-text px-4 py-2.5">
          </div>
        </div>
        <div class="as-box px-4 md:px-5 py-3">
        <div class="flex items-baseline gap-4 py-3 text-sm leading-[1.8] as-row">
          <div class="order-last shrink-0 as-meta whitespace-nowrap">{{view.regDtText}}</div>
          <div class="flex-1 min-w-0"><span class="font-medium text-brand">자막 제작자 신청을 제출하였습니다.</span></div>
        </div>
        <div v-for="node in view.polls" :key="node.no" class="flex items-baseline gap-4 py-3 text-sm leading-[1.8] as-row">
          <div class="order-last shrink-0 as-meta whitespace-nowrap">{{node.regDtText}}</div>
          <div v-if="node.name" class="flex-1 min-w-0">
            <span class="mr-2">
              <i v-if="node.vote > 0" class="fa-solid fa-circle-check text-emerald-500"></i>
              <i v-if="node.vote == 0" class="fa-solid fa-comment-dots text-violet-400"></i>
              <i v-if="node.vote < 0" class="fa-solid fa-circle-xmark text-red-700/80"></i>
            </span>
            <span class="mr-3 font-semibold text-ink">{{node.name}}</span>
            <span class="text-ink-2">{{node.comment}}</span>
          </div>
          <div v-else class="flex-1 min-w-0">
            <span class="font-medium text-brand">{{node.comment}}</span>
          </div>
        </div>
        </div>

      </div>

      <div v-else-if="applyNo == 0">
        <h1 class="as-page-title">자막 제작자 신청</h1>
        <h2 class="as-section-title mt-8 mb-3">자막제작자 신청 동의사항</h2>
        <ul class="as-box p-6 space-y-2.5 list-disc list-outside pl-10 text-sm leading-[1.8] text-ink-2">
          <li>권한신청을 위해서 4편이상(동일작품)의 자막 작업물이 있어야 합니다.</li>
          <li>블로그 내에 불법영상이나 불건전한 정보가 있을 경우 거부될 수 있습니다.</li>
          <li>권한 부여 후에도 위와 같은 사항이 발견되면 고지 없이 권한이 회수될 수 있습니다.</li>
          <li>자막 도용시 추후에 적발되더라도 권한이 회수될 수 있습니다.</li>
        </ul>

        <label class="as-switch my-7">
          <input type="checkbox" v-model="agree" name="agree">
          <span class="as-switch-track"></span>
          <span class="ml-3 text-sm font-medium text-ink">위 사항을 읽고 확인하였습니다.</span>
        </label>

        <div><h2 class="as-section-title">블로그 주소</h2></div>
        <p class="as-desc mt-2">4편 이상의 작업물이 있어야 합니다.</p>
        <div class="mt-3 max-w-[520px]">
          <input type="text" v-model="website" name="website" placeholder="https://example-blog.com" class="px-4 py-3 as-input-text">
        </div>
        <p class="as-meta mt-2.5">※ 추가 내용은 신청 완료 후 심사과정에서 작성 가능합니다.</p>
        <div class="mt-6">
          <input type="button" value="신청하기" @click="doApply()" class="as-btn-primary py-2.5" />
        </div>
      </div>

    </div>

    <div v-if="user.isLogin && (!view || view.applyNo != 0)" class="flex justify-between items-center mb-5">
      <h2 class="as-section-title">신청 목록</h2>
      <router-link to="/translator/apply?applyNo=0" class="as-btn-primary py-2">자막제작자 신청</router-link>
    </div>

    <div class="grid gap-4 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-4">
      <div v-for="(node, i) in list.content" class="p-4 as-card">
        <div class="flex items-center">
          <div class="w-[44px] text-[26px] shrink-0">
            <i v-if="node.result == 'ACT'" class="fa-solid fa-circle-play text-amber-400"></i>
            <i v-if="node.result == 'PASS'" class="fa-solid fa-circle-check text-emerald-500"></i>
            <i v-if="node.result == 'FAIL'" class="fa-solid fa-circle-xmark text-ink-3"></i>
          </div>
          <div class="flex-1 break-all">
            <div class="as-meta">#{{node.applyNo}}</div>
            <div class="my-0.5 font-semibold text-ink hover:text-brand transition-colors">
              <router-link :to="`/translator/apply?applyNo=${node.applyNo}`">
                {{node.name}}
              </router-link>
            </div>
            <div class="as-meta">{{node.regDtText}}</div>
          </div>
        </div>
      </div>
    </div>


  </div>

</template>

<script setup lang="ts">
import {computed, nextTick, onUnmounted, Ref, ref} from "vue";
import PageData from "../../common/PageData";
import {onBeforeRouteUpdate, useRouter} from "vue-router";
import { ScrollLoader } from "raon";
import {TranslatorApply} from "../../domain/translator/TranslatorApply";
import {Locate} from "raon";
import translatorRemote from "../../domain/translator/remote/translatorRemote";
import {sessionStore} from "../../domain/session/sessionStore";
import Session from "../../domain/session/Session";
import toast from "../../common/toast";

const session = sessionStore();
const user = computed(() => session.user) as Ref<Session>;
const list = ref(PageData.empty()) as Ref<PageData<TranslatorApply>>;
const view = ref(null) as Ref<TranslatorApply|null>;
const page = ref(0);
const router = useRouter();

const sl = new ScrollLoader().onNeedNextPage(() => {
  page.value++;
  loadList();
});

const agree = ref(false);
const website = ref('');
const point = ref('0');
const comment = ref('');
const applyNo = ref(-1);

let lastAgendaNo = -1;

function clear() {
  lastAgendaNo = -1;
  page.value = 0;
  agree.value = false;
  website.value = '';
  point.value = '0';
}

function load(locate: Locate = new Locate()) {
  loadView(locate);
  loadList();
}

function loadView(locate: Locate = new Locate()) {
  const no = applyNo.value = locate.getIntParameter('applyNo', -1);
  if (no > 0) {
    if (lastAgendaNo != no) {
      lastAgendaNo = no;
      translatorRemote.getApply(no).then(node => view.value = node);
    } else {
      view.value = view.value;
    }
  } else if (no == 0) {
    if (!user.value.isLogin) {
      router.push('/translator/apply');
      return;
    }
    lastAgendaNo = -1;
    view.value = new TranslatorApply();
  } else {
    lastAgendaNo = -1;
    view.value = null;
  }
}

function loadList() {
  const isFirstPage = page.value == 0;

  translatorRemote.getApplyList(page.value).then(pageData => {
    if (isFirstPage) {
      list.value = pageData;
    } else {
      list.value = list.value.merge(pageData);
    }
    nextTick(() => sl.watch(pageData.next));
  });
}

function doApply() {
  if (!agree.value) {
    toast.error('자막제작자 신청 동의사항을 읽고 동의해주세요.');
    return;
  }
  translatorRemote.addApply(website.value).then(res => {
    if (res.code == 'ok') {
      router.push(`/translator/apply?applyNo=${res.data}`)
    } else {
      toast.error(res.message);
    }
  });
}

let commentSending = false;

function doComment() {
  if (commentSending || !comment.value.trim()) {
    return;
  }
  commentSending = true;
  const node = view.value!!;
  translatorRemote.addApplyPoll(node.applyNo, point.value, comment.value).then(res => {
    commentSending = false;
    comment.value = '';
    if (res.code == 'ok') {
      clear();
      load();
    } else {
      toast.error(res.message);
    }
  }).catch(() => commentSending = false);
}

load();

onBeforeRouteUpdate((to, from, next) => {
  clear();
  load(new Locate(to.fullPath));
  next();
});

onUnmounted(() => {
  sl.destroy();
});

</script>

<style scoped>

</style>

<style scoped>
@reference "../../common/tailwind.pcss";

.vote-bar {
  @apply flex items-center overflow-hidden;
  border-radius: var(--radius-ctl);
  background: var(--as-surface-2);
  border: 1px solid var(--as-line);
  transition: border-color .18s ease, box-shadow .18s ease;
  &:focus-within { border-color: var(--as-brand); box-shadow: var(--as-ring) }
}

.vote-btn {
  @apply w-9 h-10 shrink-0 inline-flex items-center justify-center cursor-pointer text-sm opacity-30 grayscale transition-all duration-150;
  &:hover { opacity: .65 }
  &.is-on { @apply opacity-100 grayscale-0 }
}
</style>
