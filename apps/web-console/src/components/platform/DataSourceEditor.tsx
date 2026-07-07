import { useEffect, useState } from "react";

import { useTranslation } from "react-i18next";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";

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



interface DataSourceEditorProps {

  path: string;

  onClose?: () => void;

  onOpenProperties?: () => void;

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

      if (result.connected) {
        setTestFeedback(t("platform:dataSource.testOk"));
        return;
      }
      setTestFeedback(result.message || t("platform:dataSource.testFailed"));

    },

    onError: (error: Error) => setTestFeedback(error.message),

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

    return <div className="op-alert op-alert-error">{String(dataQuery.error)}</div>;

  }



  return (

    <PlatformSqlEditorShell

      title={displayName || dataQuery.data?.displayName || t("platform:dataSource.title")}

      subtitle={t("platform:dataSource.subtitle")}

      path={path}

      onClose={onClose}

      onOpenProperties={onOpenProperties}

      toolbar={

        <>

          <button

            type="button"

            className="btn"

            disabled={testMutation.isPending || saveMutation.isPending}

            onClick={() => testMutation.mutate()}

          >

            {t("platform:dataSource.testConnection")}

          </button>

          <button

            type="button"

            className="btn primary"

            disabled={saveMutation.isPending || !canSave}

            onClick={() => saveMutation.mutate()}

          >

            {saveMutation.isPending ? t("common:action.saving") : t("common:action.save")}

          </button>

        </>

      }

    >

      <form

        className="form-grid report-builder-form"

        onSubmit={(e) => {

          e.preventDefault();

          saveMutation.mutate();

        }}

      >

        <label>

          {t("platform:dataSource.displayName")}

          <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} />

        </label>

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

        <label className="full">

          {t("common:field.description")}

          <textarea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />

        </label>

        {testFeedback && <p className="hint full">{testFeedback}</p>}

        {saveError && <p className="hint error full">{saveError}</p>}

        {saveMutation.isSuccess && <p className="hint full">{t("common:action.saved")}</p>}

      </form>



      <section className="form-grid report-builder-form full" style={{ marginTop: "1.5rem" }}>

        <h3 className="full">{t("platform:dataSource.sqlConsole")}</h3>

        <p className="hint full">{t("platform:dataSource.sqlHint")}</p>

        <label className="full">

          SQL

          <textarea

            className="mono"

            rows={8}

            value={sqlQuery}

            onChange={(e) => setSqlQuery(e.target.value)}

            spellCheck={false}

          />

        </label>

        <div className="full">

          <button

            type="button"

            className="btn primary"

            disabled={executeMutation.isPending || !sqlQuery.trim()}

            onClick={() => executeMutation.mutate()}

          >

            {executeMutation.isPending

              ? t("platform:dataSource.executing")

              : t("platform:dataSource.executeQuery")}

          </button>

        </div>

        {queryError && <p className="hint error full">{queryError}</p>}

        {queryResult?.kind === "update" && (

          <p className="hint full">

            {t("platform:dataSource.updateResult", { count: queryResult.updateCount })}

          </p>

        )}

        {queryResult?.kind === "rows" && (

          <>

            <p className="hint full">

              {t("platform:dataSource.rowCount", { count: queryResult.rowCount })}

            </p>

            <div className="full">

              <BffDataTable rows={queryResult.rows} />

            </div>

          </>

        )}

      </section>

    </PlatformSqlEditorShell>

  );

}

