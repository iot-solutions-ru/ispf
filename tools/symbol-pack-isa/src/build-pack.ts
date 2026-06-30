import fs from "node:fs";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { ALL_ISA_SYMBOLS } from "./symbols.js";
import { PACK_CATEGORY_FILES, type IsaSymbolDef, type PackCategory, type PackSymbolRecord } from "./types.js";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, "../../..");
const PACK_DIR = path.join(ROOT, "apps/web-console/src/scada/symbols/packs/ispf-pid-v1");
const PACK_ID = "ispf-pid-v1";

function toRecord(def: IsaSymbolDef): PackSymbolRecord {
  return {
    id: `pack.ispf-pid.${def.slug}`,
    category: def.category,
    nameEn: def.nameEn,
    nameRu: def.nameRu,
    defaultWidth: def.defaultWidth ?? 64,
    defaultHeight: def.defaultHeight ?? 64,
    viewBox: def.viewBox ?? "0 0 64 64",
    svg: def.svg,
    ports: def.ports,
    tags: def.tags ?? [],
  };
}

export function buildPackRecords(): PackSymbolRecord[] {
  return ALL_ISA_SYMBOLS.map(toRecord);
}

function writePack(): void {
  const records = buildPackRecords();
  const ids = new Set<string>();
  for (const rec of records) {
    if (ids.has(rec.id)) throw new Error(`Duplicate symbol id: ${rec.id}`);
    ids.add(rec.id);
  }

  fs.mkdirSync(PACK_DIR, { recursive: true });

  const byCategory = new Map<PackCategory, PackSymbolRecord[]>();
  for (const cat of Object.keys(PACK_CATEGORY_FILES) as PackCategory[]) {
    byCategory.set(cat, []);
  }
  for (const rec of records) {
    byCategory.get(rec.category)!.push(rec);
  }

  const categories: { id: PackCategory; file: string; count: number }[] = [];
  for (const [cat, file] of Object.entries(PACK_CATEGORY_FILES) as [PackCategory, string][]) {
    const list = byCategory.get(cat)!;
    list.sort((a, b) => a.nameEn.localeCompare(b.nameEn));
    fs.writeFileSync(path.join(PACK_DIR, file), JSON.stringify(list) + "\n", "utf8");
    categories.push({ id: cat, file, count: list.length });
  }

  const manifest = {
    version: 2,
    id: PACK_ID,
    license: "Apache-2.0",
    authorship: "Original ISA/ISO functional diagrams — ISPF Core Contributors (2026). Not traced from vendor WMF/SVG.",
    legalNotice: "Functional P&ID shapes per ISA-5.1 / ISO 14617 conventions. See LICENSE.md.",
    generatedAt: new Date().toISOString(),
    generator: "tools/symbol-pack-isa",
    totalSymbols: records.length,
    categories,
  };

  fs.writeFileSync(path.join(PACK_DIR, "manifest.json"), JSON.stringify(manifest, null, 2) + "\n", "utf8");

  console.log(`Wrote ${records.length} symbols to ${PACK_DIR}`);
  for (const c of categories) {
    console.log(`  ${c.id}: ${c.count}`);
  }
}

const isMain =
  process.argv[1] != null &&
  import.meta.url === pathToFileURL(path.resolve(process.argv[1])).href;

if (isMain) {
  writePack();
}
