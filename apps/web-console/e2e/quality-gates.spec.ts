import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockAuthConfig, mockAuthenticatedApi, seedAuthSession } from "./fixtures/apiMocks";
import { buildStressMimicDocument } from "./fixtures/stressMimic";

const OPERATOR_E2E_URL = "/?mode=operator&app=e2e-operator";
const MIN_MIMIC_FPS = Number(process.env.MIMIC_MIN_FPS ?? 60);
const STRESS_ELEMENTS = Number(process.env.MIMIC_STRESS_ELEMENTS ?? 500);

async function openOperatorE2e(page: import("@playwright/test").Page) {
  await Promise.all([
    page.waitForResponse((response) => response.url().includes("/api/v1/auth/me") && response.ok(), {
      timeout: 15_000,
    }),
    page.goto(OPERATOR_E2E_URL),
  ]);
  await expect(page.getByTestId("operator-shell")).toBeVisible({ timeout: 15_000 });
}

function axeForShell(page: import("@playwright/test").Page) {
  return new AxeBuilder({ page })
    .exclude('[disabled]')
    .exclude('[aria-disabled="true"]')
    .exclude('.btn:disabled')
    .exclude('.tree-context-menu-item:disabled');
}

test.describe("a11y baseline", () => {
  test("login page has no critical axe violations", async ({ page }) => {
    await mockAuthConfig(page);
    await page.goto("/");
    const results = await axeForShell(page).analyze();
    const critical = results.violations.filter((v) => v.impact === "critical");
    expect(critical, JSON.stringify(critical, null, 2)).toEqual([]);
  });

  test("login page passes color-contrast (WCAG AA)", async ({ page }) => {
    await mockAuthConfig(page);
    await page.goto("/");
    const results = await axeForShell(page).withRules(["color-contrast"]).analyze();
    expect(results.violations, JSON.stringify(results.violations, null, 2)).toEqual([]);
  });

  test("operator shell has no critical axe violations", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await openOperatorE2e(page);
    const results = await axeForShell(page).analyze();
    const critical = results.violations.filter((v) => v.impact === "critical");
    expect(critical, JSON.stringify(critical, null, 2)).toEqual([]);
  });

  test("operator shell passes color-contrast (WCAG AA)", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await openOperatorE2e(page);
    const results = await axeForShell(page).withRules(["color-contrast"]).analyze();
    expect(results.violations, JSON.stringify(results.violations, null, 2)).toEqual([]);
  });
});

test.describe("mimic runtime FPS", () => {
  test(`stress mimic renders ${STRESS_ELEMENTS} elements at target FPS`, async ({ page }) => {
    const stressDoc = buildStressMimicDocument(STRESS_ELEMENTS);
    expect(stressDoc.elements).toHaveLength(STRESS_ELEMENTS);

    const diagramJson = JSON.stringify(stressDoc);
    await mockAuthenticatedApi(page, {
      dashboardLayout: {
        widgets: [
          {
            id: "mimic-stress",
            type: "scada-mimic",
            title: "Stress mimic",
            x: 0,
            y: 0,
            w: 12,
            h: 8,
            diagramJson,
          },
        ],
      },
    });
    await seedAuthSession(page);
    await openOperatorE2e(page);
    await expect(page.locator(".dashboard-shell")).toBeVisible({ timeout: 20_000 });
    await expect(page.locator(".dash-widget-scada-mimic")).toBeVisible({ timeout: 20_000 });

    await expect
      .poll(async () => page.locator(".scada-mimic-svg > g").count(), { timeout: 15_000 })
      .toBeGreaterThanOrEqual(STRESS_ELEMENTS);

    // Warm-up rAF before FPS sampling
    await page.evaluate(() => new Promise<void>((resolve) => requestAnimationFrame(() => resolve())));

    const fps = await page.evaluate(async () => {
      const samples: number[] = [];
      let frames = 0;
      let start = performance.now();
      await new Promise<void>((resolve) => {
        const tick = (now: number) => {
          frames += 1;
          if (now - start >= 2000) {
            samples.push((frames * 1000) / (now - start));
            if (samples.length >= 2) {
              resolve();
              return;
            }
            frames = 0;
            start = now;
          }
          requestAnimationFrame(tick);
        };
        requestAnimationFrame(tick);
      });
      return Math.min(...samples);
    });

    console.log(`mimic stress FPS (min of 2x2s): ${fps.toFixed(1)} (floor ${MIN_MIMIC_FPS})`);
    expect(fps).toBeGreaterThanOrEqual(MIN_MIMIC_FPS);
  });
});
