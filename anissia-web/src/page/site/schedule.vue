<template>

  <div :ref="e => containerRef = e" class="as-page as-cp-close">

    <div class="md:flex md:gap-6 justify-between items-start">
      <div class="md:flex-1 p-5 flex as-box overflow-hidden">
        <div class="flex m-auto">
          <iframe v-if="asd.type === 'list'" :ref="e => listFrameRef = e" class="preview-border" src="/schedule/2015" :width="listMaxWidth" :height="asd.listHeight" @load="drawHtml"></iframe>
          <iframe v-if="asd.type === 'timeline'" :ref="e => timelineFrameRef = e" class="preview-border" src="/schedule/2026" :width="timelineMaxWidth" :height="asd.timelineHeight" @load="drawHtml"></iframe>
          <div v-else-if="asd.type === 'img'" class="preview-img preview-border" :style="({width: `${imgMaxWidth}px`,height: `${imgHeight}px`, background: `#${asd.imgListBg}`, 'overflow-y': asd.imgScroll ? 'auto' : 'hidden'})">
            <div class="img-preview" ondragstart="return false" onselectstart="return false">
              <div class="img-title" :style="{background: `#${asd.imgTitleBg}`, color: `#${asd.imgTitle}`}">애니편성표</div>
              <div class="img-ymd" :style="{background: `#${asd.imgYmdBg}`, color: `#${asd.imgYmd}`}">{{asd.imgDataYmd}}</div>
              <div class="img-node" :style="{background: `#${asd.imgListBg}`, color: `#${asd.imgList}`}" v-for="node in asd.imgDataList" :key="node">{{node}}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="md:w-[268px] shrink-0 max-md:mt-6">
        <label class="sub-title">편성표 타입</label>
        <div v-choice class="as-segment md:flex-col">
          <button type="button" @click="setType('timeline')" :class="{'is-on': asd.type == 'timeline'}">
            타임라인 타입
          </button>
          <button type="button" @click="setType('list')" :class="{'is-on': asd.type == 'list'}">
            리스트 타입
          </button>
          <button type="button" @click="setType('img')" :class="{'is-on': asd.type == 'img'}">
            이미지 (블로그)
          </button>
        </div>
        <div v-if="asd.type == 'list'">
          <div class="select-none">
            <label class="sub-title">배경</label>
            <div class="flex space-x-2">
              <div class="color-unit-box" @click="e => openCp(e, 'listBgLight')" :style="`background:#${asd.listBgLight}`"></div>
              <div class="color-unit-box" @click="e => openCp(e, 'listBgDark')" :style="`background:#${asd.listBgDark}`"></div>
            </div>
            <label class="sub-title">타이틀 (배경 / 글자)</label>
            <div class="flex space-x-2">
              <div class="flex color-unit-box">
                <div @click="e => openCp(e, 'listTitleBgLight')" :style="`background:#${asd.listTitleBgLight}`"></div>
                <div @click="e => openCp(e, 'listTitleLight')" :style="`background:#${asd.listTitleLight}`"></div>
              </div>
              <div class="flex color-unit-box">
                <div @click="e => openCp(e, 'listTitleBgDark')" :style="`background:#${asd.listTitleBgDark}`"></div>
                <div @click="e => openCp(e, 'listTitleDark')" :style="`background:#${asd.listTitleDark}`"></div>
              </div>
            </div>
            <label class="sub-title">요일 (배경 / 글자)</label>
            <div class="flex space-x-2">
              <div class="flex color-unit-box">
                <div @click="e => openCp(e, 'listNavBgLight')" :style="`background:#${asd.listNavBgLight}`"></div>
                <div @click="e => openCp(e, 'listNavLight')" :style="`background:#${asd.listNavLight}`"></div>
              </div>
              <div class="flex color-unit-box">
                <div @click="e => openCp(e, 'listNavBgDark')" :style="`background:#${asd.listNavBgDark}`"></div>
                <div @click="e => openCp(e, 'listNavDark')" :style="`background:#${asd.listNavDark}`"></div>
              </div>
            </div>
            <label class="sub-title">요일 (활성) (배경 / 글자)</label>
            <div class="flex space-x-2">
              <div class="flex color-unit-box">
                <div @click="e => openCp(e, 'listNavActBgLight')" :style="`background:#${asd.listNavActBgLight}`"></div>
                <div @click="e => openCp(e, 'listNavActLight')" :style="`background:#${asd.listNavActLight}`"></div>
              </div>
              <div class="flex color-unit-box">
                <div @click="e => openCp(e, 'listNavActBgDark')" :style="`background:#${asd.listNavActBgDark}`"></div>
                <div @click="e => openCp(e, 'listNavActDark')" :style="`background:#${asd.listNavActDark}`"></div>
              </div>
            </div>
            <label class="sub-title">리스트 (배경 / 글자)</label>
            <div class="flex space-x-2">
              <div class="flex color-unit-box">
                <div @click="e => openCp(e, 'listListBgLight')" :style="`background:#${asd.listListBgLight}`"></div>
                <div @click="e => openCp(e, 'listListLight')" :style="`background:#${asd.listListLight}`"></div>
              </div>
              <div class="flex color-unit-box">
                <div @click="e => openCp(e, 'listListBgDark')" :style="`background:#${asd.listListBgDark}`"></div>
                <div @click="e => openCp(e, 'listListDark')" :style="`background:#${asd.listListDark}`"></div>
              </div>
            </div>
            <label class="sub-title">리스트 (활성) (배경 / 글자)</label>
            <div class="flex space-x-2">
              <div class="flex color-unit-box">
                <div @click="e => openCp(e, 'listListActBgLight')" :style="`background:#${asd.listListActBgLight}`"></div>
                <div @click="e => openCp(e, 'listListActLight')" :style="`background:#${asd.listListActLight}`"></div>
              </div>
              <div class="flex color-unit-box">
                <div @click="e => openCp(e, 'listListActBgDark')" :style="`background:#${asd.listListActBgDark}`"></div>
                <div @click="e => openCp(e, 'listListActDark')" :style="`background:#${asd.listListActDark}`"></div>
              </div>
            </div>
            <label class="sub-title">접두어 (글자)</label>
            <div class="flex space-x-2">
              <div class="color-unit-box" @click="e => openCp(e, 'listPrefixLight')" :style="`background:#${asd.listPrefixLight}`"></div>
              <div class="color-unit-box" @click="e => openCp(e, 'listPrefixDark')" :style="`background:#${asd.listPrefixDark}`"></div>
            </div>
          </div>
          <label class="sub-title">모양</label>
          <div class="flex items-center space-x-2 mb-2">
            <span class="w-5 shrink-0 text-center text-xs text-ink-3"><i class="fa-solid fa-left-right"></i></span>
            <input type="range" v-model="asd.listWidth" min="180" max="900" step="10" class="as-range flex-1">
            <input type="number" v-model="asd.listWidth" class="as-input-text w-[52px]! py-1.5 text-center text-xs!" maxlength="3" />
          </div>
          <div class="flex items-center space-x-2 mb-2">
            <span class="w-5 shrink-0 text-center text-xs text-ink-3"><i class="fa-solid fa-up-down"></i></span>
            <input type="range" v-model="asd.listHeight" min="240" max="780" step="10" class="as-range flex-1">
            <input type="number" v-model="asd.listHeight" class="as-input-text w-[52px]! py-1.5 text-center text-xs!" maxlength="3" />
          </div>
          <label class="sub-title">HTML 코드</label>
          <textarea readonly :value="listCode" class="p-3.5 h-[120px] md:h-[162px] as-input-text text-xs! font-mono leading-[1.7]!"></textarea>
          <div class="mt-3">
            <button @click="doCopyClipboard(listCode)" class="w-full py-2.5 as-btn-primary">
              <i class="fa-regular fa-copy mr-1.5"></i>복사하기
            </button>
          </div>
        </div>
        <div v-if="asd.type == 'timeline'">
          <div class="select-none">
            <label class="sub-title">프리셋</label>
            <div class="grid grid-cols-4 gap-1.5">
              <button v-for="preset in PRESETS" :key="preset.name" type="button" class="preset" :class="{'is-on': timelineSrc == encodeTheme(preset.theme)}" @click="applyPreset(preset.theme)">
                <span class="preset-chip">
                  <span :style="presetStyle(preset.theme.light)"></span>
                  <span :style="presetStyle(preset.theme.dark)"></span>
                </span>
                <span class="preset-name">{{preset.name}}</span>
              </button>
            </div>

            <label class="sub-title">포인트</label>
            <div class="flex space-x-2">
              <div class="color-unit-box" @click="e => openCp(e, 'timelineAccentLight')" :style="`background:#${asd.timelineAccentLight}`"></div>
              <div class="color-unit-box" @click="e => openCp(e, 'timelineAccentDark')" :style="`background:#${asd.timelineAccentDark}`"></div>
            </div>
            <label class="sub-title">바탕</label>
            <div class="flex space-x-2">
              <div class="color-unit-box" @click="e => openCp(e, 'timelineBaseLight')" :style="`background:#${asd.timelineBaseLight}`"></div>
              <div class="color-unit-box" @click="e => openCp(e, 'timelineBaseDark')" :style="`background:#${asd.timelineBaseDark}`"></div>
            </div>
            <label class="sub-title">분위기</label>
            <div class="flex space-x-2">
              <div class="color-unit-box" @click="e => openCp(e, 'timelineAuraLight')" :style="`background:#${asd.timelineAuraLight}`"></div>
              <div class="color-unit-box" @click="e => openCp(e, 'timelineAuraDark')" :style="`background:#${asd.timelineAuraDark}`"></div>
            </div>
            <label class="sub-title">번짐 세기</label>
            <div class="flex items-center space-x-2">
              <span class="w-5 shrink-0 text-center text-xs text-ink-3"><i class="fa-solid fa-wand-magic-sparkles"></i></span>
              <input type="range" v-model.number="asd.timelineGlow" min="0" max="100" step="1" class="as-range flex-1">
              <input type="number" v-model.number="asd.timelineGlow" class="as-input-text w-[52px]! py-1.5 text-center text-xs!" maxlength="3" />
            </div>
            <p class="mt-3 text-xs text-ink-3 leading-relaxed">고른 색은 기준점입니다. 글자·테두리·유리면·그라데이션은 읽기 좋게 자동으로 맞춰집니다. (왼쪽 라이트 / 오른쪽 다크)</p>
          </div>

          <label class="sub-title">모양</label>
          <div class="flex items-center space-x-2 mb-2">
            <span class="w-5 shrink-0 text-center text-xs text-ink-3"><i class="fa-solid fa-left-right"></i></span>
            <input type="range" v-model="asd.timelineWidth" min="240" max="900" step="10" class="as-range flex-1">
            <input type="number" v-model="asd.timelineWidth" class="as-input-text w-[52px]! py-1.5 text-center text-xs!" maxlength="3" />
          </div>
          <div class="flex items-center space-x-2 mb-2">
            <span class="w-5 shrink-0 text-center text-xs text-ink-3"><i class="fa-solid fa-up-down"></i></span>
            <input type="range" v-model="asd.timelineHeight" min="300" max="880" step="10" class="as-range flex-1">
            <input type="number" v-model="asd.timelineHeight" class="as-input-text w-[52px]! py-1.5 text-center text-xs!" maxlength="3" />
          </div>
          <label class="sub-title">HTML 코드</label>
          <textarea readonly :value="timelineCode" class="p-3.5 h-[120px] md:h-[162px] as-input-text text-xs! font-mono leading-[1.7]!"></textarea>
          <div class="mt-3">
            <button @click="doCopyClipboard(timelineCode)" class="w-full py-2.5 as-btn-primary">
              <i class="fa-regular fa-copy mr-1.5"></i>복사하기
            </button>
          </div>
        </div>
        <div v-if="asd.type == 'img'">
          <div class="select-none">
            <label class="sub-title">제목 (배경색 / 글자색)</label>
            <div class="flex space-x-2">
              <div class="color-unit-box" @click="e => openCp(e, 'imgTitleBg')" :style="`background:#${asd.imgTitleBg}`"></div>
              <div class="color-unit-box" @click="e => openCp(e, 'imgTitle')" :style="`background:#${asd.imgTitle}`"></div>
            </div>
            <label class="sub-title">날짜 (배경색 / 글자색)</label>
            <div class="flex space-x-2">
              <div class="color-unit-box" @click="e => openCp(e, 'imgYmdBg')" :style="`background:#${asd.imgYmdBg}`"></div>
              <div class="color-unit-box" @click="e => openCp(e, 'imgYmd')" :style="`background:#${asd.imgYmd}`"></div>
            </div>
            <label class="sub-title">목록 (배경색 / 글자색)</label>
            <div class="flex space-x-2">
              <div class="color-unit-box" @click="e => openCp(e, 'imgListBg')" :style="`background:#${asd.imgListBg}`"></div>
              <div class="color-unit-box" @click="e => openCp(e, 'imgList')" :style="`background:#${asd.imgList}`"></div>
            </div>
          </div>

          <label class="sub-title">모양</label>
          <div class="flex items-center space-x-2 mb-2">
            <span class="w-5 shrink-0 text-center text-xs text-ink-3"><i class="fa-solid fa-left-right"></i></span>
            <input type="range" v-model="asd.imgWidth" min="150" max="900" step="10" class="as-range flex-1">
            <input type="text" v-model="asd.imgWidth" class="as-input-text w-[52px]! py-1.5 text-center text-xs!" maxlength="3" />
          </div>
          <div class="flex items-center space-x-2 mb-2">
            <span class="w-5 shrink-0 text-center text-xs text-ink-3"><i class="fa-solid fa-up-down"></i></span>
            <input type="range" v-model="asd.imgSize" min="5" max="20" step="1" class="as-range flex-1">
            <input type="text" v-model="asd.imgSize" class="as-input-text w-[52px]! py-1.5 text-center text-xs!" maxlength="3" />
          </div>
          <label for="use-scroll" class="as-switch my-4">
            <input type="checkbox" v-model="asd.imgScroll" id="use-scroll">
            <span class="as-switch-track"></span>
            <span class="ml-3 text-sm font-medium text-ink-2">스크롤 사용여부</span>
          </label>
          <label class="sub-title">HTML 코드</label>
          <textarea readonly :value="imgCode" class="p-3.5 h-[88px] md:h-[162px] as-input-text text-xs! font-mono leading-[1.7]!"></textarea>
          <div class="mt-3">
            <button @click="doCopyClipboard(imgCode)" class="w-full py-2.5 as-btn-primary">
              <i class="fa-regular fa-copy mr-1.5"></i>복사하기
            </button>
          </div>
        </div>

      </div>
    </div>

    <h2 class="as-section-title mt-12 mb-4">애니 편성표</h2>

    <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <div class="info-box as-card">
        <div class="w-[50px]"><img class="w-full" src="./schedule/icon-schedule.svg"/></div>
        <div class="flex-1 pl-4">
          <a href="/schedule/2026" target="_blank"><h5>애니편성표 (2026)</h5></a>
          <p>애니편성표 타임라인 버전</p>
        </div>
      </div>

      <div class="info-box as-card">
        <div class="w-[50px]"><img class="w-full" src="./schedule/icon-schedule.svg"/></div>
        <div class="flex-1 pl-4">
          <a href="/schedule/2015" target="_blank"><h5>애니편성표 (2015)</h5></a>
          <p>애니편성표 리스트 버전</p>
        </div>
      </div>

      <div class="info-box as-card">
        <div class="w-[50px]"><img class="w-full" src="./schedule/icon-schedule.svg"/></div>
        <div class="flex-1 pl-4">
          <a href="/schedule/2009" target="_blank"><h5>애니편성표 (2009)</h5></a>
          <p>리메이크 클래식 버전.</p>
        </div>
      </div>

      <div class="info-box as-card">
        <div class="w-[50px]"><img class="w-full" src="./schedule/icon-time.svg"/></div>
        <div class="flex-1 pl-4">
          <router-link to="/introduce"><h5>애니시아 연혁</h5></router-link>
          <p>애니시아 연혁소개</p>
        </div>
      </div>
    </div>

    <h2 class="as-section-title mt-12 mb-2">서드파티</h2>
    <p class="as-desc mb-4">
      "애니시아 API" 를 통해 만들어진 프로그램입니다.<br/>
      각 프로그램에 대한 문의는 해당 개발자/단체에 하셔야 합니다.
    </p>

    <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <div class="info-box as-card">
        <div class="w-[50px]"><img class="w-full" src="./schedule/3rd-ios.svg"/></div>
        <div class="flex-1 pl-4">
          <a href="https://apps.apple.com/kr/app/aeni-pyeonseongpyo/id917536862" target="_blank"><h5>애니 편성표</h5></a>
          <p>iOS / Young Ho Kim / 2014년</p>
        </div>
      </div>
      <div class="info-box as-card">
        <div class="w-[50px]"><img class="w-full" src="./schedule/3rd-ios.svg"/></div>
        <div class="flex-1 pl-4">
          <a href="https://github.com/qkdxorjs1002/AniSched-Desktop#homebrew-macos-1011-%EC%9D%B4%EC%83%81" target="_blank"><h5>AniSched-Desktop</h5></a>
          <p>macOS / Novang / 2021년</p>
        </div>
      </div>
      <div class="info-box as-card">
        <div class="w-[50px]"><img class="w-full" src="./schedule/3rd-android.svg"/></div>
        <div class="flex-1 pl-4">
          <a href="https://play.google.com/store/apps/details?id=com.novang.anisched" target="_blank"><h5>AniSched</h5></a>
          <p>Android / Novang / 2021년</p>
        </div>
      </div>
      <div class="info-box as-card">
        <div class="w-[50px]"><img class="w-full" src="./schedule/3rd-android.svg"/></div>
        <div class="flex-1 pl-4">
          <a href="https://play.google.com/store/apps/details?id=anissia.android.schedule" target="_blank"><h5>애니 편성표</h5></a>
          <p>Android / 애니시아 / 2021년</p>
        </div>
      </div>
      <div class="info-box as-card">
        <div class="w-[50px]"><img class="w-full" src="./schedule/3rd-windows.svg"/></div>
        <div class="flex-1 pl-4">
          <a href="https://www.microsoft.com/store/apps/9PB5WSXN3TMN" target="_blank"><h5>AniSched</h5></a>
          <p>Windows / Novang / 2015년</p>
        </div>
      </div>
      <div class="info-box as-card">
        <div class="w-[50px]"><img class="w-full" src="./schedule/3rd-chrome.svg"/></div>
        <div class="flex-1 pl-4">
          <a href="https://chrome.google.com/webstore/detail/anisched/lkpfenhnbjcjekjihacpcoekgdclobdn" target="_blank"><h5>AniSched</h5></a>
          <p>Chrome / Novang / 2015년</p>
        </div>
      </div>
    </div>

    <h2 class="as-section-title mt-12 mb-4">소스코드 / API</h2>

    <div class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">

      <div class="info-box as-card">
        <div class="w-[50px]"><img class="w-full" src="./schedule/3rd-api.svg"/></div>
        <div class="flex-1 pl-4">
          <a href="https://github.com/anissia-net/anissia/tree/master/docs/anime_schdule.md" target="_blank"><h5>API 가이드</h5></a>
          <p>3rd party 애니편성표 앱 제작 가이드</p>
        </div>
      </div>

      <div class="info-box as-card">
        <div class="w-[50px]"><img class="w-full" src="./schedule/3rd-doc.svg"/></div>
        <div class="flex-1 pl-4">
          <a href="https://github.com/anissia-net/anissia/tree/master/docs/anime_rank.md" target="_blank"><h5>랭킹 집계기준</h5></a>
          <p>애니메이션 랭킹 집계기준 문서</p>
        </div>
      </div>

      <div class="info-box as-card">
        <div class="w-[50px]"><img class="w-full" src="./schedule/icon-code.svg"/></div>
        <div class="flex-1 pl-4">
          <a href="https://github.com/anissia-net/anissia/tree/master/anissia-web" target="_blank"><h5>프론트엔드</h5></a>
          <p>Vue.js, Typescript</p>
        </div>
      </div>

      <div class="info-box as-card">
        <div class="w-[50px]"><img class="w-full" src="./schedule/icon-code.svg"/></div>
        <div class="flex-1 pl-4">
          <a href="https://github.com/anissia-net/anissia/tree/master/anissia-core" target="_blank"><h5>백엔드</h5></a>
          <p>Kotlin, Spring, JPA, QueryDSL, Elasticsearch</p>
        </div>
      </div>

    </div>

  </div>

  <div v-if="cpShow" class="as-cp-not-close fixed inline-block select-none" :style="`width:256px;height:283px;top:${cpY}px;left:${cpX}px;`">
    <color-picker :color="cpColor" :callback="onCpPick"/>
  </div>

