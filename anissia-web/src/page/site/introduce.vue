<template>

  <div class="as-page">

    <h1 class="as-page-title mb-3">애니시아</h1>
    <p class="as-desc leading-[1.9] as-a-color">
      애니시아의 시작은 2007년도 테라시아 마니아즈파트의 애니메이션탭 소메뉴였습니다.<br/>
      시간이 지나면서 단순히 애니메이션 편성표 기능뿐만이 아니라 자막 정보도 제공하기 시작했습니다.<br/>
      이렇게 애니시아라는 곳이 단지 여러분들의 원활한 애니메이션 감상을 위한 허브 사이트입니다.
    </p>

    <h2 class="as-section-title mt-14 mb-3">운영진</h2>
    <p class="as-desc leading-[1.9]">
      애니시아의 관리를 총괄하고 있는 운영진입니다.
    </p>
    <div class="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <div v-for="node in adminMembers" :key="node.name" class="p-5 as-card flex flex-col">
        <div>
          <div class="text-md font-semibold text-ink">{{node.name}}</div>
          <div class="as-meta mt-1.5 leading-[1.7]" v-html="node.date"></div>
        </div>
        <div class="mt-auto pt-4 flex flex-wrap gap-1.5">
          <span class="as-tag-xs" v-for="sn in node.tags" :key="sn.text">
            <a :href="sn.link" v-if="sn.link">{{sn.text}} <i class="fa-solid fa-arrow-up-right-from-square ml-1 text-[9px] opacity-60"></i></a>
            <span v-else>{{sn.text}}</span>
          </span>
        </div>
      </div>
    </div>

    <h2 class="as-section-title mt-14 mb-3">개발 지원</h2>
    <p class="as-desc leading-[1.9] as-a-color">
      테라시아(현 <a href="https://gs.saro.me">가리사니</a>)의 멤버로 애니시아 독립 이전에는 운영을 이후에는 지원을 하고 있습니다.
    </p>
    <div class="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <div v-for="node in supportMembers" :key="node.name" class="p-5 as-card flex flex-col">
        <div>
          <div class="text-md font-semibold text-ink">{{node.name}}</div>
          <div class="as-meta mt-1.5 leading-[1.7]" v-html="node.date"></div>
        </div>
        <div class="mt-auto pt-4 flex flex-wrap gap-1.5">
          <span class="as-tag-xs" v-for="sn in node.tags" :key="sn.text">
            <a :href="sn.link" v-if="sn.link">{{sn.text}} <i class="fa-solid fa-arrow-up-right-from-square ml-1 text-[9px] opacity-60"></i></a>
            <span v-else>{{sn.text}}</span>
          </span>
        </div>
      </div>
    </div>

    <h2 class="as-section-title mt-14 mb-3">그래픽 지원</h2>
    <div class="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <div v-for="node in supportGraphicMembers" :key="node.name" class="p-5 as-card flex flex-col">
        <div>
          <div class="text-md font-semibold text-ink">{{node.name}}</div>
          <div class="as-meta mt-1.5 leading-[1.7]" v-html="node.date"></div>
        </div>
        <div class="mt-auto pt-4 flex flex-wrap gap-1.5">
          <span class="as-tag-xs" v-for="sn in node.tags" :key="sn.text">
            <a :href="sn.link" v-if="sn.link">{{sn.text}} <i class="fa-solid fa-arrow-up-right-from-square ml-1 text-[9px] opacity-60"></i></a>
            <span v-else>{{sn.text}}</span>
          </span>
        </div>
      </div>
    </div>

    <h2 class="as-section-title mt-14 mb-3">과거 맴버</h2>
    <div class="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <div v-for="node in prevMembers" :key="node.name" class="p-5 as-card flex flex-col">
        <div>
          <div class="text-md font-semibold text-ink">{{node.name}}</div>
          <div class="as-meta mt-1.5 leading-[1.7]" v-html="node.date"></div>
        </div>
        <div class="mt-auto pt-4 flex flex-wrap gap-1.5">
          <span class="as-tag-xs" v-for="sn in node.tags" :key="sn.text">
            <a :href="sn.link" v-if="sn.link">{{sn.text}} <i class="fa-solid fa-arrow-up-right-from-square ml-1 text-[9px] opacity-60"></i></a>
            <span v-else>{{sn.text}}</span>
          </span>
        </div>
      </div>
    </div>

    <h2 class="as-section-title mt-14 mb-4">연혁</h2>
    <ol class="relative pl-6 timeline">
      <li v-for="node in siteHistory" :key="node.date + node.desc" class="relative pb-7 last:pb-0">
        <span class="history-dot"></span>
        <time class="as-meta">{{node.date}}</time>
        <h3 class="mt-1 text-sm font-medium text-ink whitespace-pre-line">{{node.desc}}</h3>
        <a :href="node.link" target="_blank" v-if="node.link" class="as-input-btn py-1 mt-2.5 text-xs!">
          자세히 <i class="fa-solid fa-chevron-right ml-1 text-[9px]"></i>
        </a>
      </li>
    </ol>

    <h2 class="as-section-title mt-14 mb-3">갤러리</h2>
    <div class="mt-4 grid gap-4 md:grid-cols-2 xl:grid-cols-3">

      <div v-for="node in gallery" :key="node.src"  class="as-card overflow-hidden flex flex-col">
        <div class="flex-1 flex items-end justify-center p-4 pb-0 text-center bg-muted/40">
          <a v-if="node.popup" :href="node.src" target="_blank">
            <img class="m-auto max-w-full" :src="node.src" alt="" />
          </a>
          <img v-else class="m-auto max-w-full" :src="node.src" alt="" />
        </div>
        <div class="p-5">
          <h5 class="text-md font-semibold text-ink">{{node.title}}</h5>
          <p class="mt-2 text-sm leading-[1.8] text-ink-2">{{node.desc}}</p>
        </div>
      </div>

    </div>


  </div>

