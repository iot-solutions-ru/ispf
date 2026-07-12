import i18n from "../i18n";
import { OPERATOR_APPS_ROOT, isOperatorAppChildPath } from "./operatorAppsPath";
import {
  ALERT_RULES_ROOT,
  CORRELATORS_ROOT,
  isAlertRulePath,
  isCorrelatorPath,
} from "./automationPath";
import type { ObjectType } from "../types";
import { BINDINGS_ROOT, DATA_SOURCES_ROOT, MIGRATIONS_ROOT, SCHEDULES_ROOT } from "./platformSqlPath";
import { isQueryPath, QUERIES_ROOT } from "./queryPath";
import { isMesCatalogContainer, isPlatformCatalogContainer, isPlatformReportsFolder } from "./platformCatalogPath";
import { EVENT_FILTERS_ROOT } from "./eventFilterPath";
import { PROCESS_PROGRAMS_ROOT } from "./processProgramPath";

export const APPLICATIONS_ROOT = "root.platform.applications";
export const REPORTS_ROOT = "root.platform.reports";

export type CreateDialogMode =
  | "object"
  | "application"
  | "operator-app"
  | "alert-rule"
  | "correlator"
  | "report"
  | "data-source"
  | "migration"
  | "sql-binding"
  | "schedule"
  | "query"
  | "event-filter"
  | "process-program";

export function resolveCreateDialogMode(parentPath: string): CreateDialogMode {
  if (parentPath === APPLICATIONS_ROOT) {
    return "application";
  }
  if (parentPath === OPERATOR_APPS_ROOT) {
    return "operator-app";
  }
  if (parentPath === ALERT_RULES_ROOT) {
    return "alert-rule";
  }
  if (parentPath === CORRELATORS_ROOT) {
    return "correlator";
  }
  if (parentPath === DATA_SOURCES_ROOT || parentPath.endsWith(".data-sources")) {
    return "data-source";
  }
  if (parentPath === MIGRATIONS_ROOT || parentPath.endsWith(".migrations")) {
    return "migration";
  }
  if (parentPath === BINDINGS_ROOT || parentPath.endsWith(".bindings")) {
    return "sql-binding";
  }
  if (parentPath === SCHEDULES_ROOT || parentPath.endsWith(".schedules")) {
    return "schedule";
  }
  if (parentPath === QUERIES_ROOT) {
    return "query";
  }
  if (parentPath === EVENT_FILTERS_ROOT) {
    return "event-filter";
  }
  if (parentPath === PROCESS_PROGRAMS_ROOT) {
    return "process-program";
  }
  if (isPlatformReportsFolder(parentPath)) {
    return "report";
  }
  return "object";
}

/** Context menu label, e.g. «Create dashboard», «Create device». */
export function createContextMenuLabel(parentPath: string): string {
  return i18n.t(`explorer:contextMenu.create.${resolveCreateLabelKind(parentPath)}`);
}

export function createActionLabel(parentPath: string): string {
  return `+ ${createContextMenuLabel(parentPath)}`;
}

export function resolveCreateLabelKind(parentPath: string): string {
  const mode = resolveCreateDialogMode(parentPath);
  if (mode !== "object") {
    return mode;
  }
  switch (defaultObjectTypeForParent(parentPath)) {
    case "DEVICE":
      return "device";
    case "DASHBOARD":
      return "dashboard";
    case "MIMIC":
      return "mimic";
    case "REPORT":
      return "report";
    case "WORKFLOW":
      return "workflow";
    case "BLUEPRINT":
      return "blueprint";
    case "WORK_ORDER":
      return "work-order";
    case "OPERATION":
      return "operation";
    case "LOT":
      return "lot";
    case "SHIFT":
      return "shift";
    case "QUALITY_RECORD":
      return "quality-record";
    default:
      if (parentPath.endsWith(".instances")) {
        return "instance";
      }
      return "object";
  }
}

/** Parent catalog folder for a new visual group from tree context. */
export function resolveVisualGroupParentPath(
  contextPath: string,
  _objectType?: ObjectType,
): string | null {
  if (!contextPath) {
    return null;
  }
  if (isPlatformCatalogContainer(contextPath)) {
    return contextPath;
  }
  const parts = contextPath.split(".");
  for (let index = parts.length - 1; index >= 2; index -= 1) {
    const candidate = parts.slice(0, index).join(".");
    if (isPlatformCatalogContainer(candidate)) {
      return candidate;
    }
  }
  return null;
}

export function canCreateVisualGroupAt(path: string, objectType?: ObjectType): boolean {
  if (objectType === "VISUAL_GROUP") {
    return false;
  }
  return resolveVisualGroupParentPath(path, objectType) !== null;
}

/** Catalog folder that owns visual groups for objects under `objectPath`. */
export function resolveVisualGroupCatalogParent(
  objectPath: string,
  objectType?: ObjectType,
): string | null {
  return resolveVisualGroupParentPath(objectPath, objectType);
}

export function filterVisualGroupsInCatalog<T extends { path: string; type: ObjectType; groupRef?: boolean }>(
  objects: T[],
  catalogParentPath: string,
): T[] {
  return objects.filter(
    (obj) =>
      !obj.groupRef
      && obj.type === "VISUAL_GROUP"
      && resolveVisualGroupCatalogParent(obj.path, "VISUAL_GROUP") === catalogParentPath,
  );
}

