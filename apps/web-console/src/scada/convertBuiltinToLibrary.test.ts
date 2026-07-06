import { describe, expect, it } from "vitest";
import {
  convertDocumentToLibrarySymbols,
  convertElementToLibrarySymbol,
  ensureLibrarySymbolForBuiltin,
  isBuiltinSymbolId,
  isLibraryPlaceholderSvg,
  LIBRARY_PLACEHOLDER_MARKER,
  librarySymbolIdForBuiltin,
} from "./convertBuiltinToLibrary";
import { resolveElementSymbol, listDocumentCustomSymbols } from "./symbols/registry";
import type { MimicElement, ScadaMimicDocument } from "../types/scadaMimic";

describe("convertBuiltinToLibrary", () => {
  it("detects built-in symbol ids", () => {
    expect(isBuiltinSymbolId("gen.block")).toBe(true);
    expect(isBuiltinSymbolId("custom:lib-gen-block")).toBe(false);
    expect(isBuiltinSymbolId("custom.svg")).toBe(false);
    expect(isBuiltinSymbolId("pack.ispf-pid.vertical-tank")).toBe(false);
  });

  it("maps builtin id to lib id", () => {
    expect(librarySymbolIdForBuiltin("gen.block")).toBe("lib-gen-block");
    expect(librarySymbolIdForBuiltin("data-block")).toBe("lib-data-block");
  });

  it("creates library def from core preset", () => {
    const { def, created } = ensureLibrarySymbolForBuiltin("gen.block", []);
    expect(created).toBe(true);
    expect(def.id).toBe("lib-gen-block");
    expect(def.sourceSymbolId).toBe("gen.block");
    expect(def.svg).toContain("ispf-power");
    expect(isLibraryPlaceholderSvg(def.svg, def.sourceSymbolId)).toBe(false);
  });

  it("marks auto-generated placeholder svg", () => {
    const svg = `${LIBRARY_PLACEHOLDER_MARKER}<rect/><text>tank.vertical</text>`;
    expect(isLibraryPlaceholderSvg(svg, "tank.vertical")).toBe(true);
  });

  it("resolveElementSymbol uses pack SVG for legacy library placeholder", () => {
    const { def, customSymbols } = ensureLibrarySymbolForBuiltin("tank.vertical", []);
    expect(def.svg).toContain("<rect");
    const sym = resolveElementSymbol(
      { symbolId: `custom:${def.id}`, props: { width: def.width, height: def.height } },
      customSymbols
    );
    expect(sym?.id).toBe("custom:lib-tank-vertical");
    expect(sym?.paletteProps?.svg).toContain("<rect");
  });

  it("resolveElementSymbol maps legacy tank.vertical id to pack SVG", () => {
    const sym = resolveElementSymbol({ symbolId: "tank.vertical", props: {} });
    expect(sym?.id).toBe("pack.ispf-pid.vertical-tank");
  });

  it("converts element to custom library reference", () => {
    const el: MimicElement = {
      id: "gpu1",
      symbolId: "gen.block",
      layerId: "layer-default",
      x: 0,
      y: 0,
      bindings: {},
      props: { label: "ГПУ-1", width: 112, height: 112 },
    };
    const { element, customSymbols, converted } = convertElementToLibrarySymbol(el, []);
    expect(converted).toBe(true);
    expect(element.symbolId).toBe("custom:lib-gen-block");
    expect(element.props?.label).toBe("ГПУ-1");
    expect(element.props?.svg).toBeUndefined();
    expect(customSymbols.some((s) => s.id === "lib-gen-block")).toBe(true);
    expect(customSymbols.find((s) => s.id === "lib-gen-block")?.inUserLibrary).toBeUndefined();
  });

  it("palette lists only inUserLibrary symbols", () => {
    const symbols = listDocumentCustomSymbols([
      { id: "lib-a", name: "Bootstrap", svg: "<g/>", width: 64, height: 64 },
      { id: "lib-b", name: "Mine", svg: "<g/>", width: 64, height: 64, inUserLibrary: true },
    ]);
    expect(symbols).toHaveLength(1);
    expect(symbols[0].id).toBe("custom:lib-b");
  });

  it("converts whole document", () => {
    const doc: ScadaMimicDocument = {
      version: 2,
      width: 800,
      height: 600,
      layers: [{ id: "layer-default", name: "Main", visible: true }],
      elements: [
        {
          id: "a",
          symbolId: "gen.block",
          layerId: "layer-default",
          x: 1,
          y: 2,
          bindings: {},
        },
        {
          id: "b",
          symbolId: "custom:lib-breaker",
          layerId: "layer-default",
          x: 3,
          y: 4,
          bindings: {},
        },
      ],
      connections: [],
    };
    const next = convertDocumentToLibrarySymbols(doc);
    expect(next.elements[0].symbolId).toBe("custom:lib-gen-block");
    expect(next.elements[1].symbolId).toBe("custom:lib-breaker");
  });
});
