import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { packDirectory } from "../src/pack.mjs";
import { unpackBundle } from "../src/unpack.mjs";
import { compactStringify, readJsonFile, repoRoot } from "../src/util.mjs";

const demoBundle = path.join(repoRoot(), "examples/demo-app/bundle.json");

test("demo-app bundle round-trips pack/unpack", () => {
  const original = readJsonFile(demoBundle);
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "ispf-cli-"));
  const repoDir = path.join(tmp, "repo");
  unpackBundle(original, repoDir);
  const repacked = packDirectory(repoDir);
  assert.equal(compactStringify(repacked.migrations), compactStringify(original.migrations));
  const byReportId = (arr) => [...arr].sort((a, b) => a.reportId.localeCompare(b.reportId));
  assert.equal(compactStringify(byReportId(repacked.reports)), compactStringify(byReportId(original.reports)));
  assert.equal(repacked.version, original.version);
  assert.equal(repacked.schemaName, original.schemaName);
  const normDash = (d) => ({
    ...d,
    layout: JSON.parse(d.layoutJson),
    layoutJson: undefined,
  });
  assert.deepEqual(repacked.dashboards.map(normDash), original.dashboards.map(normDash));
  assert.equal(compactStringify(repacked.operatorUi), compactStringify(original.operatorUi));
});
