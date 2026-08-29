<template>

  <div class="as-page max-w-[720px]!">

    <div class="as-page-head">
      <h1 class="as-page-title">회원 정보</h1>
      <p class="as-desc">계정 정보를 확인하고 변경할 수 있습니다.</p>
    </div>

    <section class="as-box acc-card">

      <div class="acc-row">
        <div class="acc-icon"><i class="fa-regular fa-envelope"></i></div>
        <div class="acc-body">
          <div class="acc-label">계정</div>
          <div class="acc-value break-all">{{account.email}}</div>
        </div>
      </div>

      <div class="acc-row">
        <div class="acc-icon"><i class="fa-regular fa-calendar-check"></i></div>
        <div class="acc-body">
          <div class="acc-label">가입일</div>
          <div class="acc-value tabular-nums">{{account.regDtText}}</div>
        </div>
      </div>

      <div class="acc-row">
        <div class="acc-icon"><i class="fa-solid fa-shield-halved"></i></div>
        <div class="acc-body">
          <div class="acc-label">권한</div>
          <div class="flex flex-wrap gap-1.5 mt-1.5">
            <span class="as-tag-xs" v-for="node in account.rolesText" :key="node">{{node}}</span>
          </div>
        </div>
      </div>

      <div class="acc-row">
        <div class="acc-icon"><i class="fa-regular fa-user"></i></div>
        <div class="acc-body">
          <div class="acc-label">닉네임</div>
          <div v-if="mode != 'edit-nickname'" class="acc-value">{{account.name}}</div>
          <div v-else class="acc-form">
            <input type="text" v-model="editNickname" name="nickname" class="px-4 py-2.5 as-input-text" placeholder="닉네임">
            <input type="password" v-model="editNicknamePassword" name="password" placeholder="암호" class="px-4 py-2.5 as-input-text">
            <div class="acc-form-actions">
              <button type="button" @click="mode = 'none'" class="as-input-btn flex-1 py-2.5">취소</button>
              <button type="button" @click="updateNickname" class="as-btn-primary flex-1 py-2.5">수정</button>
            </div>
          </div>
        </div>
        <button v-if="mode != 'edit-nickname'" type="button" @click="mode = 'edit-nickname'" class="as-input-btn acc-row-btn">수정</button>
      </div>

      <div class="acc-row">
        <div class="acc-icon"><i class="fa-solid fa-key"></i></div>
        <div class="acc-body">
          <div class="acc-label">암호</div>
          <div v-if="mode != 'edit-password'" class="acc-value tracking-widest text-ink-3!">••••••••</div>
          <div v-else class="acc-form">
            <input type="password" v-model="editPrevPassword" name="password" placeholder="기존 암호" class="px-4 py-2.5 as-input-text">
            <input type="password" v-model="editPassword" name="password1" placeholder="새 암호" class="px-4 py-2.5 as-input-text">
            <input type="password" v-model="editPasswordConfirm" name="password2" placeholder="새 암호 확인" class="px-4 py-2.5 as-input-text">
            <div class="acc-form-actions">
              <button type="button" @click="mode = 'none'" class="as-input-btn flex-1 py-2.5">취소</button>
              <button type="button" @click="updatePassword" class="as-btn-primary flex-1 py-2.5">암호변경</button>
            </div>
          </div>
        </div>
        <button v-if="mode != 'edit-password'" type="button" @click="mode = 'edit-password'" class="as-input-btn acc-row-btn">변경</button>
      </div>

    </section>

    <section class="as-box acc-card acc-card-danger mt-6">

      <div class="acc-row">
        <div class="acc-icon is-danger"><i class="fa-regular fa-trash-can"></i></div>
        <div class="acc-body">
          <div class="acc-label is-danger">회원 탈퇴</div>
          <p class="acc-sub">탈퇴하면 계정과 작성한 글/댓글이 모두 삭제되며 되돌릴 수 없습니다.</p>

          <p v-if="account.roles.length > 0" class="acc-sub mt-2">
            권한이 있는 계정은 탈퇴할 수 없습니다. 운영진에게 문의해주세요.
          </p>

          <div v-else-if="mode == 'withdraw'" class="acc-form">
            <input type="password" v-model="withdrawPassword" name="withdrawPassword" placeholder="암호" class="px-4 py-2.5 as-input-text">
            <div class="acc-form-actions">
              <button type="button" @click="mode = 'none'" class="as-input-btn flex-1 py-2.5">취소</button>
              <button type="button" @click="withdraw" class="as-input-btn flex-1 py-2.5 text-danger!">탈퇴하기</button>
            </div>
          </div>
        </div>
        <button v-if="account.roles.length == 0 && mode != 'withdraw'" type="button" @click="mode = 'withdraw'"
                class="as-input-btn acc-row-btn text-danger!">탈퇴</button>
      </div>

    </section>

  </div>

