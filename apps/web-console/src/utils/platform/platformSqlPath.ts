import { APPLICATIONS_ROOT } from "../object/createObjectMode";

export const DATA_SOURCES_ROOT = "root.platform.data-sources";
export const BINDINGS_ROOT = "root.platform.bindings";
export const MIGRATIONS_ROOT = "root.platform.migrations";
export const SCHEDULES_ROOT = "root.platform.schedules";

export function isDataSourcesRoot(path: string): boolean {
  return path === DATA_SOURCES_ROOT;
}

export function isSchedulesRoot(path: string): boolean {
  return path === SCHEDULES_ROOT;
}

export function isBindingsRoot(path: string): boolean {
  return path === BINDINGS_ROOT;
}

export function isMigrationsRoot(path: string): boolean {
  return path === MIGRATIONS_ROOT;
}

export function isDataSourcePath(path: string): boolean {
  return path.startsWith(`${DATA_SOURCES_ROOT}.`);
}

export function isMigrationPath(path: string): boolean {
  return path.startsWith(`${MIGRATIONS_ROOT}.`);
}

export function isSqlBindingPath(path: string): boolean {
  return path.startsWith(`${BINDINGS_ROOT}.`);
}

export function isSchedulePath(path: string): boolean {
  return path.startsWith(`${SCHEDULES_ROOT}.`);
}

/** Deployed app leaves under bindings / migrations / schedules (same editors as platform catalog). */
export function isApplicationSqlCatalogLeaf(path: string): boolean {
  if (!path.startsWith(`${APPLICATIONS_ROOT}.`)) {
    return false;
  }
  const rel = path.slice(APPLICATIONS_ROOT.length + 1);
  const parts = rel.split(".");
  if (parts.length < 3) {
    return false;
  }
  const folder = parts[1];
  return folder === "bindings" || folder === "migrations" || folder === "schedules";
}

export function isPlatformSqlObjectPath(path: string): boolean {
  return (
    isDataSourcePath(path)
    || isMigrationPath(path)
    || isSqlBindingPath(path)
    || isSchedulePath(path)
    || isApplicationSqlCatalogLeaf(path)
  );
}
