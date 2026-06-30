import type { MimicAction, MimicElement, ScadaMimicDocument } from "../types/scadaMimic";

export function primaryAction(element: MimicElement): MimicAction | undefined {
  const actions = element.actions ?? [];
  return actions.find((a) => a.trigger !== "context") ?? actions[0];
}

export function contextActions(element: MimicElement): MimicAction[] {
  return (element.actions ?? [])
    .filter((a) => a.trigger === "context")
    .sort((a, b) => (b.order ?? 0) - (a.order ?? 0));
}

export function applyToggleLayer(
  doc: ScadaMimicDocument,
  layerId: string
): ScadaMimicDocument {
  return {
    ...doc,
    layers: doc.layers.map((layer) =>
      layer.id === layerId ? { ...layer, visible: !layer.visible } : layer
    ),
  };
}

export function applyCycleUnit(element: MimicElement, action: MimicAction): MimicElement {
  const modes = action.unitModes ?? ["mm", "m3", "t"];
  const current = String(element.props?.unitMode ?? modes[0]);
  const idx = modes.indexOf(current);
  const next = modes[(idx + 1) % modes.length];
  return {
    ...element,
    props: { ...element.props, unitMode: next },
  };
}

export function applyToggleExpand(element: MimicElement, action: MimicAction): MimicElement {
  const key = action.expandProp ?? "tableExpand";
  const expanded = element.props?.[key] === "full";
  return {
    ...element,
    props: { ...element.props, [key]: expanded ? "compact" : "full" },
  };
}

export function resolveNavigateUrl(action: MimicAction, appId?: string): string | null {
  if (action.url?.trim()) return action.url.trim();
  if (action.dashboardPath?.trim()) {
    const dash = action.dashboardPath.trim();
    const params = new URLSearchParams();
    params.set("mode", "operator");
    if (appId) params.set("app", appId);
    return `/?${params.toString()}#${dash}`;
  }
  if (action.mimicPath?.trim()) {
    return null;
  }
  return null;
}

export function buildElementTooltip(
  element: MimicElement,
  values: Record<string, unknown>
): string | undefined {
  const tip = element.tooltip;
  if (!tip) return undefined;
  if (tip.template) {
    return tip.template.replace(/\{(\w+)\}/g, (_, key: string) => {
      const v = values[key] ?? element.props?.[key];
      return v == null ? "—" : String(v);
    });
  }
  if (tip.bindingKeys?.length) {
    return tip.bindingKeys
      .map((key) => {
        const v = values[key];
        return v == null ? null : `${key}: ${v}`;
      })
      .filter(Boolean)
      .join("\n");
  }
  return undefined;
}
