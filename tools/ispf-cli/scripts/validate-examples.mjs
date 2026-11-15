#!/usr/bin/env node
/**
 * Offline `ispf validate --local --strict` for every tracked example bundle.
 * Remote validate needs a running server and stays out of this gate.
 */
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import { runValidate } from "../src/validate.mjs";

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../../..");

function trackedBundles() {
  const listed = execFileSync("git", ["ls-files", "-z", "--", "examples/**/bundle.json"], {
    cwd: root,
    encoding: "utf8",
  });
  return listed.split("\0").filter(Boolean).sort();
}

const failures = [];
const files = trackedBundles();
if (files.length === 0) {
  console.error("FAIL: no tracked examples/**/bundle.json found");
  process.exit(1);
}
for (const rel of files) {
  const abs = path.join(root, rel);
  const results = await runValidate(abs, { localOnly: true, strict: true });
  if (results.local?.status === "OK") {
    console.log(`OK ${rel}`);
    continue;
  }
  const errors = results.local?.errors ?? ["no local result"];
  failures.push({ rel, errors });
  console.error(`FAIL ${rel}`);
  for (const error of errors) {
    console.error(`  ${error}`);
  }
}

console.log(`${files.length - failures.length}/${files.length} bundles passed local --strict`);
if (failures.length > 0) {
  process.exit(1);
}
