import type { ReactNode } from "react";
import { Input, InputNumber, Segmented } from "antd";
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
      <Segmented<DataSourceConnectionMode>
        className="ds-mode-segment"
        aria-label={t("platform:dataSource.connectionMode")}
        value={connectionMode}
        onChange={onConnectionModeChange}
        options={[
          { label: t("platform:dataSource.modeInternalShort"), value: "internal" },
          { label: t("platform:dataSource.modeExternalShort"), value: "external" },
        ]}
      />

      {connectionMode === "internal" ? (
        <Field label={t("platform:dataSource.schema")} span={2}>
          <Input
            value={schemaName}
            onChange={(e) => onSchemaNameChange(e.target.value)}
            required
            placeholder={t("platform:dataSource.schemaPlaceholder")}
          />
        </Field>
      ) : (
        <>
          <Field label={t("platform:dataSource.jdbcUrl")} span={2}>
            <Input value={jdbcUrl} onChange={(e) => onJdbcUrlChange(e.target.value)} required />
          </Field>
          <Field label={t("platform:dataSource.jdbcDriverClass")}>
            <Input value={jdbcDriverClass} onChange={(e) => onJdbcDriverClassChange(e.target.value)} />
          </Field>
          <Field label={t("platform:dataSource.poolSize")}>
            <InputNumber
              min={1}
              max={20}
              value={poolSize}
              onChange={(value) => onPoolSizeChange(value ?? 1)}
            />
          </Field>
          <Field label={t("platform:dataSource.jdbcUsername")}>
            <Input value={jdbcUsername} onChange={(e) => onJdbcUsernameChange(e.target.value)} />
          </Field>
          <Field label={t("platform:dataSource.jdbcPassword")}>
            <Input.Password
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
