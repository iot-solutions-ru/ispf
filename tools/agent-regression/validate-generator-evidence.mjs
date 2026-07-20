#!/usr/bin/env node
/**
 * Validate BL-180 live-generator evidence JSON shape.
 * Optional --enforce-soft fails when softBudgetMet is false.
 */
import fs from "node:fs";

const args = process.argv.slice(2);
let resultsPath = "build/agent-regression/live-generator-results.json";
let enforceSoft = false;
for (let i = 0; i < args.length; i++) {
  if (args[i] === "--results" && args[i + 1]) {
    resultsPath = args[++i];
  } else if (args[i] === "--enforce-soft") {
    enforceSoft = true;
  }
}

if (!fs.existsSync(resultsPath)) {
  console.error(`FAIL: missing evidence file ${resultsPath}`);
  process.exit(1);
}

const raw = fs.readFileSync(resultsPath, "utf8");
let data;
try {
  data = JSON.parse(raw);
} catch (e) {
  console.error(`FAIL: invalid JSON in ${resultsPath}: ${e.message}`);
  process.exit(1);
}

const required = ["generatedAt", "source", "softBudgetMs", "softBudgetMet", "functionalOk", "domains"];
for (const key of required) {
  if (!(key in data)) {
    console.error(`FAIL: missing field "${key}" in ${resultsPath}`);
    process.exit(1);
  }
}

if (!Array.isArray(data.domains) || data.domains.length === 0) {
  console.error(`FAIL: domains[] empty in ${resultsPath}`);
  process.exit(1);
}

for (const row of data.domains) {
  for (const key of ["domain", "status", "elapsedMs", "softBudgetMs", "softBudgetMet"]) {
    if (!(key in row)) {
      console.error(`FAIL: domain row missing "${key}"`);
      process.exit(1);
    }
  }
}

console.log(
  `OK: ${resultsPath} domains=${data.domains.length} functionalOk=${data.functionalOk} softBudgetMet=${data.softBudgetMet}`
);
for (const row of data.domains) {
  console.log(
    `  - ${row.domain}: status=${row.status} elapsedMs=${row.elapsedMs} softBudgetMet=${row.softBudgetMet}`
  );
}

if (!data.functionalOk) {
  console.error("FAIL: functionalOk=false");
  process.exit(1);
}

if (enforceSoft && !data.softBudgetMet) {
  console.error("FAIL: softBudgetMet=false (--enforce-soft)");
  process.exit(1);
}

if (!enforceSoft && !data.softBudgetMet) {
  console.warn("WARN: softBudgetMet=false (soft signal; pass without --enforce-soft)");
}
