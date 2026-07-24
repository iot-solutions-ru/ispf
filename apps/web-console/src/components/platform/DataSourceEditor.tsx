import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Input } from "antd";
import {
  executeDataSourceQuery,
  fetchDataSource,
  testDataSourceConnection,
  updateDataSource,
  type DataSourceQueryResult,
} from "../../api/platformSql";
import PlatformSqlEditorShell from "./PlatformSqlEditorShell";
import BffDataTable from "../operator/BffDataTable";
import DataSourceConnectionFields, {
  isDataSourceConnectionValid,
  type DataSourceConnectionMode,
} from "./DataSourceConnectionFields";

const { TextArea } = Input;

interface DataSourceEditorProps {
  path: string;
  onClose?: () => void;
  onOpenProperties?: () => void;
}

function StatusBanner({ tone, children }: { tone: "ok" | "error" | "neutral"; children: string }) {
  return (
    <Alert
      className={`ds-status-banner ds-status-banner--${tone}`}
      type={tone === "ok" ? "success" : tone === "error" ? "error" : "info"}
      showIcon
      message={children}
    />
  );
}

export default function DataSourceEditor({ path, onClose, onOpenProperties }: DataSourceEditorProps) {
  const { t } = useTranslation(["platform", "common"]);
  const queryClient = useQueryClient();
  const dataQuery = useQuery({
    queryKey: ["data-source", path],
    queryFn: () => fetchDataSource(path),
  });

  const [displayName, setDisplayName] = useState("");
  const [connectionMode, setConnectionMode] = useState<DataSourceConnectionMode>("internal");
  const [schemaName, setSchemaName] = useState("");
  const [jdbcUrl, setJdbcUrl] = useState("");
  const [jdbcDriverClass, setJdbcDriverClass] = useState("");
  const [jdbcUsername, setJdbcUsername] = useState("");
  const [jdbcPassword, setJdbcPassword] = useState("");
  const [poolSize, setPoolSize] = useState(5);
  const [description, setDescription] = useState("");
  const [saveError, setSaveError] = useState<string | null>(null);
  const [testFeedback, setTestFeedback] = useState<string | null>(null);
  const [testOk, setTestOk] = useState<boolean | null>(null);
  const [sqlQuery, setSqlQuery] = useState("SELECT 1 AS n");
  const [queryError, setQueryError] = useState<string | null>(null);
  const [queryResult, setQueryResult] = useState<DataSourceQueryResult | null>(null);

  useEffect(() => {
    if (!dataQuery.data) {
      return;
    }
    setDisplayName(dataQuery.data.variableDisplayName || dataQuery.data.displayName);
    setConnectionMode(dataQuery.data.connectionMode === "external" ? "external" : "internal");
    setSchemaName(dataQuery.data.schemaName);
    setJdbcUrl(dataQuery.data.jdbcUrl);
    setJdbcDriverClass(dataQuery.data.jdbcDriverClass);
    setJdbcUsername(dataQuery.data.jdbcUsername);
    setJdbcPassword("");
    setPoolSize(dataQuery.data.poolSize || 5);
    setDescription(dataQuery.data.description ?? "");
  }, [dataQuery.data]);

  const payload = {
    displayName: displayName.trim(),
    connectionMode,
    schemaName: schemaName.trim(),
    jdbcUrl: jdbcUrl.trim(),
    jdbcDriverClass: jdbcDriverClass.trim(),
    jdbcUsername: jdbcUsername.trim(),
    jdbcPassword: jdbcPassword.trim() || undefined,
    poolSize,
    description,
  };

  const saveMutation = useMutation({
    mutationFn: () => updateDataSource(path, payload),
    onSuccess: async () => {
      setSaveError(null);
      await queryClient.invalidateQueries({ queryKey: ["data-source", path] });
      await queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
    onError: (error: Error) => setSaveError(error.message),
  });

  const testMutation = useMutation({
    mutationFn: () =>
      testDataSourceConnection(
        path,
        connectionMode === "external"
          ? {
              jdbcUrl: jdbcUrl.trim(),
              jdbcDriverClass: jdbcDriverClass.trim() || undefined,
              jdbcUsername: jdbcUsername.trim(),
              jdbcPassword: jdbcPassword.trim() || undefined,
              poolSize,
            }
          : undefined,
      ),
    onSuccess: (result) => {
      setTestOk(result.connected);
      if (result.connected) {
        setTestFeedback(t("platform:dataSource.testOk"));
        return;
      }
      setTestFeedback(result.message || t("platform:dataSource.testFailed"));
    },
    onError: (error: Error) => {
      setTestOk(false);
      setTestFeedback(error.message);
    },
  });

  const executeMutation = useMutation({
    mutationFn: () => executeDataSourceQuery(path, { query: sqlQuery }),
    onSuccess: (result) => {
      setQueryError(null);
      setQueryResult(result);
    },
    onError: (error: Error) => {
      setQueryError(error.message);
      setQueryResult(null);
    },
  });

  const canSave = isDataSourceConnectionValid({ connectionMode, schemaName, jdbcUrl });

  if (dataQuery.isLoading) {
    return <p className="hint">{t("platform:dataSource.loading")}</p>;
  }

  if (dataQuery.error) {
    return <Alert type="error" showIcon message={String(dataQuery.error)} />;
  }

  return (
    <PlatformSqlEditorShell
      fillHeight
      className="data-source-editor"
      title={displayName || dataQuery.data?.displayName || t("platform:dataSource.title")}
      subtitle={t("platform:dataSource.subtitle")}
      path={path}
      onClose={onClose}
      onOpenProperties={onOpenProperties}
      toolbar={
        <>
          <Button
            disabled={testMutation.isPending || saveMutation.isPending}
            onClick={() => testMutation.mutate()}
          >
            {t("platform:dataSource.testConnection")}
          </Button>
          <Button
            type="primary"
            disabled={saveMutation.isPending || !canSave}
            onClick={() => saveMutation.mutate()}
          >
            {saveMutation.isPending ? t("common:action.saving") : t("common:action.save")}
          </Button>
        </>
      }
    >
      <div className="data-source-editor-layout">
        <aside className="data-source-editor-settings panel-card">
          <h3 className="data-source-section-title">{t("platform:dataSource.sectionGeneral")}</h3>
          <form
            className="data-source-settings-form"
            onSubmit={(e) => {
              e.preventDefault();
              saveMutation.mutate();
            }}
          >
            <label className="ds-field ds-field--span-2">
              <span className="ds-field-label">{t("platform:dataSource.displayName")}</span>
              <Input value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
            </label>

            <h3 className="data-source-section-title data-source-section-title--connection">
              {t("platform:dataSource.sectionConnection")}
            </h3>

            <DataSourceConnectionFields
              connectionMode={connectionMode}
              schemaName={schemaName}
              jdbcUrl={jdbcUrl}
              jdbcDriverClass={jdbcDriverClass}
              jdbcUsername={jdbcUsername}
              jdbcPassword={jdbcPassword}
              poolSize={poolSize}
              onConnectionModeChange={setConnectionMode}
              onSchemaNameChange={setSchemaName}
              onJdbcUrlChange={setJdbcUrl}
              onJdbcDriverClassChange={setJdbcDriverClass}
              onJdbcUsernameChange={setJdbcUsername}
              onJdbcPasswordChange={setJdbcPassword}
              onPoolSizeChange={setPoolSize}
              passwordPlaceholder={dataQuery.data?.jdbcPassword ? "********" : ""}
            />

            <label className="ds-field ds-field--span-2">
              <span className="ds-field-label">{t("common:field.description")}</span>
              <TextArea
                className="data-source-description"
                rows={2}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </label>

            {testFeedback && (
              <StatusBanner tone={testOk === true ? "ok" : "error"}>{testFeedback}</StatusBanner>
            )}
            {saveError && <StatusBanner tone="error">{saveError}</StatusBanner>}
            {saveMutation.isSuccess && (
              <StatusBanner tone="ok">{t("common:action.saved")}</StatusBanner>
            )}
          </form>
        </aside>

        <section className="data-source-editor-sql panel-card" aria-label={t("platform:dataSource.sqlConsole")}>
          <header className="data-source-sql-head">
            <div>
              <h3 className="data-source-section-title">{t("platform:dataSource.sqlConsole")}</h3>
              <p className="data-source-sql-hint">{t("platform:dataSource.sqlHint")}</p>
            </div>
            <Button
              type="primary"
              disabled={executeMutation.isPending || !sqlQuery.trim()}
              onClick={() => executeMutation.mutate()}
            >
              {executeMutation.isPending
                ? t("platform:dataSource.executing")
                : t("platform:dataSource.executeQuery")}
            </Button>
          </header>

          <TextArea
            className="data-source-sql-input mono"
            value={sqlQuery}
            onChange={(e) => setSqlQuery(e.target.value)}
            spellCheck={false}
            aria-label="SQL"
          />

          {queryError && <StatusBanner tone="error">{queryError}</StatusBanner>}

          {queryResult?.kind === "update" && (
            <p className="data-source-sql-meta tabular-nums">
              {t("platform:dataSource.updateResult", { count: queryResult.updateCount })}
            </p>
          )}

          {queryResult?.kind === "rows" && (
            <div className="data-source-sql-results">
              <p className="data-source-sql-meta tabular-nums">
                {t("platform:dataSource.rowCount", { count: queryResult.rowCount })}
              </p>
              <div className="data-source-sql-table-wrap">
                <BffDataTable rows={queryResult.rows} />
              </div>
            </div>
          )}
        </section>
      </div>
    </PlatformSqlEditorShell>
  );
}
