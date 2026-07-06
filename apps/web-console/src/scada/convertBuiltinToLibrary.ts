import type { MimicCustomSymbol, MimicElement, ScadaMimicDocument } from "../types/scadaMimic";
import { getPackRecord } from "./symbols/legacyBuiltinPackMap";
import { getPackSymbol } from "./symbols/symbolPackLoader";
import miniTecMimic from "../../../../packages/ispf-server/src/main/resources/bootstrap/mini-tec-mimic.json";
import { legacyPackSvg } from "./symbols/legacyBuiltinPackMap";

/** Pack SVG symbol from ispf-pid-v1 palette. */
export function isPackSymbolId(symbolId: string): boolean {
  return symbolId.startsWith("pack.");
}

/** Built-in palette symbol (not document library or inline custom.svg). Pack SVG symbols are not builtins. */
export function isBuiltinSymbolId(symbolId: string): boolean {
  return (
    symbolId !== "custom.svg" &&
    !symbolId.startsWith("custom:") &&
    !isPackSymbolId(symbolId)
  );
}

export function librarySymbolIdForBuiltin(builtinId: string): string {
  return `lib-${builtinId.replace(/\./g, "-")}`;
}

export function librarySymbolIdForPack(packId: string): string {
  return packId.replace(/^pack\./, "lib-pack-").replace(/\./g, "-");
}

const CORE_PRESETS = new Map<string, MimicCustomSymbol>();
for (const sym of (miniTecMimic as { customSymbols?: MimicCustomSymbol[] }).customSymbols ?? []) {
  if (sym.sourceSymbolId) {
    CORE_PRESETS.set(sym.sourceSymbolId, sym);
  }
}

export function findLibraryDef(
  customSymbols: MimicCustomSymbol[] | undefined,
  builtinId: string
): MimicCustomSymbol | undefined {
  if (!customSymbols?.length) return undefined;
  const libId = librarySymbolIdForBuiltin(builtinId);
  return (
    customSymbols.find((s) => s.sourceSymbolId === builtinId) ??
    customSymbols.find((s) => s.id === libId)
  );
}

export const LIBRARY_PLACEHOLDER_MARKER = "<!-- ispf:library-placeholder -->";

/** True when library SVG was auto-generated (no real geometry yet). */
export function isLibraryPlaceholderSvg(svg: string | undefined, sourceSymbolId?: string): boolean {
  if (!svg?.trim()) return true;
  if (svg.includes(LIBRARY_PLACEHOLDER_MARKER)) return true;
  if (!sourceSymbolId) return false;
  return (
    svg.includes('fill="#161b22"') &&
    svg.includes('stroke="#30363d"') &&
    svg.includes(`>${sourceSymbolId}<`)
  );
}

/** Create or reuse a document-library symbol for a built-in palette id. */
export function ensureLibrarySymbolForBuiltin(
  builtinId: string,
  customSymbols: MimicCustomSymbol[] = []
): { def: MimicCustomSymbol; customSymbols: MimicCustomSymbol[]; created: boolean } {
  const existing = findLibraryDef(customSymbols, builtinId);
  if (existing) {
    return { def: existing, customSymbols, created: false };
  }

  const preset = CORE_PRESETS.get(builtinId);
  if (preset) {
    const def = { ...preset };
    return { def, customSymbols: [...customSymbols, def], created: true };
  }

  const pack = legacyPackSvg(builtinId);
  if (pack) {
    const def: MimicCustomSymbol = {
      id: librarySymbolIdForBuiltin(builtinId),
      name: pack.name,
      svg: pack.svg,
      width: pack.width,
      height: pack.height,
      viewBox: pack.viewBox,
      ports: pack.ports,
      sourceSymbolId: builtinId,
    };
    return { def, customSymbols: [...customSymbols, def], created: true };
  }

  throw new Error(`Unknown symbol: ${builtinId}`);
}

function findPackLibraryDef(
  customSymbols: MimicCustomSymbol[] | undefined,
  packId: string
): MimicCustomSymbol | undefined {
  if (!customSymbols?.length) return undefined;
  const libId = librarySymbolIdForPack(packId);
  return (
    customSymbols.find((s) => s.sourceSymbolId === packId) ??
    customSymbols.find((s) => s.id === libId)
  );
}

