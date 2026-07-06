#!/usr/bin/env node
/** Scale x/w and columns in dashboard layout Java files (fine horizontal grid). */
import fs from "node:fs";
import path from "node:path";

const SCALE = 7;
const TARGET_COLUMNS = 12 * SCALE;

function scaleHorizontal(text) {
  return text
    .replace(/"columns":\s*12/g, `"columns": ${TARGET_COLUMNS}`)
    .replace(/\{"columns":12,/g, `{"columns":${TARGET_COLUMNS},`)
    .replace(/"x":\s*(\d+)/g, (_, n) => {
      const v = Number(n);
      return `"x": ${v === 0 ? 0 : v * SCALE}`;
    })
    .replace(/"w":\s*(\d+)/g, (_, n) => `"w": ${Math.max(1, Number(n) * SCALE)}`);
}

const repoRoot = path.resolve(import.meta.dirname, "..");
for (const rel of [
  "packages/ispf-server/src/main/java/com/ispf/server/dashboard/DashboardLayouts.java",
  "packages/ispf-server/src/main/resources/bootstrap/mini-tec/dashboards",
  "packages/ispf-server/src/main/java/com/ispf/server/bootstrap/TankFarmDashboardLayouts.java",
  "packages/ispf-server/src/main/java/com/ispf/server/bootstrap/LabTrainingBundleLayouts.java",
]) {
  const filePath = path.join(repoRoot, rel);
  fs.writeFileSync(filePath, scaleHorizontal(fs.readFileSync(filePath, "utf8")));
  console.log(`updated ${rel}`);
}
