import type {
  DashboardLayout,
  DashboardWidget,
  TabPanelTab,
  TabPanelWidget,
} from "../../types/dashboard";
import { parseJsonArray } from "./dashboardUtils";

export const NESTED_CONTAINER_TYPES = [
  "panel",
  "composite-widget",
  "drawer-panel",
  "tab-panel",
  "carousel",
  "steps-panel",
] as const;

export type NestedContainerType = (typeof NESTED_CONTAINER_TYPES)[number];

export type WidgetSlotRef =
  | { kind: "root" }
  | { kind: "children"; containerId: string }
  | { kind: "tab"; containerId: string; tabId: string }
  | { kind: "slide"; containerId: string; slideIndex: number }
  | { kind: "step"; containerId: string; stepId: string };

export interface WidgetLocation {
  widget: DashboardWidget;
  slot: WidgetSlotRef;
  parent: DashboardWidget | null;
}

interface CarouselSlide {
  id?: string;
  label?: string;
  children: DashboardWidget[];
}

interface StepDef {
  id: string;
  label: string;
  children: DashboardWidget[];
}

export function isNestedContainerType(type: string): type is NestedContainerType {
  return (NESTED_CONTAINER_TYPES as readonly string[]).includes(type);
}

export function slotRefKey(slot: WidgetSlotRef): string {
  switch (slot.kind) {
    case "root":
      return "root";
    case "children":
      return `children:${slot.containerId}`;
    case "tab":
      return `tab:${slot.containerId}:${slot.tabId}`;
    case "slide":
      return `slide:${slot.containerId}:${slot.slideIndex}`;
    case "step":
      return `step:${slot.containerId}:${slot.stepId}`;
  }
}

export function parseSlotRefKey(key: string): WidgetSlotRef | null {
  if (key === "root") return { kind: "root" };
  const [kind, containerId, extra] = key.split(":");
  if (!containerId) return null;
  switch (kind) {
    case "children":
      return { kind: "children", containerId };
    case "tab":
      return extra ? { kind: "tab", containerId, tabId: extra } : null;
    case "slide":
      return extra != null && extra !== ""
        ? { kind: "slide", containerId, slideIndex: Number(extra) }
        : null;
    case "step":
      return extra ? { kind: "step", containerId, stepId: extra } : null;
    default:
      return null;
  }
}

