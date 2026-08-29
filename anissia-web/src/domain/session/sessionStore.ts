import Session from "./Session";
import {defineStore} from "pinia";
import {ref} from "vue";

export const sessionStore = defineStore("user", () => {
    const user = ref(new Session());

    function setUser(_user: Session) {
        user.value = _user;
    }

    return {user, setUser};
});