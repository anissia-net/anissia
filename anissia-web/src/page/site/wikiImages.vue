<template>
  <div class="as-page">
    <h1 class="as-page-title mb-6">{{upload ? '이미지 업로드' : '위키 이미지'}}</h1>
    <WikiNavigation :query="query" class="mb-6"/>
    <router-link v-if="selected || upload" :to="{path: '/wiki/images', query: query ? {q: query} : {}}" class="as-link text-sm inline-block mb-5">이미지 목록</router-link>
    <WikiImageDetail v-if="selected" :key="selected" :image-id="selected"/>
    <WikiImageUpload v-else-if="upload"/>
    <WikiImageLibrary v-else/>
  </div>
</template>

<script setup lang="ts">
import WikiImageLibrary from '../../domain/wiki/WikiImageLibrary.vue';
import WikiNavigation from '../../domain/wiki/WikiNavigation.vue';
import WikiImageDetail from '../../domain/wiki/WikiImageDetail.vue';
import WikiImageUpload from '../../domain/wiki/WikiImageUpload.vue';
import {computed} from 'vue';
import {useRoute} from 'vue-router';
const route = useRoute();
const selected = computed(() => typeof route.query.imageId === 'string' && /^[a-f0-9]{32}$/.test(route.query.imageId) ? route.query.imageId : '');
const upload = computed(() => !selected.value && route.query.upload === '1');
const query = computed(() => typeof route.query.q === 'string' ? route.query.q : '');
</script>
