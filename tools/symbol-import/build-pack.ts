/**
 * Full pipeline: catalog → convert → stylize → pack JSON chunks.
 */
import fs from "node:fs";
import path from "node:path";
import {
  CATALOG_PATH,
  PACK_CATEGORY_FILES,
  PACK_OUT_DIR,
  STYLIZED_DIR,
  type PackCategory,
} from "./config.js";
import { convertCatalog } from "./convert-wmf.js";
import { extractCatalog } from "./extract-catalog.js";
import { stylizeAll } from "./stylize-svg.js";
import type { CatalogEntry } from "./utils.js";
import { nameRuFromEn } from "./utils.js";

export interface PackSymbol {
  id: string;
  category: PackCategory;
  nameEn: string;
  nameRu: string;
  defaultWidth: number;
  defaultHeight: number;
  viewBox: string;
  svg: string;
  ports: { id: string; x: number; y: number }[];
  tags: string[];
}

export function buildPackFromStylized(): { symbols: PackSymbol[]; rejected: string[] } {
  if (!fs.existsSync(CATALOG_PATH)) {
    throw new Error(`Missing catalog at ${CATALOG_PATH}`);
  }
  const catalog = JSON.parse(fs.readFileSync(CATALOG_PATH, "utf8")) as { entries: CatalogEntry[] };
  const symbols: PackSymbol[] = [];
  const rejected: string[] = [];

  for (const entry of catalog.entries) {
    const styledPath = path.join(STYLIZED_DIR, `${entry.slug}.json`);
    if (!fs.existsSync(styledPath)) {
      rejected.push(entry.slug);
      continue;
    }
    const styled = JSON.parse(fs.readFileSync(styledPath, "utf8")) as {
      svg: string;
      viewBox: string;
      width: number;
      height: number;
      ports: { id: string; x: number; y: number }[];
    };
    if (!styled.svg?.trim() || !styled.viewBox) {
      rejected.push(entry.slug);
      continue;
    }
    symbols.push({
      id: `pack.ispf-pid.${entry.slug}`,
      category: entry.category,
      nameEn: entry.name,
      nameRu: nameRuFromEn(entry.name),
      defaultWidth: styled.width,
      defaultHeight: styled.height,
      viewBox: styled.viewBox,
      svg: styled.svg,
      ports: styled.ports,
      tags: entry.tags,
    });
  }

  return { symbols, rejected };
}

export function writePackFiles(symbols: PackSymbol[]): void {
  fs.mkdirSync(PACK_OUT_DIR, { recursive: true });

  const byCategory = new Map<PackCategory, PackSymbol[]>();
  for (const cat of Object.keys(PACK_CATEGORY_FILES) as PackCategory[]) {
    byCategory.set(cat, []);
  }
  for (const sym of symbols) {
    byCategory.get(sym.category)?.push(sym);
  }

  const manifest = {
    version: 1,
    id: "ispf-pid-v1",
    generatedAt: new Date().toISOString(),
    totalSymbols: symbols.length,
    categories: Object.entries(PACK_CATEGORY_FILES).map(([id, file]) => ({
      id,
      file,
      count: byCategory.get(id as PackCategory)?.length ?? 0,
    })),
  };

  fs.writeFileSync(path.join(PACK_OUT_DIR, "manifest.json"), JSON.stringify(manifest, null, 2));

  for (const [cat, file] of Object.entries(PACK_CATEGORY_FILES) as [PackCategory, string][]) {
    const chunk = byCategory.get(cat) ?? [];
    fs.writeFileSync(path.join(PACK_OUT_DIR, file), JSON.stringify(chunk, null, 0));
  }

  console.log(`Pack written to ${PACK_OUT_DIR} (${symbols.length} symbols)`);
}

async function runFull(limit?: number): Promise<void> {
  await extractCatalog();
  if (limit) {
    // Re-slice catalog for limited runs
    const catalog = JSON.parse(fs.readFileSync(CATALOG_PATH, "utf8")) as { entries: CatalogEntry[] };
    catalog.entries = catalog.entries.slice(0, limit);
    fs.writeFileSync(CATALOG_PATH, JSON.stringify(catalog, null, 2));
  }
  await convertCatalog(limit);
  stylizeAll();
  const { symbols, rejected } = buildPackFromStylized();
  writePackFiles(symbols);
  if (rejected.length) {
    console.log(`Rejected (no stylized SVG): ${rejected.length}`);
  }
}

const isMain = process.argv[1]?.replace(/\\/g, "/").endsWith("build-pack.ts");
if (isMain) {
  const full = process.argv.includes("--full");
  const limit = process.argv.includes("--limit") ? Number(process.argv[process.argv.indexOf("--limit") + 1]) : undefined;

  if (full) {
    runFull(limit).catch((err) => {
      console.error(err);
      process.exit(1);
    });
  } else {
    try {
      const { symbols, rejected } = buildPackFromStylized();
      writePackFiles(symbols);
      console.log(`Rejected: ${rejected.length}`);
    } catch (err) {
      console.error(err);
      process.exit(1);
    }
  }
}
