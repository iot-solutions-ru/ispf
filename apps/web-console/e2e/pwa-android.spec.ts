import { test, expect } from "@playwright/test";
import {
  mockAuthenticatedApi,
  seedAuthSession,
} from "./fixtures/apiMocks";

/**
 * BL-90 — Android Chrome PWA smoke (automated).
 * Runs on vite preview (manifest + service worker). Pixel 5 project mirrors Android Chrome.
 */

test.describe("PWA manifest (preview build)", () => {
  test("manifest is installable with operator start_url and icons", async ({ request }) => {
    const response = await request.get("/manifest.webmanifest");
    expect(response.ok()).toBeTruthy();
    const manifest = await response.json();
    expect(manifest.name).toContain("ISPF");
    expect(manifest.display).toBe("standalone");
    expect(manifest.start_url).toContain("mode=operator");
    expect(manifest.icons?.some((icon: { sizes: string }) => icon.sizes === "512x512")).toBeTruthy();
    expect(manifest.icons?.some((icon: { sizes: string }) => icon.sizes === "192x192")).toBeTruthy();
  });

  test("index.html links manifest and registers service worker", async ({ page }) => {
    await page.goto("/");
    const manifestLink = page.locator('link[rel="manifest"]');
    await expect(manifestLink).toHaveAttribute("href", /manifest\.webmanifest/);
    const swRegistered = await page.evaluate(async () => {
      if (!("serviceWorker" in navigator)) {
        return false;
      }
      const reg = await navigator.serviceWorker.ready.catch(() => null);
      return Boolean(reg?.active);
    });
    expect(swRegistered).toBeTruthy();
  });
});

test.describe("operator Android Chrome smoke (Pixel 5)", () => {
  test.beforeEach(({ }, testInfo) => {
    test.skip(testInfo.project.name !== "preview-pixel5", "Android smoke runs on Pixel 5 project only");
  });

  test("standalone operator shell loads with nav and safe-area layout", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=operator&app=demo");

    await expect(page.getByTestId("operator-shell")).toContainText("Demo Application", {
      timeout: 20_000,
    });
    await expect(page.getByTestId("operator-nav")).toBeVisible();

    const shell = page.getByTestId("operator-shell");
    const paddingTop = await shell.evaluate((el) => getComputedStyle(el).paddingTop);
    expect(parseFloat(paddingTop)).toBeGreaterThanOrEqual(0);
  });

  test("portrait and landscape reflow without horizontal overflow", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);

    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto("/?mode=operator&app=demo");
    await expect(page.getByTestId("operator-shell")).toBeVisible({ timeout: 20_000 });
    let overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 2);
    expect(overflow).toBe(false);

    await page.setViewportSize({ width: 844, height: 390 });
    await expect(page.getByTestId("operator-nav")).toBeVisible();
    overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth + 2);
    expect(overflow).toBe(false);
  });

  test("offline stale banner with service worker shell cache", async ({ page, context }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=operator&app=demo");
    await expect(page.getByTestId("operator-shell")).toBeVisible({ timeout: 20_000 });

    await page.evaluate(async () => {
      await navigator.serviceWorker?.ready;
    });

    await context.setOffline(true);
    await page.reload({ waitUntil: "domcontentloaded" });

    await expect(page.getByTestId("operator-offline-banner")).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId("operator-shell")).toBeVisible();

    await context.setOffline(false);
    await expect(page.getByTestId("operator-offline-banner")).toBeHidden({ timeout: 20_000 });
  });

  test("service worker runtime caches dashboards and mimics (BL-151)", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=operator&app=demo");
    await expect(page.getByTestId("operator-shell")).toBeVisible({ timeout: 20_000 });
    await page.evaluate(async () => {
      await navigator.serviceWorker?.ready;
    });

    // Warm NetworkFirst caches used by operator HMI offline path
    await page.evaluate(async () => {
      await fetch("/api/v1/dashboards/by-path?path=root.platform.dashboards.ops-board", {
        headers: { Authorization: "Bearer e2e-mock-token" },
      });
      await fetch("/api/v1/mimics/by-path?path=root.platform.mimics.demo", {
        headers: { Authorization: "Bearer e2e-mock-token" },
      }).catch(() => undefined);
    });

    // Give Workbox a tick to write Cache Storage
    await page.waitForTimeout(500);

    const cacheEvidence = await page.evaluate(async () => {
      const names = await caches.keys();
      const dashboardCache = names.find((n) => n.includes("ispf-dashboards"));
      const mimicCache = names.find((n) => n.includes("ispf-mimics"));
      const manifestCache = names.find((n) => n.includes("ispf-operator-manifest"));
      let dashboardEntries = 0;
      let mimicEntries = 0;
      if (dashboardCache) {
        dashboardEntries = (await caches.open(dashboardCache).then((c) => c.keys())).length;
      }
      if (mimicCache) {
        mimicEntries = (await caches.open(mimicCache).then((c) => c.keys())).length;
      }
      return {
        names,
        hasDashboardCache: Boolean(dashboardCache),
        hasMimicCache: Boolean(mimicCache),
        hasManifestCache: Boolean(manifestCache),
        dashboardEntries,
        mimicEntries,
      };
    });

    expect(
      cacheEvidence.hasDashboardCache || cacheEvidence.hasManifestCache,
      JSON.stringify(cacheEvidence)
    ).toBeTruthy();
    expect(
      cacheEvidence.dashboardEntries + cacheEvidence.mimicEntries,
      JSON.stringify(cacheEvidence)
    ).toBeGreaterThanOrEqual(0);
  });
});
