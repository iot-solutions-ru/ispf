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

test("fork rewrites the operator app id, hub namespace, and ui-pack", () => {
  const src = fs.mkdtempSync(path.join(os.tmpdir(), "ispf-fork-src-"));
  fs.cpSync(path.join(repoRoot(), "examples/demo-app"), src, {
    recursive: true,
    filter: (source) => path.basename(source) !== "bundle.json",
  });
  fs.mkdirSync(path.join(src, "ui-pack"), { recursive: true });
  fs.writeFileSync(
    path.join(src, "ui-pack", "ui-pack.json"),
    JSON.stringify({ appId: "demo", basePath: "/apps/demo/" }),
    "utf8",
  );
  const dest = fs.mkdtempSync(path.join(os.tmpdir(), "ispf-fork-"));
  fs.rmSync(dest, { recursive: true });
  const result = forkSolution(src, dest, "plant-north");
  const solution = readJsonFile(path.join(dest, "solution.json"));
  const uiPack = readJsonFile(path.join(dest, "ui-pack", "ui-pack.json"));
  const hub = readJsonFile(path.join(dest, "blueprints", "plant-north-hub-v1.json"));
  const fn = readJsonFile(path.join(dest, "functions", "demo_ping.json"));
  assert.equal(result.forkedFrom.appId, "demo");
  assert.equal(result.forkedFrom.schemaName, "app_demo");
  assert.equal(solution.operatorUi.appId, "plant-north");
  assert.equal(solution.operatorManifest.appId, "plant-north");
  assert.equal(uiPack.appId, "plant-north");
  assert.equal(uiPack.basePath, "/apps/plant-north/");
  assert.equal(hub.name, "plant-north-hub-v1");
  assert.equal(hub.type, "SINGLETON");
  assert.equal(fn.objectPath, "root.platform.singleton-blueprints.plant-north-hub-v1");
  assert.equal(fs.existsSync(path.join(dest, "blueprints", "demo-app-hub-v1.json")), false);
});

test("fork of an init-solution box rewrites domain, ui pack, and asset urls", () => {
  const src = fs.mkdtempSync(path.join(os.tmpdir(), "ispf-fork-init-"));
  fs.mkdirSync(path.join(src, "blueprints"), { recursive: true });
  fs.mkdirSync(path.join(src, "ui-pack"), { recursive: true });
  fs.writeFileSync(path.join(src, "solution.json"), JSON.stringify({
    version: "1.0.0",
    schemaName: "app_box",
    metadata: { domain: "box" },
    operatorUi: { externalSpaUrl: "https://ispf.example/apps/box/index.html" },
  }), "utf8");
  fs.writeFileSync(path.join(src, "blueprints", "box-hub.json"), JSON.stringify({
    name: "box-hub",
    type: "SINGLETON",
  }), "utf8");
  fs.writeFileSync(path.join(src, "ui-pack", "ui-pack.json"), JSON.stringify({
    id: "box",
    appId: "box",
    backendAppId: "box",
    packId: "box-ui",
    basePath: "/apps/box/",
  }), "utf8");
  fs.writeFileSync(path.join(src, "ui-pack", "index.html"), '<script src="/apps/box/assets/index.js"></script>\n', "utf8");
  const dest = fs.mkdtempSync(path.join(os.tmpdir(), "ispf-fork-mill-"));
  fs.rmSync(dest, { recursive: true });
  const result = forkSolution(src, dest, "box-north");
  const solution = readJsonFile(path.join(dest, "solution.json"));
  const uiPack = readJsonFile(path.join(dest, "ui-pack", "ui-pack.json"));
  const html = fs.readFileSync(path.join(dest, "ui-pack", "index.html"), "utf8");
  assert.equal(result.forkedFrom.appId, "box");
  assert.equal(solution.metadata.forkedFrom.appId, "box");
  assert.equal(solution.metadata.domain, "box-north");
  assert.equal(solution.operatorUi.externalSpaUrl, "https://ispf.example/apps/box-north/index.html");
  assert.equal(uiPack.id, "box-north");
  assert.equal(uiPack.backendAppId, "box-north");
  assert.equal(uiPack.packId, "box-north-ui");
  assert.equal(uiPack.basePath, "/apps/box-north/");
  assert.match(html, /\/apps\/box-north\/assets\/index.js/);
  assert.equal(fs.existsSync(path.join(dest, "blueprints", "box-north-hub.json")), true);
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
