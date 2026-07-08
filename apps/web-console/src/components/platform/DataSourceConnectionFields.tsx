import type { ReactNode } from "react";
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

function Field({
  label,
  children,
  span = 1,
}: {
  label: string;
  children: ReactNode;
  span?: 1 | 2;
}) {
  return (
    <label className={`ds-field${span === 2 ? " ds-field--span-2" : ""}`}>
      <span className="ds-field-label">{label}</span>
      {children}
    </label>
  );
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
    <div className="ds-connection-fields">
      <div className="ds-mode-segment" role="tablist" aria-label={t("platform:dataSource.connectionMode")}>
        <button
          type="button"
          role="tab"
          aria-selected={connectionMode === "internal"}
          className={`ds-mode-segment-btn${connectionMode === "internal" ? " active" : ""}`}
          onClick={() => onConnectionModeChange("internal")}
        >
          {t("platform:dataSource.modeInternalShort")}
        </button>
        <button
          type="button"
          role="tab"
          aria-selected={connectionMode === "external"}
          className={`ds-mode-segment-btn${connectionMode === "external" ? " active" : ""}`}
          onClick={() => onConnectionModeChange("external")}
        >
          {t("platform:dataSource.modeExternalShort")}
        </button>
      </div>

      {connectionMode === "internal" ? (
        <Field label={t("platform:dataSource.schema")} span={2}>
          <input
            value={schemaName}
            onChange={(e) => onSchemaNameChange(e.target.value)}
            required
            placeholder={t("platform:dataSource.schemaPlaceholder")}
          />
        </Field>
      ) : (
        <>
          <Field label={t("platform:dataSource.jdbcUrl")} span={2}>
            <input value={jdbcUrl} onChange={(e) => onJdbcUrlChange(e.target.value)} required />
          </Field>
          <Field label={t("platform:dataSource.jdbcDriverClass")}>
            <input value={jdbcDriverClass} onChange={(e) => onJdbcDriverClassChange(e.target.value)} />
          </Field>
          <Field label={t("platform:dataSource.poolSize")}>
            <input
              type="number"
              min={1}
              max={20}
              value={poolSize}
              onChange={(e) => onPoolSizeChange(Number(e.target.value))}
            />
          </Field>
          <Field label={t("platform:dataSource.jdbcUsername")}>
            <input value={jdbcUsername} onChange={(e) => onJdbcUsernameChange(e.target.value)} />
          </Field>
          <Field label={t("platform:dataSource.jdbcPassword")}>
            <input
              type="password"
              value={jdbcPassword}
              onChange={(e) => onJdbcPasswordChange(e.target.value)}
              placeholder={passwordPlaceholder}
              autoComplete="new-password"
            />
          </Field>
        </>
      )}
    </div>
  );
}
