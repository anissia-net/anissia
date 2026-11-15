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
      const images = ['favicon.ico', 'favicon.png', 'app-icon.svg', 'app-icon-192.png', 'app-icon-512.png', 'apple-touch-icon.png', 'push-icon.svg'];
      const size = (file: string) => {
        const output = bundle[file];
        return output.type === 'chunk' ? output.code.length : output.source.length;
      };
      const priority = (file: string) => /\.(js|css|woff2?|ttf|otf)$/.test(file) ? 1 : 0;
      // Cloudflare permits 100 rules; prioritize code, fonts, and the largest images.
      const files = Object.keys(bundle)
        .filter(file => /-[A-Za-z0-9_-]{8,}\.(js|css|avif|webp|png|jpe?g|gif|svg|ico|woff2?|ttf|otf)$/.test(file))
        .sort((left, right) => priority(right) - priority(left) || size(right) - size(left))
        .slice(0, 100 - images.length - 5);
      this.emitFile({
        type: 'asset',
        fileName: '_headers',
        source: files.map(file => `/${file}\n  Cache-Control: public, max-age=31536000, immutable\n`).join('\n')
          + images.map(file => `\n/${file}\n  Cache-Control: public, max-age=3600\n`).join('')
          + '\n/sw.js\n  Cache-Control: no-cache\n\n/static-cache.js\n  Cache-Control: no-cache\n\n/manifest.webmanifest\n  Cache-Control: no-cache\n'
          + '\n/wiki-new.txt\n  Content-Type: text/plain; charset=utf-8\n\n/llms.txt\n  Content-Type: text/plain; charset=utf-8\n'
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
