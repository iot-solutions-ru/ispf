import type { ComponentType } from "react";
import type { MimicCustomSymbol, MimicElement, SymbolDefinition, SymbolRenderProps } from "../../types/scadaMimic";
import { isLibraryPlaceholderSvg } from "../convertBuiltinToLibrary";
import { defaultEdgePorts, portsFromProps } from "../customSvg";
import { CustomSvgSymbol } from "./customSvg";
import { getPackRecord, getPackRecordForLegacyBuiltin, LEGACY_BUILTIN_TO_PACK } from "./legacyBuiltinPackMap";
import {
  PACK_CATEGORY_IDS,
  ensurePackLoaded,
  getPackSymbol,
  listPackSymbols,
  listPackSymbolsByCategory,
  loadPackManifest,
  type PackSymbolRecord,
} from "./symbolPackLoader";

export interface RegisteredSymbol extends SymbolDefinition {
  render: ComponentType<SymbolRenderProps>;
  paletteProps?: Record<string, unknown>;
}

const port = (id: string, x: number, y: number) => ({ id, x, y });

/** Built-in palette entries (SVG-only). Process/electrical/domain React symbols removed — use ispf-pid-v1 pack. */
const SYMBOLS: RegisteredSymbol[] = [
  {
    id: "custom.svg",
    category: "common",
    nameKey: "symbols.customSvg",
    defaultWidth: 64,
    defaultHeight: 64,
    ports: [port("n", 32, 0), port("s", 32, 64), port("e", 64, 32), port("w", 0, 32)],
    bindingSchema: [],
    render: CustomSvgSymbol,
  },
];

const byId = new Map(SYMBOLS.map((s) => [s.id, s]));

export const SYMBOL_CATALOG: SymbolDefinition[] = SYMBOLS;

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

function resolveLegacyBuiltin(id: string): RegisteredSymbol | undefined {
  const rec = getPackRecordForLegacyBuiltin(id);
  return rec ? packRecordToRegistered(rec) : undefined;
}

function resolvePackSymbol(id: string): RegisteredSymbol | undefined {
  const loaded = getPackSymbol(id);
  if (loaded) return loaded;
  const rec = getPackRecord(id);
  return rec ? packRecordToRegistered(rec) : undefined;
}

export function getSymbol(id: string): RegisteredSymbol | undefined {
  return byId.get(id) ?? resolvePackSymbol(id) ?? resolveLegacyBuiltin(id);
}

export function customSymbolToRegistered(def: MimicCustomSymbol): RegisteredSymbol {
  const w = def.width;
  const h = def.height;
  return {
    id: `custom:${def.id}`,
    category: "custom",
    nameKey: def.name,
    displayName: def.name,
    defaultWidth: w,
    defaultHeight: h,
    ports: def.ports ?? defaultEdgePorts(w, h),
    bindingSchema: def.bindingSchema ?? [],
    render: CustomSvgSymbol,
    paletteProps: {
      svg: def.svg,
      viewBox: def.viewBox ?? `0 0 ${w} ${h}`,
      behaviors: def.behaviors,
    },
  };
}

export function listDocumentCustomSymbols(customSymbols?: MimicCustomSymbol[]): RegisteredSymbol[] {
  if (!customSymbols?.length) return [];
  return customSymbols.filter((s) => s.inUserLibrary === true).map(customSymbolToRegistered);
}

function resolveLibraryDefSvg(def: MimicCustomSymbol): MimicCustomSymbol {
  if (!def.sourceSymbolId || !isLibraryPlaceholderSvg(def.svg, def.sourceSymbolId)) {
    return def;
  }
  const legacy = getPackRecordForLegacyBuiltin(def.sourceSymbolId);
  if (!legacy) return def;
  return {
    ...def,
    svg: legacy.svg,
    viewBox: legacy.viewBox,
    width: def.width || legacy.defaultWidth,
    height: def.height || legacy.defaultHeight,
    ports: def.ports?.length ? def.ports : legacy.ports,
  };
}

