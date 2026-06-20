import type { ObjectSummary, TreeNode } from "../types";

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
    list.sort((a, b) => a.displayName.localeCompare(b.displayName));
  }

  function build(parentPath: string): TreeNode[] {
    const children = childMap.get(parentPath) ?? [];
    return children
      .filter((c) => byPath.has(c.path))
      .map((object) => ({
        object,
        children: build(object.path),
      }));
  }

  return build("");
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
