import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";
import { VitePWA } from "vite-plugin-pwa";

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: "autoUpdate",
      includeAssets: ["pwa-192.png", "pwa-512.png"],
      manifest: {
        name: "ISPF Operator Console",
        short_name: "ISPF",
        description: "Industrial operator HMI for ISPF dashboards and alarming",
        theme_color: "#0d1117",
        background_color: "#0d1117",
        display: "standalone",
        orientation: "any",
        start_url: "/?mode=operator",
        scope: "/",
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
        globPatterns: ["**/*.{js,css,html,ico,png,svg,woff2}"],
        navigateFallback: "index.html",
        navigateFallbackDenylist: [/^\/operator-printing/],
        runtimeCaching: [
          {
            // BL-151: last operator manifest / UI for 8h offline PWA
            urlPattern: /^\/api\/v1\/applications\/[^/]+\/(operator-manifest|operator-ui|hmi-ui)$/,
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
            urlPattern: /^\/api\/v1\/dashboards\//,
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
            urlPattern: /^\/api\/v1\/mimics\//,
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
            urlPattern: /^\/api\//,
            handler: "NetworkOnly",
          },
        ],
      },
    }),
  ],
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          "vendor-react": ["react", "react-dom", "react-router-dom"],
          "vendor-query": ["@tanstack/react-query"],
          "vendor-charts": ["recharts"],
          "vendor-map": ["maplibre-gl", "react-map-gl/maplibre"],
        },
      },
    },
  },
  test: {
    environment: "node",
    include: ["src/**/*.test.ts", "src/**/*.test.tsx"],
    environmentMatchGlobs: [
      ["src/components/**/*.test.tsx", "jsdom"],
      ["src/hooks/**/*.test.tsx", "jsdom"],
    ],
    setupFiles: ["src/test/setup.ts"],
  },
  server: {
    port: 5173,
    proxy: {
      "/api": "http://localhost:8080",
      "/ws": {
        target: "ws://localhost:8080",
        ws: true,
      },
    },
  },
});
