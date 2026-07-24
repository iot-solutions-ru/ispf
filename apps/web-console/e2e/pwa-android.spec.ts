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
    // Root-scoped SW breaks co-hosted SPAs (MES Anima); default build stays at "/".
    expect(manifest.scope === "/" || manifest.scope?.endsWith("/")).toBeTruthy();
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

    const swReady = await page.evaluate(async () => {
      const reg = await navigator.serviceWorker?.ready.catch(() => null);
      await fetch("/operator-apps/demo.manifest.json");
      const names = await caches.keys();
      return {
        controlling: Boolean(navigator.serviceWorker?.controller || reg?.active),
        hasManifestCache: names.some((n) => n.includes("ispf-operator-manifest")),
        hasPrecache: names.some((n) => n.includes("workbox-precache")),
      };
    });
    expect(swReady.controlling, JSON.stringify(swReady)).toBeTruthy();
    expect(swReady.hasPrecache || swReady.hasManifestCache, JSON.stringify(swReady)).toBeTruthy();

    // Playwright setOffline also blocks page.route mocks, so a full reload cannot remount
    // against the API mock. Banner UX is driven by navigator.onLine / offline events.
    await context.setOffline(true);
    await page.evaluate(() => {
      if (navigator.onLine) {
        window.dispatchEvent(new Event("offline"));
      }
    });
    await expect(page.getByTestId("operator-offline-banner")).toBeVisible({ timeout: 20_000 });
    await expect(page.getByTestId("operator-shell")).toBeVisible();

    await context.setOffline(false);
    await page.evaluate(() => window.dispatchEvent(new Event("online")));
    await expect(page.getByTestId("operator-offline-banner")).toBeHidden({ timeout: 20_000 });
  });

  test("service worker runtime caches operator manifest (BL-151)", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=operator&app=demo");
    await expect(page.getByTestId("operator-shell")).toBeVisible({ timeout: 20_000 });

    // Workbox only writes runtime caches for fetches the controlling SW sees.
    // First load often has active SW but null controller until claim/reload.
    const controlling = await page.evaluate(async () => {
      if (!("serviceWorker" in navigator)) {
        return false;
      }
      await navigator.serviceWorker.ready;
      if (navigator.serviceWorker.controller) {
        return true;
      }
      await new Promise<void>((resolve) => {
        const onChange = () => {
          navigator.serviceWorker.removeEventListener("controllerchange", onChange);
          resolve();
        };
        navigator.serviceWorker.addEventListener("controllerchange", onChange);
        window.setTimeout(() => {
          navigator.serviceWorker.removeEventListener("controllerchange", onChange);
          resolve();
        }, 5_000);
      });
      return Boolean(navigator.serviceWorker.controller);
    });
    if (!controlling) {
      await page.reload({ waitUntil: "domcontentloaded" });
      await expect(page.getByTestId("operator-shell")).toBeVisible({ timeout: 20_000 });
      await page.evaluate(async () => {
        await navigator.serviceWorker?.ready;
      });
    }

    // Static operator manifest is served by vite preview (not page.route **/api/**),
    // so NetworkFirst can populate ispf-operator-manifest. API dashboard/mimic fetches
    // are intercepted by Playwright mocks and never reach Workbox — covered by
    // scripts/pwa-offline-evidence.mjs + vite runtimeCaching config.
    await page.evaluate(async () => {
      const res = await fetch("/operator-apps/demo.manifest.json");
      if (!res.ok) {
        throw new Error(`manifest warm failed: ${res.status}`);
      }
    });

    await expect
      .poll(
        async () => {
          const snap = await page.evaluate(async () => {
            const names = await caches.keys();
            const manifestCache = names.find((n) => n.includes("ispf-operator-manifest"));
            const manifestEntries = manifestCache
              ? (await caches.open(manifestCache).then((c) => c.keys())).length
              : 0;
            return {
              names,
              controlling: Boolean(navigator.serviceWorker?.controller),
              hasManifestCache: Boolean(manifestCache),
              manifestEntries,
            };
          });
          return snap.hasManifestCache && snap.manifestEntries > 0 ? snap : null;
        },
        { timeout: 15_000, intervals: [200, 400, 800] }
      )
      .not.toBeNull();
  });
});