const CONTAINER_OBJECT_TYPES: ObjectType[] = [
  "ROOT",
  "TENANT",
  "PLATFORM",
  "DEVICES",
  "DASHBOARDS",
  "REPORTS",
  "WORKFLOWS",
  "ALERT_RULES",
  "CORRELATORS",
  "QUERIES",
  "EVENT_FILTERS",
  "PROCESS_PROGRAMS",
  "DATA_SOURCES",
  "SCHEDULES",
  "BINDINGS",
  "MIGRATIONS",
  "APPLICATIONS",
  "OPERATOR_APPS",
  "MIMICS",
  "MES",
  "WORK_ORDERS",
  "OPERATIONS",
  "LOTS",
  "SHIFTS",
  "QUALITY_RECORDS",
  "MES_INSTANCES",
  "BLUEPRINT",
  "CUSTOM",
];

/** Whether the selected tree node can have a child created under it. */
export function canCreateChildAt(path: string, objectType: ObjectType | undefined): boolean {
  if (!path) {
    return false;
  }
  if (objectType === "VISUAL_GROUP") {
    return false;
  }
  if (
    path === OPERATOR_APPS_ROOT
    || path === ALERT_RULES_ROOT
    || path === CORRELATORS_ROOT
    || path === QUERIES_ROOT
    || path === EVENT_FILTERS_ROOT
    || path === PROCESS_PROGRAMS_ROOT
  ) {
    return true;
  }
  if (path === APPLICATIONS_ROOT) {
    return objectType === "APPLICATIONS";
  }
  if (isOperatorAppChildPath(path)) {
    return false;
  }
  if (isAlertRulePath(path) || isCorrelatorPath(path) || isQueryPath(path)) {
    return false;
  }
  if (path.startsWith(`${APPLICATIONS_ROOT}.`)) {
    return false;
  }
  if (path.startsWith(`${OPERATOR_APPS_ROOT}.`)) {
    return false;
  }
  if (path.startsWith("root.platform.security")) {
    return false;
  }
  if (isPlatformCatalogContainer(path)) {
    return true;
  }

  if (!objectType || !CONTAINER_OBJECT_TYPES.includes(objectType)) {
    return false;
  }

  if (path === "root" || path === "root.platform") {
    return true;
  }

  if (objectType === "CUSTOM") {
    return true;
  }

  return isPlatformCatalogContainer(path) || isMesCatalogContainer(path);
}

export function defaultObjectTypeForParent(parentPath: string): ObjectType {
  if (parentPath.endsWith(".dashboards")) {
    return "DASHBOARD";
  }
  if (parentPath.endsWith(".mimics")) {
    return "MIMIC";
  }
  if (parentPath.endsWith(".reports")) {
    return "REPORT";
  }
  if (parentPath.endsWith(".workflows")) {
    return "WORKFLOW";
  }
  if (parentPath.endsWith(".devices")) {
    return "DEVICE";
  }
  if (parentPath.endsWith(".queries")) {
    return "CUSTOM";
  }
  if (parentPath.endsWith(".event-filters")) {
    return "EVENT_FILTER";
  }
  if (parentPath.endsWith(".process-programs")) {
    return "PROCESS_PROGRAM";
  }
  if (parentPath.endsWith(".work-orders")) {
    return "WORK_ORDER";
  }
  if (parentPath.endsWith(".operations")) {
    return "OPERATION";
  }
  if (parentPath.endsWith(".lots")) {
    return "LOT";
  }
  if (parentPath.endsWith(".shifts")) {
    return "SHIFT";
  }
  if (parentPath.endsWith(".quality-records")) {
    return "QUALITY_RECORD";
  }
  if (
    parentPath.endsWith(".relative-blueprints")
    || parentPath.endsWith(".instance-types")
    || parentPath.endsWith(".absolute-blueprints")
  ) {
    return "BLUEPRINT";
  }
  return "CUSTOM";
}

/** Platform type filter for INSTANCE blueprints in create dialog (undefined = all types). */
export function instanceTypeFilterForParent(parentPath: string): ObjectType | undefined {
  if (parentPath.endsWith(".devices")) {
    return "DEVICE";
  }
  if (parentPath.endsWith(".dashboards")) {
    return "DASHBOARD";
  }
  if (parentPath.endsWith(".mimics")) {
    return "MIMIC";
  }
  if (parentPath.endsWith(".reports")) {
    return "REPORT";
  }
  if (parentPath.endsWith(".workflows")) {
    return "WORKFLOW";
  }
  if (parentPath.endsWith(".work-orders")) {
    return "WORK_ORDER";
  }
  if (parentPath.endsWith(".operations")) {
    return "OPERATION";
  }
  if (parentPath.endsWith(".lots")) {
    return "LOT";
  }
  if (parentPath.endsWith(".shifts")) {
    return "SHIFT";
  }
  if (parentPath.endsWith(".quality-records")) {
    return "QUALITY_RECORD";
  }
  return undefined;
}

/** Tree node name for app id (same rules as server sanitizeNodeName). */
export function sanitizeAppNodeName(appId: string): string {
  if (!appId.trim()) {
    return "node";
  }
  let sanitized = appId.replace(/[^a-zA-Z0-9_-]/g, "_");
  if (!sanitized) {
    return "node";
  }
  if (/^\d/.test(sanitized)) {
    sanitized = `n_${sanitized}`;
  }
  return sanitized;
}

export function applicationObjectPath(appId: string): string {
  return `${APPLICATIONS_ROOT}.${sanitizeAppNodeName(appId)}`;
}

export function operatorAppObjectPath(appId: string): string {
  return `${OPERATOR_APPS_ROOT}.${sanitizeAppNodeName(appId)}`;
}