</template>

<script setup lang="ts">
import timetable_1_jpg from './introduce/timetable.1.jpg';
import timetable_2_jpg from './introduce/timetable.2.jpg';
import timetable_3_jpg from './introduce/timetable.3.jpg';
import timetable_4_jpg from './introduce/timetable.4.jpg';
import timetable_radio_jpg from './introduce/timetable-radio.jpg';
import timetable_widget_1_jpg from './introduce/timetable-widget.1.jpg';
import timetable_widget_2_jpg from './introduce/timetable-widget.2.jpg';
import timetable_widget_3_jpg from './introduce/timetable-widget.3.jpg';
import timetable_widget_4_jpg from './introduce/timetable-widget.4.jpg';
import other_1_jpg from './introduce/other.1.jpg';
import anissia_2_jpg from './introduce/anissia.2.jpg';
import anissia_3_jpg from './introduce/anissia.3.jpg';
import anissia_4_jpg from './introduce/anissia.4.jpg';
import anissia_5_jpg from './introduce/anissia.5.jpg';
import anissia_6_jpg from './introduce/anissia.6.jpg';
import anissia_2007 from './introduce/anissia.2007.jpg';
import anissia_2022_1 from './introduce/anissia.2022.1.png';
import anissia_2022_2 from './introduce/anissia.2022.2.png';
import anissia_2026_1 from './introduce/anissia.2026.1.png';
import anissia_2026_2 from './introduce/anissia.2026.2.png';

import {ref} from "vue";
import {SiteHistory} from "./introduce/SiteHistory";
import {SiteMember} from "./introduce/SiteMember";
import {SiteGallery} from "./introduce/SiteGallery";

const nowYear = new Date().getFullYear();

const adminMembers = ref<SiteMember[]>([
  {
    name: '또_탈퇴된코란',
    date: `2009 ~ ${nowYear}`,
    tags: [
      { text: '기획' },
      { text: '관리' },
      { text: '애니시아 서버 제공' },
      { text: '블로그', link: 'https://coran.co.kr' },
      { text: 'X', link: 'https://x.com/c0ran' }
    ]
  },
  {
    name: 'Leon',
    date: `2015 ~ ${nowYear}`,
    tags: [
      { text: '관리' }
    ]
  },
]);

