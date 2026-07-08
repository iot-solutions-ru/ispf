#!/usr/bin/env node
/**
 * Validate agent-regression scenario files and referenced bundle manifests (BL-178).
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "../..");
const scenariosDir = path.join(repoRoot, "tools/agent-regression/scenarios");
const live = process.argv.includes("--live");

const BUNDLE_REQUIRED = ["version", "displayName"];

function fail(message) {
  console.error("FAIL:", message);
  process.exit(1);
}

function readJson(filePath) {
  try {
    return JSON.parse(fs.readFileSync(filePath, "utf8"));
  } catch (e) {
    fail(`${filePath}: ${e.message}`);
  }
}

function validateScenarioShape(scenario, file) {
  const required = ["id", "version", "domain", "title", "prompt"];
  for (const key of required) {
    if (scenario[key] == null || String(scenario[key]).trim() === "") {
      fail(`${file}: missing ${key}`);
    }
  }
  if (!["scada", "mes", "hvac"].includes(scenario.domain)) {
    fail(`${file}: invalid domain ${scenario.domain}`);
  }
  if (scenario.bundle) {
    if (!scenario.bundle.appId || !scenario.bundle.manifestPath) {
      fail(`${file}: bundle requires appId and manifestPath`);
    }
  }
}

function validateBundleManifest(manifestPath, appId) {
  const abs = path.join(repoRoot, manifestPath);
  if (!fs.existsSync(abs)) {
    fail(`bundle not found: ${manifestPath}`);
  }
  const manifest = readJson(abs);
  for (const key of BUNDLE_REQUIRED) {
    if (manifest[key] == null || String(manifest[key]).trim() === "") {
      fail(`${manifestPath}: missing ${key}`);
    }
  }
  if (manifest.operatorUi?.appId && manifest.operatorUi.appId !== appId) {
    fail(`${manifestPath}: operatorUi.appId mismatch (expected ${appId})`);
  }
  return manifest;
}

async function liveValidateBundle(appId, manifestPath) {
  const baseUrl = (process.env.ISPF_BASE_URL ?? "http://localhost:8080").replace(/\/$/, "");
  const token = process.env.ISPF_API_TOKEN ?? "";
  const manifest = fs.readFileSync(path.join(repoRoot, manifestPath), "utf8");
  const headers = { "Content-Type": "application/json" };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const response = await fetch(
    `${baseUrl}/api/v1/applications/${encodeURIComponent(appId)}/bundle/validate`,
    { method: "POST", headers, body: manifest }
  );
  const text = await response.text();
  let json;
  try {
    json = JSON.parse(text);
  } catch {
    fail(`live validate ${appId}: ${response.status} ${text}`);
  }
  if (!response.ok || json.status !== "OK") {
    fail(`live validate ${appId}: ${text}`);
  }
}

const files = fs.readdirSync(scenariosDir).filter((f) => f.endsWith(".json")).sort();
if (files.length < 30) {
  fail(`expected at least 30 scenarios, found ${files.length}`);
}

let bundleChecks = 0;
for (const file of files) {
  const scenario = readJson(path.join(scenariosDir, file));
  validateScenarioShape(scenario, file);
  if (scenario.bundle?.manifestPath) {
    validateBundleManifest(scenario.bundle.manifestPath, scenario.bundle.appId);
    bundleChecks++;
    if (live) {
      await liveValidateBundle(scenario.bundle.appId, scenario.bundle.manifestPath);
    }
  }
}

console.log(`OK: ${files.length} scenarios, ${bundleChecks} bundle manifests validated${live ? " (live API)" : ""}`);
