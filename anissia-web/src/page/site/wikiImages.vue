<template>
  <div class="as-page">
    <h1 class="as-page-title mb-6">{{selected ? '문서 이미지' : '이미지 보관함'}}</h1>
    <WikiNavigation :query="query" class="mb-6"/>
    <router-link v-if="isOperator && (selected || upload)" :to="{path: '/wiki/images', query: query ? {q: query} : {}}" class="as-link text-sm inline-block mb-5">이미지 보관함</router-link>
    <WikiImageDetail v-if="selected" :key="selected" :image-id="selected"/>
    <p v-else-if="upload" class="as-desc">이미지는 위키 문서나 저장된 템플릿의 편집 화면에서 올려 주세요.</p>
    <WikiImageLibrary v-else/>
  </div>
</template>

<script setup lang="ts">
import WikiImageLibrary from '../../domain/wiki/WikiImageLibrary.vue';
import WikiNavigation from '../../domain/wiki/WikiNavigation.vue';
import WikiImageDetail from '../../domain/wiki/WikiImageDetail.vue';
import {computed} from 'vue';
import {useRoute} from 'vue-router';
import {sessionStore} from '../../domain/session/sessionStore';
const route = useRoute();
const session = sessionStore();
const isOperator = computed(() => session.user.roles.some(role => role === 'ROOT' || role === 'TRANSLATOR'));
const selected = computed(() => typeof route.query.imageId === 'string' && /^[a-f0-9]{32}$/.test(route.query.imageId) ? route.query.imageId : '');
const upload = computed(() => !selected.value && route.query.upload === '1');
const query = computed(() => typeof route.query.q === 'string' ? route.query.q : '');
</script>