/** Document-library copy of a pack symbol (not in user palette until edited). */
export function ensureLibrarySymbolForPack(
  packId: string,
  customSymbols: MimicCustomSymbol[] = []
): { def: MimicCustomSymbol; customSymbols: MimicCustomSymbol[]; created: boolean } {
  const existing = findPackLibraryDef(customSymbols, packId);
  if (existing) {
    return { def: existing, customSymbols, created: false };
  }

  const reg = getPackSymbol(packId);
  const rec = getPackRecord(packId);
  const svg =
    (typeof reg?.paletteProps?.svg === "string" && reg.paletteProps.svg) ||
    rec?.svg ||
    "";
  if (!svg.trim()) {
    throw new Error(`Unknown pack symbol: ${packId}`);
  }

  const width = reg?.defaultWidth ?? rec?.defaultWidth ?? 64;
  const height = reg?.defaultHeight ?? rec?.defaultHeight ?? 64;
  const viewBox =
    (typeof reg?.paletteProps?.viewBox === "string" && reg.paletteProps.viewBox) ||
    rec?.viewBox ||
    `0 0 ${width} ${height}`;
  const name =
    (typeof reg?.paletteProps?.nameRu === "string" && reg.paletteProps.nameRu) ||
    reg?.displayName ||
    rec?.nameRu ||
    rec?.nameEn ||
    packId;

  const def: MimicCustomSymbol = {
    id: librarySymbolIdForPack(packId),
    name,
    svg,
    width,
    height,
    viewBox,
    ports: reg?.ports ?? rec?.ports,
    sourceSymbolId: packId,
  };
  return { def, customSymbols: [...customSymbols, def], created: true };
}

export function convertPackToLibrarySymbol(
  element: MimicElement,
  customSymbols: MimicCustomSymbol[] = []
): { element: MimicElement; customSymbols: MimicCustomSymbol[]; converted: boolean } {
  if (!isPackSymbolId(element.symbolId)) {
    return { element, customSymbols, converted: false };
  }

  const { def, customSymbols: nextSymbols } = ensureLibrarySymbolForPack(
    element.symbolId,
    customSymbols
  );

  const propW = Number(element.props?.width);
  const propH = Number(element.props?.height);
  const { svg: _svg, viewBox: _viewBox, ...restProps } = element.props ?? {};

  return {
    element: {
      ...element,
      symbolId: `custom:${def.id}`,
      props: {
        ...restProps,
        width: propW || def.width,
        height: propH || def.height,
      },
    },
    customSymbols: nextSymbols,
    converted: true,
  };
}

export function convertElementToLibrarySymbol(
  element: MimicElement,
  customSymbols: MimicCustomSymbol[] = []
): { element: MimicElement; customSymbols: MimicCustomSymbol[]; converted: boolean } {
  if (!isBuiltinSymbolId(element.symbolId)) {
    return { element, customSymbols, converted: false };
  }

  const { def, customSymbols: nextSymbols } = ensureLibrarySymbolForBuiltin(
    element.symbolId,
    customSymbols
  );

  const propW = Number(element.props?.width);
  const propH = Number(element.props?.height);
  const { svg: _svg, viewBox: _viewBox, ...restProps } = element.props ?? {};

  return {
    element: {
      ...element,
      symbolId: `custom:${def.id}`,
      props: {
        ...restProps,
        width: propW || def.width,
        height: propH || def.height,
      },
    },
    customSymbols: nextSymbols,
    converted: true,
  };
}

export function convertDocumentToLibrarySymbols(doc: ScadaMimicDocument): ScadaMimicDocument {
  let customSymbols = [...(doc.customSymbols ?? [])];
  let converted = 0;
  const elements = doc.elements.map((el) => {
    const result = convertElementToLibrarySymbol(el, customSymbols);
    customSymbols = result.customSymbols;
    if (result.converted) converted++;
    return result.element;
  });
  return converted > 0 ? { ...doc, elements, customSymbols } : doc;
}

export function documentHasBuiltinSymbols(doc: ScadaMimicDocument): boolean {
  return doc.elements.some((el) => isBuiltinSymbolId(el.symbolId));
}
