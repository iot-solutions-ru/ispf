import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { applyMigration, fetchMigration, updateMigration } from "../../api/platformSql";
import PlatformSqlEditorShell from "./PlatformSqlEditorShell";
import { useDataSourceOptions } from "./useDataSourceOptions";

interface MigrationEditorProps {
  path: string;
  onClose?: () => void;
  onOpenProperties?: () => void;
}

export default function MigrationEditor({ path, onClose, onOpenProperties }: MigrationEditorProps) {
  const queryClient = useQueryClient();
  const migrationQuery = useQuery({
    queryKey: ["migration", path],
    queryFn: () => fetchMigration(path),
  });
  const dataSourcesQuery = useDataSourceOptions();

  const [scriptId, setScriptId] = useState("");
  const [version, setVersion] = useState("1.0.0");
  const [dataSourcePath, setDataSourcePath] = useState("");
  const [sql, setSql] = useState("");
  const [saveError, setSaveError] = useState<string | null>(null);
  const [applyError, setApplyError] = useState<string | null>(null);

  useEffect(() => {
    if (!migrationQuery.data) {
      return;
    }
    setScriptId(migrationQuery.data.scriptId);
    setVersion(migrationQuery.data.version);
    setDataSourcePath(migrationQuery.data.dataSourcePath);
    setSql(migrationQuery.data.sql);
  }, [migrationQuery.data]);

  const saveMutation = useMutation({
    mutationFn: () =>
      updateMigration(path, {
        scriptId: scriptId.trim(),
        version: version.trim(),
        dataSourcePath: dataSourcePath.trim(),
        sql,
      }),
    onSuccess: async () => {
      setSaveError(null);
      await queryClient.invalidateQueries({ queryKey: ["migration", path] });
    },
    onError: (error: Error) => setSaveError(error.message),
  });

  const applyMutation = useMutation({
    mutationFn: () => applyMigration(path),
    onSuccess: async () => {
      setApplyError(null);
      await queryClient.invalidateQueries({ queryKey: ["migration", path] });
    },
    onError: (error: Error) => setApplyError(error.message),
  });

  const applied = migrationQuery.data?.applied ?? false;

  if (migrationQuery.isLoading) {
    return <p className="hint">Загрузка миграции…</p>;
  }

  if (migrationQuery.error) {
    return <div className="op-alert op-alert-error">{String(migrationQuery.error)}</div>;
  }

  return (
    <PlatformSqlEditorShell
      title={scriptId || migrationQuery.data?.scriptId || "Миграция"}
      subtitle={
        applied
          ? `Применена${migrationQuery.data?.appliedAt ? ` — ${migrationQuery.data.appliedAt}` : ""}`
          : "Ожидает применения (SQL изменён или ещё не выполнялся)"
      }
      path={path}
      onClose={onClose}
      onOpenProperties={onOpenProperties}
      toolbar={
        <>
          <button
            type="button"
            className="btn primary"
            disabled={saveMutation.isPending}
            onClick={() => saveMutation.mutate()}
          >
            {saveMutation.isPending ? "Сохранение…" : "Сохранить"}
          </button>
          <button
            type="button"
            className="btn"
            disabled={applyMutation.isPending || !dataSourcePath.trim() || !sql.trim()}
            onClick={() => {
              if (confirm("Применить SQL-миграцию в целевой схеме?")) {
                applyMutation.mutate();
              }
            }}
          >
            {applyMutation.isPending ? "Применение…" : "Применить миграцию"}
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
          Script ID
          <input value={scriptId} onChange={(e) => setScriptId(e.target.value)} />
        </label>
        <label>
          Version
          <input value={version} onChange={(e) => setVersion(e.target.value)} />
        </label>
        <label className="full">
          Data source *
          <select value={dataSourcePath} onChange={(e) => setDataSourcePath(e.target.value)} required>
            <option value="">— выберите —</option>
            {(dataSourcesQuery.data ?? []).map((ds) => (
              <option key={ds.path} value={ds.path}>
                {ds.displayName} ({ds.path})
              </option>
            ))}
          </select>
        </label>
        <label className="full">
          SQL (DDL/DML)
          <textarea
            className="mono"
            rows={14}
            value={sql}
            onChange={(e) => setSql(e.target.value)}
            spellCheck={false}
          />
        </label>
        {migrationQuery.data?.checksum && (
          <p className="hint full mono small">Checksum: {migrationQuery.data.checksum}</p>
        )}
        {saveError && <p className="hint error full">{saveError}</p>}
        {applyError && <p className="hint error full">{applyError}</p>}
        {saveMutation.isSuccess && <p className="hint full">Сохранено</p>}
        {applyMutation.isSuccess && <p className="hint full">Миграция применена</p>}
      </form>
    </PlatformSqlEditorShell>
  );
}
