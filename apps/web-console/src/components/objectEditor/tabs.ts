import {
  isQueryCatalogDefinition,
  isQueryCatalogEntry,
} from "../../utils/ui/queryPath";

export type ObjectEditorTab =
  | "general"
  | "federation"
  | "driver"
  | "haystack"
  | "brick"
  | "deploy"
  | "export"
  | "access"
  | "variables"
  | "computations"
  | "events"
  | "functions"
  | "history";

export const OBJECT_EDITOR_TABS: readonly ObjectEditorTab[] = [
  "general",
  "federation",
  "driver",
  "haystack",
  "brick",
  "deploy",
  "export",
  "access",
  "variables",
  "computations",
  "events",
  "functions",
  "history",
] as const;

export type ObjectEditorTabVisibility = {
  path: string;
  canManage: boolean;
  canManageAcl: boolean;
  objectType?: string;
  hasHaystackMetadata: boolean;
  hasBrickMetadata: boolean;
};

/** Registry: which inspector tabs are available for the current object. */
export function visibleObjectEditorTabs(ctx: ObjectEditorTabVisibility): ObjectEditorTab[] {
  const isRootPath = ctx.path === "root";
  const isDevice = ctx.objectType === "DEVICE";
  const isApplication = ctx.objectType === "APPLICATION";
  const showFederation = ctx.canManage && !isRootPath;
  const showAccess = ctx.canManageAcl && !isDevice && !isApplication;

  const list: ObjectEditorTab[] = ["general"];
  if (showFederation) {
    list.push("federation");
  }
  if (isDevice) {
    list.push("driver");
    if (ctx.hasHaystackMetadata) {
      list.push("haystack");
    }
    if (ctx.hasBrickMetadata) {
      list.push("brick");
    }
  }
  if (isApplication) {
    list.push("deploy");
  }
  if (ctx.canManage && ctx.path.startsWith("root.platform")) {
    list.push("export");
  }
  if (showAccess) {
    list.push("access");
  }
  if (isQueryCatalogEntry(ctx.path, ctx.objectType)) {
    if (isQueryCatalogDefinition(ctx.path)) {
      list.push("functions");
    }
    list.push("history");
    return list;
  }
  list.push("variables", "computations", "events", "functions", "history");
  return list;
}
