#!/usr/bin/env node
/**
 * Post-deploy: apply driverId/config for ITM notification, SSH, and OPC UA DEVICEs.
 *
 * Reads deviceDriverHints from m11-ui-overlay.json (or --hints path).
 * Requires ISPF session: ISPF_BASE_URL + ISPF_TOKEN (Bearer), or cookie jar.
 *
 * Usage:
 *   set ISPF_BASE_URL=https://ispf.iot-solutions.ru
 *   set ISPF_TOKEN=...
 *   node examples/it-infra-monitoring/scripts/configure-integrations.mjs
 *
 * Dry-run (print payloads only):
 *   node .../configure-integrations.mjs --dry-run
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const dryRun = process.argv.includes("--dry-run");

const overlay = JSON.parse(
  fs.readFileSync(path.join(root, "m11-ui-overlay.json"), "utf8")
);
const hints = overlay.deviceDriverHints || {};
const base = (process.env.ISPF_BASE_URL || "http://127.0.0.1:8080").replace(/\/$/, "");
const token = process.env.ISPF_TOKEN || "";

if (!Object.keys(hints).length) {
  console.error("No deviceDriverHints in m11-ui-overlay.json");
  process.exit(1);
}

async function configure(objectPath, cfg) {
  const { pointMapping, driverId, ...rest } = cfg;
  const configuration = Object.fromEntries(
    Object.entries(rest).map(([k, v]) => [k, String(v)])
  );
  const body = {
    driverId,
    pollIntervalMs: Number(configuration.pollIntervalMs || 2000),
    configuration,
    pointMappings: pointMapping || {},
    autoStart: false,
  };
  console.log(objectPath, JSON.stringify(body));
  if (dryRun) return;
  if (!token) {
    throw new Error("ISPF_TOKEN required unless --dry-run");
  }
  const url = `${base}/api/v1/drivers/runtime/configure?devicePath=${encodeURIComponent(objectPath)}`;
  const res = await fetch(url, {
    method: "PUT",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`${objectPath}: ${res.status} ${text}`);
  }
}

for (const [objectPath, cfg] of Object.entries(hints)) {
  await configure(objectPath, cfg);
}
console.log(dryRun ? "dry-run ok" : "configured ok");
