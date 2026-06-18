import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  deleteObject,
  fetchObject,
  fetchVariables,
  updateObject,
} from "../api";
import type { ObjectSummary, VariableDto } from "../types";
import { recordDisplayValue } from "../utils/tree";
import EditVariableDialog from "./EditVariableDialog";

interface ObjectInspectorProps {
  path: string;
  onDeleted: () => void;
}

type Tab = "general" | "variables" | "events";

export default function ObjectInspector({ path, onDeleted }: ObjectInspectorProps) {
  const queryClient = useQueryClient();
  const [tab, setTab] = useState<Tab>("general");
  const [displayName, setDisplayName] = useState("");
  const [description, setDescription] = useState("");
  const [editingVariable, setEditingVariable] = useState<VariableDto | null>(null);

  const objectQuery = useQuery({
    queryKey: ["object", path],
    queryFn: () => fetchObject(path),
  });

  const variablesQuery = useQuery({
    queryKey: ["variables", path],
    queryFn: () => fetchVariables(path),
    enabled: tab === "variables",
  });

  useEffect(() => {
    if (objectQuery.data) {
      setDisplayName(objectQuery.data.displayName);
      setDescription(objectQuery.data.description);
    }
  }, [objectQuery.data]);

  const saveMutation = useMutation({
    mutationFn: () => updateObject(path, { displayName, description }),
    onSuccess: (updated: ObjectSummary) => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.setQueryData(["object", path], updated);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteObject(path),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      onDeleted();
    },
  });

  if (objectQuery.isLoading) {
    return <div className="inspector-empty">Загрузка объекта…</div>;
  }

  if (objectQuery.error || !objectQuery.data) {
    return <div className="inspector-empty error">Не удалось загрузить объект</div>;
  }

  const obj = objectQuery.data;
  const isRoot = path === "root";

  return (
    <div className="inspector">
      <header className="inspector-header">
        <div>
          <h2>{obj.displayName}</h2>
          <code className="path-code">{obj.path}</code>
        </div>
        <div className="inspector-actions">
          {!isRoot && (
            <button
              type="button"
              className="btn danger"
              disabled={deleteMutation.isPending}
              onClick={() => {
                if (confirm(`Удалить объект «${obj.displayName}» и все дочерние?`)) {
                  deleteMutation.mutate();
                }
              }}
            >
              Удалить
            </button>
          )}
        </div>
      </header>

      <nav className="tabs">
        {(["general", "variables", "events"] as Tab[]).map((t) => (
          <button
            key={t}
            type="button"
            className={tab === t ? "active" : ""}
            onClick={() => setTab(t)}
          >
            {t === "general" ? "Свойства" : t === "variables" ? "Переменные" : "События"}
          </button>
        ))}
      </nav>

      {tab === "general" && (
        <section className="panel">
          <form
            className="form-grid"
            onSubmit={(e) => {
              e.preventDefault();
              saveMutation.mutate();
            }}
          >
            <label>
              Имя
              <input value={displayName} onChange={(e) => setDisplayName(e.target.value)} />
            </label>
            <label>
              Тип
              <input value={obj.type} readOnly className="readonly" />
            </label>
            <label className="full">
              Описание
              <textarea
                rows={3}
                value={description}
                onChange={(e) => setDescription(e.target.value)}
              />
            </label>
            <label>
              ID
              <input value={obj.id} readOnly className="readonly" />
            </label>
            <label>
              Модель
              <input value={obj.templateId ?? "—"} readOnly className="readonly" />
            </label>
            <label>
              Создан
              <input value={new Date(obj.createdAt).toLocaleString()} readOnly className="readonly" />
            </label>
            <div className="full form-actions">
              <button type="submit" className="btn primary" disabled={saveMutation.isPending}>
                Сохранить
              </button>
              {saveMutation.isSuccess && <span className="hint success">Сохранено</span>}
              {saveMutation.error && <span className="hint error">Ошибка сохранения</span>}
            </div>
          </form>
        </section>
      )}

      {tab === "variables" && (
        <section className="panel">
          {variablesQuery.isLoading && <p>Загрузка переменных…</p>}
          {variablesQuery.data && variablesQuery.data.length === 0 && (
            <p className="hint">Нет переменных</p>
          )}
          {variablesQuery.data && variablesQuery.data.length > 0 && (
            <table className="data-table">
              <thead>
                <tr>
                  <th>Имя</th>
                  <th>Значение</th>
                  <th>Запись</th>
                  <th>Привязка</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {variablesQuery.data.map((v) => (
                  <tr key={v.name}>
                    <td><code>{v.name}</code></td>
                    <td className="mono">{recordDisplayValue(v.value)}</td>
                    <td>{v.writable ? "да" : "нет"}</td>
                    <td className="mono small">{v.bindingExpression ?? "—"}</td>
                    <td>
                      {v.writable && (
                        <button
                          type="button"
                          className="btn small"
                          onClick={() => setEditingVariable(v)}
                        >
                          Изменить
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}

      {tab === "events" && (
        <section className="panel">
          {obj.eventNames.length === 0 ? (
            <p className="hint">Нет событий</p>
          ) : (
            <ul className="event-list">
              {obj.eventNames.map((name) => (
                <li key={name}>
                  <code>{name}</code>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {editingVariable && (
        <EditVariableDialog
          objectPath={path}
          variable={editingVariable}
          onClose={() => setEditingVariable(null)}
          onSaved={() => {
            queryClient.invalidateQueries({ queryKey: ["variables", path] });
            setEditingVariable(null);
          }}
        />
      )}
    </div>
  );
}
