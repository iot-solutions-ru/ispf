import { defineConfig, devices } from "@playwright/test";

/** HMI quality gates: a11y (axe) + mimic FPS profiling (S21). */
const baseURL = process.env.E2E_BASE_URL ?? "http://127.0.0.1:5173";
const liveMode = Boolean(process.env.E2E_BASE_URL);

export default defineConfig({
  testDir: "./e2e",
  testMatch: [/quality-gates\.spec\.ts$/],
  fullyParallel: false,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? [["github"], ["list"]] : "list",
  timeout: 90_000,
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
  projects: [
    {
      name: "quality-chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
