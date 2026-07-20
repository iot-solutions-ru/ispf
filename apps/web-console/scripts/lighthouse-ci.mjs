#!/usr/bin/env node
/**
 * Lighthouse CI gate on vite preview (S21-03 / BL-95, S29 operator shell).
 */
import { spawn } from "node:child_process";
import { createServer } from "node:net";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import lighthouse from "lighthouse";
import * as chromeLauncher from "chrome-launcher";
import puppeteer from "puppeteer-core";
import { chromium } from "playwright";
import { installLighthouseApiMocks } from "./lighthouse-mocks.mjs";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const PREVIEW_PORT = Number(process.env.LH_PORT ?? 4173);
const PREVIEW_HOST = process.env.LH_HOST ?? "127.0.0.1";
const ORIGIN = `http://${PREVIEW_HOST}:${PREVIEW_PORT}`;
const LOGIN_URL = process.env.LH_URL ?? `${ORIGIN}/`;
const OPERATOR_URL = process.env.LH_OPERATOR_URL ?? `${ORIGIN}/?mode=operator&app=e2e-operator`;

const MIN_PERFORMANCE = Number(process.env.LH_MIN_PERFORMANCE ?? 0);
const MIN_ACCESSIBILITY = Number(process.env.LH_MIN_ACCESSIBILITY ?? 85);
const MIN_ACCESSIBILITY_OPERATOR = Number(process.env.LH_MIN_ACCESSIBILITY_OPERATOR ?? 90);

function portFree(port) {
  return new Promise((resolve) => {
    const server = createServer();
    server.once("error", () => resolve(false));
    server.listen(port, PREVIEW_HOST, () => server.close(() => resolve(true)));
  });
}

async function ensurePreview() {
  if (!(await portFree(PREVIEW_PORT))) {
    console.log(`Preview already listening on ${PREVIEW_PORT}`);
    return null;
  }
  console.log("Starting vite preview…");
  const viteBin = join(root, "node_modules", "vite", "bin", "vite.js");
  const child = spawn(
    process.execPath,
    [viteBin, "preview", "--host", PREVIEW_HOST, "--port", String(PREVIEW_PORT)],
    { cwd: root, stdio: "ignore" }
  );
  for (let i = 0; i < 60; i++) {
    if (!(await portFree(PREVIEW_PORT))) break;
    await new Promise((r) => setTimeout(r, 1000));
  }
  return child;
}

function categoryScore(lhr, category) {
  return Math.round((lhr.categories[category]?.score ?? 0) * 100);
}

async function waitForPreviewReady() {
  for (let i = 0; i < 120; i++) {
    try {
      const res = await fetch(`${ORIGIN}/`);
      if (res.ok) return;
    } catch {
      // preview still starting
    }
    await new Promise((r) => setTimeout(r, 500));
  }
  throw new Error(`Preview not ready at ${ORIGIN}`);
}

async function runLighthouseBestOf(chromePort, url, page = undefined, attempts = 2) {
  let best = { perf: 0, a11y: 0 };
  for (let i = 0; i < attempts; i++) {
    const scores = await runLighthouse(chromePort, url, page);
    best = {
      perf: Math.max(best.perf, scores.perf),
      a11y: Math.max(best.a11y, scores.a11y),
    };
  }
  return best;
}

async function runLighthouse(chromePort, url, page = undefined) {
  const result = await lighthouse(
    url,
    {
      logLevel: "error",
      output: "json",
      port: chromePort,
      onlyCategories: ["performance", "accessibility"],
    },
    undefined,
    page
  );
  return {
    perf: categoryScore(result.lhr, "performance"),
    a11y: categoryScore(result.lhr, "accessibility"),
  };
}

async function prepareOperatorPage(chromePort) {
  const browser = await puppeteer.connect({
    browserURL: `http://127.0.0.1:${chromePort}`,
    defaultViewport: { width: 1280, height: 800 },
  });
  const page = await browser.newPage();
  await installLighthouseApiMocks(page, ORIGIN);
  // Operator shell keeps polling / WS even under mocks — networkidle0 never settles on CI.
  await page.goto(OPERATOR_URL, { waitUntil: "domcontentloaded", timeout: 60_000 });
  await page.waitForSelector('[data-testid="operator-shell"]', { timeout: 30_000 });
  return { browser, page };
}

function checkScores(label, url, scores, minPerf, minA11y, failures) {
  console.log(`Lighthouse ${label} ${url}: performance=${scores.perf}, accessibility=${scores.a11y}`);
  if (minPerf > 0 && scores.perf < minPerf) failures.push(`${label} performance ${scores.perf} < ${minPerf}`);
  if (scores.a11y < minA11y) failures.push(`${label} accessibility ${scores.a11y} < ${minA11y}`);
}

const preview = await ensurePreview();
await waitForPreviewReady();
const chromePath = process.env.CHROME_PATH || chromium.executablePath();
const chrome = await chromeLauncher.launch({
  chromePath,
  chromeFlags: ["--headless", "--no-sandbox"],
});
const failures = [];

try {
  const loginScores = await runLighthouseBestOf(chrome.port, LOGIN_URL);
  checkScores("login", LOGIN_URL, loginScores, MIN_PERFORMANCE, MIN_ACCESSIBILITY, failures);

  const { browser, page } = await prepareOperatorPage(chrome.port);
  try {
    const operatorScores = await runLighthouse(chrome.port, page.url(), page);
    checkScores("operator", OPERATOR_URL, operatorScores, 0, MIN_ACCESSIBILITY_OPERATOR, failures);
  } finally {
    await browser.disconnect();
  }

  if (failures.length) {
    console.error("Lighthouse gate FAILED:", failures.join("; "));
    process.exit(1);
  }
  console.log("Lighthouse gate OK");
} finally {
  await chrome.kill();
  preview?.kill();
}
