#!/usr/bin/env node
/**
 * Repo hygiene gate (code-analysis F-09): keep binaries and oversized files out of git history.
 *
 * Rules (over `git ls-files`, i.e. tracked files only):
 *   1. Files with a binary extension must match an allow-list entry.
 *   2. Any tracked file larger than MAX_FILE_BYTES must match the size allow-list.
 *   3. All ui-pack zips together must stay under UI_PACK_BUDGET_BYTES (they are the only
 *      binaries we ship from this repo; sources live in the SPA repos, see ADR-0054).
 *
 * Usage: node tools/repo-hygiene/check-tracked-binaries.mjs
 * Exit code 1 on any violation; prints every violation, not just the first.
 */
import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");

const MAX_FILE_BYTES = 2 * 1024 * 1024; // 2 MiB
const UI_PACK_BUDGET_BYTES = 8 * 1024 * 1024; // 8 MiB for all catalog ui-pack zips together

const BINARY_EXT = /\.(zip|jar|war|ear|gz|tgz|bz2|xz|7z|rar|exe|dll|so|dylib|bin|class|pdf|docx?|xlsx?|pptx?|mp4|mov|avi|wmf|emf|psd|iso|img|db|sqlite)$/i;

/** Binary files we knowingly keep. Keep this list short and explain each entry. */
const BINARY_ALLOW = [
  // Only shipped binaries: hosted SPA packs sold through the local marketplace (ADR-0054).
  /^examples\/marketplace-catalog\/[^/]+\/[^/]+\.zip$/,
  // Gradle wrapper — standard.
  /^gradle\/wrapper\/gradle-wrapper\.jar$/,
  // JAI 1.1.3 is not on Maven Central; vendored for YARG report rendering.
  /^third-party\/maven-repo\/.+\.jar$/,
  // YARG report fixtures — real workbooks are the test input.
  /^packages\/ispf-server\/src\/test\/resources\/yarg\/[^/]+\.xlsx?$/,
];

/** Text files legitimately above MAX_FILE_BYTES. */
const SIZE_ALLOW = [
  // Generated AI context pack (tools/ai-pack/build.py), tracked so the agent works offline.
  /(^|\/)ispf-context-pack\.json$/,
  /^packages\/ispf-ai-agent\/src\/main\/resources\/ai\/context-pack\.json$/,
  // Shipped ui-pack zips are covered by the budget rule instead.
  /^examples\/marketplace-catalog\/[^/]+\/[^/]+\.zip$/,
];

const tracked = execFileSync("git", ["ls-files", "-z"], { cwd: root, maxBuffer: 64 * 1024 * 1024 })
  .toString("utf8")
  .split("\0")
  .filter(Boolean);

const violations = [];
let uiPackTotal = 0;
const uiPackZips = [];

for (const rel of tracked) {
  const abs = path.join(root, rel);
  let size;
  try {
    size = fs.statSync(abs).size;
  } catch {
    continue; // deleted in working tree but still in index — nothing to measure
  }

  if (BINARY_EXT.test(rel) && !BINARY_ALLOW.some((re) => re.test(rel))) {
    violations.push(`binary not allow-listed: ${rel} (${fmt(size)}) — publish it as a CI/Release artifact instead`);
  }
  if (size > MAX_FILE_BYTES && !SIZE_ALLOW.some((re) => re.test(rel))) {
    violations.push(`file exceeds ${fmt(MAX_FILE_BYTES)}: ${rel} (${fmt(size)})`);
  }
  if (/^examples\/marketplace-catalog\/[^/]+\/[^/]+\.zip$/.test(rel)) {
    uiPackTotal += size;
    uiPackZips.push(`${rel} (${fmt(size)})`);
  }
  // Mirrors of catalog zips outside the catalog are exactly what we de-duplicated; block regressions.
  if (/^examples\/(?!marketplace-catalog\/)[^/]+\/[^/]+\.zip$/.test(rel)) {
    violations.push(`ui-pack zip outside examples/marketplace-catalog: ${rel} — the catalog folder is the single copy`);
  }
}

if (uiPackTotal > UI_PACK_BUDGET_BYTES) {
  violations.push(
    `ui-pack zips total ${fmt(uiPackTotal)} > budget ${fmt(UI_PACK_BUDGET_BYTES)}:\n    ${uiPackZips.join("\n    ")}`,
  );
}

if (violations.length) {
  console.error(`check-tracked-binaries: ${violations.length} violation(s)`);
  for (const v of violations) console.error(`  - ${v}`);
  process.exit(1);
}
console.log(
  `check-tracked-binaries: OK (${tracked.length} tracked files, ui-pack zips ${fmt(uiPackTotal)} / ${fmt(UI_PACK_BUDGET_BYTES)})`,
);

function fmt(bytes) {
  if (bytes >= 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MiB`;
  if (bytes >= 1024) return `${Math.round(bytes / 1024)} KiB`;
  return `${bytes} B`;
}
