<template>

  <div>

    <div v-if="isAdminMode">
      <div class="as-section-head">
        <h2 class="as-section-title">운영기록</h2>
        <span class="as-meta pb-2">명령어 <b class="text-ink-2">/도움말</b></span>
      </div>

      <div class="relative mb-5">
        <i class="fa-solid fa-terminal absolute left-4 top-1/2 -translate-y-1/2 text-xs text-ink-3 pointer-events-none"></i>
        <input type="text" name="query" v-model="query" @keyup.enter="doQuery" autocomplete="off" placeholder="운영기록작성  /도움말" class="pl-10 pr-4 py-3 as-input-text">
      </div>

      <div v-if="openHelp" class="p-5 mb-5 text-sm leading-[2] as-box">
        <div class="as-sub-title mb-3">명령어 도움말</div>
        <b>/권한반납 닉네임</b> - 자막제작자 권한을 반납합니다.<br/>
        <b>/차단 닉네임 차단일 사유</b> - ex) /차단 홍길동 360 광고글 : 추후 게시판 삭제까지 같이 되도록 변경될 예정<br/>
        <b>/검색엔진 전체갱신</b> - 위험<br/>
        <b>/검색엔진 초기화</b> - 위험
      </div>

      <router-link v-if="translatorApplyCount" to="/translator/apply"
                   class="flex items-center gap-2.5 px-4 py-3 mb-4 text-sm rounded-ctl border border-brand/25 bg-brand-soft text-brand hover:brightness-105 transition">
        <i class="fa-solid fa-signature"></i>
        <span>현재 <b>{{translatorApplyCount}}</b> 건의 자막제작자 권한요청이 있습니다.</span>
        <i class="fa-solid fa-chevron-right ml-auto text-[10px]"></i>
      </router-link>
    </div>

    <div :class="isAdminMode ? 'as-box px-4 md:px-5 py-3' : ''">
      <div v-if="isAdminMode && list.loaded && list.content.length == 0" class="as-empty py-16!">운영기록이 없습니다.</div>
      <div v-for="(node, idx) in list.content" :key="node.apNo" class="flex items-baseline gap-4 py-3 text-sm break-all leading-[1.8] as-row anissia-home-reduce-10">
        <div class="order-last shrink-0 as-meta whitespace-nowrap">{{node.regDtText}}</div>
        <div class="flex-1 min-w-0">
        <div v-if="node.code == 'TEXT'" v-html="node.html" :class="({'opacity-50': !node.published})"></div>
        <div v-else-if="node.code == 'ANIME'">
          <div>
            <span v-html="node.html"></span>
            <span v-if="node.hasDetail"> · <b @click="node.openDetail = !node.openDetail" class="cursor-pointer as-link">{{node.openDetail ? '접기' : '자세히'}}</b></span>
          </div>
          <div v-if="node.openDetail" class="ap-diff">
            <div v-for="item in node.codeAnimeChangedList" :key="item.nm" class="ap-diff-row">
              <div class="ap-diff-name">{{item.nm}}</div>
              <div class="ap-diff-value">
                <template v-if="item.pv">
                  <span class="ap-diff-old" :class="({'is-none': (item.pv == '-')})">{{item.pv}}</span>
                  <i class="fa-solid fa-arrow-right-long ap-diff-arrow"></i>
                </template>
                <span class="ap-diff-new">{{item.nv}}</span>
              </div>
            </div>
          </div>
        </div>
        <div v-else-if="node.code == 'DEL'">
          <div>
            <span v-html="node.html"></span>
            <span> · <b @click="node.openDetail = !node.openDetail" class="cursor-pointer as-link">{{node.openDetail ? '접기' : '자세히'}}</b></span>
          </div>
          <div v-if="node.openDetail" class="ap-diff">
            <div class="ap-diff-head">{{node.data2}}</div>
            <div class="ap-diff-body whitespace-pre-wrap">{{node.data3}}</div>
          </div>
        </div>
        <div v-else-if="node.code == 'WITHDRAW'">
          <div v-html="node.html"></div>
          <div class="ap-note">
            <span v-if="node.data2">{{node.data2}}</span>
            <span v-if="node.data3">{{node.data3}}</span>
          </div>
        </div>
        <div v-else>
          <div v-html="node.html"></div>
          <div class="ap-note">지원하지 않는 활동 패널 코드 : {{node.code}}</div>
        </div>
        </div>

      </div>
    </div>
  </div>



