#!/usr/bin/env node
/**
 * Rewrite agent-regression scenarios for human UI reproducibility (BL-178).
 * Run: node tools/agent-regression/apply-human-ui-rewrites.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const scenariosDir = path.join(__dirname, "scenarios");
const rewritesPath = path.join(__dirname, "human-ui-rewrites.json");

const rewrites = JSON.parse(fs.readFileSync(rewritesPath, "utf8"));
let updated = 0;
let missing = 0;

for (const [id, patch] of Object.entries(rewrites.scenarios)) {
  const file = path.join(scenariosDir, `${id}.json`);
  if (!fs.existsSync(file)) {
    console.warn("WARN: missing scenario file for", id);
    missing += 1;
    continue;
  }
  const current = JSON.parse(fs.readFileSync(file, "utf8"));
  const next = {
    ...current,
    ...patch,
    acceptance: {
      ...(current.acceptance ?? {}),
      ...(patch.acceptance ?? {}),
    },
  };
  if (patch.bundle) {
    next.bundle = patch.bundle;
  }
  // Keep stable ordering for readability
  const ordered = {
    id: next.id,
    version: next.version ?? "1",
    domain: next.domain,
    ...(next.lane ? { lane: next.lane } : {}),
    ...(next.kind ? { kind: next.kind } : {}),
    title: next.title,
    ...(next.description ? { description: next.description } : {}),
    prompt: next.prompt,
    ...(next.uiJourney ? { uiJourney: next.uiJourney } : {}),
    ...(next.humanSteps ? { humanSteps: next.humanSteps } : {}),
    ...(next.playbook ? { playbook: next.playbook } : {}),
    ...(next.bundle ? { bundle: next.bundle } : {}),
    ...(next.acceptance && Object.keys(next.acceptance).length
      ? { acceptance: next.acceptance }
      : {}),
  };
  fs.writeFileSync(file, `${JSON.stringify(ordered, null, 2)}\n`, "utf8");
  updated += 1;
}

console.log(`Updated ${updated} scenarios; missing ${missing}`);
