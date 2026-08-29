<template>
  <router-view/>
</template>

<script setup lang="ts">
import theme from "./common/theme";
import sessionService from "./domain/session/remote/sessionService";
import {useRouter} from "vue-router";
import {sessionStore} from "./domain/session/sessionStore";
import {onBeforeMount} from "vue";

const router = useRouter();
const session = sessionStore();

theme.apply()

function sync() {
    sessionService.sync().then(e => sessionService.amendPathBySession(location.pathname, router));
}

function reload() {
  location.reload();
}

window['exReload'] = () => { reload(); };

onBeforeMount(() => {
  setInterval(() => { sync(); }, 60000);
  sync();
});
</script>
