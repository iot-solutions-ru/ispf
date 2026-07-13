import type { DashboardSession } from "../components/dashboard/DashboardContext";
import { resolveWidgetPath } from "../components/dashboard/dashboardUtils";
import type { MimicBinding, MimicSymbolBehavior } from "../types/scadaMimic";
import { resolveBindingValue } from "./bindingResolver";

export interface TopologySvgHitArea {
  nodeName: string;
  objectPath: string;
  id?: string;
}

export interface TopologySvgConfig {
  viewBox?: string;
  width?: number;
  height?: number;
  backgroundColor?: string;
  bindings: Record<string, MimicBinding>;
  behaviors: MimicSymbolBehavior[];
  hitAreas: TopologySvgHitArea[];
  svgInner?: string;
}

export function parseTopologyConfig(topologyJson?: string): TopologySvgConfig | null {
  const raw = topologyJson?.trim();
  if (!raw) return null;
  try {
    const parsed = JSON.parse(raw) as Partial<TopologySvgConfig>;
    if (!parsed.bindings || !parsed.behaviors) return null;
    return {
      viewBox: parsed.viewBox ?? "0 0 1309 503",
      width: parsed.width ?? 1309,
      height: parsed.height ?? 503,
      backgroundColor: parsed.backgroundColor ?? "#EEF2F6",
      bindings: parsed.bindings,
      behaviors: parsed.behaviors,
      hitAreas: parsed.hitAreas ?? [],
      svgInner: parsed.svgInner,
    };
  } catch {
    return null;
  }
}

export function collectTopologyBindingPaths(
  bindings: Record<string, MimicBinding>,
  session?: Pick<DashboardSession, "selection" | "params">
): string[] {
  const paths = new Set<string>();
  for (const binding of Object.values(bindings)) {
    const objectPath = session
      ? resolveWidgetPath(
          binding.objectPath,
          binding.selectionKey,
          session.selection,
          undefined,
          session.params
        )
      : binding.objectPath?.trim();
    if (objectPath?.trim()) {
      paths.add(objectPath.trim());
    }
  }
  return [...paths];
}

export function resolveTopologyBindingValues(
  bindings: Record<string, MimicBinding>,
  session: Pick<DashboardSession, "selection" | "params">,
  variablesByPath: Record<string, import("../types").VariableDto[] | undefined>
): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  for (const [key, binding] of Object.entries(bindings)) {
    out[key] = resolveBindingValue(binding, session, variablesByPath);
  }
  return out;
}

export function extractSvgInnerFromDocument(svgText: string): string {
  const trimmed = svgText.trim();
  if (!trimmed) return "";
  const match = trimmed.match(/<svg[^>]*>([\s\S]*)<\/svg>\s*$/i);
  return match ? match[1].trim() : trimmed;
}