</template>

<script setup lang="ts">
import {computed, defineAsyncComponent, onMounted, onUnmounted, ref, watch} from "vue";
import {whenIdle} from "../../common/idle";
import {DEFAULT_THEME, encodeTheme, PRESETS, Seed, Theme} from "../schedule/2026/palette";
import {DateFormat} from "raon";
import animeRemote from "../../domain/anime/remote/animeRemote";
import toast from "../../common/toast";

// 색 고르기(colorjs 포함)는 무거워 따로 받는다 — 한가할 때 미리 받아 두어 처음 열 때 기다리지 않게 한다.
const loadColorPicker = () => import("../../domain/colorPicker/ColorPicker.vue");
const ColorPicker = defineAsyncComponent(loadColorPicker);

const asd = ref({
  type: 'timeline',

  listBgLight: 'ffffff', listTitleBgLight: '5987b6', listTitleLight: 'ffffff',
  listNavBgLight: 'f2f2f2', listNavLight: '497ba7', listNavActBgLight: '9cb3c7', listNavActLight: 'ffffff',
  listListBgLight: 'ffffff', listListLight: '555555', listListActBgLight: 'f8f8f8', listListActLight: '2474ce',
  listPrefixLight: 'cb3434',
  listBgDark: '000000', listTitleBgDark: '000000', listTitleDark: '777777',
  listNavBgDark: '111111', listNavDark: '777777', listNavActBgDark: '111111', listNavActDark: 'c3b443',
  listListBgDark: '070707', listListDark: '999999', listListActBgDark: '000000', listListActDark: 'cccccc',
  listPrefixDark: '3a7da3',
  listWidth: 650, listHeight: 400,

  timelineAccentLight: DEFAULT_THEME.light.accent, timelineBaseLight: DEFAULT_THEME.light.base, timelineAuraLight: DEFAULT_THEME.light.aura,
  timelineAccentDark: DEFAULT_THEME.dark.accent, timelineBaseDark: DEFAULT_THEME.dark.base, timelineAuraDark: DEFAULT_THEME.dark.aura,
  timelineGlow: DEFAULT_THEME.glow,
  timelineWidth: 720, timelineHeight: 640,

  imgTitleBg: '63a883', imgTitle: 'ffffff',
  imgYmdBg: 'd8d8d8', imgYmd: '000000',
  imgListBg: 'ffffff', imgList: '000000',
  imgScroll: true,
  imgWidth: 180, imgSize: 10,
  imgDataList: [] as string[],
  imgDataYmd: new DateFormat().format("yyyy년 MM월 dd일"),
});

