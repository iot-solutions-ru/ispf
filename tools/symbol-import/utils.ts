import { EXCLUDE_PATTERNS, type PackCategory } from "./config.js";

export interface CatalogEntry {
  slug: string;
  name: string;
  category: PackCategory;
  source: "wincc" | "tia";
  zipPath: string;
  ext: "wmf" | "emf";
  tags: string[];
}

export function normalizeSlug(name: string): string {
  return name
    .replace(/\.(wmf|emf|svg)$/i, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80);
}

export function shouldExcludePath(fullPath: string): boolean {
  return EXCLUDE_PATTERNS.some((re) => re.test(fullPath));
}

export function mapPackCategory(folderPath: string, fileName: string): PackCategory {
  const ctx = `${folderPath}/${fileName}`.toLowerCase();
  if (/\bvalve/.test(ctx)) return "pack-valves";
  if (/\bpump/.test(ctx)) return "pack-pumps";
  if (/\btank|\bvessel|\bcontainer|\bdrum|\breactor/.test(ctx)) return "pack-tanks";
  if (/\bpipe|\bduct|\bpiping/.test(ctx)) return "pack-pipes";
  if (/\bsensor|\bmeter|\bflow|\btransmitter|\bgauge/.test(ctx)) return "pack-sensors";
  if (/\belectrical|\bmotor|\bbreaker|\btransformer|\bbusbar|\bfuse|\brelay/.test(ctx)) return "pack-electrical";
  if (/\bisa\b/.test(ctx)) return "pack-isa";
  return "pack-misc";
}

export function extractTags(name: string, folderPath: string): string[] {
  const parts = `${folderPath} ${name}`
    .toLowerCase()
    .replace(/[^a-z0-9\s]/g, " ")
    .split(/\s+/)
    .filter((w) => w.length > 2);
  return [...new Set(parts)].slice(0, 12);
}

/** Rough Russian label — keeps Latin names for unknown equipment terms. */
export function nameRuFromEn(name: string): string {
  const map: Record<string, string> = {
    valve: "клапан",
    gate: "задвижка",
    ball: "шаровой",
    butterfly: "дисковый",
    check: "обратный",
    pump: "насос",
    tank: "резервуар",
    vessel: "сосуд",
    pipe: "труба",
    sensor: "датчик",
    flow: "расход",
    meter: "счётчик",
    motor: "двигатель",
    blower: "вентилятор",
    compressor: "компрессор",
    heat: "тепло",
    exchanger: "теплообменник",
    filter: "фильтр",
    electrical: "электрический",
    transformer: "трансформатор",
    breaker: "выключатель",
  };
  let ru = name;
  for (const [en, tr] of Object.entries(map)) {
    ru = ru.replace(new RegExp(`\\b${en}\\b`, "gi"), tr);
  }
  return ru;
}
