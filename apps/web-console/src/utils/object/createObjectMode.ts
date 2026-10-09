import i18n from "../../i18n/index";
import { OPERATOR_APPS_ROOT, isOperatorAppChildPath } from "../operator/operatorAppsPath";
import {
  ALERT_RULES_ROOT,
  CORRELATORS_ROOT,
  isAlertRulePath,
  isCorrelatorPath,
} from "../automation/automationPath";
import type { ObjectType } from "../../types";
import { BINDINGS_ROOT, DATA_SOURCES_ROOT, MIGRATIONS_ROOT, SCHEDULES_ROOT } from "../platform/platformSqlPath";
import { isQueryPath, QUERIES_ROOT } from "../ui/queryPath";
import { isMesCatalogContainer, isPlatformCatalogContainer, isPlatformReportsFolder } from "../platform/platformCatalogPath";
import { EVENT_FILTERS_ROOT } from "../automation/eventFilterPath";
import { PROCESS_PROGRAMS_ROOT } from "../automation/processProgramPath";

export const APPLICATIONS_ROOT = "root.platform.applications";
export const REPORTS_ROOT = "root.platform.reports";
const DEVICES_ROOT = "root.platform.devices";
const DASHBOARDS_ROOT = "root.platform.dashboards";
const WORKFLOWS_ROOT = "root.platform.workflows";
const MIMICS_ROOT = "root.platform.mimics";
const INSTANCES_ROOT = "root.platform.instances";
const MES_ROOT = "root.platform.mes";

