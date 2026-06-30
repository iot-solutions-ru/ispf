import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));

export const ROOT = path.resolve(__dirname, "../..");
export const WORK_DIR = path.join(__dirname, ".work");
export const CATALOG_PATH = path.join(WORK_DIR, "catalog.json");
export const CONVERTED_DIR = path.join(WORK_DIR, "svg-raw");
export const STYLIZED_DIR = path.join(WORK_DIR, "svg-stylized");
export const REJECTED_PATH = path.join(WORK_DIR, "rejected.json");
export const PACK_OUT_DIR = path.join(ROOT, "apps/web-console/src/scada/symbols/packs/ispf-pid-v1");

export const WINCC_ZIP =
  process.env.WINCC_ZIP ?? path.join(process.env.USERPROFILE ?? "", "Downloads/SIMATIC WinCC flexible Graphics.zip");
export const TIA_ZIP =
  process.env.TIA_ZIP ?? path.join(process.env.USERPROFILE ?? "", "Downloads/TIA_Portal_Graphics_DVDpart.zip");

/** WinCC SymbolFactory folder names to include (2 Colors outline). */
export const WINCC_INCLUDE_FOLDERS = [
  "ISA Symbols",
  "Tanks",
  "Pumps",
  "Valves",
  "Pipes",
  "Sensors",
  "Flow Meters",
  "Electrical",
  "Blowers",
  "Compressors",
  "Containers",
  "ASHRAE Controls & Equipment",
  "Heating",
  "Process Cooling",
  "Process Heating",
  "Mixers",
  "Heat Exchangers",
  "Filters",
  "Agitators",
];

/** Substrings that exclude a path or folder name. */
export const EXCLUDE_PATTERNS = [
  /3-?d/i,
  /filled/i,
  /flags/i,
  /runtimecontrol/i,
  /pushbutton/i,
  /computer keys/i,
  /\bnature\b/i,
  /\barrows\b/i,
  /\bfood\b/i,
  /\bhmi\b/i,
  /miscellaneous/i,
  /\.gif$/i,
  /\.bmp$/i,
  /\.png$/i,
  /\.jpg$/i,
];

export const PACK_CATEGORIES = [
  "pack-valves",
  "pack-pumps",
  "pack-tanks",
  "pack-pipes",
  "pack-sensors",
  "pack-electrical",
  "pack-isa",
  "pack-misc",
] as const;

export type PackCategory = (typeof PACK_CATEGORIES)[number];

export const PACK_CATEGORY_FILES: Record<PackCategory, string> = {
  "pack-valves": "valves.json",
  "pack-pumps": "pumps.json",
  "pack-tanks": "tanks.json",
  "pack-pipes": "pipes.json",
  "pack-sensors": "sensors.json",
  "pack-electrical": "electrical.json",
  "pack-isa": "isa.json",
  "pack-misc": "misc.json",
};
