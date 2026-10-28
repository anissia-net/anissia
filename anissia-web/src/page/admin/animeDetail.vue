<template>
  <div class="as-page">
    <p v-if="loading" class="as-empty" role="status">애니메이션을 불러오는 중입니다.</p>
    <div v-else-if="error" class="as-empty" role="alert">{{error}} <button class="as-link" @click="loadAnime()">다시 시도</button></div>
    <div v-if="anime" class="mb-10">
      <div v-if="anime.animeNo > 0 || (anime.animeNo == 0 && animeNo == 0)">

        <table class="as-table as-table-kv">
          <thead>
            <tr>
              <th colspan="2" class="text-center">애니메이션 {{anime.animeNo != 0 ? '편집' : '신규등록'}}</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <th>제목</th>
              <td>
                <input type="text" v-model="anime.subject" name="subject" placeholder="제목" class="p-2.5 as-input-text">
              </td>
            </tr>
            <tr>
              <th>원제</th>
              <td>
                <input type="text" v-model="anime.originalSubject" name="originalSubject" placeholder="원제" class="p-2.5 as-input-text" lang="ja">
              </td>
            </tr>
            <tr>
              <th>장르</th>
              <td>
                <div @click="toggleGenreOpen">
                  <div v-for="genre in anime.editGenres" :key="genre" class="chip">
                    {{genre}}
                  </div>
                  <div v-if="anime.editGenres.length == 0" class="py-1.5 inline-block cursor-pointer text-sm text-ink-3">
                    선택된 장르가 없습니다.
                  </div>
                </div>
                <div v-if="anime?.editGenresOpen" class="mt-3">
                  <div v-for="genre in genres" :key="genre" @click="toggleGenre(genre)" class="chip" :class="{'is-on': anime.editGenres.indexOf(genre) != -1}">
                    {{genre}}
                  </div>
                </div>

              </td>
            </tr>
            <tr>
              <th>상태</th>
              <td>
                <div v-choice class="as-segment as-segment-inline">
                <label :class="{'is-on': anime.status == 'ON'}">
                  <input type="radio" name="status" v-model="anime.status" value="ON" class="hidden" />편성표
                </label>
                <label :class="{'is-on': anime.status == 'OFF'}">
                  <input type="radio" name="status" v-model="anime.status" value="OFF" class="hidden" />편성표-결방
                </label>
                <label :class="{'is-on': anime.status == 'END'}">
                  <input type="radio" name="status" v-model="anime.status" value="END" class="hidden" />완결
                </label>
                </div>
              </td>
            </tr>
            <tr>
              <th>요일</th>
              <td>
                <div v-choice class="as-segment as-segment-inline">
                <label v-for="(week, i) in weekList" :key="week" :class="{'is-on': (i+'') == anime.week}">
                  <input type="radio" name="week" v-model="anime.week" :value="i+''" class="hidden" />
                  {{week}}
                </label>
                </div>
              </td>
            </tr>
            <tr v-if="anime.week != '7'">
              <th>시간</th>
              <td>
                <input type="time" v-model="anime.time" class="p-2 as-input-text w-min!"/>
              </td>
            </tr>
            <tr>
              <th>시작일</th>
              <td>
                <div class="date-edit">
                  <div v-choice class="as-segment as-segment-inline">
                    <label v-for="dateType in dateTypeList" :key="dateType" :class="{'is-on': dateType == anime.editStartDateType}">
                      <input type="radio" name="startDate" v-model="anime.editStartDateType" :value="dateType" class="hidden" />
                      {{dateType}}
                    </label>
                  </div>
                  <div v-if="anime.editStartDateType != 'N/A'" class="date-fields">
                    <input type="text" v-model="anime.editStartDateYear" maxlength="4" placeholder="YYYY" class="as-input-text w-[3.625rem]! py-1.5 text-center"> 년
                    <template v-if="anime.editStartDateType != 'Y'">
                      <input type="text" v-model="anime.editStartDateMonth" maxlength="2" placeholder="MM" class="as-input-text w-[2.5rem]! py-1.5 text-center"> 월
                      <template v-if="anime.editStartDateType != 'YM'">
                        <input type="text" v-model="anime.editStartDateDate" maxlength="2" placeholder="DD" class="as-input-text w-[2.5rem]! py-1.5 text-center"> 일
                      </template>
                    </template>
                  </div>
                </div>
              </td>
            </tr>
            <tr>
              <th>종료일</th>
              <td>
                <div class="date-edit">
                  <div v-choice class="as-segment as-segment-inline">
                    <label v-for="dateType in dateTypeList" :key="dateType" :class="{'is-on': dateType == anime.editEndDateType}">
                      <input type="radio" name="endDate" v-model="anime.editEndDateType" :value="dateType" class="hidden" />
                      {{dateType}}
                    </label>
                  </div>
                  <div v-if="anime.editEndDateType != 'N/A'" class="date-fields">
                    <input type="text" v-model="anime.editEndDateYear" maxlength="4" placeholder="YYYY" class="as-input-text w-[3.625rem]! py-1.5 text-center"> 년
                    <template v-if="anime.editEndDateType != 'Y'">
                      <input type="text" v-model="anime.editEndDateMonth" maxlength="2" placeholder="MM" class="as-input-text w-[2.5rem]! py-1.5 text-center"> 월
                      <template v-if="anime.editEndDateType != 'YM'">
                        <input type="text" v-model="anime.editEndDateDate" maxlength="2" placeholder="DD" class="as-input-text w-[2.5rem]! py-1.5 text-center"> 일
                      </template>
                    </template>
                  </div>
                </div>
              </td>
            </tr>
            <tr>
              <th>웹사이트</th>
              <td>
                <input type="text" v-model="anime.website" name="website" placeholder="웹사이트" class="p-2.5 as-input-text">
              </td>
            </tr>
            <tr>
              <th>X</th>
              <td>
                <input type="text" v-model="anime.x" name="x" placeholder="X" class="p-2.5 as-input-text">
              </td>
            </tr>
            <tr v-if="anime.animeNo > 0">
              <th>썸네일</th>
              <td>
                <AnimeThumbnailPicker :key="anime.animeNo" :anime-no="anime.animeNo" :thumbnail="anime.thumbnail" @changed="thumbnailChanged"/>
              </td>
            </tr>
            <tr v-if="anime.animeNo > 0">
              <th>비고</th>
              <td>
                <input type="text" v-model="anime.note" name="x" placeholder="비고" class="p-2.5 as-input-text">
              </td>
            </tr>
            <tr v-if="anime.animeNo != 0">
              <th>자막참여자</th>
              <td>
                <span v-for="cp in anime.captions" :key="cp.name" class="mr-4">{{cp.name}}</span>
                <input type="button" value="자막참여" @click="addCaption()" class="as-input-btn p-1.5" />
              </td>
            </tr>
          </tbody>
        </table>
        <div class="mt-4 flex justify-between gap-2">
          <input type="button" value="삭제" @click="doDelete()" class="as-input-btn py-2 hover:text-danger!" />
          <input type="button" value="저장" @click="doSave()" class="as-btn-primary py-2" />
        </div>


      </div>
      <div v-else>
        <div class="text-xl text-center my-32">
          존재하지 않거나 삭제된 애니메이션 입니다.
        </div>
      </div>

    </div>

  </div>
