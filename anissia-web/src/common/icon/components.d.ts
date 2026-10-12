import AsIcon from './AsIcon.vue';

declare module 'vue' {
  export interface GlobalComponents {
    AsIcon: typeof AsIcon;
  }
}

export {};
