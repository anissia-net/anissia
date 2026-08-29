<template>


  <section class="flex items-center justify-center min-h-[calc(100vh-14rem)] py-16 transition-opacity duration-300" :class="({'opacity-0': !show})">
    <div class="as-page-narrow">

      <div class="text-center mb-8">
        <h1 class="as-page-title">로그인</h1>
        <p class="as-desc mt-2">애니시아 계정으로 로그인합니다.</p>
      </div>

      <div class="as-box p-7">
        <div class="space-y-5">
          <div>
            <label class="as-sub-title mb-2">계정</label>
            <input type="email" name="email" v-model="email" class="px-4 py-3 as-input-text" placeholder="user@example.com">
          </div>
          <div>
            <label class="as-sub-title mb-2">암호</label>
            <input type="password" name="password" v-model="password" @keyup.enter="doLogin" placeholder="••••••••" class="px-4 py-3 as-input-text">
          </div>

          <div class="flex items-center justify-between pt-1">
            <label class="as-switch">
              <input type="checkbox" id="remember" v-model="makeLoginToken">
              <span class="as-switch-track"></span>
              <span class="ml-3 text-sm font-medium text-ink-2">자동로그인</span>
            </label>
            <router-link to="/recover" class="text-sm font-medium as-link">암호분실</router-link>
          </div>

          <button type="button" @click="doLogin" class="as-btn-primary py-3 w-full text-base!">로그인</button>
        </div>
      </div>

      <p class="text-sm text-center mt-6 text-ink-3">
        아직 계정이 없으신가요? <router-link to="/register" class="font-semibold as-link">회원가입</router-link>
      </p>

    </div>
  </section>

</template>

<script setup lang="ts">
import {ref} from "vue";
import sessionService from "../../domain/session/remote/sessionService";
import {Locate} from "raon";
import {useRouter} from "vue-router";
import {sessionStore} from "../../domain/session/sessionStore";

const email = ref('');
const password = ref('');
const makeLoginToken = ref(false);
const router = useRouter();
const path = new Locate().getParameter('path', '');
const show = ref(path == '');
const session = sessionStore();

function doLogin() {
  sessionService.login(email.value, password.value, makeLoginToken.value).then(success => {
      if (success) {
          router.push(path && path.startsWith('/') ? path : '/');
      }
  });
}

if (!show.value) {
  setTimeout(() => show.value = true, 200);
}


</script>
