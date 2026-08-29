<template>
  <div id="sc2024" class="duration-300">
    <div class="container duration-300 pb-10 m-auto px-1 sm:px-2 md:px-4 min-h-screen py-0" @click="e => evtClickClosePopup(e)">

      <!-- 타이틀 -->
      <div class="font-extralight h-16 text-center flex items-center">
        <div class="text-2xl leading-none flex-1 pl-9 duration-300 title-text"><a href="/" target="_blank"><span class="max-[270px]:hidden">애니</span><span class="max-[350px]:hidden">메이션</span> 편성표</a></div>
        <button class="text-lg px-4 py-2.5 duration-300 title-text" @click="toggleColorMode()">
          <span class="dark:hidden"><i class="fa-solid fa-sun"></i></span>
          <span class="hidden dark:inline"><i class="fa-solid fa-moon"></i></span>
        </button>
      </div>

      <!-- 내비게이션바 -->
      <div class="flex justify-between max-[350px]:space-x-0 space-x-2 sm:space-x-3 md:space-x-4">
        <div v-for="(week, idx) in weekList" @click="getAnimeList(idx)" class="flex-auto duration-300 rounded-[3px] shadow-xs border text-center font-bold text-md md:text-lg py-2 cursor-pointer nav-box" :class="({'nav-box-pick': idx == weekNow})">{{week}}</div>
      </div>

      <!-- 애니메이션 리스트 -->
      <transition name="fade">
        <div class="mt-4" v-if="animeList.length">
          <div class="mt-3 grid gap-4 md:grid-cols-2 xl:grid-cols-3">
            <div v-for="(node) in animeList" class="p-4 duration-300 rounded-[3px] shadow-xs border list-box">
              <div @click="getCaptionList(node);" class="cursor-pointer">
                <!-- 시간 -->
                <div v-if="node.scheduleTime != '-'" class="text-md font-bold duration-300 list-box-text-highlight"> {{node.scheduleTime}} </div>
                <div class="text-md mt-1 font-bold duration-300 list-box-text-subject">
                  <!-- 방영태그 -->
                  <span v-if="node.subjectPrefix">[<span class="duration-300 list-box-text-highlight">{{node.subjectPrefix}}</span>] </span>
                  <!-- 한글제목 -->
                  {{node.subject}}
                </div>
                <!-- 원어제목 -->
                <div class="text-xs mt-1 duration-300 list-box-text-originalsubject" v-if="node.originalSubject">{{node.originalSubject}}</div>
              </div>
              <!-- 태그박스 -->
              <div class="mt-1 space-x-1 space-y-2">
                <span class="rounded-[2px] shadow-xs inline-block text-xs border px-[5px] py-[3px] duration-300 list-box-tag" v-for="tag in node.tags" :key="tag">{{tag}}</span>
                <span class="rounded-[2px] shadow-xs inline-block text-xs border px-[5px] py-[3px] duration-300 list-box-tag" v-for="tag in node.genres.split(/,/g)" :key="tag"><router-link :to="`/anime?q=%23${encodeURIComponent(tag)}`">{{tag}}</router-link></span>
                <span class="rounded-[2px] shadow-xs inline-block text-xs border px-[5px] py-[3px] duration-300 list-box-tag" v-if="node.website"><a :href="node.website" target="_blank" class="fas fa-home"></a></span>
                <span class="rounded-[2px] shadow-xs inline-block text-xs border px-[5px] py-[3px] duration-300 list-box-tag" v-if="node.x"><a :href="node.x" target="_blank" class="fa-brands fa-x-twitter"></a></span>
                <span class="rounded-[2px] shadow-xs inline-block text-xs border px-[5px] py-[3px] duration-300 list-box-tag" v-if="node.captionCount"><span class="fas fa-closed-captioning"></span> {{node.captionCount}}</span>
              </div>
            </div>
          </div>
        </div>
      </transition>

      <!-- 팝업 -->
      <transition name="fade">
        <div class="fixed inset-0 backdrop-blur-sm bg-gray-800/10" v-if="animeNow != null">
          <div class="popup min-[460px]:w-[450px] mt-[50px] sm:mt-[100px] md:mt-[150px] mb-0 mx-auto p-4 rounded-md shadow-lg text-base font-semibold text-center bg-zinc-50/85 dark:bg-zinc-900/85 text-gray-800 dark:text-zinc-300">
            <!-- 제목 -->
            <div class="border-b border-gray-200 dark:border-zinc-800 pb-3">
              <a target="_blank" :href="`/anime?animeNo=${animeNow.animeNo}`" class="duration-300 hover:opacity-80">
                <!-- 한글제목 -->
                <div class="text-xl font-bold">
                  {{animeNow.subject}}
                </div>
                <!-- 원어제목 -->
                <div class="text-xs mt-1">
                  {{animeNow.originalSubject}}
                </div>
              </a>
            </div>
            <!-- 태그 -->
            <div class="mt-1 space-x-1 space-y-2 border-b pb-3 border-gray-200 dark:border-zinc-800 ">
              <span class="rounded-[2px] shadow-xs inline-block text-xs border px-[5px] py-[3px] duration-300 bg-[#e6edf3] border-[#e6edf3] text-[#595f6e] dark:bg-[#171a24] dark:border-[#171a24] dark:text-[#eee] hover:bg-[#c6cdd3] hover:border-[#c6cdd3] dark:hover:bg-[#35363a] dark:hover:border-[#35363a]" v-for="tag in animeNow.tags" :key="tag">{{tag}}</span>
            </div>
            <!-- 자막제작자 목록 -->
            <div class="mt-3">
              <div class="mt-3 text-[15px]" v-for="node in captionList" :key="`${node.name}`">
                <div v-if="node.website !== ''">
                  <a :href="node.website" target="_blank" class="block duration-300 hover:opacity-80">
                    <span>{{node.episodeText}}</span> <span>{{node.name}}</span> <span>{{node.updDtText}}</span>
                  </a>
                </div>
                <div v-else>
                  <span>{{node.episodeText}}</span> <span>{{node.name}}</span> <span>{{node.updDtText}}</span>
                </div>
              </div>
            </div>
            <!-- 자막제작자 없음 -->
            <div v-if="captionList.length === 0" class="text-[15px] font-normal leading-10 pt-10 pb-[50px] px-0">자막 제작자가 없습니다.</div>
          </div>
        </div>
      </transition>

      <!-- 서버상태 체크 -->
      <div v-if="ajaxState.state == 'loading'" class="h-screen w-screen fixed flex items-center top-0 z-30 backdrop-blur-[2px] bg-white/20 dark:bg-black/10">
        <div class="w-screen text-center my-8 opacity-80">
          <div><i class="opacity-80 text-[100px] fa-solid fa-gear as-fa-spin"></i></div>
          <div class="text-4xl my-12">loading...</div>
        </div>
      </div>
      <div v-else-if="ajaxState.state == 'error'" class="h-screen w-screen fixed flex items-center top-0 z-30 backdrop-blur-[2px] bg-white/20 dark:bg-black/10">
        <div class="w-screen text-center my-8">
          <div><i class="fa-solid fa-screwdriver-wrench opacity-80 text-[100px]"></i></div>
          <div class="text-4xl mt-12 mb-8">서버 연결 실패</div>
          <div class="text-md opacity-80 leading-[2]">
            현재 애니시아 서버에 연결할 수 없습니다.
            <br/>빠르게 정상화 하도록 하겠습니다.
            <br/>문의 : auth@anissia.net
          </div>
        </div>
      </div>
    </div>
    <div id="user-style"></div>
  </div>
