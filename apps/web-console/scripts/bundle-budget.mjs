#!/usr/bin/env node
/**
 * Enforce JS bundle size budget after `npm run build` (S21-05 / BL-95).
 */
import { readFileSync, readdirSync, statSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const distAssets = join(root, "dist", "assets");
const budget = JSON.parse(readFileSync(join(root, "scripts", "bundle-budget.json"), "utf8"));

function kb(size) {
  return Math.round(size / 1024);
}

const files = readdirSync(distAssets).filter((f) => f.endsWith(".js"));
let total = 0;
let entryKb = 0;
const failures = [];

for (const file of files) {
  const size = statSync(join(distAssets, file)).size;
  total += size;
  if (file.startsWith("index-")) {
    entryKb = Math.max(entryKb, kb(size));
  }
  for (const [prefix, maxKb] of Object.entries(budget.maxChunkKb ?? {})) {
    if (file.includes(prefix) && kb(size) > maxKb) {
      failures.push(`${file}: ${kb(size)} KB > ${maxKb} KB (${prefix})`);
    }
  }
}

const totalKb = kb(total);
console.log(`Bundle: ${files.length} JS files, total ${totalKb} KB, entry ${entryKb} KB`);

if (totalKb > budget.maxTotalJsKb) {
  failures.push(`total JS ${totalKb} KB > ${budget.maxTotalJsKb} KB`);
}
if (entryKb > budget.maxEntryJsKb) {
  failures.push(`entry JS ${entryKb} KB > ${budget.maxEntryJsKb} KB`);
}

if (failures.length > 0) {
  console.error("Bundle budget FAILED:");
  for (const line of failures) console.error(`  - ${line}`);
  process.exit(1);
}
console.log("Bundle budget OK");
