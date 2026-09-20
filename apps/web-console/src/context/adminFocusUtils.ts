// Extracted from AdminFocusContext.tsx so that component modules only export components
// (react-refresh/only-export-components keeps Fast Refresh state-preserving).
import type {
  AdminFocusSurface,
  AdminClientFocus,
  AdminFocusTrailStep,
  AdminFocusActivity,
  AdminClientFocusPayload,
} from "./useAdminFocus";

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

export function buildClientFocusPayload(
  stack: AdminClientFocus[],
  recentActions: AdminFocusActivity[]
): AdminClientFocusPayload | null {
  const top = stack.at(-1);
  if (!top) {
    return null;
  }
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
        return step.label.includes(":") ? (step.label.split(":").at(-1) ?? leaf) : leaf;
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

export const MAX_RECENT_ACTIONS = 16;

export function compactDetailForTrail(detail: Record<string, unknown> | undefined): Record<string, unknown> | undefined {
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
