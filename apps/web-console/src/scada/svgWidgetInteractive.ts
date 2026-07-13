import type { SvgWidget } from "../types/dashboard";
import type { MimicBinding, MimicBindingSlot, MimicSymbolBehavior } from "../types/scadaMimic";
import { syncBindingSchemaFromBehaviors } from "./symbolBehaviors";
import type { TopologySvgHitArea } from "./topologySvgConfig";
import { parseTopologyConfig } from "./topologySvgConfig";

export interface SvgInteractiveConfig {
  viewBox: string;
  backgroundColor: string;
  bindings: Record<string, MimicBinding>;
  behaviors: MimicSymbolBehavior[];
  bindingSchema: MimicBindingSlot[];
  hitAreas: TopologySvgHitArea[];
  svgInner?: string;
}

function parseJsonField<T>(raw?: string): T | undefined {
  const trimmed = raw?.trim();
  if (!trimmed) return undefined;
  try {
    return JSON.parse(trimmed) as T;
  } catch {
    return undefined;
  }
}

export function readSvgWidgetBehaviors(widget: Pick<SvgWidget, "behaviorsJson" | "topologyJson">): MimicSymbolBehavior[] {
  const direct = parseJsonField<MimicSymbolBehavior[]>(widget.behaviorsJson);
  if (direct?.length) return direct;
  return parseTopologyConfig(widget.topologyJson)?.behaviors ?? [];
}

export function readSvgWidgetBindings(widget: Pick<SvgWidget, "bindingsJson" | "topologyJson">): Record<string, MimicBinding> {
  const direct = parseJsonField<Record<string, MimicBinding>>(widget.bindingsJson);
  if (direct && Object.keys(direct).length > 0) return direct;
  return parseTopologyConfig(widget.topologyJson)?.bindings ?? {};
}

export function readSvgWidgetBindingSchema(
  widget: Pick<SvgWidget, "bindingSchemaJson" | "behaviorsJson" | "topologyJson">,
  behaviors: MimicSymbolBehavior[]
): MimicBindingSlot[] {
  const direct = parseJsonField<MimicBindingSlot[]>(widget.bindingSchemaJson);
  if (direct?.length) return direct;
  return syncBindingSchemaFromBehaviors(behaviors);
}

export function readSvgWidgetHitAreas(widget: Pick<SvgWidget, "hitAreasJson" | "topologyJson">): TopologySvgHitArea[] {
  const direct = parseJsonField<TopologySvgHitArea[]>(widget.hitAreasJson);
  if (direct?.length) return direct;
  return parseTopologyConfig(widget.topologyJson)?.hitAreas ?? [];
}

export function readSvgWidgetInner(widget: Pick<SvgWidget, "svgInnerJson" | "topologyJson">): string | undefined {
  const direct = widget.svgInnerJson?.trim();
  if (direct) return direct;
  return parseTopologyConfig(widget.topologyJson)?.svgInner;
}

/** Same model as SCADA custom symbol: behaviors drive bindingSchema; bindings map keys to variables. */
export function resolveSvgInteractiveConfig(widget: SvgWidget): SvgInteractiveConfig | null {
  const behaviors = readSvgWidgetBehaviors(widget);
  const bindings = readSvgWidgetBindings(widget);
  if (!behaviors.length || Object.keys(bindings).length === 0) {
    const legacy = parseTopologyConfig(widget.topologyJson);
    if (!legacy?.behaviors.length || !Object.keys(legacy.bindings).length) {
      return null;
    }
    return {
      viewBox: widget.viewBox ?? legacy.viewBox ?? "0 0 1309 503",
      backgroundColor: widget.backgroundColor ?? legacy.backgroundColor ?? "#EEF2F6",
      bindings: legacy.bindings,
      behaviors: legacy.behaviors,
      bindingSchema: readSvgWidgetBindingSchema(widget, legacy.behaviors),
      hitAreas: legacy.hitAreas,
      svgInner: legacy.svgInner,
    };
  }

  return {
    viewBox: widget.viewBox ?? "0 0 1309 503",
    backgroundColor: widget.backgroundColor ?? "#EEF2F6",
    bindings,
    behaviors,
    bindingSchema: readSvgWidgetBindingSchema(widget, behaviors),
    hitAreas: readSvgWidgetHitAreas(widget),
    svgInner: readSvgWidgetInner(widget),
  };
}

export function isSvgInteractiveWidget(widget: SvgWidget): boolean {
  return resolveSvgInteractiveConfig(widget) !== null;
}

export function serializeSvgInteractivePatch(input: {
  behaviors: MimicSymbolBehavior[];
  bindings: Record<string, MimicBinding>;
  bindingSchema: MimicBindingSlot[];
  hitAreas: TopologySvgHitArea[];
  svgInner?: string;
  viewBox?: string;
  backgroundColor?: string;
}): Partial<SvgWidget> {
  return {
    behaviorsJson: JSON.stringify(input.behaviors),
    bindingsJson: JSON.stringify(input.bindings),
    bindingSchemaJson: JSON.stringify(input.bindingSchema),
    hitAreasJson: JSON.stringify(input.hitAreas),
    svgInnerJson: input.svgInner,
    viewBox: input.viewBox,
    backgroundColor: input.backgroundColor,
    topologyJson: undefined,
  };
}
