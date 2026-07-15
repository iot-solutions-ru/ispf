/**
 * Expand topology hitAreas with link targets from existing stroke behaviors.
 * Does not require the original Illustrator SVG source.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const siteDir = path.dirname(fileURLToPath(import.meta.url));
const configPath = path.join(siteDir, "topology-svg-config.json");
const metaPath = path.join(siteDir, "topology-meta.json");

const config = JSON.parse(fs.readFileSync(configPath, "utf8"));
const bindings = config.bindings ?? {};
const behaviors = config.behaviors ?? [];

const hitAreas = [];
const seen = new Set();

for (const behavior of behaviors) {
  const target = String(behavior.target ?? "").replace(/^#/, "");
  if (!target || seen.has(target)) continue;
  const bind = behavior.bind;
  const objectPath = bindings[bind]?.objectPath;
  if (!objectPath) continue;
  seen.add(target);

  if (behavior.type === "fill" && target.startsWith("back_")) {
    const nodeName = target.slice("back_".length);
    hitAreas.push({
      nodeName,
      objectPath,
      id: target,
      kind: "zone",
      label: nodeName,
    });
  } else if (behavior.type === "stroke") {
    hitAreas.push({
      nodeName: bind,
      objectPath,
      id: target,
      kind: "link",
      label: String(bind).replace(/^link_/, "").replaceAll("_", " "),
    });
  }
}

config.hitAreas = hitAreas;
fs.writeFileSync(configPath, JSON.stringify(config));
if (fs.existsSync(metaPath)) {
  const meta = JSON.parse(fs.readFileSync(metaPath, "utf8"));
  meta.hitAreas = hitAreas.length;
  meta.linkHits = hitAreas.filter((a) => a.kind === "link").length;
  meta.zoneHits = hitAreas.filter((a) => a.kind === "zone").length;
  meta.generatedAt = new Date().toISOString();
  fs.writeFileSync(metaPath, JSON.stringify(meta, null, 2));
}

console.log(
  `Updated hitAreas: ${hitAreas.length} (zones=${hitAreas.filter((a) => a.kind === "zone").length}, links=${hitAreas.filter((a) => a.kind === "link").length})`
);
