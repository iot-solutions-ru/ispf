import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { diffSummary } from "../src/diff.mjs";
import { initSolution } from "../src/init-solution.mjs";
import { readJsonFile } from "../src/util.mjs";

test("init-solution writes a singleton hub and its schema", () => {
  const dest = fs.mkdtempSync(path.join(os.tmpdir(), "ispf-init-"));
  fs.rmSync(dest, { recursive: true });
  const result = initSolution(dest, "plant-north", "Plant North");
  const solution = readJsonFile(path.join(dest, "solution.json"));
  const blueprint = readJsonFile(path.join(dest, "blueprints", "plant-north-hub.json"));
  assert.equal(result.schemaName, "app_plant_north");
  assert.equal(solution.schemaName, "app_plant_north");
  assert.equal(blueprint.type, "SINGLETON");
});

test("diff names functions that exist on only one side", () => {
  const summary = diffSummary(
    { functions: [{ name: "hold" }, { name: "release" }] },
    { functions: [{ name: "hold" }] }
  );
  assert.equal(summary.length, 1);
  assert.match(summary[0].note, /release/);
});
