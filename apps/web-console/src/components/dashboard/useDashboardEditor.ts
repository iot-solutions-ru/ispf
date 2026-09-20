// Extracted from DashboardEditorContext.tsx so that component modules only export components
// (react-refresh/only-export-components keeps Fast Refresh state-preserving).
import { createContext, useContext } from "react";
import type { DashboardLayout, DashboardWidget, WidgetType } from "../../types/dashboard";
import type { WidgetSlotRef } from "./widgetLayoutTree";

export interface ContainerActiveSlots {
  tabId: Record<string, string>;
  slideIndex: Record<string, number>;
  stepId: Record<string, string>;
}

export interface DashboardEditorContextValue {
  enabled: boolean;
  layout: DashboardLayout;
  refreshIntervalMs: number;
  selectedWidgetId: string | null;
  draggingWidgetId: string | null;
  dropTargetSlotKey: string | null;
  activeSlots: ContainerActiveSlots;
  selectWidget: (widgetId: string | null) => void;
  setChildrenAtSlot: (slot: WidgetSlotRef, children: DashboardWidget[]) => void;
  updateWidget: (widget: DashboardWidget) => void;
  addWidget: (type: WidgetType) => void;
  deleteSelectedWidget: () => void;
  setDraggingWidgetId: (widgetId: string | null) => void;
  setDropTargetSlotKey: (slotKey: string | null) => void;
  reparentToSlot: (widgetId: string, slot: WidgetSlotRef) => void;
  setActiveTab: (containerId: string, tabId: string) => void;
  setActiveSlide: (containerId: string, slideIndex: number) => void;
  setActiveStep: (containerId: string, stepId: string) => void;
}

export const DashboardEditorContext = createContext<DashboardEditorContextValue | null>(null);

export function useDashboardEditor(): DashboardEditorContextValue | null {
  return useContext(DashboardEditorContext);
}

export function useDashboardEditorRequired(): DashboardEditorContextValue {
  const ctx = useContext(DashboardEditorContext);
  if (!ctx) {
    throw new Error("DashboardEditorContext is required");
  }
  return ctx;
}
