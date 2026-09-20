// Extracted from CustomSvgEditor.tsx so that the component module only exports components
// (react-refresh/only-export-components).
import type { MimicElement } from "../../types/scadaMimic";
import { isBuiltinSymbolId, isPackSymbolId } from "../../scada/convertBuiltinToLibrary";

export function isCustomSvgElement(element: MimicElement): boolean {
  return element.symbolId === "custom.svg" || element.symbolId.startsWith("custom:");
}

export function supportsSvgMarkupEditor(element: MimicElement): boolean {
  return isCustomSvgElement(element) || isBuiltinSymbolId(element.symbolId) || isPackSymbolId(element.symbolId);
}
