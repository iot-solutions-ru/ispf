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
    let detail = text || `Request failed: ${response.status}`;
    try {
      const parsed = JSON.parse(text) as { detail?: string; title?: string };
      if (parsed.detail) {
        detail = parsed.detail;
      } else if (parsed.title && response.status === 403) {
        detail = "Admin role required for this operation";
      }
    } catch {
      // keep raw text
    }
    throw new Error(detail);
  }
  return response.json();
}

export interface DataSourceDefinition {
  path: string;
  displayName: string;
  description: string;
  variableDisplayName: string;
  connectionMode: string;
  schemaName: string;
  jdbcUrl: string;
  jdbcDriverClass: string;
  jdbcUsername: string;
  jdbcPassword: string;
  poolSize: number;
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
  connectionMode?: string;
  schemaName?: string;
  jdbcUrl?: string;
  jdbcDriverClass?: string;
  jdbcUsername?: string;
  jdbcPassword?: string;
  poolSize?: number;
  description?: string;
}): Promise<DataSourceDefinition> {
  return request("/api/v1/data-sources", {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function updateDataSource(
  path: string,
  payload: {
    displayName?: string;
    connectionMode?: string;
    schemaName?: string;
    jdbcUrl?: string;
    jdbcDriverClass?: string;
    jdbcUsername?: string;
    jdbcPassword?: string;
    poolSize?: number;
    description?: string;
  },
): Promise<DataSourceDefinition> {
  return request(`/api/v1/data-sources/by-path?path=${encodeURIComponent(path)}`, {
    method: "PUT",
    body: JSON.stringify(payload),
  });
}

export function testDataSourceConnection(
  path: string,
  probe?: {
    jdbcUrl?: string;
    jdbcDriverClass?: string;
    jdbcUsername?: string;
    jdbcPassword?: string;
    poolSize?: number;
  },
): Promise<{ path: string; connected: boolean; message?: string }> {
  return request(`/api/v1/data-sources/by-path/test-connection?path=${encodeURIComponent(path)}`, {
    method: "POST",
    body: JSON.stringify(probe ?? {}),
  });
}

export interface DataSourceQueryResult {
  kind: string;
  rows: Array<Record<string, unknown>>;
  columns: string[];
  rowCount: number;
  updateCount: number;
}

export interface DataSourceTableNode {
  name: string;
  type: string;
}

export interface DataSourceSchemaNode {
  name: string;
  catalog: string | null;
  schema: string | null;
  truncated: boolean;
  tables: DataSourceTableNode[];
}

export interface DataSourceCatalog {
  readOnly: boolean;
  schemasTruncated: boolean;
  productName: string;
  identifierQuote: string;
  defaultSchema: string | null;
  schemas: DataSourceSchemaNode[];
}

export interface DataSourceColumn {
  name: string;
  typeName: string;
  size: number;
  nullable: boolean;
  primaryKey: boolean;
}

export interface DataSourceColumnSpec {
  name: string;
  type: string;
  length?: number;
  nullable?: boolean;
  primaryKey?: boolean;
}

export function fetchDataSourceCatalog(path: string): Promise<DataSourceCatalog> {
  return request(`/api/v1/data-sources/by-path/metadata?path=${encodeURIComponent(path)}`);
}

export function fetchDataSourceColumns(
  path: string,
  locator: { catalog?: string | null; schema?: string | null; table: string },
): Promise<DataSourceColumn[]> {
  const params = new URLSearchParams({ path, table: locator.table });
  if (locator.catalog) {
    params.set("catalog", locator.catalog);
  }
  if (locator.schema) {
    params.set("schema", locator.schema);
  }
  return request(`/api/v1/data-sources/by-path/metadata/columns?${params.toString()}`);
}

export function createDataSourceTable(
  path: string,
  payload: { table: string; columns: DataSourceColumnSpec[] },
): Promise<{ schema: string; table: string }> {
  return request(`/api/v1/data-sources/by-path/ddl/create-table?path=${encodeURIComponent(path)}`, {
    method: "POST",
    body: JSON.stringify(payload),
  });
}

export function executeDataSourceQuery(
  path: string,
  payload: {
    query: string;
    params?: unknown[];
    maxRows?: number;
  },
): Promise<DataSourceQueryResult> {
  return request(`/api/v1/data-sources/by-path/execute-query?path=${encodeURIComponent(path)}`, {
    method: "POST",
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