const supportMembers = ref<SiteMember[]>([
  {
    name: '박용서',
    date: `2007 ~ 2013: 개발운영 (애니시아 독립 전)<br/>2013 ~ ${nowYear}: 개발지원`,
    tags: [
      { text: '개발총괄' },
      { text: '애니편성표' },
      { text: '애니시아' },
      { text: '시스템' },
      { text: 'API' },
      { text: '디자인' },
      { text: '기획' },
      { text: '사로', link: 'https://marker.saro.me' },
      { text: 'GITHUB', link: 'https://github.com/marker-seoul' },
      { text: '페이스북', link: 'https://www.facebook.com/j.saro.co' }
    ]
  },
  {
    name: '코네',
    date: `2024`,
    tags: [
      { text: '애니편성표' },
      { text: 'GITHUB', link: 'https://github.com/CodeName393' },
      { text: '블로그', link: 'https://codename393.tistory.com/' }
    ]
  },
]);

const supportGraphicMembers = ref<SiteMember[]>([
  {
    name: '당무지',
    date: `2023 ~ ${nowYear}`,
    tags: [
      { text: '애니시아 아이콘, 애니편성표 아이콘' },
      { text: '블로그', link: 'https://blog.naver.com/vip125' }
    ]
  },
]);

const prevMembers = ref<SiteMember[]>([
  {
    name: 'UTPasiirs',
    date: '2015 ~ 2018',
    tags: [
      { text: '관리', link: '' }
    ]
  },
  {
    name: '매디',
    date: `2007 ~ 2013<br/>테라시아 소속 일러스트 총괄`,
    tags: [
      { text: '일러스트총괄', link: '' },
      { text: '아이콘', link: '' },
      { text: '애니편성표', link: '' },
      { text: '블로그', link: 'https://blog.naver.com/ehozil' }
    ]
  },
  {
    name: 'ORS (단체)',
    date: '2009 ~ 2011',
    tags: [
      { text: '라디오편성표' }
    ]
  },
  {
    name: 'Reve',
    date: '2007 ~ 2011',
    tags: [
      { text: 'iOS' }
    ]
  },
  {
    name: '메티오',
    date: '2009 ~ 2010',
    tags: [
      { text: '일러스트' },
      { text: '디자인' },
      { text: 'Anissia 마크' }
    ]
  },
  {
    name: 'Annyeong',
    date: '2009',
    tags: [
      { text: '다음위젯' }
    ]
  },
  {
    name: 'Kernys',
    date: '2009',
    tags: [
      { text: '다음위젯' }
    ]
  },
  {
    name: '랜스',
    date: '2007 ~ 2009',
    tags: [
      { text: '애니편성표 프로그램' },
      { text: '블로그', link: 'http://blog.lancekun.com/tc/' }
    ]
  },
]);

