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
  /**
   * Widget id that last wrote each selection slot.
   * Missing when the writer passed no owner, or the slot path was replaced elsewhere.
   */
  selectionOwner: Record<string, string>;
  setSelection: (key: string, path: string, ownerId?: string) => void;
  setParams: (patch: Record<string, unknown>) => void;
  navigateToDashboard: (path: string, options?: OpenDashboardOptions) => void;
  openDashboardModal: (path: string, title?: string, options?: OpenDashboardOptions) => void;
  closeDashboardModal: () => void;
}

export const noop = () => {};

/** Session param that remembers which widget wrote each selection slot. Survives refresh. */
export const SELECTION_OWNER_PARAM = "@selectionOwner";

type SelectionOwnerEntry = { path: string; ownerId: string };

function readSelectionOwnerMap(params: Record<string, unknown>): Record<string, SelectionOwnerEntry> {
  const raw = params[SELECTION_OWNER_PARAM];
  if (!raw || typeof raw !== "object") {
    return {};
  }
  const map: Record<string, SelectionOwnerEntry> = {};
  for (const [key, entry] of Object.entries(raw as Record<string, unknown>)) {
    if (!entry || typeof entry !== "object") {
      continue;
    }
    const path = (entry as SelectionOwnerEntry).path;
    const ownerId = (entry as SelectionOwnerEntry).ownerId;
    if (typeof path === "string" && typeof ownerId === "string") {
      map[key] = { path, ownerId };
    }
  }
  return map;
}

/** Owner counts only while it still matches the path currently stored in the slot. */
export function selectionOwnerFromSession(
  session: Pick<DashboardSession, "selection" | "params">
): Record<string, string> {
  const owners = readSelectionOwnerMap(session.params);
  const active: Record<string, string> = {};
  for (const [key, path] of Object.entries(session.selection)) {
    const owner = owners[key];
    if (owner && owner.path === path) {
      active[key] = owner.ownerId;
    }
  }
  return active;
}

export function sessionWithSelection(
  session: DashboardSession,
  key: string,
  path: string,
  ownerId?: string
): DashboardSession {
  const owners = { ...readSelectionOwnerMap(session.params) };
  if (ownerId) {
    owners[key] = { path, ownerId };
  } else {
    delete owners[key];
  }
  return {
    ...session,
    selection: { ...session.selection, [key]: path },
    params: { ...session.params, [SELECTION_OWNER_PARAM]: owners },
  };
}

const defaultValue: DashboardContextValue = {
  selection: {},
  params: {},
  widgets: {},
  selectionOwner: {},
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
