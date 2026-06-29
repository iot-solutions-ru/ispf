import type { MimicElement } from "../types/scadaMimic";
import type { RegisteredSymbol } from "./symbols/registry";

/** Props passed into symbol renderers — merge library palette props with element overrides. */
export function elementRenderProps(
  element: MimicElement,
  symbol: RegisteredSymbol | undefined
): Record<string, unknown> {
  if (element.symbolId === "custom.svg" || element.symbolId.startsWith("custom:")) {
    return { ...(symbol?.paletteProps ?? {}), ...(element.props ?? {}) };
  }
  return { ...(symbol?.paletteProps ?? {}), ...(element.props ?? {}) };
}