</template>

<script setup lang="ts">
import {ref} from "vue";
import {useRouter} from "vue-router";
import accountRemote from "../../domain/account/remote/accountRemote";
import {Account} from "../../domain/account/Account";
import sessionService from "../../domain/session/remote/sessionService";
import toast from "../../common/toast";

const router = useRouter();

const account = ref(new Account());

const mode = ref('none');

const editNickname = ref('');
const editNicknamePassword = ref('');

const editPrevPassword = ref('');
const editPassword = ref('');
const editPasswordConfirm = ref('');

const withdrawPassword = ref('');

function load() {
  mode.value = 'none';
  withdrawPassword.value = '';
  accountRemote.getAccount().then(e => {
    account.value = e;
    editNickname.value = e.name;
  });
}

function withdraw() {
  if (!confirm('정말로 탈퇴하시겠습니까?\n- 계정이 삭제됩니다.\n- 작성한 글과 댓글이 모두 삭제됩니다.\n- 되돌릴 수 없습니다.')) {
    return;
  }
  accountRemote.withdraw(withdrawPassword.value).then(e => {
    if (e.success) {
      sessionService.logout();
      toast.success('탈퇴가 완료되었습니다.');
      router.push('/');
    } else {
      toast.error(e.message || '탈퇴에 실패하였습니다.');
    }
  });
}

function updateNickname() {
  accountRemote.updateUserName(editNickname.value, editNicknamePassword.value).then(e => {
    if (e.success) {
      load();
    } else {
      toast.error(e.message || '닉네임 변경에 실패하였습니다.');
    }
  })
}

function updatePassword() {
  accountRemote.updateUserPassword(editPrevPassword.value, editPassword.value, editPasswordConfirm.value).then(e => {
    if (e.success) {
      load();
    } else {
      toast.error(e.message || '암호변경에 실패하였습니다.');
    }
  });
}

load();
</script>

<style scoped>
@reference "../../common/tailwind.pcss";

.acc-card {
  @apply p-3;
}

.acc-row {
  @apply flex items-start gap-4 p-3.5;
}

.acc-icon {
  @apply shrink-0 w-10 h-10 inline-flex items-center justify-center rounded-xl text-[15px];
  background: var(--as-brand-soft);
  color: var(--as-brand);
  &.is-danger {
    background: color-mix(in oklab, var(--as-danger) 10%, transparent);
    color: var(--as-danger);
  }
}

.acc-body {
  @apply flex-1 min-w-0;
}

.acc-label {
  @apply text-xs font-semibold uppercase;
  letter-spacing: .08em;
  color: var(--as-ink-3);
  &.is-danger { color: var(--as-danger) }
}

.acc-value {
  @apply mt-0.5 text-sm font-medium;
  color: var(--as-ink);
}

.acc-sub {
  @apply mt-1 text-sm;
  color: var(--as-ink-2);
}

.acc-row-btn {
  @apply shrink-0 self-center py-1.5 text-xs!;
}

.acc-form {
  @apply mt-3 max-w-[320px] space-y-3;
}

.acc-form-actions {
  @apply flex gap-2;
}

.acc-card-danger {
  border-color: color-mix(in oklab, var(--as-danger) 22%, var(--as-glass-line));
}
</style>
