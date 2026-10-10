import {createApp} from 'vue'
import '@fortawesome/fontawesome-free/css/all.min.css'
import 'nabi-note/nabi.css'
import './common/style.pcss'
import './common/nabi.pcss'
import App from './App.vue'
import router from "./common/router";
import {createPinia} from "pinia";
import {vChoice} from "./common/choice";
import {installEdgeLight} from "./common/edgeLight";

const __origin = location.origin;
const __server_list = ['https://anissia.net', 'https://test.anissia.net', 'http://localhost', 'http://192.', 'http://172.', 'http://10.'];
if (__server_list.findIndex(e => __origin.startsWith(e)) == -1) {
    location.href = 'https://anissia.net';
    throw 'invalid origin';
}

createApp(App)
    .use(router)
    .use(createPinia())
    .directive('choice', vChoice)
    .mount('#app');

installEdgeLight(router);