const maxWidth = ref(0);
const containerRef = ref(null) as any;
const listFrameRef = ref(null) as any;
const listMaxWidth = computed(() => Math.min(maxWidth.value, asd.value.listWidth));
const listCode = computed(() => `<iframe src="${location.origin + '/schedule/2015#' + listSrc.value}" width="${asd.value.listWidth}" height="${asd.value.listHeight}" frameborder="0"></iframe>`);
const listSrc = computed(() => asd.value.listBgLight + asd.value.listTitleBgLight + asd.value.listTitleLight + asd.value.listNavBgLight + asd.value.listNavLight + asd.value.listNavActBgLight +
    asd.value.listNavActLight + asd.value.listListBgLight + asd.value.listListLight + asd.value.listListActBgLight + asd.value.listListActLight + asd.value.listPrefixLight +
    asd.value.listBgDark + asd.value.listTitleBgDark + asd.value.listTitleDark + asd.value.listNavBgDark + asd.value.listNavDark + asd.value.listNavActBgDark +
    asd.value.listNavActDark + asd.value.listListBgDark + asd.value.listListDark + asd.value.listListActBgDark + asd.value.listListActDark + asd.value.listPrefixDark);

const timelineFrameRef = ref(null) as any;
const timelineMaxWidth = computed(() => Math.min(maxWidth.value, asd.value.timelineWidth));
const timelineSrc = computed(() => encodeTheme({
  light: {accent: asd.value.timelineAccentLight, base: asd.value.timelineBaseLight, aura: asd.value.timelineAuraLight},
  dark: {accent: asd.value.timelineAccentDark, base: asd.value.timelineBaseDark, aura: asd.value.timelineAuraDark},
  glow: asd.value.timelineGlow,
}));
const timelineCode = computed(() => `<iframe src="${location.origin + '/schedule/2026#' + timelineSrc.value}" width="${asd.value.timelineWidth}" height="${asd.value.timelineHeight}" frameborder="0"></iframe>`);
watch(timelineSrc, () => asd.value.type == 'timeline' && drawHtml());

