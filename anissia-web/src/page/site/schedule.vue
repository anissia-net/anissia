<template>

  <div :ref="e => containerRef = e" class="as-page as-cp-close">

    <div class="md:flex md:gap-6 justify-between items-start">
      <div class="md:flex-1 p-5 flex as-box overflow-hidden">
        <div class="flex m-auto">
          <iframe v-if="asd.type === 'card'" :ref="e => cardFrameRef = e" class="preview-border" src="/schedule/2024" :width="cardMaxWidth" :height="asd.cardHeight" @load="drawHtml"></iframe>
          <iframe v-if="asd.type === 'list'" :ref="e => listFrameRef = e" class="preview-border" src="/schedule/2015" :width="listMaxWidth" :height="asd.listHeight" @load="drawHtml"></iframe>
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
        <div class="as-segment md:flex-col">
          <button type="button" @click="setType('card')" :class="{'is-on': asd.type == 'card'}">
            카드 타입
          </button>
          <button type="button" @click="setType('list')" :class="{'is-on': asd.type == 'list'}">
            리스트 타입
          </button>
          <button type="button" @click="setType('img')" :class="{'is-on': asd.type == 'img'}">
            이미지 (블로그)
          </button>
        </div>
        <div v-if="asd.type == 'card'">
          <label class="sub-title">커스텀 모드</label>
          <div class="as-segment">
            <button type="button" @click="asd.cardSimpleMode = true" :class="{'is-on': asd.cardSimpleMode}">
              간편
            </button>
            <button type="button" @click="asd.cardSimpleMode = false" :class="{'is-on': !asd.cardSimpleMode}">
              자세히
            </button>
          </div>

          <div class="select-none">
            <label class="sub-title">배경</label>
            <div class="flex space-x-2">
              <div class="color-unit-box" @click="e => openCp(e, 'cardBgLight')" :style="`background:#${asd.cardBgLight}`"></div>
              <div class="color-unit-box" @click="e => openCp(e, 'cardBgDark')" :style="`background:#${asd.cardBgDark}`"></div>
            </div>

            <label class="sub-title">타이틀 글자 (기본 / 활성)</label>
            <div class="flex space-x-2">
              <div class="flex color-unit-box">
                <div @click="e => openCp(e, 'cardTitleLight')" :style="`background:#${asd.cardTitleLight}`"></div>
                <div @click="e => openCp(e, 'cardTitleHoverLight')" :style="`background:#${asd.cardTitleHoverLight}`"></div>
              </div>
              <div class="flex color-unit-box">
                <div @click="e => openCp(e, 'cardTitleDark')" :style="`background:#${asd.cardTitleDark}`"></div>
                <div @click="e => openCp(e, 'cardTitleHoverDark')" :style="`background:#${asd.cardTitleHoverDark}`"></div>
              </div>
            </div>

            <div v-if="asd.cardSimpleMode">
              <label class="sub-title">타일 - 기본 (배경 / 글자)</label>
              <div class="flex space-x-2">
                <div class="flex color-unit-box-3">
                  <div @click="e => openCp(e, 'cardNavBgLight')" :style="`background:#${asd.cardNavBgLight}`"></div>
                  <div @click="e => openCp(e, 'cardNavTextLight')" :style="`background:#${asd.cardNavTextLight}`"></div>
                </div>
                <div class="flex color-unit-box-3">
                  <div @click="e => openCp(e, 'cardNavBgDark')" :style="`background:#${asd.cardNavBgDark}`"></div>
                  <div @click="e => openCp(e, 'cardNavTextDark')" :style="`background:#${asd.cardNavTextDark}`"></div>
                </div>
              </div>

              <label class="sub-title">타일 - 활성 (배경 / 글자)</label>
              <div class="flex space-x-2">
                <div class="flex color-unit-box-3">
                  <div @click="e => openCp(e, 'cardNavBgPickLight')" :style="`background:#${asd.cardNavBgPickLight}`"></div>
                  <div @click="e => openCp(e, 'cardNavTextPickLight')" :style="`background:#${asd.cardNavTextPickLight}`"></div>
                </div>
                <div class="flex color-unit-box-3">
                  <div @click="e => openCp(e, 'cardNavBgPickDark')" :style="`background:#${asd.cardNavBgPickDark}`"></div>
                  <div @click="e => openCp(e, 'cardNavTextPickDark')" :style="`background:#${asd.cardNavTextPickDark}`"></div>
                </div>
              </div>
            </div>
            <div v-else>
              <label class="sub-title">요일 - 기본 (배경 / 글자 / 테두리)</label>
              <div class="flex space-x-2">
                <div class="flex color-unit-box-3">
                  <div @click="e => openCp(e, 'cardNavBgLight')" :style="`background:#${asd.cardNavBgLight}`"></div>
                  <div @click="e => openCp(e, 'cardNavTextLight')" :style="`background:#${asd.cardNavTextLight}`"></div>
                  <div @click="e => openCp(e, 'cardNavBorderLight')" :style="`background:#${asd.cardNavBorderLight}`"></div>
                </div>
                <div class="flex color-unit-box-3">
                  <div @click="e => openCp(e, 'cardNavBgDark')" :style="`background:#${asd.cardNavBgDark}`"></div>
                  <div @click="e => openCp(e, 'cardNavTextDark')" :style="`background:#${asd.cardNavTextDark}`"></div>
                  <div @click="e => openCp(e, 'cardNavBorderDark')" :style="`background:#${asd.cardNavBorderDark}`"></div>
                </div>
              </div>

              <label class="sub-title">요일 - 활성 (배경 / 글자 / 테두리)</label>
              <div class="flex space-x-2">
                <div class="flex color-unit-box-3">
                  <div @click="e => openCp(e, 'cardNavBgPickLight')" :style="`background:#${asd.cardNavBgPickLight}`"></div>
                  <div @click="e => openCp(e, 'cardNavTextPickLight')" :style="`background:#${asd.cardNavTextPickLight}`"></div>
                  <div @click="e => openCp(e, 'cardNavBorderPickLight')" :style="`background:#${asd.cardNavBorderPickLight}`"></div>
                </div>
                <div class="flex color-unit-box-3">
                  <div @click="e => openCp(e, 'cardNavBgPickDark')" :style="`background:#${asd.cardNavBgPickDark}`"></div>
                  <div @click="e => openCp(e, 'cardNavTextPickDark')" :style="`background:#${asd.cardNavTextPickDark}`"></div>
                  <div @click="e => openCp(e, 'cardNavBorderPickDark')" :style="`background:#${asd.cardNavBorderPickDark}`"></div>
                </div>
              </div>
            </div>

            <div v-if="!asd.cardSimpleMode">
              <label class="sub-title">목록 - 카드 기본 (배경 / 테두리)</label>
              <div class="flex space-x-2">
                <div class="flex color-unit-box">
                  <div @click="e => openCp(e, 'cardListBgLight')" :style="`background:#${asd.cardListBgLight}`"></div>
                  <div @click="e => openCp(e, 'cardListBorderLight')" :style="`background:#${asd.cardListBorderLight}`"></div>
                </div>
                <div class="flex color-unit-box">
                  <div @click="e => openCp(e, 'cardListBgDark')" :style="`background:#${asd.cardListBgDark}`"></div>
                  <div @click="e => openCp(e, 'cardListBorderDark')" :style="`background:#${asd.cardListBorderDark}`"></div>
                </div>
              </div>

              <label class="sub-title">목록 - 카드 활성 (배경 / 테두리)</label>
              <div class="flex space-x-2">
                <div class="flex color-unit-box">
                  <div @click="e => openCp(e, 'cardListBgPickLight')" :style="`background:#${asd.cardListBgPickLight}`"></div>
                  <div @click="e => openCp(e, 'cardListBorderPickLight')" :style="`background:#${asd.cardListBorderPickLight}`"></div>
                </div>
                <div class="flex color-unit-box">
                  <div @click="e => openCp(e, 'cardListBgPickDark')" :style="`background:#${asd.cardListBgPickDark}`"></div>
                  <div @click="e => openCp(e, 'cardListBorderPickDark')" :style="`background:#${asd.cardListBorderPickDark}`"></div>
                </div>
              </div>
            </div>

            <label class="sub-title">목록 - 글자 (접두어 / 한글제목 / 원어제목)</label>
            <div class="flex space-x-2">
              <div class="flex color-unit-box-3">
                <div @click="e => openCp(e, 'cardListTextHighlightLight')" :style="`background:#${asd.cardListTextHighlightLight}`"></div>
                <div @click="e => openCp(e, 'cardListTextSubjectLight')" :style="`background:#${asd.cardListTextSubjectLight}`"></div>
                <div @click="e => openCp(e, 'cardListTextOriginalSubjectLight')" :style="`background:#${asd.cardListTextOriginalSubjectLight}`"></div>
              </div>
              <div class="flex color-unit-box-3">
                <div @click="e => openCp(e, 'cardListTextHighlightDark')" :style="`background:#${asd.cardListTextHighlightDark}`"></div>
                <div @click="e => openCp(e, 'cardListTextSubjectDark')" :style="`background:#${asd.cardListTextSubjectDark}`"></div>
                <div @click="e => openCp(e, 'cardListTextOriginalSubjectDark')" :style="`background:#${asd.cardListTextOriginalSubjectDark}`"></div>
              </div>
            </div>

            <label class="sub-title">목록 - 글자 활성 (접두어 / 한글제목 / 원어제목)</label>
            <div class="flex space-x-2">
              <div class="flex color-unit-box-3">
                <div @click="e => openCp(e, 'cardListTextHighlightPickLight')" :style="`background:#${asd.cardListTextHighlightPickLight}`"></div>
                <div @click="e => openCp(e, 'cardListTextSubjectPickLight')" :style="`background:#${asd.cardListTextSubjectPickLight}`"></div>
                <div @click="e => openCp(e, 'cardListTextOriginalSubjectPickLight')" :style="`background:#${asd.cardListTextOriginalSubjectPickLight}`"></div>
              </div>
              <div class="flex color-unit-box-3">
                <div @click="e => openCp(e, 'cardListTextHighlightPickDark')" :style="`background:#${asd.cardListTextHighlightPickDark}`"></div>
                <div @click="e => openCp(e, 'cardListTextSubjectPickDark')" :style="`background:#${asd.cardListTextSubjectPickDark}`"></div>
                <div @click="e => openCp(e, 'cardListTextOriginalSubjectPickDark')" :style="`background:#${asd.cardListTextOriginalSubjectPickDark}`"></div>
              </div>
            </div>

            <label class="sub-title">목록 - 태그 (배경 / 글자)</label>
            <div class="flex space-x-2">
              <div class="flex color-unit-box">
                <div @click="e => openCp(e, 'cardListTagBgLight')" :style="`background:#${asd.cardListTagBgLight}`"></div>
                <div @click="e => openCp(e, 'cardListTagTextLight')" :style="`background:#${asd.cardListTagTextLight}`"></div>
              </div>
              <div class="flex color-unit-box">
                <div @click="e => openCp(e, 'cardListTagBgDark')" :style="`background:#${asd.cardListTagBgDark}`"></div>
                <div @click="e => openCp(e, 'cardListTagTextDark')" :style="`background:#${asd.cardListTagTextDark}`"></div>
              </div>
            </div>

            <label class="sub-title">목록 - 태그 활성 (배경 / 글자)</label>
            <div class="flex space-x-2">
              <div class="flex color-unit-box">
                <div @click="e => openCp(e, 'cardListTagBgPickLight')" :style="`background:#${asd.cardListTagBgPickLight}`"></div>
                <div @click="e => openCp(e, 'cardListTagTextPickLight')" :style="`background:#${asd.cardListTagTextPickLight}`"></div>
              </div>
              <div class="flex color-unit-box">
                <div @click="e => openCp(e, 'cardListTagBgPickDark')" :style="`background:#${asd.cardListTagBgPickDark}`"></div>
                <div @click="e => openCp(e, 'cardListTagTextPickDark')" :style="`background:#${asd.cardListTagTextPickDark}`"></div>
              </div>
            </div>

          </div>
          
          <label class="sub-title">모양</label>
          <div class="flex items-center space-x-2 mb-2">
            <span class="w-5 shrink-0 text-center text-xs text-ink-3"><i class="fa-solid fa-left-right"></i></span>
            <input type="range" v-model="asd.cardWidth" min="240" max="900" step="10" class="as-range flex-1">
            <input type="number" v-model="asd.cardWidth" class="as-input-text w-[52px]! py-1.5 text-center text-xs!" maxlength="3" />
          </div>
          <div class="flex items-center space-x-2 mb-2">
            <span class="w-5 shrink-0 text-center text-xs text-ink-3"><i class="fa-solid fa-up-down"></i></span>
            <input type="range" v-model="asd.cardHeight" min="300" max="880" step="10" class="as-range flex-1">
            <input type="number" v-model="asd.cardHeight" class="as-input-text w-[52px]! py-1.5 text-center text-xs!" maxlength="3" />
          </div>
          <label class="sub-title">HTML 코드</label>
          <textarea readonly :value="cardCode" class="p-3.5 h-[120px] md:h-[162px] as-input-text text-xs! font-mono leading-[1.7]!"></textarea>
          <div class="mt-3">
            <button @click="doCopyClipboard(cardCode)" class="w-full py-2.5 as-btn-primary">
              <i class="fa-regular fa-copy mr-1.5"></i>복사하기
            </button>
          </div>
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
          <a href="/schedule/2024" target="_blank"><h5>애니편성표 (2024)</h5></a>
          <p>애니편성표 카드 버전</p>
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
          <a href="https://github.com/anissia-net/document/blob/main/api_anime_schdule.md" target="_blank"><h5>API 가이드</h5></a>
          <p>3rd party 애니편성표 앱 제작 가이드</p>
        </div>
      </div>

      <div class="info-box as-card">
        <div class="w-[50px]"><img class="w-full" src="./schedule/3rd-doc.svg"/></div>
        <div class="flex-1 pl-4">
          <a href="https://github.com/anissia-net/document/blob/main/doc_anime_rank.md" target="_blank"><h5>랭킹 집계기준</h5></a>
          <p>애니메이션 랭킹 집계기준 문서</p>
        </div>
      </div>

      <div class="info-box as-card">
        <div class="w-[50px]"><img class="w-full" src="./schedule/icon-code.svg"/></div>
        <div class="flex-1 pl-4">
          <a href="https://github.com/anissia-net/web" target="_blank"><h5>프론트엔드</h5></a>
          <p>Vue.js, Typescript</p>
        </div>
      </div>

      <div class="info-box as-card">
        <div class="w-[50px]"><img class="w-full" src="./schedule/icon-code.svg"/></div>
        <div class="flex-1 pl-4">
          <a href="https://github.com/anissia-net/core" target="_blank"><h5>백엔드</h5></a>
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
import {computed, onMounted, onUnmounted, ref} from "vue";
import ColorPicker from "../../domain/colorPicker/ColorPicker.vue";
import {DateFormat} from "raon";
import animeRemote from "../../domain/anime/remote/animeRemote";
import toast from "../../common/toast";


