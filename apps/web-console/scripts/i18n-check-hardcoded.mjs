#!/usr/bin/env node
/**
 * BL-132 — fail CI when new hardcoded user-facing strings appear in TSX.
 * Baseline shrinks over time; run with --update-baseline after intentional fixes.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "../src");
const baselinePath = path.resolve(__dirname, "i18n-hardcoded-baseline.json");
const updateBaseline = process.argv.includes("--update-baseline");

const SKIP_DIRS = new Set(["locales", "test", "__mocks__"]);
const SKIP_SUFFIX = [".test.ts", ".test.tsx", ".d.ts"];

/** JSX text nodes: >Visible text< */
const JSX_TEXT = />([^<>{}[\n]+?)</g;

/** Common UI string props without t() */
const UI_ATTR =
  /(?:placeholder|title|aria-label|alt)=["']([^"'{][^"']{2,})["']/g;

/** Cyrillic anywhere on line (strong signal) */
const CYRILLIC = /[\u0400-\u04FF]/;

function shouldScanFile(relPath) {
  if (!relPath.endsWith(".tsx")) {
    return false;
  }
  if (SKIP_SUFFIX.some((suffix) => relPath.endsWith(suffix))) {
    return false;
  }
  const parts = relPath.split(path.sep);
  return !parts.some((part) => SKIP_DIRS.has(part));
}

function walk(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      if (SKIP_DIRS.has(entry.name)) {
        continue;
      }
      walk(full, files);
    } else {
      files.push(full);
    }
  }
  return files;
}

function isIgnoredLine(line) {
  const trimmed = line.trim();
  if (!trimmed || trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("{/*")) {
    return true;
  }
  if (line.includes("t(") || line.includes("Trans") || line.includes("i18nKey")) {
    return true;
  }
  if (line.includes("className") && !CYRILLIC.test(line)) {
    return true;
  }
  if (line.includes("console.") || line.includes("throw new Error")) {
    return true;
  }
  // TypeScript generic types (e.g. Map<string, …>) are not user-facing copy.
  if (/\bMap\s*</.test(line) && !line.includes("</")) {
    return true;
  }
  return false;
}

function looksLikeUiText(text) {
  const value = text.trim();
  if (value.length < 3) {
    return false;
  }
  if (/^[\d\s.,:;/%+-]+$/.test(value)) {
    return false;
  }
  if (/^(true|false|null|undefined)$/i.test(value)) {
    return false;
  }
  if (/^[a-z][a-zA-Z0-9_-]*$/.test(value) && !CYRILLIC.test(value)) {
    return false;
  }
  if (!/[A-Za-z\u0400-\u04FF]/.test(value)) {
    return false;
  }
  return true;
}

function scanFile(absPath) {
  const rel = path.relative(root, absPath);
  if (!shouldScanFile(rel)) {
    return [];
  }
  const lines = fs.readFileSync(absPath, "utf8").split("\n");
  const hits = [];

  lines.forEach((line, index) => {
    if (isIgnoredLine(line)) {
      return;
    }

    if (CYRILLIC.test(line)) {
      hits.push({
        file: rel.replace(/\\/g, "/"),
        line: index + 1,
        kind: "cyrillic",
        text: line.trim().slice(0, 120),
      });
      return;
    }

    for (const match of line.matchAll(JSX_TEXT)) {
      const text = match[1]?.trim() ?? "";
      if (looksLikeUiText(text)) {
        hits.push({
          file: rel.replace(/\\/g, "/"),
          line: index + 1,
          kind: "jsx-text",
          text: text.slice(0, 120),
        });
      }
    }

    for (const match of line.matchAll(UI_ATTR)) {
      const text = match[1]?.trim() ?? "";
      if (looksLikeUiText(text)) {
        hits.push({
          file: rel.replace(/\\/g, "/"),
          line: index + 1,
          kind: "ui-attr",
          text: text.slice(0, 120),
        });
      }
    }
  });

  return hits;
}

function hitKey(hit) {
  return `${hit.file}:${hit.line}:${hit.kind}:${hit.text}`;
}

const allHits = walk(root)
  .flatMap(scanFile)
  .sort((a, b) => hitKey(a).localeCompare(hitKey(b)));

if (updateBaseline) {
  fs.writeFileSync(baselinePath, `${JSON.stringify(allHits, null, 2)}\n`, "utf8");
  console.log(`i18n hardcoded baseline updated (${allHits.length} entries)`);
  process.exit(0);
}

if (!fs.existsSync(baselinePath)) {
  console.error(`Missing baseline ${path.relative(process.cwd(), baselinePath)} — run with --update-baseline`);
  process.exit(1);
}

const baseline = JSON.parse(fs.readFileSync(baselinePath, "utf8"));
const baselineKeys = new Set(baseline.map(hitKey));
const currentKeys = new Set(allHits.map(hitKey));

const newHits = allHits.filter((hit) => !baselineKeys.has(hitKey(hit)));
const removed = baseline.filter((hit) => !currentKeys.has(hitKey(hit)));

if (newHits.length) {
  console.error(`i18n hardcoded gate: ${newHits.length} new violation(s)`);
  for (const hit of newHits.slice(0, 20)) {
    console.error(`  ${hit.file}:${hit.line} [${hit.kind}] ${hit.text}`);
  }
  if (newHits.length > 20) {
    console.error(`  … and ${newHits.length - 20} more`);
  }
  process.exit(1);
}

if (removed.length) {
  console.warn(
    `i18n hardcoded: ${removed.length} baseline entries removed (consider --update-baseline to shrink debt)`,
  );
}

console.log(`i18n hardcoded gate OK (${baseline.length} baseline entries)`);
