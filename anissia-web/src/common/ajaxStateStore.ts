import {defineStore} from "pinia";
import {ref, Ref} from "vue";

export const ajaxStateStore = defineStore("state", () => {
    const stateList = ['ok', 'error', 'loading'];
    const state = ref('ok') as Ref<string>;

    function set(val: string) {
        if (state.value != val) {
            state.value = val;
        }
    }
    function setOk() {
        set('ok');
    }

    function setError() {
        set('error');
    }

    function setLoading() {
        set('loading');
    }

    return {state, setOk, setError, setLoading};
});