const asd = ref({
  type: 'list',

  cardSimpleMode: true,
  cardBgLight: 'ffffff',
  cardTitleLight: '27272a',
  cardTitleHoverLight: '0369a1',
  cardNavBgLight: 'ffffff', cardNavTextLight: '9ca3af', cardNavBorderLight: 'e4e4e7',
  cardNavBgPickLight: 'ffffff', cardNavTextPickLight: '27272a', cardNavBorderPickLight: 'd4d4d8',
  cardListBgLight: 'ffffff', cardListBorderLight: 'e4e4e7',
  cardListBgPickLight: 'ffffff', cardListBorderPickLight: 'd4d4d8',
  cardListTextHighlightLight: '2563eb', cardListTextSubjectLight: '1f2937', cardListTextOriginalSubjectLight: '27272a',
  cardListTextHighlightPickLight: '2563eb', cardListTextSubjectPickLight: '1f2937', cardListTextOriginalSubjectPickLight: '27272a',
  cardListTagBgLight: 'e6edf3', cardListTagTextLight: '595f6e',
  cardListTagBgPickLight: 'c6cdd3', cardListTagTextPickLight: '595f6e',
  cardBgDark: '000000',
  cardTitleDark: 'a1a1aa',
  cardTitleHoverDark: 'e5e7eb',
  cardNavBgDark: '000000', cardNavTextDark: '4b5563', cardNavBorderDark: '1f1f22',
  cardNavBgPickDark: '000000', cardNavTextPickDark: 'a1a1aa', cardNavBorderPickDark: '27272a',
  cardListBgDark: '000000', cardListBorderDark: '1f1f22',
  cardListBgPickDark: '000000', cardListBorderPickDark: '27272a',
  cardListTextHighlightDark: '3b82f6', cardListTextSubjectDark: 'd4d4d8', cardListTextOriginalSubjectDark: 'a1a1aa',
  cardListTextHighlightPickDark: '3b82f6', cardListTextSubjectPickDark: 'd4d4d8', cardListTextOriginalSubjectPickDark: 'a1a1aa',
  cardListTagBgDark: '171a24', cardListTagTextDark: 'eeeeee',
  cardListTagBgPickDark: '35363a', cardListTagTextPickDark: 'eeeeee',
  cardWidth: 800, cardHeight: 640,

  listBgLight: 'ffffff', listTitleBgLight: '5987b6', listTitleLight: 'ffffff',
  listNavBgLight: 'f2f2f2', listNavLight: '497ba7', listNavActBgLight: '9cb3c7', listNavActLight: 'ffffff',
  listListBgLight: 'ffffff', listListLight: '555555', listListActBgLight: 'f8f8f8', listListActLight: '2474ce',
  listPrefixLight: 'cb3434',
  listBgDark: '000000', listTitleBgDark: '000000', listTitleDark: '777777',
  listNavBgDark: '111111', listNavDark: '777777', listNavActBgDark: '111111', listNavActDark: 'c3b443',
  listListBgDark: '070707', listListDark: '999999', listListActBgDark: '000000', listListActDark: 'cccccc',
  listPrefixDark: '3a7da3',
  listWidth: 650, listHeight: 400,

  imgTitleBg: '63a883', imgTitle: 'ffffff',
  imgYmdBg: 'd8d8d8', imgYmd: '000000',
  imgListBg: 'ffffff', imgList: '000000',
  imgScroll: true,
  imgWidth: 180, imgSize: 10,
  imgDataList: [] as string[],
  imgDataYmd: new DateFormat().format("yyyy년 MM월 dd일"),
});

