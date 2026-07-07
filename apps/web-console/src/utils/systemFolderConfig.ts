import type { TFunction } from "i18next";
import {
  ABSOLUTE_BLUEPRINTS_ROOT,
  INSTANCE_TYPES_ROOT,
  RELATIVE_BLUEPRINTS_ROOT,
} from "../types/blueprints";
import type { ObjectType } from "../types";
import { isOperatorAppChildPath } from "./operatorAppsPath";
import { isSecurityRolePath, isSecurityRolesRoot } from "./securityRolePath";
import { isSecurityUserPath, isSecurityUsersRoot } from "./securityUserPath";
import {
  isAlertRulePath,
  isAlertRulesRoot,
  isCorrelatorPath,
  isCorrelatorsRoot,
} from "./automationPath";
import { isFederationRoot } from "./federationPath";
import { isTenantsRoot } from "./tenantPath";
import { localizedSystemFolderMeta } from "./systemFolderI18n";

const APPLICATION_SUBFOLDER_SUFFIXES = [
  ".functions",
  ".reports",
  ".schedules",
  ".bindings",
  ".migrations",
  ".screens",
] as const;

export const DATA_SOURCES_ROOT = "root.platform.data-sources";

const CATALOG_CONTAINER_TYPES: ReadonlySet<ObjectType> = new Set([
  "PLATFORM",
  "DEVICES",
  "DASHBOARDS",
  "WORKFLOWS",
  "ALERT_RULES",
  "CORRELATORS",
  "QUERIES",
  "EVENT_FILTERS",
  "PROCESS_PROGRAMS",
  "DATA_SOURCES",
  "OPERATOR_APPS",
  "SECURITY",
  "FUNCTIONS",
  "REPORTS",
  "SCHEDULES",
  "BINDINGS",
  "MIGRATIONS",
  "SCREENS",
]);

const EXACT_CATALOG_PATHS: ReadonlySet<string> = new Set([
  "root.platform",
  "root.platform.devices",
  RELATIVE_BLUEPRINTS_ROOT,
  INSTANCE_TYPES_ROOT,
  ABSOLUTE_BLUEPRINTS_ROOT,
  "root.platform.instances",
  "root.platform.dashboards",
  "root.platform.reports",
  "root.platform.data-sources",
  "root.platform.schedules",
  "root.platform.bindings",
  "root.platform.migrations",
  "root.platform.workflows",
  "root.platform.alert-rules",
  "root.platform.correlators",
  "root.platform.queries",
  "root.platform.event-filters",
  "root.platform.process-programs",
  "root.platform.applications",
  "root.platform.operator-apps",
  "root.platform.security",
]);

export interface SystemFolderListMeta {
  title: string;
  description: string;
  idColumnLabel: string;
}

const ID_COLUMN_BY_TYPE: Partial<Record<ObjectType, "section" | "appId" | "id">> = {
  PLATFORM: "section",
  SECURITY: "section",
  APPLICATIONS: "appId",
  OPERATOR_APPS: "appId",
};

function isApplicationSubfolder(path: string): boolean {
  return APPLICATION_SUBFOLDER_SUFFIXES.some((suffix) => path.endsWith(suffix));
}

function resolveCatalogType(path: string, objectType?: ObjectType): ObjectType | null {
  if (
    path === RELATIVE_BLUEPRINTS_ROOT
    || path === INSTANCE_TYPES_ROOT
    || path === ABSOLUTE_BLUEPRINTS_ROOT
  ) {
    return "BLUEPRINT";
  }
  if (objectType && CATALOG_CONTAINER_TYPES.has(objectType)) {
    return objectType;
  }
  if (EXACT_CATALOG_PATHS.has(path)) {
    const byPath: Record<string, ObjectType> = {
      "root.platform": "PLATFORM",
      "root.platform.devices": "DEVICES",
      "root.platform.dashboards": "DASHBOARDS",
      "root.platform.reports": "REPORTS",
      "root.platform.data-sources": "DATA_SOURCES",
      "root.platform.schedules": "SCHEDULES",
      "root.platform.bindings": "BINDINGS",
      "root.platform.migrations": "MIGRATIONS",
      "root.platform.workflows": "WORKFLOWS",
      "root.platform.alert-rules": "ALERT_RULES",
      "root.platform.correlators": "CORRELATORS",
      "root.platform.applications": "APPLICATIONS",
      "root.platform.operator-apps": "OPERATOR_APPS",
      "root.platform.security": "SECURITY",
    };
    return byPath[path] ?? null;
  }
  if (isApplicationSubfolder(path)) {
    if (path.endsWith(".functions")) return "FUNCTIONS";
    if (path.endsWith(".reports")) return "REPORTS";
    if (path.endsWith(".schedules")) return "SCHEDULES";
    if (path.endsWith(".bindings")) return "BINDINGS";
    if (path.endsWith(".migrations")) return "MIGRATIONS";
    if (path.endsWith(".screens")) return "SCREENS";
  }
  return null;
}

export function isSystemCatalogFolder(path: string, objectType?: ObjectType): boolean {
  if (
    isSecurityUsersRoot(path)
    || isSecurityRolesRoot(path)
    || isSecurityUserPath(path)
    || isSecurityRolePath(path)
    || isOperatorAppChildPath(path)
    || isFederationRoot(path)
    || isTenantsRoot(path)
    || (isAlertRulePath(path) && !isAlertRulesRoot(path))
    || (isCorrelatorPath(path) && !isCorrelatorsRoot(path))
  ) {
    return false;
  }

  if (objectType === "BLUEPRINT" && !EXACT_CATALOG_PATHS.has(path)) {
    return false;
  }

  return resolveCatalogType(path, objectType) !== null;
}

export function getSystemFolderListMeta(
  path: string,
  t: TFunction,
  objectType?: ObjectType,
  displayName?: string,
  description?: string,
): SystemFolderListMeta {
  const catalogType = resolveCatalogType(path, objectType);
  const idColumnFallback = (catalogType && ID_COLUMN_BY_TYPE[catalogType]) || "id";
  return localizedSystemFolderMeta(t, path, displayName, description, idColumnFallback);
}

export function childIdFromPath(parentPath: string, childPath: string): string {
  if (!childPath.startsWith(`${parentPath}.`)) {
    return childPath.split(".").pop() ?? childPath;
  }
  return childPath.slice(parentPath.length + 1);
}