/** Resolve symbol for a placed element (pack SVG, inline custom.svg, or document library custom:id). */
export function resolveElementSymbol(
  element: Pick<MimicElement, "symbolId" | "props">,
  customSymbols?: MimicCustomSymbol[]
): RegisteredSymbol | undefined {
  if (element.symbolId.startsWith("custom:")) {
    const id = element.symbolId.slice("custom:".length);
    const raw = customSymbols?.find((s) => s.id === id);
    if (!raw) return undefined;
    const def = resolveLibraryDefSvg(raw);
    const w = Number(element.props?.width) || def.width;
    const h = Number(element.props?.height) || def.height;
    const reg = customSymbolToRegistered(def);
    return {
      ...reg,
      ports: portsFromProps(element.props, w, h),
    };
  }
  const base = getSymbol(element.symbolId);
  if (!base) return undefined;
  if (element.symbolId === "custom.svg") {
    const w = Number(element.props?.width) || base.defaultWidth;
    const h = Number(element.props?.height) || base.defaultHeight;
    const schemaRaw = element.props?.bindingSchema;
    const bindingSchema = Array.isArray(schemaRaw)
      ? (schemaRaw as RegisteredSymbol["bindingSchema"])
      : base.bindingSchema;
    return {
      ...base,
      bindingSchema,
      ports: portsFromProps(element.props, w, h),
    };
  }
  const w = Number(element.props?.width) || base.defaultWidth;
  const h = Number(element.props?.height) || base.defaultHeight;
  return {
    ...base,
    ports: portsFromProps(element.props, w, h),
  };
}

/** Resolve symbol when picking from palette (placement). */
export function resolvePlacementSymbol(
  symbolId: string,
  customSymbols?: MimicCustomSymbol[]
): RegisteredSymbol | undefined {
  if (symbolId.startsWith("custom:")) {
    const id = symbolId.slice("custom:".length);
    const def = customSymbols?.find((s) => s.id === id);
    return def ? customSymbolToRegistered(def) : undefined;
  }
  return getSymbol(symbolId);
}

export function listSymbolsByCategory(category: string): RegisteredSymbol[] {
  const base = SYMBOLS.filter((s) => s.category === category);
  if ((PACK_CATEGORY_IDS as readonly string[]).includes(category)) {
    return [...base, ...listPackSymbolsByCategory(category)];
  }
  return base;
}

export function listAllSymbols(): RegisteredSymbol[] {
  return [...SYMBOLS, ...listPackSymbols()];
}

export const SYMBOL_CATEGORIES = ["common", ...PACK_CATEGORY_IDS] as const;

export { ensurePackLoaded, loadPackManifest, LEGACY_BUILTIN_TO_PACK };

export function symbolSize(
  element: Pick<MimicElement, "symbolId" | "scale" | "props">,
  customSymbols?: MimicCustomSymbol[]
) {
  const scale = element.scale ?? 1;
  const propW = typeof element.props?.width === "number" ? element.props.width : Number(element.props?.width);
  const propH = typeof element.props?.height === "number" ? element.props.height : Number(element.props?.height);

  let defaultW = 64;
  let defaultH = 64;
  if (element.symbolId.startsWith("custom:")) {
    const raw = customSymbols?.find((s) => s.id === element.symbolId.slice("custom:".length));
    const def = raw ? resolveLibraryDefSvg(raw) : undefined;
    if (def) {
      defaultW = def.width;
      defaultH = def.height;
    }
  } else {
    const sym = getSymbol(element.symbolId);
    defaultW = sym?.defaultWidth ?? 64;
    defaultH = sym?.defaultHeight ?? 64;
  }

  return {
    width: (Number.isFinite(propW) && propW > 0 ? propW : defaultW) * scale,
    height: (Number.isFinite(propH) && propH > 0 ? propH : defaultH) * scale,
  };
}
