import type { DashboardWidget } from "../../types/dashboard";
import { parseJsonArray, parseJsonObject } from "./widgetEditorJson";

/** JSON maps whose values are session.params names. */
const SESSION_PARAM_VALUE_FIELDS = ["paramBindingsJson", "syncFieldsToSessionJson"] as const;

/** JSON maps whose keys are session.params names. */
const SESSION_PARAM_KEY_FIELDS = ["rowParamsFromRowJson", "rowParamsJson", "cardParamsJson"] as const;

/** JSON arrays of session.params names. */
const SESSION_PARAM_LIST_FIELDS = ["requireSessionParamsJson", "clearSessionParamsJson"] as const;

function addName(names: Set<string>, value: unknown) {
  if (typeof value === "string" && value.trim()) names.add(value.trim());
}

function addObjectSide(names: Set<string>, raw: unknown, side: "key" | "value") {
  if (typeof raw !== "string") return;
  const parsed = parseJsonObject(raw);
  for (const [key, value] of Object.entries(parsed)) {
    addName(names, side === "key" ? key : value);
  }
}

/** Unique session.params names already set on widgets of this dashboard, sorted. */
export function collectDashboardSessionParams(widgets: DashboardWidget[]): string[] {
  const names = new Set<string>();
  for (const widget of widgets) {
    const record = widget as unknown as Record<string, unknown>;
    addName(names, record.paramKey);
    addName(names, record.contextPathKey);
    for (const field of SESSION_PARAM_VALUE_FIELDS) {
      addObjectSide(names, record[field], "value");
    }
    for (const field of SESSION_PARAM_KEY_FIELDS) {
      addObjectSide(names, record[field], "key");
    }
    if (widget.type === "report") {
      addObjectSide(names, record.contextParamsJson, "value");
    }
    if (widget.type === "dashboard-link") {
      addObjectSide(names, record.contextParamsJson, "key");
    }
    for (const field of SESSION_PARAM_LIST_FIELDS) {
      const raw = record[field];
      if (typeof raw !== "string") continue;
      for (const item of parseJsonArray<unknown>(raw, [])) addName(names, item);
    }
    if (widget.type === "function-form" && typeof record.fieldsJson === "string") {
      for (const field of parseJsonArray<{ paramKey?: unknown }>(record.fieldsJson, [])) {
        addName(names, field?.paramKey);
      }
    }
  }
  return [...names].sort((a, b) => a.localeCompare(b));
}
