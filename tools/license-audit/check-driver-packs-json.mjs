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
  "ispf-driver-dnp3": "LicenseRef-StepFunc-NonCommercial",
  "ispf-driver-ipmi": "GPL-3.0-or-later",
  "ispf-driver-sip": "LicenseRef-NIST-PublicDomain",
  "ispf-driver-bacnet": "GPL-3.0-only",
  "ispf-driver-dlms": "GPL-2.0-only",
  "ispf-driver-iec104": "GPL-3.0-or-later",
  "ispf-driver-iec104-server": "GPL-3.0-or-later",
  "ispf-driver-mbus": "MPL-2.0",
  "ispf-driver-radius": "LGPL-3.0-or-later",
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
  if (entry.licenseType.startsWith("GPL") && !module.includes("bacnet") &&
      !module.includes("dlms") && !module.includes("iec104") && !module.includes("ipmi")) {
    problems.push(`${module}: unexpected GPL licenseType ${entry.licenseType}`);
  }
}

if (problems.length > 0) {
  console.error("driver-packs.json license audit failed:");
  for (const line of problems) console.error(`  - ${line}`);
  process.exit(1);
}

console.log(`driver-packs.json license audit OK (${Object.keys(catalog).length} packs)`);
