<template>
  <div class="grid gap-10 xl:grid-cols-2 items-start">

    <section>
      <h1 class="as-page-title">자막 제작자 신청</h1>
      <h2 class="as-section-title mt-8 mb-3">자막제작자 신청 동의사항</h2>
      <ul class="as-box p-6 space-y-2.5 list-disc list-outside pl-10 text-sm leading-[1.8] text-ink-2">
        <li>권한신청을 위해서 4편이상(동일작품)의 자막 작업물이 있어야 합니다.</li>
        <li>블로그 내에 불법영상이나 불건전한 정보가 있을 경우 거부될 수 있습니다.</li>
        <li>권한 부여 후에도 위와 같은 사항이 발견되면 고지 없이 권한이 회수될 수 있습니다.</li>
        <li>자막 도용시 추후에 적발되더라도 권한이 회수될 수 있습니다.</li>
      </ul>

      <label class="as-switch my-7">
        <input type="checkbox" v-model="agree">
        <span class="as-switch-track"></span>
        <span class="ml-3 text-sm font-medium text-ink">위 사항을 읽고 확인하였습니다.</span>
      </label>

      <div><h2 class="as-section-title">블로그 주소</h2></div>
      <p class="as-desc mt-2">4편 이상의 작업물이 있어야 합니다.</p>
      <div class="mt-3 max-w-[520px]">
        <input type="text" v-model="website" placeholder="https://example-blog.com" class="px-4 py-3 as-input-text">
      </div>
      <p class="as-meta mt-2.5">※ 추가 내용은 신청 완료 후 심사과정에서 작성 가능합니다.</p>
      <div class="mt-6">
        <button class="as-btn-primary py-2.5" @click="doApply">신청하기</button>
      </div>
    </section>

    <section>
      <h1 class="as-page-title mb-6">자막 제작자 심사</h1>
      <table class="as-table as-table-kv">
        <tbody>
          <tr><th>신청일자</th><td>2026-10-02 14:22:10</td></tr>
          <tr><th>심사번호</th><td>312</td></tr>
          <tr><th>상태</th><td><span class="tu-status is-act"><span class="dot"></span>심사중</span></td></tr>
          <tr><th>신청인</th><td>코코렛</td></tr>
          <tr><th>블로그주소</th><td><a class="as-link" href="javascript:void(0)">https://cocolet.example.com</a></td></tr>
        </tbody>
      </table>

      <h2 class="as-section-title mt-10 mb-4">심사이력</h2>
      <div class="tu-vote-bar">
        <div title="수리" class="tu-vote-btn text-emerald-600" :class="{'is-on': point == 1}" @click="point = 1"><i class="fa-solid fa-circle-check"></i></div>
        <div title="반려" class="tu-vote-btn text-red-600" :class="{'is-on': point == -1}" @click="point = -1"><i class="fa-solid fa-circle-xmark"></i></div>
        <div title="의견" class="tu-vote-btn text-brand" :class="{'is-on': point == 0}" @click="point = 0"><i class="fa-solid fa-comments"></i></div>
        <input type="text" placeholder="의견" class="as-input-none flex-1 px-3 py-2.5 text-sm">
      </div>
      <div class="mt-2">
        <div class="py-3.5 text-sm leading-[1.8] as-row">
          <div class="float-right pl-4 as-meta">7일 전</div>
          <span class="font-medium text-brand">자막 제작자 신청을 제출하였습니다.</span>
        </div>
        <div v-for="h in history" :key="h.name + h.comment" class="py-3.5 text-sm leading-[1.8] as-row border-t border-line">
          <div class="float-right pl-4 as-meta">{{ h.time }}</div>
          <i v-if="h.vote > 0" class="fa-solid fa-circle-check text-emerald-500 mr-2"></i>
          <i v-if="h.vote == 0" class="fa-solid fa-comment-dots text-brand/70 mr-2"></i>
          <i v-if="h.vote < 0" class="fa-solid fa-circle-xmark text-red-700/80 mr-2"></i>
          <span class="mr-3 font-semibold text-ink">{{ h.name }}</span>
          <span class="text-ink-2">{{ h.comment }}</span>
        </div>
      </div>
    </section>

    <section class="xl:col-span-2">
      <div class="flex justify-between items-center mb-5">
        <h2 class="as-section-title">신청 목록</h2>
        <button class="as-btn-primary py-2">자막제작자 신청</button>
      </div>
      <div class="grid gap-4 sm:grid-cols-2 md:grid-cols-3 xl:grid-cols-4">
        <div v-for="a in applies" :key="a.no" class="p-4 as-card">
          <div class="flex items-center">
            <div class="w-[44px] text-[26px] shrink-0">
              <i v-if="a.result == 'ACT'" class="fa-solid fa-circle-play text-amber-400"></i>
              <i v-if="a.result == 'PASS'" class="fa-solid fa-circle-check text-emerald-500"></i>
              <i v-if="a.result == 'FAIL'" class="fa-solid fa-circle-xmark text-ink-3"></i>
            </div>
            <div class="flex-1 break-all">
              <div class="as-meta">#{{ a.no }}</div>
              <div class="my-0.5 font-semibold text-ink hover:text-brand transition-colors cursor-pointer">{{ a.name }}</div>
              <div class="as-meta">{{ a.time }}</div>
            </div>
          </div>
        </div>
      </div>
    </section>

  </div>
</template>

<script setup lang="ts">
import {ref} from "vue";
import toast from "../../../common/toast";

const agree = ref(false);
const website = ref('');
const point = ref(0);

function doApply() {
  if (!agree.value) {
    toast.error('자막제작자 신청 동의사항을 읽고 동의해주세요.');
  } else if (!website.value.startsWith('http')) {
    toast.error('블로그 주소를 입력해 주세요.\nhttp:// 또는 https:// 로 시작해야 합니다.');
  } else {
    toast.success('신청되었습니다.');
  }
}

const history = [
  {name: '애니시아', vote: 0, comment: '작업물 4편 확인했습니다. 블로그 공지 확인 중입니다.', time: '6일 전'},
  {name: '유진', vote: 1, comment: '번역 품질 좋습니다.', time: '5일 전'},
  {name: '코코렛', vote: 0, comment: '추가 작업물 링크 남깁니다.', time: '3일 전'},
];

const applies = [
  {no: 312, name: '코코렛', result: 'ACT', time: '7일 전'},
  {no: 311, name: '수퍼소닉EX', result: 'PASS', time: '2026-09-21'},
  {no: 310, name: '별명따위', result: 'PASS', time: '2026-09-02'},
  {no: 309, name: 'misael', result: 'FAIL', time: '2026-08-15'},
  {no: 308, name: '키리유', result: 'PASS', time: '2026-07-30'},
  {no: 307, name: 'fanic', result: 'ACT', time: '2026-07-11'},
  {no: 306, name: '하늬', result: 'PASS', time: '2026-06-28'},
  {no: 305, name: 'C소라', result: 'FAIL', time: '2026-06-02'},
];
</script>
