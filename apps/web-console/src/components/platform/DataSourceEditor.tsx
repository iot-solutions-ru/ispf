import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchDataSource, updateDataSource } from "../../api/platformSql";
import PlatformSqlEditorShell from "./PlatformSqlEditorShell";

interface DataSourceEditorProps {
  path: string;
  onClose?: () => void;
  onOpenProperties?: () => void;
}

export default function DataSourceEditor({ path, onClose, onOpenProperties }: DataSourceEditorProps) {
  const queryClient = useQueryClient();
  const dataQuery = useQuery({
    queryKey: ["data-source", path],
    queryFn: () => fetchDataSource(path),
  });

  const [displayName, setDisplayName] = useState("");
  const [schemaName, setSchemaName] = useState("");
  const [description, setDescription] = useState("");
  const [saveError, setSaveError] = useState<string | null>(null);

  useEffect(() => {
    if (!dataQuery.data) {
      return;
    }
    setDisplayName(dataQuery.data.variableDisplayName || dataQuery.data.displayName);
    setSchemaName(dataQuery.data.schemaName);
    setDescription(dataQuery.data.description ?? "");
  }, [dataQuery.data]);

  const saveMutation = useMutation({
    mutationFn: () =>
      updateDataSource(path, {
        displayName: displayName.trim(),
        schemaName: schemaName.trim(),
        description,
      }),
    onSuccess: async () => {
      setSaveError(null);
      await queryClient.invalidateQueries({ queryKey: ["data-source", path] });
      await queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
    onError: (error: Error) => setSaveError(error.message),
  });

  if (dataQuery.isLoading) {
    return <p className="hint">Загрузка источника данных…</p>;
  }

  if (dataQuery.error) {
    return <div className="op-alert op-alert-error">{String(dataQuery.error)}</div>;
  }

  return (
    <PlatformSqlEditorShell
      title={displayName || dataQuery.data?.displayName || "Источник данных"}
      subtitle="SQL-схема для отчётов, миграций, bindings и script-функций"
      path={path}
      onClose={onClose}
      onOpenProperties={onOpenProperties}
      toolbar={
        <button
          type="button"
          className="btn primary"
          disabled={saveMutation.isPending || !schemaName.trim()}
          onClick={() => saveMutation.mutate()}
        >
          {saveMutation.isPending ? "Сохранение…" : "Сохранить"}
        </button>
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
          Отображаемое имя
          <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
        </label>
        <label>
          PostgreSQL schema *
          <input
            value={schemaName}
            onChange={(e) => setSchemaName(e.target.value)}
            required
            placeholder="app_myapp"
          />
        </label>
        <label className="full">
          Описание
          <textarea rows={2} value={description} onChange={(e) => setDescription(e.target.value)} />
        </label>
        {saveError && <p className="hint error full">{saveError}</p>}
        {saveMutation.isSuccess && <p className="hint full">Сохранено</p>}
      </form>
    </PlatformSqlEditorShell>
  );
}
