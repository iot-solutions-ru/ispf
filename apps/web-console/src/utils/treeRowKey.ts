import type { ObjectSummary } from "../types";

/** Unique row identity in the object tree (group refs need composite keys). */
export function objectTreeKey(obj: ObjectSummary): string {
  if (obj.groupRef && obj.groupContextPath) {
    return `${obj.groupContextPath}::${obj.path}`;
  }
  return obj.path;
}

export function parseTreeRowKey(key: string): { path: string; viaGroup?: string } {
  const sep = key.indexOf("::");
  if (sep === -1) {
    return { path: key };
  }
  return { viaGroup: key.slice(0, sep), path: key.slice(sep + 2) };
}

export function treeRowKey(path: string, viaGroup?: string): string {
  return viaGroup ? `${viaGroup}::${path}` : path;
}

/** Parent path used when building the tree from a flat object list. */
export function treeParentPath(obj: ObjectSummary): string {
  if (obj.groupRef && obj.groupContextPath) {
    return obj.groupContextPath;
  }
  const dot = obj.path.lastIndexOf(".");
  return dot === -1 ? "" : obj.path.slice(0, dot);
}

export interface TreeRowSelection {
  key: string;
  path: string;
  viaGroup?: string;
}

export function selectionFromObject(obj: ObjectSummary): TreeRowSelection {
  const key = objectTreeKey(obj);
  const viaGroup = obj.groupRef ? (obj.groupContextPath ?? undefined) : undefined;
  return { key, path: obj.path, viaGroup };
}
