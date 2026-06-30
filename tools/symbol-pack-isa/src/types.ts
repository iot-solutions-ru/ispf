export type PackCategory =
  | "pack-valves"
  | "pack-pumps"
  | "pack-tanks"
  | "pack-pipes"
  | "pack-sensors"
  | "pack-electrical"
  | "pack-isa"
  | "pack-misc";

export interface MimicPort {
  id: string;
  x: number;
  y: number;
}

export interface IsaSymbolDef {
  slug: string;
  category: PackCategory;
  nameEn: string;
  nameRu: string;
  tags?: string[];
  viewBox?: string;
  defaultWidth?: number;
  defaultHeight?: number;
  ports: MimicPort[];
  svg: string;
}

export interface PackSymbolRecord {
  id: string;
  category: PackCategory;
  nameEn: string;
  nameRu: string;
  defaultWidth: number;
  defaultHeight: number;
  viewBox: string;
  svg: string;
  ports: MimicPort[];
  tags: string[];
}

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
