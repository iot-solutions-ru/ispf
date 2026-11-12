import assert from "node:assert/strict";
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { packDirectory } from "../src/pack.mjs";
import { unpackBundle } from "../src/unpack.mjs";
import { compactStringify, readJsonFile, repoRoot } from "../src/util.mjs";

const demoBundle = path.join(repoRoot(), "examples/demo-app/bundle.json");

test("unpack keeps migration order without V prefixes", () => {
  const manifest = {
    version: "1.0.0",
    schemaName: "app_order",
    migrations: [
      { id: "z_init", sql: "create table z(id int)" },
      { id: "a_alter", sql: "alter table z add note varchar(8)" },
    ],
  };
  const repoDir = fs.mkdtempSync(path.join(os.tmpdir(), "ispf-order-"));
  unpackBundle(manifest, repoDir);
  assert.equal(fs.existsSync(path.join(repoDir, "sql", "z_init.sql")), true);
  assert.equal(fs.existsSync(path.join(repoDir, "sql", "V1__z_init.sql")), false);
  const packed = packDirectory(repoDir);
  assert.deepEqual(packed.migrations.map((migration) => migration.id), ["z_init", "a_alter"]);
});

test("demo-app bundle round-trips pack/unpack", () => {
  const original = readJsonFile(demoBundle);
  const tmp = fs.mkdtempSync(path.join(os.tmpdir(), "ispf-cli-"));
  const repoDir = path.join(tmp, "repo");
  unpackBundle(original, repoDir);
  const repacked = packDirectory(repoDir);
  assert.equal(compactStringify(repacked.migrations), compactStringify(original.migrations));
  const sqlDir = path.join(repoDir, "sql");
  if (fs.existsSync(sqlDir)) {
    const sqlFiles = fs.readdirSync(sqlDir).filter((file) => file.endsWith(".sql"));
    assert.ok(sqlFiles.length > 0);
    assert.ok(sqlFiles.every((file) => !/^V\d+__/i.test(file)));
    assert.ok(fs.existsSync(path.join(sqlDir, "order.json")));
  }
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
