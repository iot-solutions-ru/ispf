import { readFieldValue } from "../../types/dashboard";
import type {
  MapMarkerMode,
  MapMarkerRule,
  MapMarkerRuleOp,
  MapMarkerStyle,
  MapMarkerWhen,
  MapPopupMode,
  MapWidget,
} from "../../types/dashboard";
import type { ObjectSummary, VariableDto } from "../../types";
import { matchesNamePattern } from "./dashboardUtils";

const MARKER_OPS = new Set<MapMarkerRuleOp>(["eq", "ne", "gt", "gte", "lt", "lte", "in"]);

/** Parse JSON string array of paths / leaf names. Invalid JSON → []. */
export function parsePathListJson(raw?: string): string[] {
  if (!raw?.trim()) {
    return [];
  }
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    return parsed
      .map((item) => String(item ?? "").trim())
      .filter((item) => item.length > 0);
  } catch {
    return [];
  }
}

function pathOrLeafMatches(objPath: string, leaf: string, list: string[]): boolean {
  return list.some((entry) => entry === objPath || entry === leaf);
}

/** Filter catalog children for a map widget (namePattern / objectType / include / exclude). */
export function filterMapCatalogChildren(
  list: ObjectSummary[],
  widget: Pick<
    MapWidget,
    "namePattern" | "objectType" | "includePathsJson" | "excludePathsJson"
  >
): ObjectSummary[] {
  const include = parsePathListJson(widget.includePathsJson);
  const exclude = parsePathListJson(widget.excludePathsJson);
  return list.filter((obj) => {
    if (widget.objectType && obj.type !== widget.objectType) {
      return false;
    }
    const leaf = obj.path.split(".").pop() ?? "";
    if (widget.namePattern && !matchesNamePattern(leaf, widget.namePattern)) {
      return false;
    }
    if (include.length > 0 && !pathOrLeafMatches(obj.path, leaf, include)) {
      return false;
    }
    if (exclude.length > 0 && pathOrLeafMatches(obj.path, leaf, exclude)) {
      return false;
    }
    return true;
  });
}

/** True when `markerRulesJson` parses to at least one valid rule. */
export function hasMarkerRules(widget: Pick<MapWidget, "markerRulesJson">): boolean {
  return parseMarkerRules(widget.markerRulesJson).length > 0;
}

/** Resolve marker mode with legacy fallback when `statusVariable` is set. */
export function resolveMapMarkerMode(
  widget: Pick<MapWidget, "markerMode" | "statusVariable" | "markerRulesJson">
): MapMarkerMode {
  if (hasMarkerRules(widget)) {
    return "default";
  }
  if (widget.markerMode) {
    return widget.markerMode;
  }
  if (widget.statusVariable?.trim()) {
    return "status-color";
  }
  return "default";
}

/** Resolve popup mode with legacy fallback from `popupVariablesJson`. */
export function resolveMapPopupMode(
  widget: Pick<MapWidget, "popupMode" | "popupVariablesJson">
): MapPopupMode {
  if (widget.popupMode) {
    return widget.popupMode;
  }
  if (widget.popupVariablesJson?.trim()) {
    return "variables";
  }
  return "label";
}

function parseWhen(raw: unknown): MapMarkerWhen | null {
  if (raw == null || typeof raw !== "object") {
    return null;
  }
  const row = raw as Record<string, unknown>;
  const variable = String(row.variable ?? "").trim();
  const opRaw = String(row.op ?? "eq").trim().toLowerCase() as MapMarkerRuleOp;
  if (!variable || !MARKER_OPS.has(opRaw)) {
    return null;
  }
  if (row.value === undefined) {
    return null;
  }
  const field = row.field != null ? String(row.field).trim() : undefined;
  const value = row.value as string | number | boolean;
  return {
    variable,
    field: field || undefined,
    op: opRaw,
    value,
  };
}

function parseStyle(raw: unknown): MapMarkerStyle | null {
  if (raw == null || typeof raw !== "object") {
    return null;
  }
  const row = raw as Record<string, unknown>;
  const type = String(row.type ?? "").trim().toLowerCase();
  if (type === "color") {
    const color = String(row.color ?? "").trim();
    return color ? { type: "color", color } : null;
  }
  if (type === "icon") {
    const url = String(row.url ?? row.iconUrl ?? "").trim();
    if (!url) {
      return null;
    }
    const sizeRaw = row.size != null ? Number(row.size) : undefined;
    const size =
      sizeRaw != null && Number.isFinite(sizeRaw)
        ? Math.max(12, Math.min(96, sizeRaw))
        : undefined;
    return { type: "icon", url, size };
  }
  return null;
}

