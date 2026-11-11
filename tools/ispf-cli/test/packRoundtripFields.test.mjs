import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { packDirectory } from "../src/pack.mjs";
import { unpackBundle } from "../src/unpack.mjs";
import { compactStringify } from "../src/util.mjs";

test("pack/unpack preserves formatVersion, analyticsFormulas, workflow tool fields", () => {
  const original = {
    formatVersion: 1,
    version: "1.0.0",
    displayName: "roundtrip-extra",
    schemaName: "app_roundtrip_extra",
    analyticsFormulas: [
      {
        id: "util",
        title: "Utilization",
        expression: "x / y",
      },
    ],
    workflows: [
      {
        path: "root.platform.workflows.tool-wf",
        title: "Tool workflow",
        status: "DRAFT",
        bpmnXml: "<definitions/>",
        inputSchemaJson: "{\"type\":\"object\"}",
        outputSchemaJson: "{\"type\":\"object\"}",
        toolDescription: "Runs the tool",
        sideEffectClass: "READ_ONLY",
        webhookSlug: "tool-wf-hook",
      },
    ],
  };

  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "ispf-pack-rt-"));
  const repoDir = path.join(tmp, "repo");
  unpackBundle(original, repoDir);
  const repacked = packDirectory(repoDir);

  assert.equal(repacked.formatVersion, 1);
  assert.equal(compactStringify(repacked.analyticsFormulas), compactStringify(original.analyticsFormulas));
  assert.equal(repacked.workflows.length, 1);
  const wf = repacked.workflows[0];
  assert.equal(wf.inputSchemaJson, original.workflows[0].inputSchemaJson);
  assert.equal(wf.outputSchemaJson, original.workflows[0].outputSchemaJson);
  assert.equal(wf.toolDescription, original.workflows[0].toolDescription);
  assert.equal(wf.sideEffectClass, original.workflows[0].sideEffectClass);
  assert.equal(wf.webhookSlug, original.workflows[0].webhookSlug);
});
