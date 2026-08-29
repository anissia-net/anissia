<template>

  <section class="flex items-center justify-center min-h-[calc(100vh-14rem)] py-16">
    <div class="as-page-narrow">

      <div class="text-center mb-8">
        <h1 class="as-page-title">회원가입</h1>
        <p class="as-desc mt-2">이메일 인증 후 계정이 생성됩니다.</p>
      </div>

      <div v-if="mode == 'none'" class="as-box p-7">
        <div class="space-y-5">
          <div>
            <label class="as-sub-title mb-2">계정</label>
            <input type="email" v-model="email" name="email" class="px-4 py-3 as-input-text" placeholder="user@example.com">
          </div>
          <div>
            <label class="as-sub-title mb-2">암호</label>
            <input type="password" v-model="password" name="password1" placeholder="••••••••" class="px-4 py-3 as-input-text">
          </div>
          <div>
            <label class="as-sub-title mb-2">암호 확인</label>
            <input type="password" v-model="passwordConfirm" name="password2" placeholder="••••••••" class="px-4 py-3 as-input-text">
          </div>
          <div>
            <label class="as-sub-title mb-2">닉네임</label>
            <input type="text" v-model="nickname" name="name" class="px-4 py-3 as-input-text" placeholder="닉네임">
          </div>
          <button type="submit" @click="sendRegisterAuthMail" class="as-btn-primary py-3 w-full text-base!">회원가입</button>
        </div>
      </div>

      <div v-if="mode == 'needMailAuth'" class="as-box px-7 py-12 text-center">
        <img class="w-[120px] m-auto" src="./recover/logo-pass.svg" />
        <p class="mt-8 text-lg font-semibold text-ink">인증메일이 전송되었습니다.</p>
        <p class="as-desc mt-2">메일함에서 인증 링크를 확인해주세요.</p>
      </div>

      <div v-if="mode == 'pass'" class="as-box px-7 py-12 text-center">
        <img class="w-[120px] m-auto" src="./recover/logo-pass.svg" />
        <p class="mt-8 text-lg font-semibold text-ink">가입완료</p>
        <p class="as-desc mt-2"><router-link to="/login" class="font-semibold as-link">로그인</router-link> 페이지로 이동하여 로그인해주세요.</p>
      </div>

      <div v-if="mode == 'wait'" class="as-box px-7 py-12 text-center">
        <img class="w-[120px] m-auto animate-[spin_3s_linear_infinite]" src="./recover/logo-wait.svg" />
        <p class="mt-8 text-lg font-semibold text-ink">이메일 인증 확인중</p>
        <p class="as-desc mt-2">이메일 인증을 확인하는 중입니다...</p>
      </div>

      <div v-if="mode == 'fail'" class="as-box px-7 py-12 text-center">
        <img class="w-[120px] m-auto" src="./recover/logo-fail.svg" />
        <p class="mt-8 text-lg font-semibold text-ink">이메일 인증에 실패하였습니다.</p>
        <p class="as-desc mt-2">{{message || '이메일 인증이 만료되었습니다.'}}</p>
      </div>

    </div>
  </section>



</template>

<script setup lang="ts">
import {onMounted, ref} from "vue";
import {useRoute} from "vue-router";
import accountRemote from "../../domain/account/remote/accountRemote";
import toast from "../../common/toast";

const mode = ref('none');
const token = ref('');
const message = ref('');

const email = ref('');
const password = ref('');
const passwordConfirm = ref('');
const nickname = ref('');

onMounted(() => {
  token.value = (useRoute().params.token || '') as string;
  if (token.value !== '') {
    mode.value = 'wait';
    validationRegister(token.value);
  }
});

function sendRegisterAuthMail() {
  accountRemote.sendRegisterAuthMail(email.value, password.value, passwordConfirm.value, nickname.value).then(res => {
    if (res.code == 'ok') {
      mode.value = 'needMailAuth';
    } else if (res.message) {
      toast.error(res.message);
    }
  });
}

function validationRegister(token: string) {
  accountRemote.validationRegister(token).then(res => {
    if (res.code == 'ok') {
      mode.value = 'pass';
    } else {
      mode.value = 'fail';
      if (res.message) {
        message.value = res.message;
      }
    }
  });
}

</script>