</template>


<!-- 스크립트 -->
<script setup lang="ts">
import {onBeforeUnmount, onMounted, Ref, ref} from "vue";
import AnimeCaption from "../../domain/anime/AnimeCaption";
import Anime from "../../domain/anime/Anime";
import animeRemote from "../../domain/anime/remote/animeRemote";
import {ajaxStateStore} from "../../common/ajaxStateStore";

const weekList = ref(['日', '月', '火', '水', '木', '金', '土', '外', '新']);
const weekNow = ref(-1);
const animeList = ref([]) as Ref<Anime[]>;
const animeNow = ref(null) as Ref<Anime|null>;
const captionList = ref([]) as Ref<AnimeCaption[]>;
const ajaxState = ajaxStateStore();
const colorMode = ref('light');

function getAnimeList(week: number): void {
  weekNow.value = week;
  animeRemote.getScheduleAnimeList(week).then((list) => animeList.value = list);
}
function getCaptionList(anime: Anime) {
  animeRemote.getAnimeCaptionList(anime.animeNo).then((list) => {
    captionList.value = list;
    animeNow.value = anime;
  });
}
function evtClickClosePopup(event: MouseEvent) {
  if (animeNow.value != null && (event.target as HTMLElement).closest('body,.popup')?.tagName == 'BODY') {
    animeNow.value = null;
  }
}
function evtKeyClosePopup(event: KeyboardEvent) {
  if (animeNow.value !== null && event.key === 'Escape') {
    animeNow.value = null;
  }
}
function applyColorMode(mode: string|null) {
  if (mode == null) {
    try {
      mode = (localStorage.getItem('schedule2024ColorMode') as string | null) || (
          matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'
      );
    } catch (e) { mode = 'light'; }
  }
  localStorage.setItem('schedule2024ColorMode', (colorMode.value = mode));
  const sc2024Classes = [document.getElementById('sc2024'), document.documentElement];
  sc2024Classes.forEach(sc2024Classes => {
    const classes = sc2024Classes.classList;
    classes.remove('light', 'dark');
    classes.add(colorMode.value);
  });
}
function toggleColorMode() {
  applyColorMode(colorMode.value == 'light' ? 'dark' : 'light');
}

