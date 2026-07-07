import { useTranslation } from "react-i18next";

export type DataSourceConnectionMode = "internal" | "external";

export interface DataSourceConnectionValues {
  connectionMode: DataSourceConnectionMode;
  schemaName: string;
  jdbcUrl: string;
  jdbcDriverClass: string;
  jdbcUsername: string;
  jdbcPassword: string;
  poolSize: number;
}

export function isDataSourceConnectionValid(
  values: Pick<DataSourceConnectionValues, "connectionMode" | "schemaName" | "jdbcUrl">,
): boolean {
  return values.connectionMode === "external"
    ? values.jdbcUrl.trim().length > 0
    : values.schemaName.trim().length > 0;
}

interface DataSourceConnectionFieldsProps extends DataSourceConnectionValues {
  onConnectionModeChange: (mode: DataSourceConnectionMode) => void;
  onSchemaNameChange: (value: string) => void;
  onJdbcUrlChange: (value: string) => void;
  onJdbcDriverClassChange: (value: string) => void;
  onJdbcUsernameChange: (value: string) => void;
  onJdbcPasswordChange: (value: string) => void;
  onPoolSizeChange: (value: number) => void;
  passwordPlaceholder?: string;
}

export default function DataSourceConnectionFields({
  connectionMode,
  schemaName,
  jdbcUrl,
  jdbcDriverClass,
  jdbcUsername,
  jdbcPassword,
  poolSize,
  onConnectionModeChange,
  onSchemaNameChange,
  onJdbcUrlChange,
  onJdbcDriverClassChange,
  onJdbcUsernameChange,
  onJdbcPasswordChange,
  onPoolSizeChange,
  passwordPlaceholder,
}: DataSourceConnectionFieldsProps) {
  const { t } = useTranslation(["platform"]);

  return (
    <>
      <label>
        {t("platform:dataSource.connectionMode")}
        <select
          value={connectionMode}
          onChange={(e) => onConnectionModeChange(e.target.value as DataSourceConnectionMode)}
        >
          <option value="internal">{t("platform:dataSource.modeInternal")}</option>
          <option value="external">{t("platform:dataSource.modeExternal")}</option>
        </select>
      </label>
      {connectionMode === "internal" ? (
        <label>
          {t("platform:dataSource.schema")}
          <input
            value={schemaName}
            onChange={(e) => onSchemaNameChange(e.target.value)}
            required
            placeholder={t("platform:dataSource.schemaPlaceholder")}
          />
        </label>
      ) : (
        <>
          <label className="full">
            {t("platform:dataSource.jdbcUrl")}
            <input value={jdbcUrl} onChange={(e) => onJdbcUrlChange(e.target.value)} required />
          </label>
          <label>
            {t("platform:dataSource.jdbcDriverClass")}
            <input value={jdbcDriverClass} onChange={(e) => onJdbcDriverClassChange(e.target.value)} />
          </label>
          <label>
            {t("platform:dataSource.poolSize")}
            <input
              type="number"
              min={1}
              max={20}
              value={poolSize}
              onChange={(e) => onPoolSizeChange(Number(e.target.value))}
            />
          </label>
          <label>
            {t("platform:dataSource.jdbcUsername")}
            <input value={jdbcUsername} onChange={(e) => onJdbcUsernameChange(e.target.value)} />
          </label>
          <label>
            {t("platform:dataSource.jdbcPassword")}
            <input
              type="password"
              value={jdbcPassword}
              onChange={(e) => onJdbcPasswordChange(e.target.value)}
              placeholder={passwordPlaceholder}
            />
          </label>
        </>
      )}
    </>
  );
}