</template>

<script setup lang="ts">
import {nextTick, onUnmounted, Ref, ref} from "vue";
import { ScrollLoader } from "raon";
import activePanelRemote from "./remote/activePanelRemote";
import PageData from "../../common/PageData";
import ActivePanelListItem from "./ActivePanelListItem";
import translatorRemote from "../translator/remote/translatorRemote";
import toast from "../../common/toast";

const props = defineProps({
  mode: String
});

const page = ref(0);
const sl = new ScrollLoader();
const list = ref(PageData.empty().notLoaded()) as Ref<PageData<ActivePanelListItem>>;
const query = ref('');
const openHelp = ref(false);
const translatorApplyCount = ref(0);
const isAdminMode = ref(false);

function loadTranslatorApplyCount() {
  translatorRemote.getAdminTranslatorApplyCount().then(count => translatorApplyCount.value = count);
}

function load() {
  const isFirstPage = page.value == 0;

  activePanelRemote.getList(page.value, props.mode!!).then(pageData => {
    if (isFirstPage) {
      list.value = pageData;
    } else {
      list.value = list.value.merge(pageData);
    }
    nextTick(() => sl.watch(pageData.next))
  });
}

function doQuery() {
  const line: string = query.value;
  query.value = '';

  if (line.trim() == '/도움말') {
    openHelp.value = true;
    return;
  } else if (!line) {
    return;
  }

  if (line.startsWith('/') || confirm('내용을 작성하시겠습니까?')) {
    activePanelRemote.doCommand(line).then(result => {
      if (result.code == 'ok') {
        page.value = 0;
        load();
      } else {
        toast.error(result.message);
      }
    });
  }
}

isAdminMode.value = props.mode == 'admin';
if (isAdminMode.value) {
  sl.onNeedNextPage(() => {
    page.value++;
    load();
  });
  loadTranslatorApplyCount();
}
load();

onUnmounted(() => {
  sl.destroy();
});

</script>

<style>
.ap-note {
  margin-top: .125rem;
  font-size: var(--text-xs);
  color: var(--as-ink-3);

  > span + span::before {
    content: "·";
    margin-inline: .5rem;
    opacity: .6;
  }
}

.ap-diff {
  margin: .75rem 0 .375rem;
  padding: .25rem 1rem;
  border-radius: var(--radius-ctl);
  border: 1px solid var(--as-line);
  background: color-mix(in oklab, var(--as-muted) 60%, transparent);
}

.ap-diff-row {
  display: flex;
  align-items: baseline;
  gap: 1rem;
  padding: .4375rem 0;
}

.ap-diff-name {
  flex: 0 0 auto;
  min-width: 72px;
  font-size: var(--text-xs);
  font-weight: 600;
  color: var(--as-ink-3);
}

.ap-diff-value {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: .375rem .625rem;
  line-height: 1.7;
}

.ap-diff-old {
  padding: 1px 8px;
  border-radius: 6px;
  background: color-mix(in oklab, var(--as-danger) 9%, transparent);
  color: color-mix(in oklab, var(--as-danger) 70%, var(--as-ink));
  &.is-none {
    background: transparent;
    color: var(--as-ink-3);
    text-decoration: none;
    opacity: .6;
    padding-inline: 0;
  }
}

.ap-diff-new {
  padding: 1px 8px;
  border-radius: 6px;
  background: color-mix(in oklab, var(--as-success) 11%, transparent);
  color: color-mix(in oklab, var(--as-success) 55%, var(--as-ink));
  font-weight: 500;
}

.ap-diff-arrow {
  font-size: 10px;
  color: var(--as-ink-3);
}

.ap-diff-head {
  padding: .625rem 0 .125rem;
  font-weight: 600;
  color: var(--as-ink);
}

.ap-diff-body {
  padding: .25rem 0 .625rem;
  color: var(--as-ink-2);
}
</style>
