#!/usr/bin/env node
/**
 * HMI offline field soak (BL-151 / Post-S33) — automated lab path.
 *
 * Opens a real demostand operator app, goes CDP-offline, samples the shell
 * every interval, reconnects, and writes JSON evidence + optional journal.
 *
 * Honesty: this is browser CDP offline against the hosted site, not an
 * on-site tablet airplane-mode soak. Still closes the automated 2h lab gap.
 *
 * Usage:
 *   cd apps/web-console && npm ci && npx playwright install chromium
 *   E2E_BASE_URL=https://ispf.iot-solutions.ru \
 *   E2E_USERNAME=admin E2E_PASSWORD=admin \
 *   E2E_OPERATOR_APP=ui-pump-station \
 *   HMI_OFFLINE_SOAK_MINUTES=120 \
 *   HMI_OFFLINE_SOAK_EVIDENCE=../../docs/evidence/hmi-offline/YYYY-MM-DD-….json \
 *     npm run pwa:offline-field-soak
 */
import { chromium } from "playwright";
import { mkdirSync, writeFileSync } from "node:fs";
import { dirname, resolve } from "node:path";

const baseUrl = (process.env.E2E_BASE_URL || "https://ispf.iot-solutions.ru").replace(/\/$/, "");
const username = process.env.E2E_USERNAME || "admin";
const password = process.env.E2E_PASSWORD || "admin";
const appId = process.env.E2E_OPERATOR_APP || "ui-pump-station";
const soakMinutes = Math.max(1, Number(process.env.HMI_OFFLINE_SOAK_MINUTES || "120"));
const sampleEverySec = Math.max(30, Number(process.env.HMI_OFFLINE_SAMPLE_EVERY_SEC || "300"));
const evidencePath = process.env.HMI_OFFLINE_SOAK_EVIDENCE
  ? resolve(process.cwd(), process.env.HMI_OFFLINE_SOAK_EVIDENCE)
  : null;
const headed = process.env.HMI_OFFLINE_SOAK_HEADED === "1";

function utcNow() {
  return new Date().toISOString();
}

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

async function login(page) {
  await page.goto(`${baseUrl}/`);
  await page.getByRole("textbox", { name: /username|user|логин|имя/i }).fill(username);
  await page.getByRole("textbox", { name: /^password|пароль$/i }).fill(password);
  await Promise.all([
    page
      .waitForResponse((r) => r.url().includes("/api/v1/auth/login") && r.ok(), { timeout: 30_000 })
      .catch(() => null),
    page.getByRole("button", { name: /sign in|log in|войти/i }).click(),
  ]);
  await page.getByRole("button", { name: /sign in|log in|войти/i }).waitFor({ state: "detached", timeout: 30_000 });
}

async function sample(page, label) {
  const row = await page.evaluate(() => {
    const shell = document.querySelector('[data-testid="operator-shell"]');
    const banner = document.querySelector('[data-testid="operator-offline-banner"]');
    const bodyText = (document.body?.innerText || "").trim();
    const white =
      !shell ||
      bodyText.length < 8 ||
      getComputedStyle(document.body).visibility === "hidden";
    return {
      shellVisible: Boolean(shell),
      bannerVisible: Boolean(banner),
      whiteScreen: white,
      online: navigator.onLine,
      title: document.title || "",
    };
  });
  return { at: utcNow(), label, ...row };
}

async function main() {
  const startedAt = utcNow();
  const samples = [];
  console.log(
    `HMI offline soak start app=${appId} minutes=${soakMinutes} sampleEverySec=${sampleEverySec} base=${baseUrl}`
  );

  const browser = await chromium.launch({ headless: !headed });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    ignoreHTTPSErrors: true,
  });
  const page = await context.newPage();
  page.setDefaultTimeout(45_000);

  let functionalOk = false;
  let reconnectOk = false;
  let error = null;

  try {
    await login(page);
    await page.goto(`${baseUrl}/?mode=operator&app=${encodeURIComponent(appId)}`);
    await page.getByTestId("operator-shell").waitFor({ state: "visible", timeout: 45_000 });
    // Warm UI (navigate once if nav links exist).
    await sleep(3_000);
    samples.push(await sample(page, "online-warm"));

    await context.setOffline(true);
    await page.evaluate(() => window.dispatchEvent(new Event("offline")));
    await sleep(2_000);
    samples.push(await sample(page, "offline-t0"));
    if (!samples.at(-1).bannerVisible) {
      console.warn("WARN: offline banner not visible at t0 (demostand may rely on SW / CDP events)");
    }
    if (samples.at(-1).whiteScreen || !samples.at(-1).shellVisible) {
      throw new Error("white screen or missing operator shell at offline t0");
    }

    const endAt = Date.now() + soakMinutes * 60_000;
    let tick = 0;
    while (Date.now() < endAt) {
      const waitMs = Math.min(sampleEverySec * 1000, Math.max(1_000, endAt - Date.now()));
      await sleep(waitMs);
      tick += 1;
      const row = await sample(page, `offline-t${tick}`);
      samples.push(row);
      console.log(
        `${row.at} ${row.label} shell=${row.shellVisible} banner=${row.bannerVisible} white=${row.whiteScreen}`
      );
      if (row.whiteScreen || !row.shellVisible) {
        throw new Error(`UI loss at ${row.label}`);
      }
    }

    await context.setOffline(false);
    await page.evaluate(() => window.dispatchEvent(new Event("online")));
    await sleep(5_000);
    // Soft reload online to exercise reconnect path.
    await page.reload({ waitUntil: "domcontentloaded" });
    await page.getByTestId("operator-shell").waitFor({ state: "visible", timeout: 60_000 });
    await sleep(3_000);
    const reconnect = await sample(page, "reconnect");
    samples.push(reconnect);
    reconnectOk = reconnect.shellVisible && !reconnect.whiteScreen && !reconnect.bannerVisible;
    functionalOk =
      samples.filter((s) => String(s.label).startsWith("offline")).every((s) => s.shellVisible && !s.whiteScreen) &&
      reconnectOk;

    console.log(`functionalOk=${functionalOk} reconnectOk=${reconnectOk} samples=${samples.length}`);
  } catch (err) {
    error = err instanceof Error ? err.message : String(err);
    console.error("SOAK FAILED:", error);
  } finally {
    await browser.close();
  }

  const endedAt = utcNow();
  const payload = {
    generatedAt: endedAt,
    source: "apps/web-console/scripts/pwa-offline-field-soak.mjs",
    baseUrl,
    platformVersionProbe: null,
    appId,
    soakMinutes,
    sampleEverySec,
    startedAt,
    endedAt,
    functionalOk,
    reconnectOk,
    sampleCount: samples.length,
    samples,
    method: "playwright-cdp-offline",
    honesty:
      "CDP Network.offline against hosted demostand operator UI — not on-site tablet airplane mode. Complements CI pwa:offline-evidence.",
    error,
  };

  try {
    const info = await fetch(`${baseUrl}/api/v1/info`).then((r) => r.json());
    payload.platformVersionProbe = info?.version || null;
  } catch {
    /* ignore */
  }

  if (evidencePath) {
    mkdirSync(dirname(evidencePath), { recursive: true });
    writeFileSync(evidencePath, `${JSON.stringify(payload, null, 2)}\n`);
    console.log(`Wrote evidence → ${evidencePath}`);
  } else {
    console.log(JSON.stringify(payload, null, 2));
  }

  process.exit(functionalOk ? 0 : 1);
}

main();