const siteHistory = ref<SiteHistory[]>([
  { date: `2026-10-10`, desc: `애니편성표 2026 출시` },
  { date: `2026-07-31`, desc: `2026 디자인 리뉴얼 (개발:사로[가리사니 개발자공간 병합])`, link: `/notice?topicNo=368` },
  { date: `2025-11-11`, desc: `애니시아 서버이전` },
  { date: `2024-08-13`, desc: `애니편성표 2024 출시 (2026 출시 후 종료)` },
  { date: `2024-02-23`, desc: `테라시아 도메인 종료`, link: `/notice?topicNo=198` },
  { date: `2024-01-24`, desc: `애니시아 안드로이드 앱 출시`, link: `https://play.google.com/store/apps/dev?id=6556202027842431619` },
  { date: `2023-04-16`, desc: `백엔드 리팩토링`, link: `/notice?topicNo=134` },
  { date: `2023-02-11`, desc: `애니시아 아이콘 변경` },
  { date: `2023-01-30`, desc: `DNS 서버변경`, link: `/notice?topicNo=115` },
  { date: `2023-01-09`, desc: `서버교체`, link: `/notice?topicNo=109` },
  { date: `2022-09-20`, desc: `2022 디자인 리뉴얼 (개발:가리사니 개발자공간)`, link: `/notice?topicNo=93` },
  { date: `2022-09-20`, desc: `공유기 교체 (간헐적 서버 다운 원인)`, link: `/notice?topicNo=88` },
  { date: `2021-02-08`, desc: `2020 리뉴얼 (개발:가리사니 개발자공간)`, link: `/notice?topicNo=27` },
  { date: `2015-07-19`, desc: `2015 리뉴얼 (개발:가리사니 개발자공간)` },
  { date: `2015-06-23`, desc: `기가랜 설치, 새로운 저전력 서버로 교체` },
  { date: `2015-05-31`, desc: `Daum위젯뱅크 서비스 종료로 새로운 위젯 서비스 시작` },
  { date: `2014-05-11`, desc: `애니편성표 모바일 웹 버젼 등재` },
  { date: `2014-04-14`, desc: `애니편성표 크롬 확장프로그램 등록` },
  { date: `2014-01-01`, desc: `가리사니 -> 또_탈퇴된코란 운영권 이전` },
  { date: `2013-12-13`, desc: `구 애니편성표 API (가리사니서버 제공) 지원종료` },
  { date: `2013-12-03`, desc: `가리사니서버 -> 또_탈퇴된코란서버로 이전` },
  { date: `2013-12-03`, desc: `애니시아 완전 독립버전 완성 - 서버이전 기초작업` },
  { date: `2013-12-02`, desc: `애니편성표 다음 위젯 리뉴얼` },
  { date: `2013-11-29`, desc: `신규 회원가입 개시 : 그동안 비공식적으로만 받음` },
  { date: `2013-11-29`, desc: `애니편성표 기능 확장 및 종영애니 표시` },
  { date: `2013-11-29`, desc: `애니시아 리뉴얼` },
  { date: `2013-11-29`, desc: `라디오 편성표 시스템 폐기 (사실상 2011년 중단)` },
  { date: `2013-10-14`, desc: `가리사니 개발자 공간에서 분리` },
  { date: `2013-10-14`, desc: `애니시아와 가리사니의 회원 데이터베이스 분리` },
  { date: `2013-02-20`, desc: `안드로이드용 애니편성표 (애니)` },
  { date: `2012-05-30`, desc: `윈도우폰용 애니편성표 (애니)` },
  { date: `2011-05-11`, desc: `아이폰용 애니편성표 (애니)` },
  { date: `2011-03-25`, desc: `공식사이트 표시기능 추가 (애니)` },
  { date: `2011-02-18`, desc: `애니위젯 폐기 (530 버전) (애니)` },
  { date: `2010-10-03`, desc: `관리시스템 자동화 (애니/라디오)` },
  { date: `2010-10-03`, desc: `서버문제로 가리사니에 재병합 (애니/라디오)` },
  { date: `2010-10-03`, desc: `테라시아 -> 가리사니 이름 변경` },
  { date: `2009-12-04`, desc: `애니위젯 등록 (625 버전) (애니)` },
  { date: `2009-09-29`, desc: `애니위젯 등록 (530 버전 / 현재폐기됨) (애니)` },
  { date: `2009-09-28`, desc: `자막제작자분이 직접 수정가능 (애니)` },
  { date: `2009-09-11`, desc: `테라시아로부터 애니시아 독립 (애니/라디오)` },
  { date: `2009-08-17`, desc: `라디오 편성표 탄생 (라디오)` },
  { date: `2009-08-17`, desc: `비정규/신작표시시작 (애니)` },
  { date: `2009-07-03`, desc: `자막제작자 표시 (애니)` },
  { date: `2009-06-21`, desc: `테라시아의 이름을 따서 anissia.net 도메인 선점` },
  { date: `2009-06-21`, desc: `애니규모가 커짐에따라 테라시아 개발자 포럼의 정체성 문제로 분리 논의` },
  { date: `2009-06-02`, desc: `또_탈퇴된코란 참여 (자막제작자 기능 및 현재 편성표 기획)` },
  { date: `2009-03-14`, desc: `애니편성표 시범시작 (애니)\n2013년 신버전이 나오면서 폐기될 예정이었으나, 폐기반대 요청이 많아 레트로 버전으로 운영중` },
  { date: `2008-08-07`, desc: `테라시아 리뉴얼 (모든 데이터 초기화)` },
  { date: `2007-02-22`, desc: `테라시아 마니아즈파트의 애니메이션탭 소매뉴 생성 : 현 애니시아의 전신` },
  { date: `2007-02-22`, desc: `테라시아 개발자 포럼(현:가리사니 개발자 공간) 개시` },
  { date: `2007-02-22`, desc: `클라비스 -> 테라시아 이름변경 후 도메인(terassia.com) 구입 서비스 개시` },
]);