const cardFrameRef = ref(null) as any;
const cardMaxWidth = computed(() => Math.min(maxWidth.value, asd.value.cardWidth));
const cardCode = computed(() => `<iframe src="${location.origin + '/schedule/2024#' + cardSrc.value}" width="${asd.value.cardWidth}" height="${asd.value.cardHeight}" frameborder="0"></iframe>`);
const cardSrc = computed(() =>
    asd.value.cardBgLight + asd.value.cardBgDark +
    asd.value.cardTitleLight + asd.value.cardTitleDark + asd.value.cardTitleHoverLight + asd.value.cardTitleHoverDark +
    asd.value.cardNavBgLight + asd.value.cardNavBgDark + asd.value.cardNavTextLight + asd.value.cardNavTextDark + asd.value.cardNavBorderLight + asd.value.cardNavBorderDark +
    asd.value.cardNavBgPickLight + asd.value.cardNavBgPickDark + asd.value.cardNavTextPickLight + asd.value.cardNavTextPickDark + asd.value.cardNavBorderPickLight + asd.value.cardNavBorderPickDark +
    asd.value.cardListBgLight + asd.value.cardListBgDark + asd.value.cardListBorderLight + asd.value.cardListBorderDark +
    asd.value.cardListBgPickLight + asd.value.cardListBgPickDark + asd.value.cardListBorderPickLight + asd.value.cardListBorderPickDark +
    asd.value.cardListTextHighlightLight + asd.value.cardListTextHighlightDark + asd.value.cardListTextSubjectLight + asd.value.cardListTextSubjectDark + asd.value.cardListTextOriginalSubjectLight + asd.value.cardListTextOriginalSubjectDark +
    asd.value.cardListTextHighlightPickLight + asd.value.cardListTextHighlightPickDark + asd.value.cardListTextSubjectPickLight + asd.value.cardListTextSubjectPickDark + asd.value.cardListTextOriginalSubjectPickLight + asd.value.cardListTextOriginalSubjectPickDark +
    asd.value.cardListTagBgLight + asd.value.cardListTagBgDark + asd.value.cardListTagTextLight + asd.value.cardListTagTextDark +
    asd.value.cardListTagBgPickLight + asd.value.cardListTagBgPickDark + asd.value.cardListTagTextPickLight + asd.value.cardListTagTextPickDark);

