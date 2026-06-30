#!/usr/bin/env node
/**
 * Fail CI when npm lockfile has packages with unknown/missing license metadata.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const lockPath = path.resolve(here, "../../apps/web-console/package-lock.json");

/** Upstream SPDX when npm metadata is incomplete. */
const LICENSE_OVERRIDES = {
  "@mapbox/jsonlint-lines-primitives": "MIT",
  buffers: "MIT",
};

const FORBIDDEN = new Set(["UNLICENSED", "UNKNOWN", "MISSING"]);

function resolveLicense(name, entry) {
  if (LICENSE_OVERRIDES[name]) return LICENSE_OVERRIDES[name];
  const lic = entry?.license;
  if (!lic) return "MISSING";
  if (Array.isArray(lic)) return lic.join(" OR ");
  return String(lic);
}

const lock = JSON.parse(fs.readFileSync(lockPath, "utf8"));
const packages = lock.packages ?? {};
const problems = [];

for (const [key, entry] of Object.entries(packages)) {
  if (!key) continue;
  const name = key.replace(/^node_modules\//, "");
  const license = resolveLicense(name, entry);
  if (FORBIDDEN.has(license) || license === "MISSING") {
    problems.push(`${name}: ${license}`);
  }
}

if (problems.length > 0) {
  console.error("npm license audit failed:");
  for (const line of problems) console.error(`  - ${line}`);
  process.exit(1);
}

console.log("npm license audit OK");
