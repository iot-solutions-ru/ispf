/**
 * Scan WinCC / TIA zip archives and build a deduplicated P&ID symbol catalog.
 */
import fs from "node:fs";
import path from "node:path";
import { execSync } from "node:child_process";
import { CATALOG_PATH, TIA_ZIP, WINCC_INCLUDE_FOLDERS, WINCC_ZIP, WORK_DIR } from "./config.js";
import type { CatalogEntry } from "./utils.js";
import { extractTags, mapPackCategory, normalizeSlug, shouldExcludePath } from "./utils.js";

export function extractZipEntry(zipPath: string, entryName: string): Buffer {
  const tmpDir = path.join(WORK_DIR, "_extract-tmp");
  fs.mkdirSync(tmpDir, { recursive: true });
  const safeName = entryName.replace(/[<>:"|?*]/g, "_");
  const outFile = path.join(tmpDir, safeName);
  fs.mkdirSync(path.dirname(outFile), { recursive: true });
  const escapedZip = zipPath.replace(/'/g, "''");
  const escapedEntry = entryName.replace(/'/g, "''");
  const escapedOut = outFile.replace(/'/g, "''");
  const ps = `Add-Type -AssemblyName System.IO.Compression.FileSystem; $z=[System.IO.Compression.ZipFile]::OpenRead('${escapedZip}'); $e=$z.Entries | Where-Object { $_.FullName -eq '${escapedEntry}' }; if($e){[System.IO.Compression.ZipFileExtensions]::ExtractToFile($e,'${escapedOut}',$true)}; $z.Dispose()`;
  execSync(`powershell -NoProfile -Command "${ps}"`, { stdio: "pipe" });
  if (!fs.existsSync(outFile)) {
    throw new Error(`Failed to extract ${entryName} from ${zipPath}`);
  }
  return fs.readFileSync(outFile);
}

function listZipEntries(zipPath: string): string[] {
  const escaped = zipPath.replace(/'/g, "''");
  const script = `Add-Type -AssemblyName System.IO.Compression.FileSystem; $z=[System.IO.Compression.ZipFile]::OpenRead('${escaped}'); $z.Entries | ForEach-Object { $_.FullName }; $z.Dispose()`;
  const out = execSync(`powershell -NoProfile -Command "${script}"`, { encoding: "utf8", maxBuffer: 50 * 1024 * 1024 });
  return out
    .split(/\r?\n/)
    .map((l) => l.trim())
    .filter(Boolean);
}

function scanWinCC(entries: string[]): CatalogEntry[] {
  const results: CatalogEntry[] = [];
  const prefix = "SymbolFactory Graphics/SymbolFactory 2 Colors/";
  for (const fullName of entries) {
    if (!fullName.startsWith(prefix)) continue;
    if (!/\.wmf$/i.test(fullName)) continue;
    if (shouldExcludePath(fullName)) continue;
    const rel = fullName.slice(prefix.length);
    const slash = rel.indexOf("/");
    if (slash < 0) continue;
    const folder = rel.slice(0, slash);
    if (!WINCC_INCLUDE_FOLDERS.includes(folder)) continue;
    const fileName = rel.slice(slash + 1);
    if (fileName.includes("/")) continue;
    const name = fileName.replace(/\.wmf$/i, "");
    const slug = normalizeSlug(name);
    if (!slug) continue;
    results.push({
      slug,
      name,
      category: mapPackCategory(folder, name),
      source: "wincc",
      zipPath: fullName,
      ext: "wmf",
      tags: extractTags(name, folder),
    });
  }
  return results;
}

function scanTIA(entries: string[]): CatalogEntry[] {
  const results: CatalogEntry[] = [];
  const prefix = "Graphics_DVDpart/Graphics/";
  for (const fullName of entries) {
    if (!fullName.startsWith(prefix)) continue;
    if (!/\.(wmf|emf)$/i.test(fullName)) continue;
    if (shouldExcludePath(fullName)) continue;
    const rel = fullName.slice(prefix.length);
    const parts = rel.split("/");
    if (parts.length < 3) continue;
    const top = parts[0];
    const allowed =
      top === "Automation equipment" ||
      (top === "Standardized symbols" && /^(ISA|Electrical)/i.test(parts[1] ?? ""));
    if (!allowed) continue;
    const pathLower = rel.toLowerCase();
    if (pathLower.includes("filled")) continue;
    if (/3-?d/.test(pathLower)) continue;
    const fileName = parts[parts.length - 1]!;
    const name = fileName.replace(/\.(wmf|emf)$/i, "");
    const folder = parts.slice(0, -1).join("/");
    const slug = normalizeSlug(name);
    if (!slug) continue;
    results.push({
      slug,
      name,
      category: mapPackCategory(folder, name),
      source: "tia",
      zipPath: fullName,
      ext: /\.emf$/i.test(fileName) ? "emf" : "wmf",
      tags: extractTags(name, folder),
    });
  }
  return results;
}

function dedupe(entries: CatalogEntry[]): CatalogEntry[] {
  const bySlug = new Map<string, CatalogEntry>();
  const sorted = [...entries].sort((a, b) => (a.source === "wincc" ? 0 : 1) - (b.source === "wincc" ? 0 : 1));
  for (const e of sorted) {
    if (!bySlug.has(e.slug)) bySlug.set(e.slug, e);
  }
  return [...bySlug.values()].sort((a, b) => a.slug.localeCompare(b.slug));
}

export async function extractCatalog(): Promise<CatalogEntry[]> {
  fs.mkdirSync(WORK_DIR, { recursive: true });

  const winccNames = fs.existsSync(WINCC_ZIP)
    ? listZipEntries(WINCC_ZIP)
    : (console.warn(`WinCC zip missing: ${WINCC_ZIP}`), [] as string[]);
  const tiaNames = fs.existsSync(TIA_ZIP)
    ? listZipEntries(TIA_ZIP)
    : (console.warn(`TIA zip missing: ${TIA_ZIP}`), [] as string[]);

  const wincc = scanWinCC(winccNames);
  const tia = scanTIA(tiaNames);
  const merged = dedupe([...wincc, ...tia]);

  fs.writeFileSync(
    CATALOG_PATH,
    JSON.stringify(
      {
        version: 1,
        generatedAt: new Date().toISOString(),
        counts: { wincc: wincc.length, tia: tia.length, merged: merged.length },
        entries: merged,
      },
      null,
      2
    )
  );

  console.log(`Catalog: ${merged.length} symbols (${wincc.length} WinCC + ${tia.length} TIA raw, deduped)`);
  return merged;
}

if (process.argv[1]?.replace(/\\/g, "/").endsWith("extract-catalog.ts")) {
  extractCatalog().catch((err) => {
    console.error(err);
    process.exit(1);
  });
}
