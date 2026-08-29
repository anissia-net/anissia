import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import {createHtmlPlugin} from "vite-plugin-html";

export default defineConfig(({command, mode}) => {
  const env = loadEnv(mode, './', 'VITE_') as any;

  const options = {
    plugins: [
        vue(),
        createHtmlPlugin({
          minify: true,
          entry: '/src/main.ts',
          template: 'index.html'
        })
    ],
    build: {
      cssTarget: ['chrome133', 'edge133', 'firefox135', 'safari18.4']
    }
  };

  if (command == 'serve') {
      options['server'] = {
          host: '0.0.0.0',
          proxy: {
              '^/api/': {
                  target: env.VITE_API_HOST,
                  ws: true,
                  changeOrigin: true,
                  rewrite: (path) => path.replace('/api/', '/')
              }
          }
      }
  }

  return options;
})
