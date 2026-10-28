import { defineConfig, loadEnv, type Plugin } from 'vite'
import vue from '@vitejs/plugin-vue'
import {createHtmlPlugin} from "vite-plugin-html";

function immutableBundleHeaders(): Plugin {
  return {
    name: 'immutable-bundle-headers',
    apply: 'build',
    enforce: 'post',
    generateBundle(_, bundle) {
      // Match only real, fingerprinted bundles. An /assets/* rule would also
      // give the SPA's HTML fallback a one-year TTL for missing asset URLs.
      const files = Object.keys(bundle).filter(file => /\.(js|css)$/.test(file));
      this.emitFile({
        type: 'asset',
        fileName: '_headers',
        source: files.map(file => `/${file}\n  Cache-Control: public, max-age=31536000, immutable\n`).join('\n')
          + '\n/sw.js\n  Cache-Control: no-cache\n\n/manifest.webmanifest\n  Cache-Control: no-cache\n'
      });
    }
  };
}

export default defineConfig(({command, mode}) => {
  const env = loadEnv(mode, './', 'VITE_') as any;

  const options = {
    plugins: [
        vue(),
        createHtmlPlugin({
          minify: true,
          entry: '/src/main.ts',
          template: 'index.html'
        }),
        immutableBundleHeaders()
    ],
    build: {
      cssTarget: ['chrome133', 'edge133', 'firefox135', 'safari18.4'],
      // One versioned JS/CSS pair, including admin and editor screens. Route
      // imports stay lazy in development but require no extra production fetches.
      cssCodeSplit: false,
      rolldownOptions: {
        output: { codeSplitting: false }
      }
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