</template>

<script setup lang="ts">
import {onUnmounted, ref} from 'vue';
import {useRoute, useRouter} from 'vue-router';
import Anime from '../../domain/anime/Anime';
import AnimeThumbnailPicker from '../../domain/anime/AnimeThumbnailPicker.vue';
import animeRemote from '../../domain/anime/remote/animeRemote';
import {animeUpdates} from '../../domain/anime/animeUpdates';
import {useListReturn} from '../../common/useListReturn';
import anissia from '../../common/anissia';
import toast from '../../common/toast';

const route = useRoute(), router = useRouter();
const animeNo = route.path.endsWith('/new') ? 0 : Number(route.params.animeNo);
const anime = ref<Anime | null>(null);
const genres = ref<string[]>([]);
const weekList = ['日', '月', '火', '水', '木', '金', '土', '外', '新'];
const dateTypeList = ['YMD', 'YM', 'Y', 'N/A'];
const loading = ref(false), error = ref('');
const {returnToList} = useListReturn('/admin/anime', ['/admin/anime', '/admin/schedule']);
let active = true;

async function loadAnime(publish = false) {
  loading.value = true;
  error.value = '';
  try {
    const node = animeNo ? await animeRemote.getAnime(animeNo) : new Anime();
    if (publish) animeUpdates.publish(animeNo, Anime.assign(node));
    if (active) anime.value = node.bindEdit();
  } catch {
    if (active) error.value = '애니메이션을 불러오지 못했습니다.';
  } finally { if (active) loading.value = false; }
}

