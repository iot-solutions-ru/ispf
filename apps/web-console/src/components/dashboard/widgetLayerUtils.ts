import type { DashboardWidget } from "../../types/dashboard";

/** Effective stacking order; falls back to widget order in layout JSON. */
export function resolveWidgetZIndex(widget: DashboardWidget, index: number): number {
  if (typeof widget.zIndex === "number" && Number.isFinite(widget.zIndex)) {
    return widget.zIndex;
  }
  return index;
}

export function isWidgetVisible(widget: DashboardWidget): boolean {
  return widget.visible !== false;
}

export function maxWidgetZIndex(widgets: DashboardWidget[]): number {
  if (widgets.length === 0) {
    return 0;
  }
  return widgets.reduce(
    (max, widget, index) => Math.max(max, resolveWidgetZIndex(widget, index)),
    0
  );
}

export function nextWidgetZIndex(widgets: DashboardWidget[]): number {
  return maxWidgetZIndex(widgets) + 1;
}

export function sortWidgetsForRender(widgets: DashboardWidget[]): DashboardWidget[] {
  return widgets
    .map((widget, index) => ({ widget, index }))
    .sort(
      (a, b) =>
        resolveWidgetZIndex(a.widget, a.index) - resolveWidgetZIndex(b.widget, b.index)
    )
    .map(({ widget }) => widget);
}

function withResolvedZIndices(widgets: DashboardWidget[]): DashboardWidget[] {
  return widgets.map((widget, index) => ({
    ...widget,
    zIndex: resolveWidgetZIndex(widget, index),
  }));
}

export function bringWidgetForward(
  widgets: DashboardWidget[],
  widgetId: string
): DashboardWidget[] {
  const ordered = sortWidgetsForRender(withResolvedZIndices(widgets));
  const index = ordered.findIndex((widget) => widget.id === widgetId);
  if (index < 0 || index >= ordered.length - 1) {
    return widgets;
  }
  const current = ordered[index];
  const above = ordered[index + 1];
  const currentZ = current.zIndex ?? 0;
  const aboveZ = above.zIndex ?? 0;
  return widgets.map((widget) => {
    if (widget.id === current.id) {
      return { ...widget, zIndex: aboveZ };
    }
    if (widget.id === above.id) {
      return { ...widget, zIndex: currentZ };
    }
    return widget;
  });
}

export function sendWidgetBackward(
  widgets: DashboardWidget[],
  widgetId: string
): DashboardWidget[] {
  const ordered = sortWidgetsForRender(withResolvedZIndices(widgets));
  const index = ordered.findIndex((widget) => widget.id === widgetId);
  if (index <= 0) {
    return widgets;
  }
  const current = ordered[index];
  const below = ordered[index - 1];
  const currentZ = current.zIndex ?? 0;
  const belowZ = below.zIndex ?? 0;
  return widgets.map((widget) => {
    if (widget.id === current.id) {
      return { ...widget, zIndex: belowZ };
    }
    if (widget.id === below.id) {
      return { ...widget, zIndex: currentZ };
    }
    return widget;
  });
}

export function bringWidgetToFront(
  widgets: DashboardWidget[],
  widgetId: string
): DashboardWidget[] {
  const nextZ = nextWidgetZIndex(widgets);
  return widgets.map((widget) =>
    widget.id === widgetId ? { ...widget, zIndex: nextZ } : widget
  );
}

export function sendWidgetToBack(
  widgets: DashboardWidget[],
  widgetId: string
): DashboardWidget[] {
  const ordered = sortWidgetsForRender(withResolvedZIndices(widgets));
  const minZ = ordered.length > 0 ? (ordered[0].zIndex ?? 0) : 0;
  return widgets.map((widget) =>
    widget.id === widgetId ? { ...widget, zIndex: minZ - 1 } : widget
  );
}
