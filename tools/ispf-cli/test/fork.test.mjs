import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { forkSolution } from "../src/fork.mjs";
import { readJsonFile, repoRoot } from "../src/util.mjs";

test("fork copies a bundle and records forkedFrom", () => {
  const src = path.join(repoRoot(), "examples/demo-app/bundle.json");
  const dest = fs.mkdtempSync(path.join(os.tmpdir(), "ispf-fork-"));
  fs.rmSync(dest, { recursive: true });
  const result = forkSolution(src, dest, "plant-north");
  const solution = readJsonFile(path.join(dest, "solution.json"));
  assert.equal(result.schemaName, "app_plant_north");
  assert.equal(solution.schemaName, "app_plant_north");
  assert.equal(solution.metadata.forkedFrom.schemaName, "app_demo");
  assert.ok(solution.metadata.forkedFrom.sha256);
});

test("fork refuses an existing directory", () => {
  const src = path.join(repoRoot(), "examples/demo-app/bundle.json");
  const dest = fs.mkdtempSync(path.join(os.tmpdir(), "ispf-fork-"));
  fs.writeFileSync(path.join(dest, "keep.txt"), "stay");
  assert.throws(() => forkSolution(src, dest, "plant-north"), /Refusing to overwrite/);
  assert.equal(fs.readFileSync(path.join(dest, "keep.txt"), "utf8"), "stay");
});

test("fork of a solution directory does not keep the upstream bundle", () => {
  const src = path.join(repoRoot(), "examples/demo-app");
  const dest = fs.mkdtempSync(path.join(os.tmpdir(), "ispf-fork-"));
  fs.rmSync(dest, { recursive: true });
  forkSolution(src, dest, "plant-north");
  assert.equal(fs.existsSync(path.join(dest, "bundle.json")), false);
  assert.equal(fs.existsSync(path.join(dest, ".git")), false);
  const solution = readJsonFile(path.join(dest, "solution.json"));
  assert.equal(solution.schemaName, "app_plant_north");
  assert.equal(solution.metadata.forkedFrom.schemaName, "app_demo");
});
