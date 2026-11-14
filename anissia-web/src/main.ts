import {createApp} from 'vue'
import './common/style.pcss'
import App from './App.vue'
import router, {installAnalytics, installNoteNavigation, installPrefetch} from "./common/router";
import {createPinia} from "pinia";
import {vChoice} from "./common/choice";
import {installEdgeLight} from "./common/edgeLight";
import AsIcon from "./common/icon/AsIcon.vue";
import {installNoteMotion} from './common/noteMotion';
import {installMobileBridge, mobilePageReady} from './common/mobileBridge';

const __origin = location.origin;
const __server_list = ['https://anissia.net', 'https://test.anissia.net', 'http://localhost', 'http://192.', 'http://172.', 'http://10.'];
if (__server_list.findIndex(e => __origin.startsWith(e)) == -1) {
    location.href = 'https://anissia.net';
    throw 'invalid origin';
}

installMobileBridge(path => { void router.push(path); });

createApp(App)
    .use(router)
    .use(createPinia())
    .directive('choice', vChoice)
    .component('AsIcon', AsIcon)
    .mount('#app');

void router.isReady().then(mobilePageReady);

installEdgeLight(router);
installPrefetch();
installNoteNavigation();
installNoteMotion();
installAnalytics();
