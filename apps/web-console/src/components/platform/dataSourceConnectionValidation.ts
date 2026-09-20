// Extracted from DataSourceConnectionFields.tsx so that component modules only export components
// (react-refresh/only-export-components keeps Fast Refresh state-preserving).

export function isDataSourceConnectionValid(
  values: Pick<DataSourceConnectionValues, "connectionMode" | "schemaName" | "jdbcUrl">,
): boolean {
  return values.connectionMode === "external"
    ? values.jdbcUrl.trim().length > 0
    : values.schemaName.trim().length > 0;
}

export interface DataSourceConnectionValues {
  connectionMode: DataSourceConnectionMode;
  schemaName: string;
  jdbcUrl: string;
  jdbcDriverClass: string;
  jdbcUsername: string;
  jdbcPassword: string;
  poolSize: number;
}

export type DataSourceConnectionMode = "internal" | "external";
