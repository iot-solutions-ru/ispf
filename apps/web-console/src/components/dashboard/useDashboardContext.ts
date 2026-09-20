// Extracted from DashboardContext.tsx so that component modules only export components
// (react-refresh/only-export-components keeps Fast Refresh state-preserving).
import { createContext, useCallback, useContext } from "react";

export type DashboardOpenMode = "navigate" | "modal";

export interface DashboardSession {
  selection: Record<string, string>;
  params: Record<string, unknown>;
  widgets?: Record<string, { visible?: boolean }>;
}

export interface OpenDashboardOptions {
  selection?: Record<string, string>;
  params?: Record<string, unknown>;
  widgets?: Record<string, { visible?: boolean }>;
}

export function emptySession(): DashboardSession {
  return { selection: {}, params: {}, widgets: {} };
}

export function mergeSession(
  current: DashboardSession,
  patch?: OpenDashboardOptions
): DashboardSession {
  if (!patch) {
    return current;
  }
  return {
    selection: patch.selection ? { ...current.selection, ...patch.selection } : current.selection,
    params: patch.params ? { ...current.params, ...patch.params } : current.params,
    widgets: patch.widgets ? { ...(current.widgets ?? {}), ...patch.widgets } : current.widgets,
  };
}

export interface DashboardContextValue extends DashboardSession {
  operatorMode?: boolean;
  /** Dashboard is rendered inside a modal overlay */
  embeddedModal?: boolean;
  setSelection: (key: string, path: string) => void;
  setParams: (patch: Record<string, unknown>) => void;
  navigateToDashboard: (path: string, options?: OpenDashboardOptions) => void;
  openDashboardModal: (path: string, title?: string, options?: OpenDashboardOptions) => void;
  closeDashboardModal: () => void;
}

export const noop = () => {};

const defaultValue: DashboardContextValue = {
  selection: {},
  params: {},
  widgets: {},
  setSelection: noop,
  setParams: noop,
  navigateToDashboard: noop,
  openDashboardModal: noop,
  closeDashboardModal: noop,
};

export const DashboardContext = createContext<DashboardContextValue>(defaultValue);

export function useDashboardContext(): DashboardContextValue {
  return useContext(DashboardContext);
}

export function triggerDashboardOpen(
  mode: DashboardOpenMode | undefined,
  targetPath: string | undefined,
  title: string | undefined,
  actions: Pick<DashboardContextValue, "navigateToDashboard" | "openDashboardModal">,
  options?: OpenDashboardOptions
): boolean {
  const path = targetPath?.trim();
  if (!path) {
    return false;
  }
  if (mode === "modal") {
    actions.openDashboardModal(path, title, options);
  } else {
    actions.navigateToDashboard(path, options);
  }
  return true;
}

export function useApplyOpenOptions() {
  const ctx = useDashboardContext();
  return useCallback(
    (options?: OpenDashboardOptions) => {
      if (!options) {
        return;
      }
      if (options.selection) {
        for (const [key, path] of Object.entries(options.selection)) {
          ctx.setSelection(key, path);
        }
      }
      if (options.params) {
        ctx.setParams(options.params);
      }
    },
    [ctx]
  );
}