/** Serialize rules back to JSON for the widget layout field. */
export function serializeMarkerRules(rules: MapMarkerRule[]): string | undefined {
  if (rules.length === 0) {
    return undefined;
  }
  return JSON.stringify(
    rules.map((rule) => ({
      ...(rule.id ? { id: rule.id } : {}),
      ...(rule.label ? { label: rule.label } : {}),
      when: {
        variable: rule.when.variable,
        ...(rule.when.field ? { field: rule.when.field } : {}),
        op: rule.when.op,
        value: rule.when.value,
      },
      style: rule.style,
    }))
  );
}

/** Parse `markerRulesJson` into valid rules (skips invalid rows). */
export function parseMarkerRules(raw?: string): MapMarkerRule[] {
  if (!raw?.trim()) {
    return [];
  }
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) {
      return [];
    }
    const out: MapMarkerRule[] = [];
    for (const item of parsed) {
      if (item == null || typeof item !== "object") {
        continue;
      }
      const row = item as Record<string, unknown>;
      const when = parseWhen(row.when);
      const style = parseStyle(row.style);
      if (!when || !style) {
        continue;
      }
      out.push({
        id: row.id != null ? String(row.id) : undefined,
        label: row.label != null ? String(row.label) : undefined,
        when,
        style,
      });
    }
    return out;
  } catch {
    return [];
  }
}

function coerceNumber(raw: unknown): number | null {
  if (typeof raw === "number" && Number.isFinite(raw)) {
    return raw;
  }
  if (typeof raw === "boolean") {
    return raw ? 1 : 0;
  }
  if (raw == null || raw === "") {
    return null;
  }
  const n = Number(String(raw).trim());
  return Number.isFinite(n) ? n : null;
}

function normalizeInList(expected: string | number | boolean): string[] {
  if (typeof expected === "string") {
    const trimmed = expected.trim();
    if (trimmed.startsWith("[")) {
      try {
        const arr = JSON.parse(trimmed) as unknown;
        if (Array.isArray(arr)) {
          return arr.map((v) => String(v).trim().toUpperCase()).filter(Boolean);
        }
      } catch {
        /* fall through to CSV */
      }
    }
    return trimmed
      .split(",")
      .map((s) => s.trim().toUpperCase())
      .filter(Boolean);
  }
  return [String(expected).trim().toUpperCase()].filter(Boolean);
}

/** Evaluate a single structured when against object variables. */
export function evaluateMarkerWhen(
  variables: VariableDto[] | undefined,
  when: MapMarkerWhen
): boolean {
  const variable = variables?.find((v) => v.name === when.variable);
  if (!variable) {
    return false;
  }
  const field = when.field?.trim() || "value";
  const actual = readFieldValue(variable.value?.rows[0], field);
  if (actual == null || actual === "") {
    return false;
  }

  const op = when.op;
  if (op === "in") {
    const token = String(actual).trim().toUpperCase();
    return normalizeInList(when.value).includes(token);
  }

  if (op === "eq" || op === "ne") {
    const left = String(actual).trim().toUpperCase();
    const right = String(when.value).trim().toUpperCase();
    return op === "eq" ? left === right : left !== right;
  }

  const leftN = coerceNumber(actual);
  const rightN = coerceNumber(when.value);
  if (leftN == null || rightN == null) {
    return false;
  }
  switch (op) {
    case "gt":
      return leftN > rightN;
    case "gte":
      return leftN >= rightN;
    case "lt":
      return leftN < rightN;
    case "lte":
      return leftN <= rightN;
    default:
      return false;
  }
}

/**
 * First matching rule wins. Returns null when no rules or none match
 * (caller should fall back to default / legacy markerMode).
 */
export function evaluateMarkerRules(
  variables: VariableDto[] | undefined,
  rules: MapMarkerRule[]
): MapMarkerStyle | null {
  for (const rule of rules) {
    if (evaluateMarkerWhen(variables, rule.when)) {
      return rule.style;
    }
  }
  return null;
}