function isPathUnder(path: string, root: string): boolean {
  return path === root || path.startsWith(`${root}.`);
}

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
  | "process-program"
  | "blueprint";

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
  if (blueprintKindForCatalog(parentPath)) {
    return "blueprint";
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
  if (parentPath.endsWith(".work-orders")) {
    return "work-order";
  }
  if (parentPath.endsWith(".operations")) {
    return "operation";
  }
  if (parentPath.endsWith(".lots")) {
    return "lot";
  }
  if (parentPath.endsWith(".shifts")) {
    return "shift";
  }
  if (parentPath.endsWith(".quality-records")) {
    return "quality-record";
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
    default:
      if (parentPath.endsWith(".instances") || isPathUnder(parentPath, INSTANCES_ROOT)) {
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
  if (path.startsWith("root.platform.mes.")) {
    return false;
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
  if (isPathUnder(parentPath, DASHBOARDS_ROOT) || parentPath.endsWith(".dashboards")) {
    return "DASHBOARD";
  }
  if (isPathUnder(parentPath, MIMICS_ROOT) || parentPath.endsWith(".mimics")) {
    return "MIMIC";
  }
  if (isPathUnder(parentPath, REPORTS_ROOT) || parentPath.endsWith(".reports")) {
    return "REPORT";
  }
  if (isPathUnder(parentPath, WORKFLOWS_ROOT) || parentPath.endsWith(".workflows")) {
    return "WORKFLOW";
  }
  if (isPathUnder(parentPath, DEVICES_ROOT) || parentPath.endsWith(".devices")) {
    return "DEVICE";
  }
  if (isPathUnder(parentPath, INSTANCES_ROOT) || isPathUnder(parentPath, MES_ROOT)) {
    return "CUSTOM";
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
  if (
    parentPath.endsWith(".work-orders")
    || parentPath.endsWith(".operations")
    || parentPath.endsWith(".lots")
    || parentPath.endsWith(".shifts")
    || parentPath.endsWith(".quality-records")
    || parentPath === "root.platform.mes"
    || (parentPath.endsWith(".instances") && parentPath.includes(".mes."))
  ) {
    return "CUSTOM";
  }
  if (
    parentPath.endsWith(".mixin-blueprints")
    || parentPath.endsWith(".instance-types")
    || parentPath.endsWith(".singleton-blueprints")
  ) {
    return "BLUEPRINT";
  }
  return "CUSTOM";
}

/** Types offered by the generic Create dialog. Federation does not use this list. */
export const PLATFORM_CREATE_TYPES: readonly ObjectType[] = [
  "CUSTOM",
  "DEVICE",
  "BLUEPRINT",
  "DASHBOARD",
  "REPORT",
  "WORKFLOW",
  "ALERT",
  "AGENT",
  "USER",
  "TENANT",
  "DRIVER",
];

/** Target types for INSTANCE and MIXIN blueprints. SINGLETON does not take one. */
export const BLUEPRINT_TARGET_OBJECT_TYPES: readonly ObjectType[] = [
  "DEVICE",
  "CUSTOM",
  "DASHBOARD",
  "WORKFLOW",
  "MIMIC",
  "ALERT",
  "REPORT",
];

export type BlueprintCatalogKind = "MIXIN" | "INSTANCE" | "SINGLETON";

/** Kind implied by a blueprint catalog folder. Null outside those folders. */
export function blueprintKindForCatalog(parentPath: string): BlueprintCatalogKind | null {
  if (parentPath.endsWith(".mixin-blueprints")) {
    return "MIXIN";
  }
  if (parentPath.endsWith(".instance-types")) {
    return "INSTANCE";
  }
  if (parentPath.endsWith(".singleton-blueprints")) {
    return "SINGLETON";
  }
  return null;
}

/**
 * Platform types the manual Create dialog may offer under this parent.
 * A visual group is created from the tree context menu, so it is not in this list.
 * A catalog and every folder inside it offer that catalog's child types.
 * Devices also offer {@code CUSTOM} for a logic object next to drivers.
 * The instances catalog offers a plain object; device-shaped twins come from an instance type.
 * Unconstrained parents ({@code root}, {@code root.platform}) keep the full list.
 */
export function platformTypesForParent(parentPath: string): ObjectType[] {
  if (isPathUnder(parentPath, DEVICES_ROOT)) {
    return ["DEVICE", "CUSTOM"];
  }
  if (isPathUnder(parentPath, INSTANCES_ROOT) || isPathUnder(parentPath, MES_ROOT)) {
    return ["CUSTOM"];
  }
  if (isPathUnder(parentPath, DASHBOARDS_ROOT)) {
    return ["DASHBOARD"];
  }
  if (isPathUnder(parentPath, WORKFLOWS_ROOT)) {
    return ["WORKFLOW"];
  }
  if (isPathUnder(parentPath, REPORTS_ROOT)) {
    return ["REPORT"];
  }
  if (isPathUnder(parentPath, MIMICS_ROOT)) {
    return ["MIMIC"];
  }
  const blueprintType = blueprintCatalogType(parentPath);
  if (blueprintType) {
    return [blueprintType];
  }
  return [...PLATFORM_CREATE_TYPES];
}

function blueprintCatalogType(parentPath: string): ObjectType | undefined {
  if (
    parentPath.endsWith(".mixin-blueprints")
    || parentPath.endsWith(".instance-types")
    || parentPath.endsWith(".singleton-blueprints")
  ) {
    return "BLUEPRINT";
  }
  return undefined;
}

/**
 * Instance-type targets the create dialog may offer.
 * Undefined means every target (unconstrained parents such as {@code root.platform}).
 * A one-element list is also sent to the API as {@code platformType}.
 */
export function instanceTypeTargetsForParent(parentPath: string): readonly ObjectType[] | undefined {
  if (isPathUnder(parentPath, DEVICES_ROOT)) {
    return ["DEVICE"];
  }
  if (isPathUnder(parentPath, DASHBOARDS_ROOT)) {
    return ["DASHBOARD"];
  }
  if (isPathUnder(parentPath, MIMICS_ROOT)) {
    return ["MIMIC"];
  }
  if (isPathUnder(parentPath, REPORTS_ROOT) || parentPath.endsWith(".reports")) {
    return ["REPORT"];
  }
  if (isPathUnder(parentPath, WORKFLOWS_ROOT)) {
    return ["WORKFLOW"];
  }
  if (isPathUnder(parentPath, MES_ROOT)) {
    return ["CUSTOM"];
  }
  if (isPathUnder(parentPath, INSTANCES_ROOT)) {
    return ["CUSTOM", "DEVICE"];
  }
  return undefined;
}

/** Single platform type for the instance-types API. Undefined when the parent allows several or all. */
export function instanceTypeFilterForParent(parentPath: string): ObjectType | undefined {
  const targets = instanceTypeTargetsForParent(parentPath);
  if (!targets || targets.length !== 1) {
    return undefined;
  }
  return targets[0];
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
