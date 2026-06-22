import type { ObjectSummary, TreeNode } from "../types";
import { APPLICATIONS_ROOT } from "./createObjectMode";

/** Hide legacy mirror subfolders under an app; keep the Applications folder and app nodes visible. */
function isHiddenLegacyApplicationPath(path: string): boolean {
  if (path === APPLICATIONS_ROOT) {
    return false;
  }
  const prefix = `${APPLICATIONS_ROOT}.`;
  if (!path.startsWith(prefix)) {
    return false;
  }
  const rest = path.slice(prefix.length);
  return rest.includes(".");
}

function compareObjects(a: ObjectSummary, b: ObjectSummary): number {
  const order = (a.sortOrder ?? 0) - (b.sortOrder ?? 0);
  if (order !== 0) {
    return order;
  }
  return a.displayName.localeCompare(b.displayName, undefined, { sensitivity: "base" });
}

export function parentObjectPath(path: string): string | null {
  if (path === "root") {
    return null;
  }
  const dot = path.lastIndexOf(".");
  return dot === -1 ? "root" : path.slice(0, dot);
}

export function buildObjectTree(objects: ObjectSummary[]): TreeNode[] {
  const byPath = new Map(objects.map((c) => [c.path, c]));
  const childMap = new Map<string, ObjectSummary[]>();

  for (const ctx of objects) {
    const dot = ctx.path.lastIndexOf(".");
    const parentPath = dot === -1 ? "" : ctx.path.slice(0, dot);
    if (!childMap.has(parentPath)) {
      childMap.set(parentPath, []);
    }
    childMap.get(parentPath)!.push(ctx);
  }

  for (const list of childMap.values()) {
    list.sort(compareObjects);
  }

  function build(parentPath: string): TreeNode[] {
    const children = childMap.get(parentPath) ?? [];
    return children
      .filter((c) => byPath.has(c.path) && !isHiddenLegacyApplicationPath(c.path))
      .map((object) => ({
        object,
        children: build(object.path),
      }));
  }

  const fromEmptyParent = build("");
  if (fromEmptyParent.length > 0) {
    return fromEmptyParent;
  }
  // Lazy-loaded subsets return children of root without the root node itself.
  return build("root");
}

export function objectIcon(type: string): string {
  switch (type) {
    case "ROOT":
      return "⬢";
    case "DEVICE":
      return "◉";
    case "MODEL":
      return "▣";
    case "DASHBOARD":
      return "▦";
    case "APPLICATION":
      return "▤";
    case "DATA_SOURCES":
    case "DATA_SOURCE":
      return "🗄";
    case "OPERATOR_APPS":
      return "🖥";
    case "REPORT":
      return "▧";
    case "USER":
      return "◎";
    case "AGENT":
      return "⬡";
    default:
      return "◈";
  }
}

export function formatVariableValue(value: unknown): string {
  if (value === null || value === undefined) {
    return "—";
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return String(value);
}

export function recordDisplayValue(record: { rows: Array<Record<string, unknown>> } | null): string {
  if (!record || record.rows.length === 0) {
    return "—";
  }
  const row = record.rows[0];
  const keys = Object.keys(row);
  if (keys.length === 1) {
    return formatVariableValue(row[keys[0]]);
  }
  return JSON.stringify(row, null, 2);
}

/** Single-line preview for tables (no pretty-print). */
export function recordCompactValue(
  record: { rows: Array<Record<string, unknown>> } | null
): string {
  if (!record || record.rows.length === 0) {
    return "—";
  }
  const row = record.rows[0];
  const keys = Object.keys(row);
  if (keys.length === 1) {
    return formatVariableValue(row[keys[0]]);
  }
  return keys.map((key) => `${key}: ${formatVariableValue(row[key])}`).join(", ");
}