function applyPreset(theme: Theme) {
  Object.assign(asd.value, {
    timelineAccentLight: theme.light.accent, timelineBaseLight: theme.light.base, timelineAuraLight: theme.light.aura,
    timelineAccentDark: theme.dark.accent, timelineBaseDark: theme.dark.base, timelineAuraDark: theme.dark.aura,
    timelineGlow: theme.glow,
  });
}
function presetStyle(seed: Seed) {
  return {background: `radial-gradient(circle at 30% 30%, #${seed.accent} 0 22%, transparent 23%), linear-gradient(135deg, #${seed.aura}, #${seed.base} 70%)`};
}

const imgMaxWidth = computed(() => Math.min(maxWidth.value, asd.value.imgWidth));
const imgHeight = computed(() => 50 + (asd.value.imgSize * 20));
const imgCode = computed(() => {
  const theme = asd.value.imgTitleBg + asd.value.imgTitle + asd.value.imgYmdBg + asd.value.imgYmd + asd.value.imgListBg + asd.value.imgList;
  const origin = location.origin;
  const api = (origin + '/api').replace('anissia.net/api', 'api.anissia.net');
  return `<div style="width:${asd.value.imgWidth}px;height:${imgHeight.value}px;background:#${asd.value.imgListBg};overflow-y:${asd.value.imgScroll ? 'auto' : 'hidden'}"><a href="${origin + '/schedule/2015'}" target="_blank"><img src="${api}/anime/schedule/svg/${asd.value.imgWidth}/${theme}"/></a></div>`;
});
function callFrame(frame: any, fnName: string, arg: string, retry: number = 40) {
  const win = frame?.contentWindow;
  if (!win) return;
  if (typeof win[fnName] == 'function') {
    win[fnName](arg);
  } else if (retry > 0) {
    setTimeout(() => callFrame(frame, fnName, arg, retry - 1), 25);
  }
}
function drawHtml() {
  if (asd.value.type == 'list') {
    callFrame(listFrameRef.value, 'repaint', listSrc.value);
  } else if (asd.value.type == 'timeline') {
    callFrame(timelineFrameRef.value, 'repaint', timelineSrc.value);
  }
}
function bindMaxWidth() {
  maxWidth.value = (containerRef.value.offsetWidth as number) - (matchMedia('(min-width: 768px)').matches ? 400 : 100);
}
function colorModeHtml(mode: string) {
  if (asd.value.type == 'list') {
    callFrame(listFrameRef.value, 'colorMode', mode);
  } else if (asd.value.type == 'timeline') {
    callFrame(timelineFrameRef.value, 'colorMode', mode);
  }
}
function setType(type: string) {
  asd.value.type = type;
  if (type == 'img' && asd.value.imgDataList.length == 0) {
    animeRemote.getScheduleAnimeList(new Date().getDay()).then((list) => asd.value.imgDataList = list.map(e => `${e.scheduleTime} ${e.subject}`));
  }
}
function doCopyClipboard(text: string) {
  navigator.clipboard.writeText(text);
  toast.success('복사되었습니다.');
}
onMounted(() => {
  bindMaxWidth();
  addEventListener('resize', bindMaxWidth, true);
});
onUnmounted(() => {
  removeEventListener('resize', bindMaxWidth, true);
});

