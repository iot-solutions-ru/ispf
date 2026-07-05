#!/usr/bin/env node
/**
 * Lighthouse CI gate on vite preview (S21-03 / BL-95).
 */
import { spawn } from "node:child_process";
import { createServer } from "node:net";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import lighthouse from "lighthouse";
import * as chromeLauncher from "chrome-launcher";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const PREVIEW_PORT = Number(process.env.LH_PORT ?? 4173);
const PREVIEW_HOST = process.env.LH_HOST ?? "127.0.0.1";
const URL = process.env.LH_URL ?? `http://${PREVIEW_HOST}:${PREVIEW_PORT}/`;

const MIN_PERFORMANCE = Number(process.env.LH_MIN_PERFORMANCE ?? 75);
const MIN_ACCESSIBILITY = Number(process.env.LH_MIN_ACCESSIBILITY ?? 85);

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
  const child = spawn(
    process.platform === "win32" ? "npm.cmd" : "npm",
    ["run", "preview", "--", "--host", PREVIEW_HOST, "--port", String(PREVIEW_PORT)],
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

const preview = await ensurePreview();
const chrome = await chromeLauncher.launch({ chromeFlags: ["--headless", "--no-sandbox"] });
try {
  const result = await lighthouse(URL, {
    logLevel: "error",
    output: "json",
    port: chrome.port,
    onlyCategories: ["performance", "accessibility"],
  });
  const perf = categoryScore(result.lhr, "performance");
  const a11y = categoryScore(result.lhr, "accessibility");
  console.log(`Lighthouse ${URL}: performance=${perf}, accessibility=${a11y}`);

  const failures = [];
  if (perf < MIN_PERFORMANCE) failures.push(`performance ${perf} < ${MIN_PERFORMANCE}`);
  if (a11y < MIN_ACCESSIBILITY) failures.push(`accessibility ${a11y} < ${MIN_ACCESSIBILITY}`);
  if (failures.length) {
    console.error("Lighthouse gate FAILED:", failures.join("; "));
    process.exit(1);
  }
  console.log("Lighthouse gate OK");
} finally {
  await chrome.kill();
  preview?.kill();
}