function readTabs(widget: TabPanelWidget): TabPanelTab[] {
  try {
    const parsed = widget.tabsJson ? (JSON.parse(widget.tabsJson) as TabPanelTab[]) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function readSlides(widget: DashboardWidget): CarouselSlide[] {
  try {
    const raw = (widget as { slidesJson?: string }).slidesJson;
    const parsed = raw ? (JSON.parse(raw) as CarouselSlide[]) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function readSteps(widget: DashboardWidget): StepDef[] {
  try {
    const raw = (widget as { stepsJson?: string }).stepsJson;
    const parsed = raw ? (JSON.parse(raw) as StepDef[]) : [];
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

export function getSlotChildrenFromContainer(
  container: DashboardWidget,
  slot: WidgetSlotRef
): DashboardWidget[] {
  if (slot.kind === "children") {
    return parseJsonArray<DashboardWidget>((container as { childrenJson?: string }).childrenJson, []);
  }
  if (slot.kind === "tab") {
    return readTabs(container as TabPanelWidget).find((tab) => tab.id === slot.tabId)?.children ?? [];
  }
  if (slot.kind === "slide") {
    return readSlides(container)[slot.slideIndex]?.children ?? [];
  }
  if (slot.kind === "step") {
    return readSteps(container).find((step) => step.id === slot.stepId)?.children ?? [];
  }
  return [];
}

export function patchContainerSlot(
  container: DashboardWidget,
  slot: WidgetSlotRef,
  children: DashboardWidget[]
): DashboardWidget {
  if (slot.kind === "children") {
    return { ...container, childrenJson: JSON.stringify(children) } as DashboardWidget;
  }
  if (slot.kind === "tab") {
    const tabs = readTabs(container as TabPanelWidget).map((tab) =>
      tab.id === slot.tabId ? { ...tab, children } : tab
    );
    return { ...container, tabsJson: JSON.stringify(tabs) } as DashboardWidget;
  }
  if (slot.kind === "slide") {
    const slides = readSlides(container).map((slide, index) =>
      index === slot.slideIndex ? { ...slide, children } : slide
    );
    return { ...container, slidesJson: JSON.stringify(slides) } as DashboardWidget;
  }
  if (slot.kind === "step") {
    const steps = readSteps(container).map((step) =>
      step.id === slot.stepId ? { ...step, children } : step
    );
    return { ...container, stepsJson: JSON.stringify(steps) } as DashboardWidget;
  }
  return container;
}

function mapContainerDescendants(
  widget: DashboardWidget,
  mapper: (child: DashboardWidget, slot: WidgetSlotRef, container: DashboardWidget) => DashboardWidget
): DashboardWidget {
  let next = widget;
  if (isNestedContainerType(widget.type)) {
    if (widget.type === "tab-panel") {
      const tabs = readTabs(widget as TabPanelWidget);
      const nextTabs = tabs.map((tab) => ({
        ...tab,
        children: tab.children.map((child) =>
          mapper(child, { kind: "tab", containerId: widget.id, tabId: tab.id }, widget)
        ),
      }));
      next = { ...widget, tabsJson: JSON.stringify(nextTabs) };
    } else if (widget.type === "carousel") {
      const slides = readSlides(widget).map((slide, index) => ({
        ...slide,
        children: (slide.children ?? []).map((child) =>
          mapper(child, { kind: "slide", containerId: widget.id, slideIndex: index }, widget)
        ),
      }));
      next = { ...widget, slidesJson: JSON.stringify(slides) };
    } else if (widget.type === "steps-panel") {
      const steps = readSteps(widget).map((step) => ({
        ...step,
        children: step.children.map((child) =>
          mapper(child, { kind: "step", containerId: widget.id, stepId: step.id }, widget)
        ),
      }));
      next = { ...widget, stepsJson: JSON.stringify(steps) };
    } else {
      const children = getSlotChildrenFromContainer(widget, { kind: "children", containerId: widget.id }).map(
        (child) => mapper(child, { kind: "children", containerId: widget.id }, widget)
      );
      next = patchContainerSlot(widget, { kind: "children", containerId: widget.id }, children);
    }
  }
  return next;
}

function forEachContainerChild(
  container: DashboardWidget,
  visit: (child: DashboardWidget) => void
): void {
  if (!isNestedContainerType(container.type)) return;
  if (container.type === "tab-panel") {
    for (const tab of readTabs(container as TabPanelWidget)) {
      for (const child of tab.children ?? []) visit(child);
    }
    return;
  }
  if (container.type === "carousel") {
    for (const slide of readSlides(container)) {
      for (const child of slide.children ?? []) visit(child);
    }
    return;
  }
  if (container.type === "steps-panel") {
    for (const step of readSteps(container)) {
      for (const child of step.children ?? []) visit(child);
    }
    return;
  }
  for (const child of getSlotChildrenFromContainer(container, {
    kind: "children",
    containerId: container.id,
  })) {
    visit(child);
  }
}

function findContainerInWidget(widget: DashboardWidget, containerId: string): DashboardWidget | null {
  if (widget.id === containerId) return widget;
  if (!isNestedContainerType(widget.type)) return null;
  let found: DashboardWidget | null = null;
  forEachContainerChild(widget, (child) => {
    if (found) return;
    found = findContainerInWidget(child, containerId);
  });
  return found;
}

export function findContainerWidget(layout: DashboardLayout, containerId: string): DashboardWidget | null {
  for (const widget of layout.widgets) {
    const found = findContainerInWidget(widget, containerId);
    if (found) return found;
  }
  return null;
}

export function findWidgetInLayout(layout: DashboardLayout, widgetId: string): WidgetLocation | null {
  for (const widget of layout.widgets) {
    if (widget.id === widgetId) {
      return { widget, slot: { kind: "root" }, parent: null };
    }
    const nested = findWidgetInContainer(widget, widgetId);
    if (nested) return nested;
  }
  return null;
}

function findWidgetInContainer(
  container: DashboardWidget,
  widgetId: string
): WidgetLocation | null {
  if (!isNestedContainerType(container.type)) return null;

  if (container.type === "tab-panel") {
    for (const tab of readTabs(container as TabPanelWidget)) {
      for (const child of tab.children ?? []) {
        if (child.id === widgetId) {
          return {
            widget: child,
            slot: { kind: "tab", containerId: container.id, tabId: tab.id },
            parent: container,
          };
        }
        const deeper = findWidgetInContainer(child, widgetId);
        if (deeper) return deeper;
      }
    }
    return null;
  }

  if (container.type === "carousel") {
    const slides = readSlides(container);
    for (let index = 0; index < slides.length; index++) {
      for (const child of slides[index]?.children ?? []) {
        if (child.id === widgetId) {
          return {
            widget: child,
            slot: { kind: "slide", containerId: container.id, slideIndex: index },
            parent: container,
          };
        }
        const deeper = findWidgetInContainer(child, widgetId);
        if (deeper) return deeper;
      }
    }
    return null;
  }

  if (container.type === "steps-panel") {
    for (const step of readSteps(container)) {
      for (const child of step.children ?? []) {
        if (child.id === widgetId) {
          return {
            widget: child,
            slot: { kind: "step", containerId: container.id, stepId: step.id },
            parent: container,
          };
        }
        const deeper = findWidgetInContainer(child, widgetId);
        if (deeper) return deeper;
      }
    }
    return null;
  }

  const slot: WidgetSlotRef = { kind: "children", containerId: container.id };
  for (const child of getSlotChildrenFromContainer(container, slot)) {
    if (child.id === widgetId) {
      return { widget: child, slot, parent: container };
    }
    const deeper = findWidgetInContainer(child, widgetId);
    if (deeper) return deeper;
  }
  return null;
}

export function getChildrenAtSlot(layout: DashboardLayout, slot: WidgetSlotRef): DashboardWidget[] {
  if (slot.kind === "root") return layout.widgets;
  const container = findContainerWidget(layout, slot.containerId);
  if (!container) return [];
  return getSlotChildrenFromContainer(container, slot);
}

function updateContainerInTree(
  widget: DashboardWidget,
  containerId: string,
  updater: (container: DashboardWidget) => DashboardWidget
): DashboardWidget {
  if (widget.id === containerId) {
    return updater(widget);
  }
  return mapContainerDescendants(widget, (child) =>
    updateContainerInTree(child, containerId, updater)
  );
}

export function setChildrenAtSlot(
  layout: DashboardLayout,
  slot: WidgetSlotRef,
  children: DashboardWidget[]
): DashboardLayout {
  if (slot.kind === "root") {
    return { ...layout, widgets: children };
  }
  return {
    ...layout,
    widgets: layout.widgets.map((widget) =>
      updateContainerInTree(widget, slot.containerId, (container) =>
        patchContainerSlot(container, slot, children)
      )
    ),
  };
}

export function updateWidgetInLayout(layout: DashboardLayout, widget: DashboardWidget): DashboardLayout {
  const location = findWidgetInLayout(layout, widget.id);
  if (!location) return layout;
  const siblings = getChildrenAtSlot(layout, location.slot).map((item) =>
    item.id === widget.id ? widget : item
  );
  return setChildrenAtSlot(layout, location.slot, siblings);
}

export function removeWidgetFromLayout(
  layout: DashboardLayout,
  widgetId: string
): { layout: DashboardLayout; removed: DashboardWidget | null } {
  const location = findWidgetInLayout(layout, widgetId);
  if (!location) return { layout, removed: null };
  const siblings = getChildrenAtSlot(layout, location.slot).filter((item) => item.id !== widgetId);
  return {
    layout: setChildrenAtSlot(layout, location.slot, siblings),
    removed: location.widget,
  };
}

export function defaultChildPosition(
  children: DashboardWidget[],
  columns: number
): Pick<DashboardWidget, "x" | "y" | "w" | "h"> {
  const w = Math.min(columns, Math.max(12, Math.floor(columns / 4)));
  if (children.length === 0) {
    return { x: 0, y: 0, w, h: 14 };
  }
  let maxBottom = 0;
  for (const child of children) {
    maxBottom = Math.max(maxBottom, child.y + child.h);
  }
  return { x: 0, y: maxBottom, w, h: 14 };
}

function containsWidget(container: DashboardWidget, widgetId: string): boolean {
  return findWidgetInContainer(container, widgetId) != null;
}

export function canReparentInto(
  layout: DashboardLayout,
  widgetId: string,
  targetSlot: WidgetSlotRef
): boolean {
  if (targetSlot.kind === "root") return true;
  if (widgetId === targetSlot.containerId) return false;
  const moving = findContainerWidget(layout, widgetId);
  if (!moving || !isNestedContainerType(moving.type)) return true;
  const targetContainer = findContainerWidget(layout, targetSlot.containerId);
  if (!targetContainer) return false;
  return !containsWidget(moving, targetContainer.id);
}

export function reparentWidgetToSlot(
  layout: DashboardLayout,
  widgetId: string,
  targetSlot: WidgetSlotRef,
  position?: Partial<Pick<DashboardWidget, "x" | "y" | "w" | "h">>
): DashboardLayout {
  if (!canReparentInto(layout, widgetId, targetSlot)) return layout;
  const { layout: without, removed } = removeWidgetFromLayout(layout, widgetId);
  if (!removed) return layout;
  const siblings = getChildrenAtSlot(without, targetSlot);
  const defaults = defaultChildPosition(siblings, without.columns);
  const nextChild: DashboardWidget = {
    ...removed,
    x: position?.x ?? removed.x ?? defaults.x,
    y: position?.y ?? removed.y ?? defaults.y,
    w: position?.w ?? removed.w ?? defaults.w,
    h: position?.h ?? removed.h ?? defaults.h,
  };
  return setChildrenAtSlot(without, targetSlot, [...siblings, nextChild]);
}

export function resolveAddTargetSlot(
  layout: DashboardLayout,
  selectedWidgetId: string | null,
  activeSlots: {
    tabId?: Record<string, string>;
    slideIndex?: Record<string, number>;
    stepId?: Record<string, string>;
  }
): WidgetSlotRef {
  if (!selectedWidgetId) return { kind: "root" };
  const selected = findWidgetInLayout(layout, selectedWidgetId);
  if (!selected) return { kind: "root" };

  if (isNestedContainerType(selected.widget.type)) {
    const container = selected.widget;
    if (container.type === "tab-panel") {
      const tabs = readTabs(container as TabPanelWidget);
      const tabId = activeSlots.tabId?.[container.id] ?? tabs[0]?.id;
      if (tabId) return { kind: "tab", containerId: container.id, tabId };
    }
    if (container.type === "carousel") {
      const slideIndex = activeSlots.slideIndex?.[container.id] ?? 0;
      return { kind: "slide", containerId: container.id, slideIndex };
    }
    if (container.type === "steps-panel") {
      const steps = readSteps(container);
      const stepId = activeSlots.stepId?.[container.id] ?? steps[0]?.id;
      if (stepId) return { kind: "step", containerId: container.id, stepId };
    }
    return { kind: "children", containerId: container.id };
  }

  return selected.slot;
}

export function defaultSlotForContainer(
  container: DashboardWidget,
  activeSlots: {
    tabId?: Record<string, string>;
    slideIndex?: Record<string, number>;
    stepId?: Record<string, string>;
  }
): WidgetSlotRef {
  if (container.type === "tab-panel") {
    const tabs = readTabs(container as TabPanelWidget);
    const tabId = activeSlots.tabId?.[container.id] ?? tabs[0]?.id ?? "tab-1";
    return { kind: "tab", containerId: container.id, tabId };
  }
  if (container.type === "carousel") {
    return {
      kind: "slide",
      containerId: container.id,
      slideIndex: activeSlots.slideIndex?.[container.id] ?? 0,
    };
  }
  if (container.type === "steps-panel") {
    const steps = readSteps(container);
    const stepId = activeSlots.stepId?.[container.id] ?? steps[0]?.id ?? "step-1";
    return { kind: "step", containerId: container.id, stepId };
  }
  return { kind: "children", containerId: container.id };
}
