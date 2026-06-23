export const DATA_SOURCES_ROOT = "root.platform.data-sources";
export const BINDINGS_ROOT = "root.platform.bindings";
export const MIGRATIONS_ROOT = "root.platform.migrations";

export function isDataSourcesRoot(path: string): boolean {
  return path === DATA_SOURCES_ROOT;
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

export function isPlatformSqlObjectPath(path: string): boolean {
  return isDataSourcePath(path) || isMigrationPath(path) || isSqlBindingPath(path);
}
