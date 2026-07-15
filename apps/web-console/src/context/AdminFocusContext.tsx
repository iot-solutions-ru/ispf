import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from "react";

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

interface AdminFocusLayer {
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

const AdminFocusContext = createContext<AdminFocusContextValue | null>(null);

const SURFACE_PRIORITY: Partial<Record<AdminFocusSurface, number>> = {
  "expression-editor": 100,
  "binding-rule": 70,
  properties: 40,
  binding: 45,
  alert: 40,
  system: 55,
  "ai-studio": 60,
  dashboard: 30,
  report: 30,
  workflow: 30,
  mimic: 30,
  blueprint: 30,
  schedule: 30,
  application: 30,
  "data-source": 30,
  migration: 30,
  explorer: 10,
  other: 5,
};

const MAX_RECENT_ACTIONS = 16;

function resolvePriority(focus: AdminClientFocus): number {
  if (typeof focus.priority === "number") {
    return focus.priority;
  }
  return SURFACE_PRIORITY[focus.surface] ?? 0;
}

function sortLayers(layers: AdminFocusLayer[]): AdminFocusLayer[] {
  return [...layers].sort((a, b) => resolvePriority(a.focus) - resolvePriority(b.focus));
}

function pickTop(layers: AdminFocusLayer[]): AdminClientFocus | null {
  const sorted = sortLayers(layers);
  return sorted.length === 0 ? null : sorted[sorted.length - 1]!.focus;
}

export function focusLayerLabel(focus: AdminClientFocus): string {
  const detail = focus.detail ?? {};
  if (focus.surface === "expression-editor") {
    const title =
      typeof detail.editorTitle === "string" && detail.editorTitle.trim()
        ? detail.editorTitle.trim()
        : "expression";
    const expr = typeof detail.expression === "string" ? detail.expression.trim() : "";
    if (expr) {
      return `${title}: ${expr.length > 40 ? `${expr.slice(0, 40)}…` : expr}`;
    }
    return title;
  }
  if (focus.surface === "binding-rule") {
    const id = typeof detail.ruleId === "string" ? detail.ruleId : "";
    return id ? `rule:${id}` : "binding-rule";
  }
  if (focus.surface === "properties" || focus.surface === "binding") {
    const tab = typeof detail.inspectorTab === "string" ? detail.inspectorTab : "";
    if (tab) {
      return `${focus.surface}/${tab}`;
    }
  }
  if (focus.surface === "system") {
    const settingsTab = typeof detail.settingsTab === "string" ? detail.settingsTab : "";
    const systemTab = typeof detail.systemTab === "string" ? detail.systemTab : "";
    if (settingsTab) {
      return `system/settings/${settingsTab}`;
    }
    if (systemTab) {
      return `system/${systemTab}`;
    }
    return "system";
  }
  if (focus.surface === "ai-studio") {
    const studioTab = typeof detail.studioTab === "string" ? detail.studioTab : "";
    return studioTab ? `ai-studio/${studioTab}` : "ai-studio";
  }
  const path = focus.objectPath?.trim();
  if (path) {
    const short = path.length > 32 ? `…${path.slice(-30)}` : path;
    return `${focus.surface}:${short}`;
  }
  return focus.surface;
}

function compactDetailForTrail(detail: Record<string, unknown> | undefined): Record<string, unknown> | undefined {
  if (!detail) {
    return undefined;
  }
  const out: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(detail)) {
    if (value == null) {
      continue;
    }
    if (key === "rules" && Array.isArray(value)) {
      out.rules = value.slice(0, 20);
      continue;
    }
    if (key === "sampleVariables" && Array.isArray(value)) {
      out.sampleVariables = value.slice(0, 24);
      continue;
    }
    if (typeof value === "string" && value.length > 400) {
      out[key] = `${value.slice(0, 400)}…`;
      continue;
    }
    out[key] = value;
  }
  return Object.keys(out).length > 0 ? out : undefined;
}

export function buildClientFocusPayload(
  stack: AdminClientFocus[],
  recentActions: AdminFocusActivity[]
): AdminClientFocusPayload | null {
  if (stack.length === 0) {
    return null;
  }
  const top = stack[stack.length - 1]!;
  const trail = stack.map((step) => ({
    surface: step.surface,
    objectPath: step.objectPath,
    objectType: step.objectType,
    label: focusLayerLabel(step),
    detail: compactDetailForTrail(step.detail),
  }));
  return {
    surface: top.surface,
    objectPath: top.objectPath,
    objectType: top.objectType,
    editorTabId: top.editorTabId,
    detail: {
      ...compactDetailForTrail(top.detail),
      trail,
      recentActions: recentActions.slice(-MAX_RECENT_ACTIONS),
    },
  };
}

