// Extracted from AdminFocusContext.tsx so that component modules only export components
// (react-refresh/only-export-components keeps Fast Refresh state-preserving).
import { createContext, useContext } from "react";

export type AdminFocusSurface =
  | "explorer"
  | "system"
  | "ai-studio"
  | "dashboard"
  | "report"
  | "workflow"
  | "mimic"
  | "blueprint"
  | "binding"
  | "binding-rule"
  | "schedule"
  | "application"
  | "data-source"
  | "migration"
  | "properties"
  | "expression-editor"
  | "alert"
  | "other";

export interface AdminClientFocus {
  surface: AdminFocusSurface;
  objectPath?: string;
  objectType?: string;
  editorTabId?: string;
  detail?: Record<string, unknown>;
  /** Higher wins when multiple layers publish (modals > editors > explorer). */
  priority?: number;
}

export interface AdminFocusTrailStep {
  surface: AdminFocusSurface;
  objectPath?: string;
  objectType?: string;
  label: string;
  detail?: Record<string, unknown>;
}

export interface AdminFocusActivity {
  at: string;
  surface: AdminFocusSurface;
  objectPath?: string;
  label: string;
}

export interface AdminFocusLayer {
  id: string;
  focus: AdminClientFocus;
}

/** Payload sent to the agent API (top focus + trail + recent actions). */
export interface AdminClientFocusPayload {
  surface: string;
  objectPath?: string;
  objectType?: string;
  editorTabId?: string;
  detail?: Record<string, unknown>;
}

interface AdminFocusContextValue {
  /** Highest-priority focus layer. */
  focus: AdminClientFocus | null;
  /** Layers sorted by priority ascending (explorer … expression). */
  focusStack: AdminClientFocus[];
  /** Breadcrumb labels for the chip / UI. */
  focusTrail: AdminFocusTrailStep[];
  recentActions: AdminFocusActivity[];
  /** Compact payload for POST .../messages clientFocus. */
  toClientFocusPayload: () => AdminClientFocusPayload | null;
  publishFocus: (id: string, focus: AdminClientFocus) => void;
  clearFocus: (id: string) => void;
  copilotOpenToken: number;
  /** Open Copilot drawer; optional prompt is auto-sent once after open. */
  requestOpenCopilot: (prompt?: string) => void;
  /** Consume one-shot prompt queued by requestOpenCopilot (clears it). */
  takePendingCopilotPrompt: () => string | null;
}

export const AdminFocusContext = createContext<AdminFocusContextValue | null>(null);

export function useAdminFocus(): AdminFocusContextValue {
  const ctx = useContext(AdminFocusContext);
  if (!ctx) {
    throw new Error("useAdminFocus must be used within AdminFocusProvider");
  }
  return ctx;
}

export function useAdminFocusOptional(): AdminFocusContextValue | null {
  return useContext(AdminFocusContext);
}
