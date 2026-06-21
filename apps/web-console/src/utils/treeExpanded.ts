import type { TreeNode } from "../types";

const EXPANDED_KEY = "ispf-tree-expanded-paths";
const SELECTED_KEY = "ispf-tree-selected-path";

export function readExpandedPaths(): Set<string> {
  try {
    const raw = sessionStorage.getItem(EXPANDED_KEY);
    if (!raw) {
      return new Set();
    }
    const parsed = JSON.parse(raw) as unknown;
    if (!Array.isArray(parsed)) {
      return new Set();
    }
    return new Set(parsed.filter((p): p is string => typeof p === "string" && p.length > 0));
  } catch {
    return new Set();
  }
}

export function writeExpandedPaths(paths: Set<string>): void {
  sessionStorage.setItem(EXPANDED_KEY, JSON.stringify([...paths]));
}

export function readSelectedPath(): string {
  return sessionStorage.getItem(SELECTED_KEY) ?? "root";
}

export function writeSelectedPath(path: string | null): void {
  if (!path) {
    sessionStorage.removeItem(SELECTED_KEY);
    return;
  }
  sessionStorage.setItem(SELECTED_KEY, path);
}

export function ancestorPaths(path: string): string[] {
  const parts = path.split(".");
  const ancestors: string[] = [];
  for (let i = 1; i < parts.length; i++) {
    ancestors.push(parts.slice(0, i).join("."));
  }
  return ancestors;
}

/** Do not auto-expand folders that would reveal more than this many immediate children. */
export const DEFAULT_EXPAND_MAX_CHILDREN = 25;

/** Paths expanded by default on first visit (depth 0..maxDepth-1 with children). */
export function defaultExpandedPaths(
  nodes: TreeNode[],
  maxDepth = 2,
  depth = 0,
  maxChildren = DEFAULT_EXPAND_MAX_CHILDREN,
): string[] {
  const paths: string[] = [];
  for (const node of nodes) {
    if (node.children.length === 0) {
      continue;
    }
    if (depth >= maxDepth) {
      continue;
    }
    if (node.children.length > maxChildren) {
      continue;
    }
    paths.push(node.object.path);
    paths.push(...defaultExpandedPaths(node.children, maxDepth, depth + 1, maxChildren));
  }
  return paths;
}
