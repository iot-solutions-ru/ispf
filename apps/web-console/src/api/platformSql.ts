import { getAuthHeaders } from "../auth/session";

async function request<T>(url: string, init?: RequestInit): Promise<T> {
  const response = await fetch(url, {
    headers: {
      "Content-Type": "application/json",
      ...getAuthHeaders(),
      ...init?.headers,
    },
    ...init,
  });
  if (!response.ok) {
    const text = await response.text();
    throw new Error(text || `Request failed: ${response.status}`);
  }
  return response.json();
}

export interface DataSourceDefinition {
  path: string;
  displayName: string;
  description: string;
  variableDisplayName: string;
  schemaName: string;
}

export interface MigrationDefinition {
  path: string;
  scriptId: string;
  version: string;
  dataSourcePath: string;
  sql: string;
  checksum: string;
  appliedAt: string;
  applied: boolean;
}

export interface SqlBindingDefinition {
  path: string;
  bindingId: string;
  targetObjectPath: string;
  variable: string;
  dataSourcePath: string;
  query: string;
  valueField: string;
  refresh: string;
  refreshIntervalMs: number;
  triggerObjectPath: string;
  triggerFunctionName: string;
  enabled: boolean;
  lastRefreshedAt: string | null;
}

export function fetchDataSource(path: string): Promise<DataSourceDefinition> {
  return request(`/api/v1/data-sources/by-path?path=${encodeURIComponent(path)}`);
}

export function createDataSource(payload: {
  name: string;
  displayName?: string;
  schemaName: string;
  description?: string;
}): Promise<DataSourceDefinition> {
  return request("/api/v1/data-sources", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateDataSource(
  path: string,
  payload: { displayName?: string; schemaName?: string; description?: string }
): Promise<DataSourceDefinition> {
  return request(`/api/v1/data-sources/by-path?path=${encodeURIComponent(path)}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function fetchMigration(path: string): Promise<MigrationDefinition> {
  return request(`/api/v1/migrations/by-path?path=${encodeURIComponent(path)}`);
}

export function createMigration(payload: {
  scriptId: string;
  version?: string;
  dataSourcePath?: string;
  sql?: string;
}): Promise<MigrationDefinition> {
  return request("/api/v1/migrations", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateMigration(
  path: string,
  payload: {
    scriptId?: string;
    version?: string;
    dataSourcePath?: string;
    sql?: string;
  }
): Promise<MigrationDefinition> {
  return request(`/api/v1/migrations/by-path?path=${encodeURIComponent(path)}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function applyMigration(path: string): Promise<{ status: string; path: string; migration: MigrationDefinition }> {
  return request(`/api/v1/migrations/by-path/apply?path=${encodeURIComponent(path)}`, {
    method: "POST",
  });
}

export function fetchSqlBinding(path: string): Promise<SqlBindingDefinition> {
  return request(`/api/v1/sql-bindings/by-path?path=${encodeURIComponent(path)}`);
}

export function createSqlBinding(payload: {
  bindingId: string;
  targetObjectPath?: string;
  variable?: string;
  dataSourcePath?: string;
  query?: string;
  valueField?: string;
  refresh?: string;
  refreshIntervalMs?: number;
  triggerObjectPath?: string;
  triggerFunctionName?: string;
  enabled?: boolean;
}): Promise<SqlBindingDefinition> {
  return request("/api/v1/sql-bindings", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateSqlBinding(
  path: string,
  payload: {
    bindingId?: string;
    targetObjectPath?: string;
    variable?: string;
    dataSourcePath?: string;
    query?: string;
    valueField?: string;
    refresh?: string;
    refreshIntervalMs?: number;
    triggerObjectPath?: string;
    triggerFunctionName?: string;
    enabled?: boolean;
  }
): Promise<SqlBindingDefinition> {
  return request(`/api/v1/sql-bindings/by-path?path=${encodeURIComponent(path)}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function refreshSqlBinding(path: string): Promise<{ status: string; path: string; binding: SqlBindingDefinition }> {
  return request(`/api/v1/sql-bindings/by-path/refresh?path=${encodeURIComponent(path)}`, {
    method: "POST",
  });
}
