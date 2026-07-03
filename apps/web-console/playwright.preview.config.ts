import { defineConfig, devices } from "@playwright/test";

/** Post-build suite: PWA manifest/SW + visual regression against `vite preview`. */
const baseURL = process.env.E2E_BASE_URL ?? "http://127.0.0.1:4173";

export default defineConfig({
  testDir: "./e2e",
  testMatch: [/pwa-android\.spec\.ts$/, /visual-regression\.spec\.ts$/],
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? [["github"], ["list"]] : "list",
  timeout: 90_000,
  snapshotPathTemplate: "{testDir}/{testFilePath}-snapshots/{arg}{ext}",
  expect: {
    toHaveScreenshot: {
      maxDiffPixelRatio: 0.04,
    },
  },
  use: {
    baseURL,
    locale: "en-US",
    trace: "on-first-retry",
  },
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : {
        command: "npm run preview -- --host 127.0.0.1 --port 4173",
        url: baseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
  projects: [
    {
      name: "preview-chromium",
      testMatch: [/visual-regression\.spec\.ts$/, /pwa-android\.spec\.ts$/],
      use: { ...devices["Desktop Chrome"] },
    },
    {
      name: "preview-pixel5",
      testMatch: /pwa-android\.spec\.ts$/,
      use: { ...devices["Pixel 5"] },
    },
  ],
});