const gallery = ref<SiteGallery[]>([
  { src: timetable_1_jpg, popup: false, title: `최초의 애니편성표 [2009년]`, desc: `애니시간표 -> 애니편성표 (기존 애니시간표는 자료소실)` },
  { src: timetable_2_jpg, popup: false, title: `두번째 애니편성표 [2009년]`, desc: `이 디자인을 선호하는 사람이 많아 현재도 제공중` },
  { src: timetable_3_jpg, popup: false, title: `세번째 애니편성표 [2015년]`, desc: `2020년에 다크모드가 추가됨 (정확히는 네번째로 2013년 부터 2015년 까지 존재한 편성표가 있음)` },
  { src: timetable_4_jpg, popup: false, title: `네번째 애니편성표 [2024년]`, desc: `애니시아 리뉴얼 디자인에 맞춘 tailwind를 사용한 반응형 테이블 버전의 편성표` },
  { src: timetable_radio_jpg, popup: false, title: `라디오 편성표 [2009년]`, desc: `라디오 편성표` },
  { src: timetable_widget_1_jpg, popup: false, title: `최초의 애니편성표 위젯 [2009년 1차]`, desc: `스크린샷이 없어 소스로 복원 [내용부분이 다를 수 있음]` },
  { src: timetable_widget_2_jpg, popup: false, title: `애니편성표 위젯 [2009년 2차]`, desc: `여러가지 배경 테마가 있음 (이때부터 커스텀의 시작)` },
  { src: timetable_widget_3_jpg, popup: false, title: `애니편성표 위젯 [2009년 3차]`, desc: `각각의 색상을 바꿀 수 있음` },
  { src: timetable_widget_4_jpg, popup: false, title: `애니편성표 위젯 [2015년]`, desc: `HTML 태그를 지원하지 않는 곳을 위한 이미지 전용으로 퀄리티는 다소 떨어짐` },
  { src: other_1_jpg, popup: false, title: `애니편성표 [2009년]`, desc: `2009년 버전의 드로잉 [작업자: 매디]` },
  { src: anissia_2007, popup: true, title: `테라시아 (현: 가리사니) [2007년]`, desc: `애니시아는 원래 개발자 사이트인 테라시아를 홍보하기 위해 만들어진 소메뉴였다.` },
  { src: anissia_2_jpg, popup: false, title: `애니시아 디자인도안 [작업자: 메티오]`, desc: `애니시아 분리독립을 위해 만들어진 도안중 하나` },
  { src: anissia_3_jpg, popup: false, title: `애니시아 디자인도안 [작업자: 메티오]`, desc: `기획을 지나치게 크게 잡았다가 개발시간이 부족하여 무산됨, 이 "Anissia 마크"는 오랫동안 애니시아의 상징으로 쓰임` },
  { src: anissia_4_jpg, popup: false, title: `애니시아 1차`, desc:`결국 Anissia 마크만 가져와서 제작` },
  { src: anissia_5_jpg, popup: false, title: `애니시아 2차 2015년`, desc:`애니시아 2차` },
  { src: anissia_6_jpg, popup: false, title: `애니시아 2차 2019년`, desc:`실제 서비스 되지 못함 / 2020 리뉴얼의 도안이라고 할 수 있음` },
  { src: anissia_2022_1, popup: true, title: `애니시아 2022년 리뉴얼`, desc:`2026년 데이터 기준으로 찍음.` },
  { src: anissia_2022_2, popup: true, title: `애니시아 2022년 리뉴얼`, desc:`2026년 데이터 기준으로 찍음.` },
  { src: anissia_2026_1, popup: true, title: `애니시아 2026년 리뉴얼`, desc:`2026년 리뉴얼` },
  { src: anissia_2026_2, popup: true, title: `애니시아 2026년 리뉴얼`, desc:`2026년 리뉴얼` },
]);

</script>

<style scoped>
@reference "../../common/tailwind.pcss";

.timeline {
  border-left: 1px solid var(--as-line-2);
}

.history-dot {
  @apply absolute w-2.5 h-2.5 rounded-full;
  top: .35rem;
  left: -1.8125rem;
  background: var(--as-canvas);
  border: 2px solid var(--as-line-2);
  transition: border-color .2s ease;
}

li:hover > .history-dot {
  border-color: var(--as-brand);
}
</style>
