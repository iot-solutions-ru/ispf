/**
 * Convert WMF/EMF entries from catalog to raw SVG via Inkscape CLI.
 */
import fs from "node:fs";
import path from "node:path";
import { spawnSync } from "node:child_process";
import { CATALOG_PATH, CONVERTED_DIR, REJECTED_PATH, TIA_ZIP, WINCC_ZIP, WORK_DIR } from "./config.js";
import { extractZipEntry } from "./extract-catalog.js";
import type { CatalogEntry } from "./utils.js";

const INKSCAPE_CANDIDATES = [
  "inkscape",
  "C:\\Program Files\\Inkscape\\bin\\inkscape.exe",
  "C:\\Program Files\\Inkscape\\inkscape.exe",
];

export function findInkscape(): string | null {
  for (const cmd of INKSCAPE_CANDIDATES) {
    try {
      const r = spawnSync(cmd, ["--version"], { encoding: "utf8", timeout: 10_000 });
      if (r.status === 0) return cmd;
    } catch {
      /* try next */
    }
  }
  return null;
}

function zipPathForSource(source: "wincc" | "tia"): string {
  return source === "wincc" ? WINCC_ZIP : TIA_ZIP;
}

function convertOne(inkscape: string, inputPath: string, outputPath: string): boolean {
  fs.mkdirSync(path.dirname(outputPath), { recursive: true });
  const r = spawnSync(inkscape, [inputPath, "--export-type=svg", `--export-filename=${outputPath}`, "--export-area-drawing"], {
    encoding: "utf8",
    timeout: 60_000,
    stdio: "pipe",
  });
  if (r.status !== 0 || !fs.existsSync(outputPath) || fs.statSync(outputPath).size < 20) {
    return false;
  }
  return true;
}

export interface ConvertResult {
  converted: number;
  failed: { slug: string; reason: string }[];
}

export async function convertCatalog(limit?: number): Promise<ConvertResult> {
  fs.mkdirSync(CONVERTED_DIR, { recursive: true });
  fs.mkdirSync(WORK_DIR, { recursive: true });

  if (!fs.existsSync(CATALOG_PATH)) {
    throw new Error(`Run extract-catalog first. Missing ${CATALOG_PATH}`);
  }

  const catalog = JSON.parse(fs.readFileSync(CATALOG_PATH, "utf8")) as { entries: CatalogEntry[] };
  const entries = limit ? catalog.entries.slice(0, limit) : catalog.entries;

  const inkscape = findInkscape();
  if (!inkscape) {
    throw new Error(
      "Inkscape not found. Install from https://inkscape.org/ or: winget install Inkscape.Inkscape"
    );
  }
  console.log(`Using Inkscape: ${inkscape}`);
  console.log(`Converting ${entries.length} symbols...`);

  const failed: { slug: string; reason: string }[] = [];
  let converted = 0;
  const wmfDir = path.join(WORK_DIR, "wmf");

  for (let i = 0; i < entries.length; i++) {
    const entry = entries[i]!;
    const outSvg = path.join(CONVERTED_DIR, `${entry.slug}.svg`);
    if (fs.existsSync(outSvg) && fs.statSync(outSvg).size > 20) {
      converted++;
      continue;
    }

    const zip = zipPathForSource(entry.source);
    const wmfPath = path.join(wmfDir, `${entry.slug}.${entry.ext}`);
    try {
      fs.mkdirSync(path.dirname(wmfPath), { recursive: true });
      const data = extractZipEntry(zip, entry.zipPath);
      fs.writeFileSync(wmfPath, data);
      const ok = convertOne(inkscape, wmfPath, outSvg);
      if (ok) {
        converted++;
      } else {
        failed.push({ slug: entry.slug, reason: "inkscape export failed" });
      }
    } catch (err) {
      failed.push({ slug: entry.slug, reason: err instanceof Error ? err.message : String(err) });
    }

    if ((i + 1) % 50 === 0) {
      console.log(`  ${i + 1}/${entries.length} (${converted} ok, ${failed.length} failed)`);
    }
  }

  fs.writeFileSync(REJECTED_PATH, JSON.stringify({ failed, converted, total: entries.length }, null, 2));
  console.log(`Converted: ${converted}/${entries.length}, failed: ${failed.length}`);
  return { converted, failed };
}

if (process.argv[1]?.replace(/\\/g, "/").endsWith("convert-wmf.ts")) {
  const limit = process.argv.includes("--limit") ? Number(process.argv[process.argv.indexOf("--limit") + 1]) : undefined;
  convertCatalog(limit).catch((err) => {
    console.error(err);
    process.exit(1);
  });
}