export function AdminFocusProvider({ children }: { children: ReactNode }) {
  const [layers, setLayers] = useState<AdminFocusLayer[]>([]);
  const [copilotOpenToken, setCopilotOpenToken] = useState(0);
  const [recentActions, setRecentActions] = useState<AdminFocusActivity[]>([]);
  const lastTopKey = useRef<string>("");
  const pendingCopilotPromptRef = useRef<string | null>(null);

  const publishFocus = useCallback((id: string, focus: AdminClientFocus) => {
    setLayers((prev) => {
      const next = prev.filter((layer) => layer.id !== id);
      next.push({ id, focus });
      return next;
    });
  }, []);

  const clearFocus = useCallback((id: string) => {
    setLayers((prev) => prev.filter((layer) => layer.id !== id));
  }, []);

  const requestOpenCopilot = useCallback((prompt?: string) => {
    const trimmed = prompt?.trim();
    pendingCopilotPromptRef.current = trimmed && trimmed.length > 0 ? trimmed : null;
    setCopilotOpenToken((n) => n + 1);
  }, []);

  const takePendingCopilotPrompt = useCallback(() => {
    const prompt = pendingCopilotPromptRef.current;
    pendingCopilotPromptRef.current = null;
    return prompt;
  }, []);

  const focusStack = useMemo(
    () => sortLayers(layers).map((layer) => layer.focus),
    [layers]
  );
  const focus = useMemo(() => pickTop(layers), [layers]);
  const focusTrail = useMemo(
    () =>
      focusStack.map((step) => ({
        surface: step.surface,
        objectPath: step.objectPath,
        objectType: step.objectType,
        label: focusLayerLabel(step),
        detail: compactDetailForTrail(step.detail),
      })),
    [focusStack]
  );

  // Append to activity ring when the top focus identity changes.
  useEffect(() => {
    if (!focus) {
      lastTopKey.current = "";
      return;
    }
    const key = `${focus.surface}|${focus.objectPath ?? ""}|${focusLayerLabel(focus)}`;
    if (key === lastTopKey.current) {
      return;
    }
    lastTopKey.current = key;
    const entry: AdminFocusActivity = {
      at: new Date().toISOString(),
      surface: focus.surface,
      objectPath: focus.objectPath,
      label: focusLayerLabel(focus),
    };
    setRecentActions((prev) => [...prev, entry].slice(-MAX_RECENT_ACTIONS));
  }, [focus]);

  const toClientFocusPayload = useCallback(
    () => buildClientFocusPayload(focusStack, recentActions),
    [focusStack, recentActions]
  );

  const value = useMemo(
    () => ({
      focus,
      focusStack,
      focusTrail,
      recentActions,
      toClientFocusPayload,
      publishFocus,
      clearFocus,
      copilotOpenToken,
      requestOpenCopilot,
      takePendingCopilotPrompt,
    }),
    [
      focus,
      focusStack,
      focusTrail,
      recentActions,
      toClientFocusPayload,
      publishFocus,
      clearFocus,
      copilotOpenToken,
      requestOpenCopilot,
      takePendingCopilotPrompt,
    ]
  );

  return <AdminFocusContext.Provider value={value}>{children}</AdminFocusContext.Provider>;
}

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

export function formatAdminFocusChip(
  focus: AdminClientFocus | null,
  trail?: AdminFocusTrailStep[] | null
): string {
  if (trail && trail.length > 0) {
    const parts = trail.map((step) => {
      if (step.surface === "expression-editor") {
        return step.label.length > 42 ? `${step.label.slice(0, 42)}…` : step.label;
      }
      if (step.surface === "binding-rule") {
        return step.label;
      }
      if (step.surface === "binding") {
        const leaf = step.objectPath?.split(".").pop() ?? "object";
        return `${leaf}/computations`;
      }
      if (step.surface === "system" || step.surface === "ai-studio") {
        return step.label;
      }
      if (step.objectPath) {
        const leaf = step.objectPath.split(".").pop() ?? step.objectPath;
        if (step.surface === "explorer" || step.surface === "properties") {
          const tab =
            typeof step.detail?.inspectorTab === "string" ? step.detail.inspectorTab : "";
          return tab ? `${leaf}/${tab}` : leaf;
        }
        return step.label.includes(":") ? step.label.split(":").slice(-1)[0]! : leaf;
      }
      return step.surface;
    });
    const joined = parts.join(" › ");
    return joined.length > 72 ? `…${joined.slice(-70)}` : joined;
  }
  if (!focus) {
    return "";
  }
  return focusLayerLabel(focus);
}

export function objectTypeToFocusSurface(objectType: string | undefined | null): AdminFocusSurface {
  switch (objectType) {
    case "DASHBOARD":
      return "dashboard";
    case "REPORT":
      return "report";
    case "WORKFLOW":
      return "workflow";
    case "MIMIC":
      return "mimic";
    case "BLUEPRINT":
      return "blueprint";
    case "BINDING":
      return "binding";
    case "SCHEDULE":
      return "schedule";
    case "APPLICATION":
      return "application";
    case "DATA_SOURCE":
      return "data-source";
    case "MIGRATION":
      return "migration";
    case "ALERT":
    case "ALERT_RULE":
      return "alert";
    default:
      return "other";
  }
}
