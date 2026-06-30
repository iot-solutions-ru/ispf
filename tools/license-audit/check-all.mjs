#!/usr/bin/env node
import { spawnSync } from "node:child_process";
import path from "node:path";
import { fileURLToPath } from "node:url";

const here = path.dirname(fileURLToPath(import.meta.url));

function run(script) {
  const result = spawnSync(process.execPath, [path.join(here, script)], {
    stdio: "inherit",
  });
  if (result.status !== 0) process.exit(result.status ?? 1);
}

run("check-npm-licenses.mjs");
run("check-driver-packs-json.mjs");
console.log("All license audits passed.");