const maxWidth = ref(0);
const containerRef = ref(null) as any;
const listFrameRef = ref(null) as any;
const listMaxWidth = computed(() => Math.min(maxWidth.value, asd.value.listWidth));
const listCode = computed(() => `<iframe src="${location.origin + '/schedule/2015#' + listSrc.value}" width="${asd.value.listWidth}" height="${asd.value.listHeight}" frameborder="0"></iframe>`);
const listSrc = computed(() => asd.value.listBgLight + asd.value.listTitleBgLight + asd.value.listTitleLight + asd.value.listNavBgLight + asd.value.listNavLight + asd.value.listNavActBgLight +
    asd.value.listNavActLight + asd.value.listListBgLight + asd.value.listListLight + asd.value.listListActBgLight + asd.value.listListActLight + asd.value.listPrefixLight +
    asd.value.listBgDark + asd.value.listTitleBgDark + asd.value.listTitleDark + asd.value.listNavBgDark + asd.value.listNavDark + asd.value.listNavActBgDark +
    asd.value.listNavActDark + asd.value.listListBgDark + asd.value.listListDark + asd.value.listListActBgDark + asd.value.listListActDark + asd.value.listPrefixDark);

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
  if (asd.value.type == 'card') {
    callFrame(cardFrameRef.value, 'repaint', cardSrc.value);
  } else if (asd.value.type == 'list') {
    callFrame(listFrameRef.value, 'repaint', listSrc.value);
  }
}
function bindMaxWidth() {
  maxWidth.value = (containerRef.value.offsetWidth as number) - (matchMedia('(min-width: 768px)').matches ? 400 : 100);
}
function colorModeHtml(mode: string) {
  if (asd.value.type == 'card') {
    callFrame(cardFrameRef.value, 'colorMode', mode);
  } else if (asd.value.type == 'list') {
    callFrame(listFrameRef.value, 'colorMode', mode);
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
  if (['card', 'list'].indexOf(asd.value.type) != -1) {
    colorModeHtml(cpTarget.value.endsWith('Dark') ? 'dark' : 'light');
  }
}

function onCpPick(color: any) {
  const asv = asd.value as any;
  const target = cpTarget.value;
  const colorMode = target.endsWith('Dark') ? 'Dark' : 'Light';

  if (asv.type == 'card' && asv.cardSimpleMode) {
    const borderColor = calcColor(color, colorMode == 'Light' ? -40 : 40);
    console.log(color, borderColor);

    switch (target.substring(0, target.length - colorMode.length)) {
      case 'cardNavBg':
        asv['cardListBg'+colorMode] = color;
        asv['cardNavBorder'+colorMode] = asv['cardListBorder'+colorMode] = borderColor;
        break;
      case 'cardNavBgPick':
        asv['cardListBgPick'+colorMode] = color;
        asv['cardNavBorderPick'+colorMode] = asv['cardListBorderPick'+colorMode] = borderColor;
        break;
    }
  }

  asv[target] = color;
  drawHtml();
}

function calcColor(color: string, offset: number) {
  const r = Math.min(Math.max(parseInt(color.substring(0, 2), 16) + offset, 0), 255);
  const g = Math.min(Math.max(parseInt(color.substring(2, 4), 16) + offset, 0), 255);
  const b = Math.min(Math.max(parseInt(color.substring(4, 6), 16) + offset, 0), 255);
  console.log(color, r, g, b);
  return ((r << 16) | (g << 8) | b).toString(16).padStart(6, '0');
}

function onCpClose(event: Event) {
  if (cpShow.value && !((event?.target as HTMLElement | null)?.closest('html,.as-cp-close,.color-unit-box,.as-cp-not-close')?.matches('.as-cp-not-close'))) {
    cpShow.value = false;
  }
}

onMounted(() => {
  addEventListener('click', onCpClose, true);
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

</style>