onMounted(() => {
  applyColorMode(null);
  getAnimeList(new Date().getDay());
  (window as any).colorMode = applyColorMode;
  window.addEventListener('keydown', evtKeyClosePopup, true);
  // 커스텀 테마
  const theme = location.hash.length > 1 ? location.hash : 'ffffff00000027272aa1a1aa0369a1e5e7ebffffff0000009ca3af4b5563e4e4e71f1f22ffffff00000027272aa1a1aad4d4d827272affffff000000e4e4e71f1f22ffffff000000d4d4d827272a2563eb3b82f61f2937d4d4d827272aa1a1aa2563eb3b82f61f2937d4d4d827272aa1a1aae6edf3171a24595f6eeeeeeec6cdd335363a595f6eeeeeee';
  ((window as any).repaint = ((colors: string) => {
    let c: string[] = colors.match(/[0-9a-f]{6}/ig) || [];
    // 16진수 색상 코드가 지정된 갯수가 아니거나 잘못된 경우 기본값 사용
    if (c.filter((e: string) => /^[0-9a-f]{6}$/i.test(e)).length != 46) {
      c = theme.match(/[0-9a-f]{6}/ig);
    }
    
    const bgLight = c[0]; //ffffff
    const bgDark = c[1]; //000000

    const titleLight = c[2]; //27272a
    const titleDark = c[3]; //a1a1aa
    const titleHoverLight = c[4]; //0369a1
    const titleHoverDark = c[5]; //e5e7eb

    const navBgLight = c[6]; //ffffff
    const navBgDark = c[7]; //000000
    const navTextLight = c[8]; //9ca3af
    const navTextDark = c[9]; //4b5563
    const navBorderLight = c[10]; //e4e4e7
    const navBorderDark = c[11]; //1f1f22

    const navBgPickLight = c[12]; //ffffff
    const navBgPickDark = c[13]; //000000
    const navTextPickLight = c[14]; //27272a
    const navTextPickDark = c[15]; //a1a1aa
    const navBorderPickLight = c[16]; //d4d4d8
    const navBorderPickDark = c[17]; //27272a

    const listBgLight = c[18]; //ffffff
    const listBgDark = c[19]; //000000
    const listBorderLight = c[20]; //e4e4e7
    const listBorderDark = c[21]; //1f1f22

    const listBgPickLight = c[22]; //ffffff
    const listBgPickDark = c[23]; //000000
    const listBorderPickLight = c[24]; //d4d4d8
    const listBorderPickDark = c[25]; //27272a

    const listTextHighlightLight = c[26]; //2563eb
    const listTextHighlightDark = c[27]; //3b82f6
    const listTextSubjectLight = c[28]; //1f2937
    const listTextSubjectDark = c[29]; //d4d4d8
    const listTextOriginalSubjectLight = c[30]; //27272a
    const listTextOriginalSubjectDark = c[31]; //a1a1aa

    const listTextHighlightPickLight = c[32]; //2563eb
    const listTextHighlightPickDark = c[33]; //3b82f6
    const listTextSubjectPickLight = c[34]; //1f2937
    const listTextSubjectPickDark = c[35]; //d4d4d8
    const listTextOriginalSubjectPickLight = c[36]; //27272a
    const listTextOriginalSubjectPickDark = c[37]; //a1a1aa

    const listTagBgLight = c[38]; //e6edf3
    const listTagBgDark = c[39]; //171a24
    const listTagTextLight = c[40]; //595f6e
    const listTagTextDark = c[41]; //eeeeee

    const listTagBgPickLight = c[42]; //c6cdd3
    const listTagBgPickDark = c[43]; //35363a
    const listTagTextPickLight = c[44]; //595f6e
    const listTagTextPickDark = c[45]; //eeeeee

    (document.getElementById('user-style') as any).innerHTML = `<style>
        #sc2024.light { background: #${bgLight} }
        #sc2024.light .title-text { color: #${titleLight} }
        #sc2024.light .title-text:hover { color: #${titleHoverLight} }

        #sc2024.light .nav-box { background: #${navBgLight}; color: #${navTextLight}; border-color: #${navBorderLight} }
        #sc2024.light .nav-box-pick, #sc2024.light .nav-box:hover { background: #${navBgPickLight}; color: #${navTextPickLight}; border-color: #${navBorderPickLight} }

        #sc2024.light .list-box { background: #${listBgLight}; border-color: #${listBorderLight} }
        #sc2024.light .list-box:hover { background: #${listBgPickLight}; border-color: #${listBorderPickLight} }
        #sc2024.light .list-box-text-highlight { color: #${listTextHighlightLight} }
        #sc2024.light .list-box-text-highlight:hover { color: #${listTextHighlightPickLight} }
        #sc2024.light .list-box-text-subject { color: #${listTextSubjectLight} }
        #sc2024.light .list-box-text-subject:hover { color: #${listTextSubjectPickLight} }
        #sc2024.light .list-box-text-originalsubject { color: #${listTextOriginalSubjectLight} }
        #sc2024.light .list-box-text-originalsubject:hover { color: #${listTextOriginalSubjectPickLight} }
        #sc2024.light .list-box-tag { background: #${listTagBgLight}; color: #${listTagTextLight}; border-color: #${listTagBgLight}; }
        #sc2024.light .list-box-tag:hover { background: #${listTagBgPickLight}; color: #${listTagTextPickLight}; border-color: #${listTagBgPickLight}; }


        #sc2024.dark { background: #${bgDark} }
        #sc2024.dark .title-text { color: #${titleDark} }
        #sc2024.dark .title-text:hover { color: #${titleHoverDark} }

        #sc2024.dark .nav-box { background: #${navBgDark}; color: #${navTextDark}; border-color: #${navBorderDark} }
        #sc2024.dark .nav-box-pick, #sc2024.dark .nav-box:hover { background: #${navBgPickDark}; color: #${navTextPickDark}; border-color: #${navBorderPickDark} }

        #sc2024.dark .list-box { background: #${listBgDark}; border-color: #${listBorderDark} }
        #sc2024.dark .list-box:hover { background: #${listBgPickDark}; border-color: #${listBorderPickDark} }
        #sc2024.dark .list-box-text-highlight { color: #${listTextHighlightDark} }
        #sc2024.dark .list-box-text-highlight:hover { color: #${listTextHighlightPickDark} }
        #sc2024.dark .list-box-text-subject { color: #${listTextSubjectDark} }
        #sc2024.dark .list-box-text-subject:hover { color: #${listTextSubjectPickDark} }
        #sc2024.dark .list-box-text-originalsubject { color: #${listTextOriginalSubjectDark} }
        #sc2024.dark .list-box-text-originalsubject:hover { color: #${listTextOriginalSubjectPickDark} }
        #sc2024.dark .list-box-tag { background: #${listTagBgDark}; color: #${listTagTextDark}; border-color: #${listTagBgDark}; }
        #sc2024.dark .list-box-tag:hover { background: #${listTagBgPickDark}; color: #${listTagTextPickDark}; border-color: #${listTagBgPickDark}; }
        </style>`;
  }))(theme);
});

onBeforeUnmount(() => {
  window.removeEventListener('keydown', evtKeyClosePopup, true);
});
</script>

<style>
/* 애니메이션 CSS */
#sc2024 {
  .fade-enter-active, .fade-leave-active { transition: opacity 0.3s; }
  .fade-enter-from, .fade-leave-to { opacity: 0; }
}
</style>
