#!/usr/bin/env node
/**
 * Validate gradle/driver-packs.json licenseType values and restricted-pack metadata.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));
const repoRoot = path.resolve(here, "../..");
const catalogPath = path.join(repoRoot, "gradle/driver-packs.json");
const catalog = JSON.parse(fs.readFileSync(catalogPath, "utf8"));

const REQUIRED_OVERRIDES = {
  "ispf-driver-sip": "LicenseRef-NIST-PublicDomain",
};

const problems = [];

for (const [module, expected] of Object.entries(REQUIRED_OVERRIDES)) {
  const entry = catalog[module];
  if (!entry) {
    problems.push(`Missing catalog entry: ${module}`);
    continue;
  }
  if (entry.licenseType !== expected) {
    problems.push(
      `${module}: expected licenseType ${expected}, got ${entry.licenseType}`,
    );
  }
}

for (const [module, entry] of Object.entries(catalog)) {
  if (!entry.licenseType || entry.licenseType === "Apache-2.0") continue;
  if (entry.licenseType.startsWith("GPL")) {
    problems.push(`${module}: unexpected GPL licenseType ${entry.licenseType}`);
  }
}

if (problems.length > 0) {
  console.error("driver-packs.json license audit failed:");
  for (const line of problems) console.error(`  - ${line}`);
  process.exit(1);
}

console.log(`driver-packs.json license audit OK (${Object.keys(catalog).length} packs)`);
