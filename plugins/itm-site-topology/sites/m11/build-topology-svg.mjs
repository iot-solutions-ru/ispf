import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const siteDir = path.dirname(fileURLToPath(import.meta.url));
const diagramPath = path.join(siteDir, "mimic-diagram.json");
const outPath = path.join(siteDir, "topology-svg-config.json");

function runMimicBuild() {
  const res = spawnSync(process.execPath, [path.join(siteDir, "build-mimic-diagram.mjs")], {
    stdio: "inherit",
    cwd: siteDir,
  });
  if (res.status !== 0) process.exit(res.status ?? 1);
}

if (!fs.existsSync(diagramPath)) {
  runMimicBuild();
}

const diagram = JSON.parse(fs.readFileSync(diagramPath, "utf8"));
const symbol = diagram.customSymbols?.find((s) => s.id === "lib-m11-topology");
const topologyElement = diagram.elements?.find((e) => e.id === "m11-topology");
const hitElements = (diagram.elements ?? []).filter((e) => String(e.id).startsWith("hit-"));

if (!symbol || !topologyElement) {
  console.error("mimic-diagram.json missing lib-m11-topology or m11-topology element");
  process.exit(1);
}

const hitAreas = hitElements.map((el) => {
  const nodeName = String(el.id).replace(/^hit-/, "");
  const action = el.actions?.[0] ?? {};
  return {
    nodeName,
    objectPath: action.objectPath,
    id: el.props?.svgHitId ?? `back_${nodeName}`,
    kind: el.props?.hitKind ?? "zone",
    label: el.tooltip ?? nodeName,
    x: el.x,
    y: el.y,
    w: el.props?.width,
    h: el.props?.height,
  };
});

const config = {
  viewBox: symbol.viewBox ?? "0 0 1309 503",
  width: symbol.width ?? 1309,
  height: symbol.height ?? 503,
  backgroundColor: diagram.background ?? "#EEF2F6",
  bindings: topologyElement.bindings ?? {},
  behaviors: symbol.behaviors ?? [],
  hitAreas,
  svgInner: symbol.svg,
};

fs.writeFileSync(outPath, JSON.stringify(config));
console.log(
  `Wrote topology-svg-config.json: ${Object.keys(config.bindings).length} bindings, ${config.behaviors.length} behaviors, ${hitAreas.length} hit areas`
);
