import { defineConfig, devices } from "@playwright/test";

const baseURL = process.env.E2E_BASE_URL ?? "http://127.0.0.1:5173";
const liveMode = Boolean(process.env.E2E_BASE_URL);

export default defineConfig({
  testDir: "./e2e",
  fullyParallel: !liveMode,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? [["github"], ["list"]] : "list",
  timeout: 60_000,
  use: {
    baseURL,
    locale: "en-US",
    trace: "on-first-retry",
  },
  webServer: liveMode
    ? undefined
    : {
        command: "npm run dev -- --host 127.0.0.1 --port 5173",
        url: baseURL,
        reuseExistingServer: !process.env.CI,
        timeout: 120_000,
      },
  projects: liveMode
    ? [
        {
          name: "live-chromium",
          testMatch: /live.*\.spec\.ts/,
          use: { ...devices["Desktop Chrome"] },
        },
      ]
    : [
        {
          name: "mocked-chromium",
          // quality-gates.spec.ts is owned by playwright.quality.config.ts (`npm run test:quality`).
          testIgnore: [
            /^.*\/live\.spec\.ts$/,
            /pwa-android\.spec\.ts$/,
            /visual-regression\.spec\.ts$/,
            /quality-gates\.spec\.ts$/,
          ],
          use: { ...devices["Desktop Chrome"] },
        },
      ],
});
