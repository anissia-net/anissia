<template>

  <section class="flex justify-center items-center min-h-[calc(100vh-14rem)] py-20">
    <div class="text-center px-6">
      <div class="as-wordmark text-7xl md:text-8xl font-extrabold">500</div>
      <h1 class="mt-6 text-xl md:text-2xl font-bold text-ink">이 페이지는 에러 예시 페이지 입니다.</h1>
      <p class="as-desc mt-3">주소를 변경하여 메인으로 돌아가시기 바랍니다.</p>
    </div>
  </section>

</template>

<script setup lang="ts">


import {ajaxStateStore} from "../common/ajaxStateStore.js";
import toast from "../common/toast.js";
import {onMounted} from "vue";

const ajaxState = ajaxStateStore();

toast.error('주소를 변경하여 메인으로 돌아가시기 바랍니다.');
toast.error('이 페이지는 에러 예시 페이지 입니다.');

onMounted(() => {
  for (let i = 0 ; i < 3 ; i++) {
    setTimeout(() => ajaxState.setError(), i * 500);
  }
});
</script>
