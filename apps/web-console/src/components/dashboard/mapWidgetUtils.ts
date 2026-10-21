import type { MapMarkerMode, MapPopupMode, MapWidget } from "../../types/dashboard";
import type { ObjectSummary } from "../../types";
import { matchesNamePattern } from "./dashboardUtils";

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

/** Resolve marker mode with legacy fallback when `statusVariable` is set. */
export function resolveMapMarkerMode(widget: Pick<MapWidget, "markerMode" | "statusVariable">): MapMarkerMode {
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
