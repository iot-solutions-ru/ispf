import { test, expect } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import { mockAuthConfig, mockAuthenticatedApi, seedAuthSession } from "./fixtures/apiMocks";
import { buildStressMimicDocument, STRESS_MIMIC_BIND_PATH } from "./fixtures/stressMimic";

const OPERATOR_E2E_URL = "/?mode=operator&app=e2e-operator";
/** CI floor (see hmi-quality-gates.md). Override via MIMIC_MIN_FPS (GHA softens to 35). */
const MIN_MIMIC_FPS = Number(process.env.MIMIC_MIN_FPS ?? 55);
/**
 * Soft floor under VARIABLE_UPDATED traffic — proves live path does not collapse.
 * Hard Phase 26 60 fps remains the static stress gate (+ optional E2E_LIVE_FPS).
 */
const MIN_MIMIC_FPS_WS = Number(process.env.MIMIC_MIN_FPS_WS ?? 35);
/** Soft floor for unmocked demostand mimic (real diagram, not 500-el stress). */
const MIN_MIMIC_FPS_LIVE = Number(process.env.MIMIC_MIN_FPS_LIVE ?? 30);
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
      // Warm one frame budget, then sample three 1.5s windows; report median (less GHA noise than min).
      await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
      const samples: number[] = [];
      let frames = 0;
      let start = performance.now();
      await new Promise<void>((resolve) => {
        const tick = (now: number) => {
          frames += 1;
          if (now - start >= 1500) {
            samples.push((frames * 1000) / (now - start));
            if (samples.length >= 3) {
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
      samples.sort((a, b) => a - b);
      return samples[1] ?? samples[0] ?? 0;
    });

    console.log(`mimic stress FPS (static, median): ${fps.toFixed(1)} (floor ${MIN_MIMIC_FPS})`);
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

        // 5 Hz OT-style updates across three 1.5s windows; median resists GHA noise.
        const wsTimer = window.setInterval(pumpWs, 200);
        pumpWs();

        await new Promise<void>((resolve) => {
          const onFrame = (now: number) => {
            frames += 1;
            if (now - start >= 1500) {
              samples.push((frames * 1000) / (now - start));
              if (samples.length >= 3) {
                resolve();
                return;
              }
              frames = 0;
              start = now;
            }
            requestAnimationFrame(onFrame);
          };
          requestAnimationFrame(onFrame);
        });

        window.clearInterval(wsTimer);
        samples.sort((a, b) => a - b);
        return { fps: samples[1] ?? samples[0] ?? 0, wsUpdates };
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

test.describe("mimic live FPS (unmocked demostand)", () => {
  test.skip(!LIVE_FPS || !hasLiveCreds, "Set E2E_LIVE_FPS=1 and E2E_USERNAME/E2E_PASSWORD for unmocked live FPS");

  test(`live operator mimic holds ≥${MIN_MIMIC_FPS_LIVE} FPS with real Object WS traffic`, async ({ page }) => {
    const username = process.env.E2E_USERNAME!;
    const password = process.env.E2E_PASSWORD!;
    const appId = process.env.E2E_OPERATOR_APP || "ui-pump-station";
    const dashboard = process.env.E2E_MIMIC_DASHBOARD || "";
    const operatorUrl =
      dashboard.length > 0
        ? `/?mode=operator&app=${encodeURIComponent(appId)}&dashboard=${encodeURIComponent(dashboard)}`
        : `/?mode=operator&app=${encodeURIComponent(appId)}`;

    await page.goto("/");
    await expect(page.getByRole("button", { name: /sign in|log in|войти/i })).toBeVisible({ timeout: 30_000 });
    await page.getByRole("textbox", { name: /username|user|логин|имя/i }).fill(username);
    await page.getByRole("textbox", { name: /^password|пароль$/i }).fill(password);
    await Promise.all([
      page.waitForResponse(
        (response) => response.url().includes("/api/v1/auth/login") && response.ok(),
        { timeout: 30_000 }
      ).catch(() => null),
      page.getByRole("button", { name: /sign in|log in|войти/i }).click(),
    ]);
    // Leave the login screen before opening operator mode.
    await expect(page.getByRole("button", { name: /sign in|log in|войти/i })).toHaveCount(0, {
      timeout: 30_000,
    });

    await page.goto(operatorUrl);
    await expect(page.getByTestId("operator-shell")).toBeVisible({ timeout: 45_000 });
    await expect(page.locator(".dash-widget-scada-mimic")).toBeVisible({ timeout: 45_000 });
    await expect
      .poll(async () => page.locator(".scada-mimic-svg > g").count(), { timeout: 30_000 })
      .toBeGreaterThan(0);

    const elementCount = await page.locator(".scada-mimic-svg > g").count();

    // Wait briefly for real Object WS VARIABLE_UPDATED (do not dispatch mock events).
    await page.evaluate(async () => {
      await new Promise<void>((resolve) => {
        let done = false;
        const finish = () => {
          if (done) return;
          done = true;
          window.removeEventListener("ispf-object-ws-message", onMsg as EventListener);
          resolve();
        };
        const onMsg = (ev: Event) => {
          const detail = (ev as CustomEvent).detail as { type?: string } | undefined;
          if (detail?.type === "VARIABLE_UPDATED") finish();
        };
        window.addEventListener("ispf-object-ws-message", onMsg as EventListener);
        window.setTimeout(finish, 12_000);
      });
    });

    const result = await page.evaluate(async () => {
      const samples: number[] = [];
      let frames = 0;
      let start = performance.now();
      let wsUpdates = 0;
      const onMsg = (ev: Event) => {
        const detail = (ev as CustomEvent).detail as { type?: string } | undefined;
        if (detail?.type === "VARIABLE_UPDATED") wsUpdates += 1;
      };
      window.addEventListener("ispf-object-ws-message", onMsg as EventListener);

      await new Promise<void>((resolve) => {
        const onFrame = (now: number) => {
          frames += 1;
          if (now - start >= 2000) {
            samples.push((frames * 1000) / (now - start));
            if (samples.length >= 3) {
              resolve();
              return;
            }
            frames = 0;
            start = now;
          }
          requestAnimationFrame(onFrame);
        };
        requestAnimationFrame(onFrame);
      });

      window.removeEventListener("ispf-object-ws-message", onMsg as EventListener);
      samples.sort((a, b) => a - b);
      return {
        fpsMedian: samples[1] ?? samples[0] ?? 0,
        fpsMin: Math.min(...samples),
        samples,
        wsUpdates,
      };
    });

    console.log(
      `live mimic FPS median=${result.fpsMedian.toFixed(1)} min=${result.fpsMin.toFixed(1)} ` +
        `(floor ${MIN_MIMIC_FPS_LIVE}) wsUpdates=${result.wsUpdates} elements≈${elementCount} app=${appId}`
    );

    const evidencePath = process.env.E2E_LIVE_FPS_EVIDENCE;
    if (evidencePath) {
      const payload = {
        generatedAt: new Date().toISOString(),
        source: "quality-gates.spec.ts live operator mimic",
        baseUrl: process.env.E2E_BASE_URL || "",
        appId,
        dashboard: dashboard || null,
        elementCount,
        softBudgetFps: MIN_MIMIC_FPS_LIVE,
        fpsMedian: Number(result.fpsMedian.toFixed(2)),
        fpsMin: Number(result.fpsMin.toFixed(2)),
        samples: result.samples.map((n) => Number(n.toFixed(2))),
        wsVariableUpdatedCount: result.wsUpdates,
        functionalOk: result.fpsMedian >= MIN_MIMIC_FPS_LIVE && result.wsUpdates > 0,
      };
      mkdirSync(dirname(evidencePath), { recursive: true });
      writeFileSync(evidencePath, `${JSON.stringify(payload, null, 2)}\n`, "utf8");
      console.log(`Wrote live FPS evidence → ${evidencePath}`);
    }

    expect(result.wsUpdates, "expected real Object WS VARIABLE_UPDATED during sample").toBeGreaterThan(0);
    expect(result.fpsMedian).toBeGreaterThanOrEqual(MIN_MIMIC_FPS_LIVE);
  });
});
