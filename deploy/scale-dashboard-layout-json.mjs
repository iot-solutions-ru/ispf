#!/usr/bin/env node
/** Scale dashboard layout JSON embedded in Java sources (rowHeight → 8, y/h scaled). */
import fs from "node:fs";
import path from "node:path";

const TARGET_ROW = 8;
const MARGIN_OLD = 12;
const MARGIN_NEW = 4;

function scaleUnits(value, fromRowHeight) {
  const scale = (fromRowHeight + MARGIN_OLD) / (TARGET_ROW + MARGIN_NEW);
  return Math.max(1, Math.round(value * scale));
}

function scaleWidget(widget, fromRowHeight) {
  if (typeof widget.y === "number") {
    widget.y = widget.y === 0 ? 0 : scaleUnits(widget.y, fromRowHeight);
  }
  if (typeof widget.h === "number") widget.h = scaleUnits(widget.h, fromRowHeight);
  return widget;
}

function scaleLayout(layout) {
  const fromRowHeight = typeof layout.rowHeight === "number" ? layout.rowHeight : 72;
  if (fromRowHeight <= TARGET_ROW) return null;
  layout.rowHeight = TARGET_ROW;
  if (Array.isArray(layout.widgets)) {
    layout.widgets = layout.widgets.map((w) => scaleWidget({ ...w }, fromRowHeight));
  }
  return layout;
}

function processJavaFile(filePath) {
  let text = fs.readFileSync(filePath, "utf8");
  let count = 0;
  text = text.replace(/(\{"columns":\d+,"rowHeight":)(\d+)(,[\s\S]*?\]\})/g, (full, prefix, rowHeightStr, rest) => {
    try {
      const layout = JSON.parse(`${prefix}${rowHeightStr}${rest}`);
      const scaled = scaleLayout(layout);
      if (!scaled) return full;
      count += 1;
      return JSON.stringify(scaled);
    } catch {
      return full;
    }
  });
  text = text.replace(/"rowHeight":\s*(\d+)/g, (match, rowHeightStr) => {
    const fromRowHeight = Number(rowHeightStr);
    if (fromRowHeight <= TARGET_ROW) return match;
    count += 1;
    return `"rowHeight": ${TARGET_ROW}`;
  });
  if (count > 0) {
    fs.writeFileSync(filePath, text);
  }
  console.log(`${filePath}: ${count} layout block(s) updated`);
}

const repoRoot = path.resolve(import.meta.dirname, "..");
const targets = [
  "packages/ispf-server/src/main/java/com/ispf/server/dashboard/DashboardLayouts.java",
  "packages/ispf-server/src/main/resources/bootstrap/mini-tec/dashboards",
  "packages/ispf-server/src/main/java/com/ispf/server/bootstrap/TankFarmDashboardLayouts.java",
  "packages/ispf-server/src/main/java/com/ispf/server/bootstrap/LabTrainingBundleLayouts.java",
];

for (const rel of targets) {
  processJavaFile(path.join(repoRoot, rel));
}
