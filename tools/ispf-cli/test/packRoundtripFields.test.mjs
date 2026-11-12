import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { packDirectory } from "../src/pack.mjs";
import { unpackBundle } from "../src/unpack.mjs";
import { compactStringify } from "../src/util.mjs";

test("pack inlines @file references that sit inside a script body", () => {
  const sql = "SELECT 'a' AS id";
  const original = {
    version: "1.0.0",
    displayName: "inline-sql",
    schemaName: "app_inline_sql",
    functions: [
      {
        functionName: "ping",
        objectPath: "root.platform.singleton-blueprints.hub",
        version: "1",
        source: {
          type: "script",
          body: JSON.stringify({ steps: [{ type: "selectMany", var: "rows", sql }] }),
        },
      },
    ],
  };
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "ispf-pack-inline-"));
  const repoDir = path.join(tmp, "repo");
  unpackBundle(original, repoDir);
  const repacked = packDirectory(repoDir);
  const body = JSON.parse(repacked.functions[0].source.body);
  assert.equal(body.steps[0].sql, sql);
});

test("pack/unpack keeps distinct SQL for each multi-step function step", () => {
  const sql1 = "SELECT id FROM mes_erp_outbox WHERE status = 'pending' AND length(id) > 0 ORDER BY id";
  const sql2 = "UPDATE mes_erp_outbox SET status = 'simulated', processed_at = NOW() WHERE id = CAST(? AS uuid)";
  const original = {
    version: "1.0.0",
    displayName: "multi-step-sql",
    schemaName: "app_multi_step_sql",
    functions: [
      {
        functionName: "poll",
        objectPath: "root.platform.singleton-blueprints.hub",
        version: "1",
        source: {
          type: "script",
          body: JSON.stringify({
            steps: [
              { type: "selectMany", var: "rows", sql: sql1 },
              { type: "exec", sql: sql2, params: ["${rows[0].id}"] },
              {
                type: "if",
                then: [{ type: "exec", sql: sql1 + " /* then */" }],
                else: [{ type: "exec", sql: sql2 + " /* else */" }],
              },
            ],
          }),
        },
      },
    ],
  };
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "ispf-pack-multi-"));
  const repoDir = path.join(tmp, "repo");
  unpackBundle(original, repoDir);
  const stepFiles = fs.readdirSync(path.join(repoDir, "functions")).filter((n) => n.includes("-step-"));
  assert.equal(stepFiles.length, 4);
  const repacked = packDirectory(repoDir);
  const body = JSON.parse(repacked.functions[0].source.body);
  assert.equal(body.steps[0].sql, sql1);
  assert.equal(body.steps[1].sql, sql2);
  assert.equal(body.steps[2].then[0].sql, sql1 + " /* then */");
  assert.equal(body.steps[2].else[0].sql, sql2 + " /* else */");
});

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
