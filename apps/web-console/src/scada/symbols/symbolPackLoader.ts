/**
 * Lazy-loaded P&ID symbol pack (ispf-pid-v1).
 */
import type { MimicPort } from "../../types/scadaMimic";
import { CustomSvgSymbol } from "./customSvg";
import type { RegisteredSymbol } from "./registry";

export const PACK_ID = "ispf-pid-v1";

export const PACK_CATEGORY_IDS = [
  "pack-valves",
  "pack-pumps",
  "pack-tanks",
  "pack-pipes",
  "pack-sensors",
  "pack-electrical",
  "pack-isa",
  "pack-misc",
] as const;

export type PackCategoryId = (typeof PACK_CATEGORY_IDS)[number];

export interface PackManifest {
  version: number;
  id: string;
  generatedAt: string;
  totalSymbols: number;
  categories: { id: PackCategoryId; file: string; count: number }[];
}

export interface PackSymbolRecord {
  id: string;
  category: PackCategoryId;
  nameEn: string;
  nameRu: string;
  defaultWidth: number;
  defaultHeight: number;
  viewBox: string;
  svg: string;
  ports: MimicPort[];
  tags?: string[];
}

const chunkLoaders: Record<string, () => Promise<{ default: PackSymbolRecord[] }>> = {
  valves: () => import("./packs/ispf-pid-v1/valves.json").then((m) => ({ default: m.default as PackSymbolRecord[] })),
  pumps: () => import("./packs/ispf-pid-v1/pumps.json").then((m) => ({ default: m.default as PackSymbolRecord[] })),
  tanks: () => import("./packs/ispf-pid-v1/tanks.json").then((m) => ({ default: m.default as PackSymbolRecord[] })),
  pipes: () => import("./packs/ispf-pid-v1/pipes.json").then((m) => ({ default: m.default as PackSymbolRecord[] })),
  sensors: () => import("./packs/ispf-pid-v1/sensors.json").then((m) => ({ default: m.default as PackSymbolRecord[] })),
  electrical: () => import("./packs/ispf-pid-v1/electrical.json").then((m) => ({ default: m.default as PackSymbolRecord[] })),
  isa: () => import("./packs/ispf-pid-v1/isa.json").then((m) => ({ default: m.default as PackSymbolRecord[] })),
  misc: () => import("./packs/ispf-pid-v1/misc.json").then((m) => ({ default: m.default as PackSymbolRecord[] })),
};

let manifestPromise: Promise<PackManifest | null> | null = null;
const loadedCategories = new Map<PackCategoryId, PackSymbolRecord[]>();
const registeredById = new Map<string, RegisteredSymbol>();
let initPromise: Promise<void> | null = null;
let loadedAll = false;

function packRecordToRegistered(rec: PackSymbolRecord): RegisteredSymbol {
  return {
    id: rec.id,
    category: rec.category,
    nameKey: rec.id,
    displayName: rec.nameEn,
    defaultWidth: rec.defaultWidth,
    defaultHeight: rec.defaultHeight,
    ports: rec.ports,
    bindingSchema: [],
    render: CustomSvgSymbol,
    paletteProps: {
      svg: rec.svg,
      viewBox: rec.viewBox,
      nameRu: rec.nameRu,
      tags: rec.tags,
    },
  };
}

function registerRecords(records: PackSymbolRecord[]): void {
  for (const rec of records) {
    const reg = packRecordToRegistered(rec);
    registeredById.set(reg.id, reg);
  }
}

export async function loadPackManifest(): Promise<PackManifest | null> {
  if (!manifestPromise) {
    manifestPromise = import("./packs/ispf-pid-v1/manifest.json")
      .then((m) => m.default as PackManifest)
      .catch(() => null);
  }
  return manifestPromise;
}

export async function loadPackCategory(categoryId: PackCategoryId): Promise<PackSymbolRecord[]> {
  if (loadedCategories.has(categoryId)) {
    return loadedCategories.get(categoryId)!;
  }
  const manifest = await loadPackManifest();
  if (!manifest) return [];
  const cat = manifest.categories.find((c) => c.id === categoryId);
  if (!cat) return [];
  const fileKey = cat.file.replace(/\.json$/, "");
  const loader = chunkLoaders[fileKey];
  if (!loader) return [];
  const mod = await loader();
  const records = mod.default ?? [];
  loadedCategories.set(categoryId, records);
  registerRecords(records);
  return records;
}

/** Preload all pack categories (call when palette opens). */
export async function ensurePackLoaded(): Promise<void> {
  if (loadedAll) return;
  if (initPromise) return initPromise;
  initPromise = (async () => {
    const manifest = await loadPackManifest();
    if (!manifest) return;
    await Promise.all(manifest.categories.map((c) => loadPackCategory(c.id)));
    loadedAll = true;
  })();
  return initPromise;
}

export function getPackSymbol(id: string): RegisteredSymbol | undefined {
  return registeredById.get(id);
}

export function listPackSymbols(): RegisteredSymbol[] {
  return [...registeredById.values()];
}

export function listPackSymbolsByCategory(category: string): RegisteredSymbol[] {
  return listPackSymbols().filter((s) => s.category === category);
}

export function isPackCategory(category: string): category is PackCategoryId {
  return (PACK_CATEGORY_IDS as readonly string[]).includes(category);
}

export function packSymbolLabel(sym: RegisteredSymbol, locale: string): string {
  if (locale.startsWith("ru")) {
    const ru = sym.paletteProps?.nameRu;
    if (typeof ru === "string" && ru) return ru;
  }
  return sym.displayName ?? sym.nameKey;
}

export function packSymbolMatchesSearch(sym: RegisteredSymbol, q: string): boolean {
  const lower = q.toLowerCase();
  if (sym.id.toLowerCase().includes(lower)) return true;
  if ((sym.displayName ?? "").toLowerCase().includes(lower)) return true;
  const ru = sym.paletteProps?.nameRu;
  if (typeof ru === "string" && ru.toLowerCase().includes(lower)) return true;
  const tags = sym.paletteProps?.tags;
  if (Array.isArray(tags) && tags.some((t) => String(t).toLowerCase().includes(lower))) return true;
  return false;
}
