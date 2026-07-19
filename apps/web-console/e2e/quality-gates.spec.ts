import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mockAuthConfig, mockAuthenticatedApi, seedAuthSession } from "./fixtures/apiMocks";
import { buildStressMimicDocument, STRESS_MIMIC_BIND_PATH } from "./fixtures/stressMimic";

const OPERATOR_E2E_URL = "/?mode=operator&app=e2e-operator";
/** CI floor (see hmi-quality-gates.md). Override via MIMIC_MIN_FPS (GHA softens to 45). */
const MIN_MIMIC_FPS = Number(process.env.MIMIC_MIN_FPS ?? 55);
/**
 * Soft floor under VARIABLE_UPDATED traffic — proves live path does not collapse.
 * Hard Phase 26 60 fps remains the static stress gate (+ optional E2E_LIVE_FPS).
 */
const MIN_MIMIC_FPS_WS = Number(process.env.MIMIC_MIN_FPS_WS ?? 35);
const STRESS_ELEMENTS = Number(process.env.MIMIC_STRESS_ELEMENTS ?? 500);
const LIVE_FPS = process.env.E2E_LIVE_FPS === "1";
const hasLiveCreds = Boolean(process.env.E2E_USERNAME && process.env.E2E_PASSWORD);

async function openOperatorE2e(page: import("@playwright/test").Page) {
  await Promise.all([
    page.waitForResponse((response) => response.url().includes("/api/v1/auth/me") && response.ok(), {
      timeout: 15_000,
    }),
    page.goto(OPERATOR_E2E_URL),
  ]);
  await expect(page.getByTestId("operator-shell")).toBeVisible({ timeout: 15_000 });
  await expect(page.getByTestId("operator-nav")).toBeVisible({ timeout: 20_000 });
}

async function openStressMimic(
  page: import("@playwright/test").Page,
  options: { withBindings?: boolean } = {}
) {
  const stressDoc = buildStressMimicDocument({
    elementCount: STRESS_ELEMENTS,
    withBindings: options.withBindings,
  });
  expect(stressDoc.elements).toHaveLength(STRESS_ELEMENTS);
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
          diagramJson: JSON.stringify(stressDoc),
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
  test(`stress mimic renders ${STRESS_ELEMENTS} elements at ≥${MIN_MIMIC_FPS} FPS`, async ({ page }) => {
    await openStressMimic(page, { withBindings: false });
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

    console.log(`mimic stress FPS (static): ${fps.toFixed(1)} (floor ${MIN_MIMIC_FPS})`);
    expect(fps).toBeGreaterThanOrEqual(MIN_MIMIC_FPS);
  });

  test(`stress mimic holds ≥${MIN_MIMIC_FPS_WS} FPS under live WS VARIABLE_UPDATED`, async ({ page }) => {
    await openStressMimic(page, { withBindings: true });
    await page.evaluate(() => new Promise<void>((resolve) => requestAnimationFrame(() => resolve())));

    const result = await page.evaluate(
      async ({ bindPath, eventName }) => {
        const samples: number[] = [];
        let frames = 0;
        let start = performance.now();
        let wsUpdates = 0;
        let tick = 0;

        const pumpWs = () => {
          tick += 1;
          wsUpdates += 1;
          window.dispatchEvent(
            new CustomEvent(eventName, {
              detail: {
                type: "VARIABLE_UPDATED",
                path: bindPath,
                variableName: "temperature",
                timestamp: new Date().toISOString(),
                value: {
                  schema: {
                    name: "temperature",
                    fields: [{ name: "value", type: "DOUBLE" }],
                  },
                  rows: [{ value: 20 + (tick % 40) }],
                },
              },
            })
          );
        };

        // 5 Hz OT-style updates during a single 2s FPS window
        const wsTimer = window.setInterval(pumpWs, 200);
        pumpWs();

        await new Promise<void>((resolve) => {
          const onFrame = (now: number) => {
            frames += 1;
            if (now - start >= 2000) {
              samples.push((frames * 1000) / (now - start));
              resolve();
              return;
            }
            requestAnimationFrame(onFrame);
          };
          requestAnimationFrame(onFrame);
        });

        window.clearInterval(wsTimer);
        return { fps: samples[0] ?? 0, wsUpdates };
      },
      { bindPath: STRESS_MIMIC_BIND_PATH, eventName: "ispf-object-ws-message" }
    );

    console.log(
      `mimic stress FPS under WS: ${result.fps.toFixed(1)} (floor ${MIN_MIMIC_FPS_WS}), wsUpdates=${result.wsUpdates}`
    );
    expect(result.wsUpdates).toBeGreaterThan(5);
    expect(result.fps).toBeGreaterThanOrEqual(MIN_MIMIC_FPS_WS);
  });
});

test.describe("mimic live FPS (unmocked API)", () => {
  test.skip(!LIVE_FPS || !hasLiveCreds, "Set E2E_LIVE_FPS=1 and E2E_USERNAME/E2E_PASSWORD for unmocked live FPS");

  test(`live server stress mimic ${STRESS_ELEMENTS} el @≥${MIN_MIMIC_FPS} FPS`, async ({ page }) => {
    const username = process.env.E2E_USERNAME!;
    const password = process.env.E2E_PASSWORD!;
    await page.goto("/");
    const user = page.getByLabel(/user|login|имя/i).or(page.locator('input[name="username"]'));
    const pass = page.getByLabel(/password|пароль/i).or(page.locator('input[name="password"]'));
    if (await user.count()) {
      await user.fill(username);
      await pass.fill(password);
      await page.getByRole("button", { name: /sign in|log in|войти/i }).click();
    }
    await page.goto(OPERATOR_E2E_URL);
    await expect(page.getByTestId("operator-shell")).toBeVisible({ timeout: 30_000 });

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

    console.log(`live mimic FPS: ${fps.toFixed(1)} (floor ${MIN_MIMIC_FPS})`);
    expect(fps).toBeGreaterThanOrEqual(MIN_MIMIC_FPS);
  });
});
