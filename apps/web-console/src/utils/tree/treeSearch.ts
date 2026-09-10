import type { ObjectSummary } from "../../types";
import { objectTreeKey } from "./treeRowKey";

export const OBJECT_SEARCH_MIN_CHARS = 2;

/** Filter already-loaded lazy tree rows. Used until the query is long enough for a full-tree search. */
export function filterLoadedObjectsForQuery(
  objects: ObjectSummary[],
  query: string,
): ObjectSummary[] {
  const q = query.trim().toLowerCase();
  if (!q) {
    return objects;
  }
  const included = new Set<string>();
  const addAncestors = (path: string) => {
    let p: string | null = path;
    while (p) {
      included.add(p);
      const dot = p.lastIndexOf(".");
      p = dot === -1 ? null : p.slice(0, dot);
    }
  };
  for (const item of objects) {
    if (item.path.toLowerCase().includes(q) || item.displayName.toLowerCase().includes(q)) {
      addAncestors(item.path);
      if (item.groupContextPath) {
        addAncestors(item.groupContextPath);
      }
      included.add(objectTreeKey(item));
    }
  }
  return objects.filter(
    (item) =>
      included.has(item.path)
      || included.has(objectTreeKey(item))
      || (item.groupContextPath != null && included.has(item.groupContextPath)),
  );
}
