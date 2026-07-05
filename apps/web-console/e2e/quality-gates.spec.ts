import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockAuthConfig, mockAuthenticatedApi, seedAuthSession } from "./fixtures/apiMocks";
import { buildStressMimicDocument } from "./fixtures/stressMimic";

const MIN_MIMIC_FPS = Number(process.env.MIMIC_MIN_FPS ?? 55);
const STRESS_ELEMENTS = Number(process.env.MIMIC_STRESS_ELEMENTS ?? 120);

test.describe("a11y baseline", () => {
  test("login page has no critical axe violations", async ({ page }) => {
    await mockAuthConfig(page);
    await page.goto("/");
    const results = await new AxeBuilder({ page })
      .disableRules(["color-contrast"])
      .analyze();
    const critical = results.violations.filter((v) => v.impact === "critical");
    expect(critical, JSON.stringify(critical, null, 2)).toEqual([]);
  });

  test("operator shell has no critical axe violations", async ({ page }) => {
    await mockAuthenticatedApi(page);
    await seedAuthSession(page);
    await page.goto("/?mode=operator");
    await expect(page.getByText("Ops board")).toBeVisible({ timeout: 15_000 });
    const results = await new AxeBuilder({ page })
      .disableRules(["color-contrast"])
      .analyze();
    const critical = results.violations.filter((v) => v.impact === "critical");
    expect(critical, JSON.stringify(critical, null, 2)).toEqual([]);
  });
});

test.describe("mimic runtime FPS", () => {
  test("stress mimic sustains target FPS", async ({ page }) => {
    const diagramJson = JSON.stringify(buildStressMimicDocument(STRESS_ELEMENTS));
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
    await page.goto("/?mode=operator");
    await expect(page.locator(".dash-widget-scada-mimic")).toBeVisible({ timeout: 20_000 });

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