const cpColor = ref('#ffffff');
const cpShow = ref(false);
const cpX = ref(0);
const cpY = ref(0);
const cpTarget = ref('');

function openCp(event: MouseEvent, target: string) {
  cpX.value = Math.max(Math.min(event.clientX - 10, window.innerWidth - 266), 0);
  cpY.value = Math.max(Math.min(event.clientY + 10, window.innerHeight - 290), 0);
  cpShow.value = true;
  cpTarget.value = target;
  cpColor.value = `#${(asd.value as any)[cpTarget.value]}`;
  if (['list', 'timeline'].indexOf(asd.value.type) != -1) {
    colorModeHtml(cpTarget.value.endsWith('Dark') ? 'dark' : 'light');
  }
}

function onCpPick(color: any) {
  const asv = asd.value as any;
  const target = cpTarget.value;
  asv[target] = color;
  drawHtml();
}

function onCpClose(event: Event) {
  if (cpShow.value && !((event?.target as HTMLElement | null)?.closest('html,.as-cp-close,.color-unit-box,.as-cp-not-close')?.matches('.as-cp-not-close'))) {
    cpShow.value = false;
  }
}

onMounted(() => {
  addEventListener('click', onCpClose, true);
  whenIdle(loadColorPicker);
});

onUnmounted(() => {
  removeEventListener('click', onCpClose, true);
});

