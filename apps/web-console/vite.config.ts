import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";

/** Subdirectory deploy (e.g. mes-demo co-host): `VITE_BASE=/console/ npm run build`. */
const base = normalizeBase(process.env.VITE_BASE ?? "/");

function normalizeBase(value: string): string {
  if (!value || value === "/") {
    return "/";
  }
  const withLeading = value.startsWith("/") ? value : `/${value}`;
  return withLeading.endsWith("/") ? withLeading : `${withLeading}/`;
}

const operatorStartUrl = base === "/" ? "/?mode=operator" : `${base}?mode=operator`;

export default defineConfig({
  base,
  plugins: [
    react(),
    VitePWA({
      registerType: "autoUpdate",
      // External registerSW.js script — inline injection would violate script-src 'self' CSP.
      injectRegister: "script",
      includeAssets: ["pwa-192.png", "pwa-512.png"],
      manifest: {
        name: "ISPF Operator Console",
        short_name: "ISPF",
        description: "Industrial operator HMI for ISPF dashboards and alarming",
        theme_color: "#0d1117",
        background_color: "#0d1117",
        display: "standalone",
        orientation: "any",
        start_url: operatorStartUrl,
        // Keep PWA scope under the console base so co-hosted SPAs (MES Anima at "/")
        // are not captured by navigateFallback on soft reload (F5).
        scope: base,
        icons: [
          {
            src: "pwa-192.png",
            sizes: "192x192",
            type: "image/png",
          },
          {
            src: "pwa-512.png",
            sizes: "512x512",
            type: "image/png",
          },
          {
            src: "pwa-512.png",
            sizes: "512x512",
            type: "image/png",
            purpose: "maskable",
          },
        ],
      },
      workbox: {
        // First navigation must be controlled so NetworkFirst runtime caches warm in e2e/CI.
        clientsClaim: true,
        skipWaiting: true,
        globPatterns: ["**/*.{js,css,html,ico,png,svg,woff2}"],
        navigateFallback: "index.html",
        // Never SPA-fallback foreign apps / APIs on a shared origin.
        navigateFallbackDenylist: [
          /^\/api(?:\/|$)/,
          /^\/ws(?:\/|$)/,
          /^\/actuator(?:\/|$)/,
          /^\/operator-printing/,
          /^\/operator(?:\/|$)/,
          /^\/tech(?:\/|$)/,
          /^\/manager(?:\/|$)/,
          /^\/admin(?:\/|$)/,
          /^\/auth(?:\/|$)/,
          /^\/console(?:\/|$)/,
        ],
        runtimeCaching: [
          {
            // Public demo/operator manifests (full URL match — Workbox tests href, not pathname)
            urlPattern: /\/operator-apps\/[^?]+\.(?:manifest|ui)\.json(?:\?.*)?$/,
            handler: "NetworkFirst",
            options: {
              cacheName: "ispf-operator-manifest",
              expiration: {
                maxEntries: 16,
                maxAgeSeconds: 60 * 60 * 8,
              },
              networkTimeoutSeconds: 5,
              cacheableResponse: {
                statuses: [0, 200],
              },
            },
          },
          {
            // BL-151: last operator manifest / UI for 8h offline PWA
            urlPattern:
              /\/api\/v1\/(?:applications\/[^/?]+\/(?:operator-manifest|operator-ui|hmi-ui)|operator-apps\/[^/?]+\/ui)(?:\?.*)?$/,
            handler: "NetworkFirst",
            options: {
              cacheName: "ispf-operator-manifest",
              expiration: {
                maxEntries: 16,
                maxAgeSeconds: 60 * 60 * 8,
              },
              networkTimeoutSeconds: 5,
              cacheableResponse: {
                statuses: [0, 200],
              },
            },
          },
          {
            urlPattern: /\/api\/v1\/dashboards\//,
            handler: "NetworkFirst",
            options: {
              cacheName: "ispf-dashboards",
              expiration: {
                maxEntries: 48,
                maxAgeSeconds: 60 * 60 * 8,
              },
              networkTimeoutSeconds: 5,
              cacheableResponse: {
                statuses: [0, 200],
              },
            },
          },
          {
            urlPattern: /\/api\/v1\/mimics\//,
            handler: "NetworkFirst",
            options: {
              cacheName: "ispf-mimics",
              expiration: {
                maxEntries: 32,
                maxAgeSeconds: 60 * 60 * 8,
              },
              networkTimeoutSeconds: 5,
              cacheableResponse: {
                statuses: [0, 200],
              },
            },
          },
          {
            urlPattern: /\/api\//,
            handler: "NetworkOnly",
          },
        ],
      },
    }),
  ],
  build: {
    rollupOptions: {
      output: {
        advancedChunks: {
          groups: [
            { name: "vendor-react", test: /\/node_modules\/(react|react-dom|react-router-dom)\// },
            { name: "vendor-query", test: /\/node_modules\/@tanstack\/react-query\// },
            { name: "vendor-charts", test: /\/node_modules\/recharts\// },
            { name: "vendor-map", test: /\/node_modules\/(maplibre-gl|react-map-gl)\// },
          ],
        },
      },
    },
  },
  test: {
    setupFiles: ["src/test/setup.ts"],
    projects: [
      {
        extends: true,
        test: {
          name: "node",
          environment: "node",
          include: ["src/**/*.test.ts"],
        },
      },
      {
        extends: true,
        test: {
          name: "jsdom",
          environment: "jsdom",
          include: ["src/**/*.test.tsx"],
        },
      },
    ],
  },
  server: {
    port: 5173,
    proxy: {
      "/hmi/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/hmi/, ""),
      },
      "/hmi/ws": {
        target: "ws://localhost:8080",
        ws: true,
        rewrite: (path) => path.replace(/^\/hmi/, ""),
      },
      "/api": "http://localhost:8080",
      "/ws": {
        target: "ws://localhost:8080",
        ws: true,
      },
    },
  },
});