function toggleGenreOpen() {
  const ani = anime.value!!;
  ani.editGenresOpen = !ani.editGenresOpen;
}

function toggleGenre(genre: string) {
  const ani = anime.value!!;
  const genres = ani.editGenres;
  if (genres.indexOf(genre) != -1) {
    ani.editGenres = ani.editGenres.filter(e => e != genre);
  } else {
    if (genres.length >= 3) {
      ani.editGenres = [...ani.editGenres.slice(1, 3), genre];
    } else {
      ani.editGenres = [...ani.editGenres, genre];
    }
  }
}

function addCaption() {
  animeRemote.addAdminCaption(anime.value?.animeNo!!).then(result => {
    if (result.code == 'ok') {
      void loadAnime(true);
    }
    toast.result(result);
  });
}

function thumbnailChanged(thumbnail: string | null) {
  if (!anime.value) return;
  anime.value.thumbnail = thumbnail;
  animeUpdates.publish(anime.value.animeNo, {thumbnail});
}

function doDelete() {
  if (confirm(`${anime.value?.subject}을(를) 삭제하시겠습니까?\n임의삭제시 권한박탈의 사유가됩니다.`)) {
    animeRemote.deleteAdminAnime(anime.value?.animeNo!!).then(result => {
      if (result.code == 'ok') {
        animeUpdates.publish(animeNo, null);
        returnToList();
      } else if (result.message) {
        toast.error(result.message);
      }
    });
  }
}

function doSave() {
  const ani = (anime.value!!).applyEdit();
  const isNew = ani.animeNo == 0;
  if (confirm(`${ani.subject}을(를) ${isNew ? '추가' : '수정'} 하시겠습니까?\n임의조작시 권한박탈의 사유가됩니다.`)) {

    if (!anissia.checkAnimeDate(ani.editStartDateType, ani.startDate)) {
      toast.error('시작일을 입력해주세요.');
      return;
    }
    if (!anissia.checkAnimeDate(ani.editEndDateType, ani.endDate)) {
      toast.error('종료일을 입력해주세요.');
      return;
    }

    if (isNew) {
      animeRemote.addAdminAnime(ani).then(result => {
        if (result.code == 'ok') {
          router.replace(`/admin/anime/${result.data}`)
          toast.success('애니메이션이 추가되었습니다.');
        } else if (result.message) {
          toast.error(result.message);
        }
      });
    } else {
      animeRemote.updateAdminAnime(ani).then(result => {
        if (result.code == 'ok') {
          void loadAnime(true);
          toast.success('애니메이션이 수정되었습니다.');
        } else if (result.message) {
          toast.error(result.message);
        }
      });
    }
  }
}

void loadAnime();
animeRemote.getGenres().then(value => { if (active) genres.value = value; });
onUnmounted(() => { active = false; });
</script>

<style scoped>
@reference "../../common/tailwind.pcss";

.date-edit {
  @apply flex flex-wrap items-center gap-x-4 gap-y-2;
}

.date-fields {
  @apply flex items-center gap-1.5 text-sm;
  color: var(--as-ink-2);
}

.chip {
  @apply inline-block mr-2 mb-2 px-3 py-1.5 text-xs font-medium cursor-pointer select-none transition-all duration-150;
  border-radius: 0.25rem;
  background: var(--as-muted);
  border: 0.0625rem solid var(--as-line);
  color: var(--as-ink-3);
  &:hover { border-color: var(--as-line-2); color: var(--as-ink-2) }
  &.is-on {
    background: var(--as-brand-soft);
    border-color: color-mix(in oklab, var(--as-brand) 35%, transparent);
    color: var(--as-brand);
  }
}

</style>
