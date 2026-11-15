import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { fileURLToPath } from "node:url";
import { readDeployInput } from "../src/deploy.mjs";
import { assertHonestDeployStatus } from "../src/honestDeploy.mjs";

const labBundle = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  "../../../examples/lab-training/bundle.json"
);

test("readDeployInput reads a bundle file and rejects a directory without solution.json", () => {
  const manifest = readDeployInput(labBundle);
  assert.equal(manifest.displayName, "Lab Training");
  assert.equal(manifest.schemaName, "app_lab_training");
  const empty = fs.mkdtempSync(path.join(os.tmpdir(), "ispf-deploy-"));
  try {
    assert.throws(() => readDeployInput(empty), /solution\.json/);
    assert.throws(() => readDeployInput(path.join(empty, "missing.json")), /Path not found/);
  } finally {
    fs.rmSync(empty, { recursive: true, force: true });
  }
});

test("assertHonestDeployStatus accepts OK", () => {
  assert.doesNotThrow(() => assertHonestDeployStatus({ status: "OK", version: "1.0.0" }));
  assert.doesNotThrow(() => assertHonestDeployStatus({ version: "1.0.0" }));
});

test("assertHonestDeployStatus rejects PARTIAL and FAILED", () => {
  assert.throws(
    () => assertHonestDeployStatus({ status: "PARTIAL", errors: ["applicationSync: boom"] }),
    (err) => err.message === "deploy PARTIAL"
  );
  assert.throws(
    () => assertHonestDeployStatus({ status: "FAILED", errors: ["register: boom"] }),
    (err) => err.message === "deploy FAILED"
  );
});
