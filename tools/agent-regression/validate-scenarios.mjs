#!/usr/bin/env node
/**
 * Validate agent-regression scenario files and referenced bundle manifests (BL-178).
 * Prints a pass-rate report; optional --results merges live agent run outcomes.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(__dirname, "../..");
const scenariosDir = path.join(repoRoot, "tools/agent-regression/scenarios");
const live = process.argv.includes("--live");
const reportOnly = process.argv.includes("--report");
const enforceRate = process.argv.includes("--enforce-rate");
const resultsArgIndex = process.argv.indexOf("--results");
const resultsPath = resultsArgIndex >= 0 ? process.argv[resultsArgIndex + 1] : null;

const MIN_SCENARIOS = 50;
const TARGET_PASS_RATE = 0.95;
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
  const errors = [];
  const required = ["id", "version", "domain", "title", "prompt"];
  for (const key of required) {
    if (scenario[key] == null || String(scenario[key]).trim() === "") {
      errors.push(`missing ${key}`);
    }
  }
  if (scenario.domain && !["scada", "mes", "hvac"].includes(scenario.domain)) {
    errors.push(`invalid domain ${scenario.domain}`);
  }
  if (scenario.bundle) {
    if (!scenario.bundle.appId || !scenario.bundle.manifestPath) {
      errors.push("bundle requires appId and manifestPath");
    }
  }
  return errors;
}

function validateBundleManifest(manifestPath, appId) {
  const errors = [];
  const abs = path.join(repoRoot, manifestPath);
  if (!fs.existsSync(abs)) {
    errors.push(`bundle not found: ${manifestPath}`);
    return { errors, manifest: null };
  }
  const manifest = readJson(abs);
  for (const key of BUNDLE_REQUIRED) {
    if (manifest[key] == null || String(manifest[key]).trim() === "") {
      errors.push(`${manifestPath}: missing ${key}`);
    }
  }
  if (manifest.operatorUi?.appId && manifest.operatorUi.appId !== appId) {
    errors.push(`${manifestPath}: operatorUi.appId mismatch (expected ${appId})`);
  }
  return { errors, manifest };
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

function loadLiveResults(filePath) {
  if (!filePath) {
    return null;
  }
  const abs = path.isAbsolute(filePath) ? filePath : path.join(repoRoot, filePath);
  if (!fs.existsSync(abs)) {
    fail(`results file not found: ${abs}`);
  }
  const raw = readJson(abs);
  const entries = Array.isArray(raw?.scenarios) ? raw.scenarios : Array.isArray(raw?.runs) ? raw.runs : null;
  if (!entries) {
    fail(`results file must contain scenarios[] or runs[]: ${abs}`);
  }
  const byId = new Map();
  for (const entry of entries) {
    const id = entry?.id ?? entry?.scenarioId;
    if (!id) {
      continue;
    }
    const status = String(entry.status ?? entry.result ?? "").toUpperCase();
    byId.set(id, status);
  }
  return byId;
}

function printPassRateReport(summary) {
  const schemaRate = summary.total > 0 ? summary.schemaPassed / summary.total : 0;
  console.log("--- agent-regression pass-rate report ---");
  console.log(`scenarios: ${summary.total} (min ${MIN_SCENARIOS})`);
  console.log(`schema pass: ${summary.schemaPassed}/${summary.total} (${pct(schemaRate)})`);
  console.log(`bundle manifests checked: ${summary.bundleChecks}`);
  console.log("by domain:");
  for (const domain of ["scada", "mes", "hvac"]) {
    const bucket = summary.byDomain[domain] ?? { pass: 0, fail: 0 };
    const total = bucket.pass + bucket.fail;
    const rate = total > 0 ? bucket.pass / total : 0;
    console.log(`  ${domain}: ${bucket.pass}/${total} (${pct(rate)})`);
  }
  if (summary.live) {
    console.log(`live agent pass: ${summary.live.passed}/${summary.live.total} (${pct(summary.live.rate)})`);
    console.log(`target live pass rate: ${pct(TARGET_PASS_RATE)}`);
    if (summary.live.missing.length > 0) {
      console.log(`live missing scenario ids: ${summary.live.missing.join(", ")}`);
    }
    if (summary.live.failed.length > 0) {
      console.log(`live failed scenario ids: ${summary.live.failed.join(", ")}`);
    }
  }
  if (summary.failed.length > 0) {
    console.log("schema failures:");
    for (const item of summary.failed) {
      console.log(`  ${item.file}: ${item.errors.join("; ")}`);
    }
  }
}

function pct(value) {
  return `${(value * 100).toFixed(1)}%`;
}

const files = fs.readdirSync(scenariosDir).filter((f) => f.endsWith(".json")).sort();
if (files.length < MIN_SCENARIOS) {
  fail(`expected at least ${MIN_SCENARIOS} scenarios, found ${files.length}`);
}

const liveResults = loadLiveResults(resultsPath);
const summary = {
  total: files.length,
  schemaPassed: 0,
  bundleChecks: 0,
  byDomain: {},
  failed: [],
  live: null,
};

for (const file of files) {
  const scenario = readJson(path.join(scenariosDir, file));
  const errors = validateScenarioShape(scenario, file);
  const domain = scenario.domain ?? "unknown";
  summary.byDomain[domain] ??= { pass: 0, fail: 0 };

  if (scenario.bundle?.manifestPath) {
    const bundleResult = validateBundleManifest(
      scenario.bundle.manifestPath,
      scenario.bundle.appId
    );
    errors.push(...bundleResult.errors);
    if (bundleResult.errors.length === 0) {
      summary.bundleChecks++;
      if (live) {
        await liveValidateBundle(scenario.bundle.appId, scenario.bundle.manifestPath);
      }
    }
  }

  if (errors.length === 0) {
    summary.schemaPassed++;
    summary.byDomain[domain].pass++;
  } else {
    summary.byDomain[domain].fail++;
    summary.failed.push({ file, errors });
    if (!reportOnly) {
      fail(`${file}: ${errors.join("; ")}`);
    }
  }
}

if (liveResults) {
  let passed = 0;
  let total = 0;
  const missing = [];
  const failed = [];
  for (const file of files) {
    const scenario = readJson(path.join(scenariosDir, file));
    total++;
    const status = liveResults.get(scenario.id);
    if (!status) {
      missing.push(scenario.id);
      continue;
    }
    if (status === "OK" || status === "PASS" || status === "PASSED" || status === "SUCCESS") {
      passed++;
    } else {
      failed.push(scenario.id);
    }
  }
  summary.live = {
    total,
    passed,
    rate: total > 0 ? passed / total : 0,
    missing,
    failed,
  };
  if (enforceRate && summary.live.rate < TARGET_PASS_RATE) {
    printPassRateReport(summary);
    fail(`live pass rate ${pct(summary.live.rate)} below target ${pct(TARGET_PASS_RATE)}`);
  }
}

printPassRateReport(summary);

if (summary.failed.length > 0) {
  process.exit(1);
}

console.log(`OK: ${files.length} scenarios, ${summary.bundleChecks} bundle manifests validated${live ? " (live API)" : ""}`);