</script>

<style scoped>
@reference "../../common/tailwind.pcss";

.sub-title {
  @apply block mt-5 mb-2 text-2xs font-semibold uppercase text-center;
  letter-spacing: .08em;
  color: var(--as-ink-3);
}

.color-unit-box, .color-unit-box-3 {
  @apply flex-1 h-[38px] overflow-hidden;
  border-radius: var(--radius-ctl);
  border: 1px solid var(--as-line);
  transition: border-color .18s ease, transform .12s ease;
  &:hover { border-color: var(--as-line-2) }
  &:active { transform: translateY(1px) }
  > div { @apply flex-1 cursor-pointer }
}

.info-box {
  @apply p-5 flex items-center gap-4;
  img { @apply w-[42px] }
  h5 {
    @apply mb-1 text-md font-semibold;
    color: var(--as-ink);
    transition: color .18s ease;
  }
  p {
    @apply text-sm;
    color: var(--as-ink-3);
  }
  &:hover h5 { color: var(--as-brand) }
}

.preview-border {
  overflow: hidden;
}

.img-preview {
  font-family: 'Malgun Gothic'; font-weight: normal; margin:0 auto; overflow: hidden; cursor:default;
  .img-title { font-size:13px; font-weight: bold; line-height:30px; height:30px; text-align: center }
  .img-ymd { font-size:12px; line-height:20px; height:20px; text-align: center }
  .img-node { font-size:13px; line-height:20px; height:20px; padding-left:2px; text-align: left; overflow: hidden; }
}
input[type=number]::-webkit-inner-spin-button { appearance: none }

.preset {
  @apply flex flex-col items-center gap-1 py-1.5 cursor-pointer;
  border-radius: var(--radius-ctl);
  transition: background-color .18s ease;
  &:hover { background: var(--as-muted) }
  &.is-on { background: var(--as-brand-soft) }
  &.is-on .preset-name { color: var(--as-brand); font-weight: 600 }
}
.preset-chip {
  @apply flex w-[42px] h-[26px] overflow-hidden;
  border-radius: 999px;
  box-shadow: 0 0 0 1px var(--as-line-2);
  > span { @apply flex-1 }
}
.preset-name {
  @apply text-2xs;
  color: var(--as-ink-2);
}

</style>
