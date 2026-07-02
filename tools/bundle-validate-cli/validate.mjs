#!/usr/bin/env node
/**
 * Thin wrapper: validate + dry-run deploy against ISPF API (BL-98).
 * Usage: node validate.mjs <bundle.json> <appId>
 */
import fs from "node:fs";

const [bundlePath, appId] = process.argv.slice(2);
if (!bundlePath || !appId) {
  console.error("Usage: validate.mjs <bundle.json> <appId>");
  process.exit(2);
}

const baseUrl = (process.env.ISPF_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
const token = process.env.ISPF_API_TOKEN ?? "";
const manifest = fs.readFileSync(bundlePath, "utf8");

const headers = {
  "Content-Type": "application/json",
};
if (token) {
  headers.Authorization = `Bearer ${token}`;
}

async function post(path, body) {
  const response = await fetch(`${baseUrl}${path}`, {
    method: "POST",
    headers,
    body,
  });
  const text = await response.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    throw new Error(`${path} failed (${response.status}): ${text}`);
  }
  if (!response.ok) {
    throw new Error(`${path} failed (${response.status}): ${text}`);
  }
  return json;
}

const validate = await post(
  `/api/v1/applications/${encodeURIComponent(appId)}/bundle/validate`,
  manifest
);
if (validate.status !== "OK") {
  console.error("validate errors:", validate.errors);
  process.exit(1);
}
console.log("validate OK", validate.warnings?.length ? `warnings=${validate.warnings.length}` : "");

const dryRun = await post(
  `/api/v1/applications/${encodeURIComponent(appId)}/bundle/validate?dryRun=true`,
  manifest
);
if (dryRun.status !== "OK") {
  console.error("dry-run errors:", dryRun.errors);
  process.exit(1);
}
console.log("dry-run OK wouldApply:", (dryRun.wouldApply ?? []).join(", "));
