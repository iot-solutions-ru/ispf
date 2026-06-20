import { useCallback, useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  deleteObject,
  fetchObjectEditor,
  setVariable,
  updateObject,
} from "../api";
import type { ObjectEditorDto, DataRecord, VariableDto } from "../types";
import {
  cloneRecord,
  ensureRecord,
  recordsEqual,
  setFieldValue,
} from "../utils/record";
import IconPicker from "./icons/IconPicker";
import ObjectTreeIcon from "./icons/ObjectTreeIcon";
import VariableFieldEditor from "./VariableFieldEditor";

interface ObjectPropertiesEditorProps {
  path: string;
  onClose: () => void;
  onDeleted: () => void;
}

type SectionKey = "info" | "variables" | "events" | "functions";

interface EditorState {
  displayName: string;
  description: string;
  iconId: string | null;
  variables: Record<string, DataRecord>;
}

function buildState(data: ObjectEditorDto): EditorState {
  const variables: Record<string, DataRecord> = {};
  for (const v of data.variables) {
    if (v.name === "uiIcon") {
      continue;
    }
    variables[v.name] = ensureRecord(v);
  }
  return {
    displayName: data.object.displayName,
    description: data.object.description,
    iconId: data.object.iconId ?? null,
    variables,
  };
}

function VariableEditorRow({
  variable,
  record,
  baseline,
  onChange,
}: {
  variable: VariableDto;
  record: DataRecord;
  baseline: DataRecord;
  onChange: (next: DataRecord) => void;
}) {
  const [showBinding, setShowBinding] = useState(false);
  const [showJson, setShowJson] = useState(false);
  const dirty = !recordsEqual(record, baseline);
  const row = record.rows[0] ?? {};
  const disabled = !variable.writable || Boolean(variable.bindingExpression);

  return (
    <article className={`property-card ${dirty ? "dirty" : ""}`}>
      <header className="property-card-header">
        <div>
          <code className="property-name">{variable.name}</code>
          <span className="property-badges">
            {variable.readable && <span className="badge">R</span>}
            {variable.writable && <span className="badge w">W</span>}
            {variable.bindingExpression && <span className="badge b">Σ</span>}
          </span>
        </div>
        <div className="property-card-tools">
          <button type="button" className="btn tiny" onClick={() => setShowBinding((v) => !v)}>
            Привязка
          </button>
          <button type="button" className="btn tiny" onClick={() => setShowJson((v) => !v)}>
            JSON
          </button>
        </div>
      </header>

      {showBinding && (
        <div className="binding-panel">
          <span className="field-label">Выражение привязки (CEL)</span>
          <code className="binding-expr">{variable.bindingExpression ?? "—"}</code>
        </div>
      )}

      {showJson ? (
        <textarea
          className="json-editor compact"
          rows={6}
          disabled={disabled}
          value={JSON.stringify(record, null, 2)}
          onChange={(e) => {
            try {
              onChange(JSON.parse(e.target.value));
            } catch {
              // ignore parse errors while typing
            }
          }}
        />
      ) : (
        <div className="property-fields">
          {record.schema.fields.map((field) => (
            <VariableFieldEditor
              key={field.name}
              field={field}
              value={row[field.name]}
              disabled={disabled}
              onChange={(val) => onChange(setFieldValue(record, field.name, val))}
            />
          ))}
          {record.schema.fields.length === 0 && (
            <p className="hint">Схема переменной пуста</p>
          )}
        </div>
      )}
    </article>
  );
}

export default function ObjectPropertiesEditor({
  path,
  onClose,
  onDeleted,
}: ObjectPropertiesEditorProps) {
  const queryClient = useQueryClient();
  const [openSections, setOpenSections] = useState<Record<SectionKey, boolean>>({
    info: true,
    variables: true,
    events: true,
    functions: true,
  });

  const editorQuery = useQuery({
    queryKey: ["object-editor", path],
    queryFn: () => fetchObjectEditor(path),
  });

  const [state, setState] = useState<EditorState | null>(null);
  const [baseline, setBaseline] = useState<EditorState | null>(null);

  useEffect(() => {
    if (editorQuery.data) {
      const next = buildState(editorQuery.data);
      setState(next);
      setBaseline(next);
    }
  }, [editorQuery.data]);

  const isDirty = useMemo(() => {
    if (!state || !baseline) return false;
    if (state.displayName !== baseline.displayName) return true;
    if (state.description !== baseline.description) return true;
    if (state.iconId !== baseline.iconId) return true;
    for (const name of Object.keys(state.variables)) {
      if (!recordsEqual(state.variables[name], baseline.variables[name])) {
        return true;
      }
    }
    return false;
  }, [state, baseline]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!state || !baseline || !editorQuery.data) return;
      await updateObject(path, {
        displayName: state.displayName,
        description: state.description,
        iconId: state.iconId ?? "",
      });
      for (const variable of editorQuery.data.variables) {
        const current = state.variables[variable.name];
        const base = baseline.variables[variable.name];
        if (variable.writable && !variable.bindingExpression && !recordsEqual(current, base)) {
          await setVariable(path, variable.name, current);
        }
      }
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["objects"] });
      await queryClient.invalidateQueries({ queryKey: ["object-editor", path] });
      const fresh = await fetchObjectEditor(path);
      const next = buildState(fresh);
      setState(next);
      setBaseline(next);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteObject(path),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      onDeleted();
      onClose();
    },
  });

  const revert = useCallback(() => {
    if (baseline) {
      setState({
        displayName: baseline.displayName,
        description: baseline.description,
        iconId: baseline.iconId,
        variables: Object.fromEntries(
          Object.entries(baseline.variables).map(([k, v]) => [k, cloneRecord(v)])
        ),
      });
    }
  }, [baseline]);

  const toggleSection = (key: SectionKey) => {
    setOpenSections((prev) => ({ ...prev, [key]: !prev[key] }));
  };

  if (editorQuery.isLoading || !state || !editorQuery.data) {
    return <div className="editor-loading">Загрузка редактора…</div>;
  }

  if (editorQuery.error) {
    return <div className="editor-loading error">Ошибка загрузки редактора</div>;
  }

  const ctx = editorQuery.data.object;
  const isRoot = path === "root";
  const crumbs = path.split(".");

  return (
    <div className="properties-editor">
      <div className="properties-editor-toolbar">
        <div className="breadcrumb">
          {crumbs.map((part, index) => {
            const crumbPath = crumbs.slice(0, index + 1).join(".");
            return (
              <span key={crumbPath} className="crumb">
                {index > 0 && <span className="crumb-sep">/</span>}
                <span>{part}</span>
              </span>
            );
          })}
        </div>
        <div className="toolbar-actions">
          <button type="button" className="btn" onClick={() => editorQuery.refetch()}>
            Обновить
          </button>
          <button type="button" className="btn" disabled={!isDirty} onClick={revert}>
            Отменить
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={!isDirty || saveMutation.isPending}
            onClick={() => saveMutation.mutate()}
          >
            Сохранить
          </button>
          {!isRoot && (
            <button
              type="button"
              className="btn danger"
              onClick={() => {
                if (confirm(`Удалить «${ctx.displayName}»?`)) {
                  deleteMutation.mutate();
                }
              }}
            >
              Удалить
            </button>
          )}
        </div>
      </div>

      <div className="properties-editor-header">
        <span className="ctx-icon">
          <ObjectTreeIcon path={ctx.path} type={ctx.type} iconId={state.iconId} size={24} />
        </span>
        <div>
          <h2>{ctx.displayName}</h2>
          <p className="hint">{ctx.description || "Универсальный редактор объекта"}</p>
        </div>
        {isDirty && <span className="dirty-pill">Изменено</span>}
      </div>

      {saveMutation.isSuccess && <div className="banner success">Изменения сохранены</div>}
      {saveMutation.error && (
        <div className="banner error">{(saveMutation.error as Error).message}</div>
      )}

      <section className="editor-section">
        <button type="button" className="section-toggle" onClick={() => toggleSection("info")}>
          <span>{openSections.info ? "▾" : "▸"}</span> Информация
        </button>
        {openSections.info && (
          <div className="section-body form-grid">
            <label>
              Отображаемое имя
              <input
                value={state.displayName}
                onChange={(e) => setState((s) => s && { ...s, displayName: e.target.value })}
              />
            </label>
            <label>
              Тип
              <input value={ctx.type} readOnly className="readonly" />
            </label>
            <label>
              Путь
              <input value={ctx.path} readOnly className="readonly" />
            </label>
            <label>
              Модель
              <input value={ctx.templateId ?? "—"} readOnly className="readonly" />
            </label>
            <label className="full">
              Описание
              <textarea
                rows={2}
                value={state.description}
                onChange={(e) => setState((s) => s && { ...s, description: e.target.value })}
              />
            </label>
            <label className="full">
              Иконка в дереве
              <IconPicker
                path={ctx.path}
                type={ctx.type}
                value={state.iconId}
                disabled={isRoot}
                onChange={(iconId) => setState((s) => s && { ...s, iconId })}
              />
            </label>
          </div>
        )}
      </section>

      <section className="editor-section">
        <button type="button" className="section-toggle" onClick={() => toggleSection("variables")}>
          <span>{openSections.variables ? "▾" : "▸"}</span> Переменные ({editorQuery.data.variables.length})
        </button>
        {openSections.variables && (
          <div className="section-body property-list">
            {editorQuery.data.variables.length === 0 && (
              <p className="hint">Нет переменных</p>
            )}
            {editorQuery.data.variables
              .filter((variable) => variable.name !== "uiIcon")
              .map((variable) => (
              <VariableEditorRow
                key={variable.name}
                variable={variable}
                record={state.variables[variable.name]}
                baseline={baseline!.variables[variable.name]}
                onChange={(next) =>
                  setState((s) =>
                    s ? { ...s, variables: { ...s.variables, [variable.name]: next } } : s
                  )
                }
              />
            ))}
          </div>
        )}
      </section>

      <section className="editor-section">
        <button type="button" className="section-toggle" onClick={() => toggleSection("events")}>
          <span>{openSections.events ? "▾" : "▸"}</span> События ({editorQuery.data.events.length})
        </button>
        {openSections.events && (
          <div className="section-body">
            {editorQuery.data.events.length === 0 ? (
              <p className="hint">Нет событий</p>
            ) : (
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Имя</th>
                    <th>Уровень</th>
                    <th>Описание</th>
                  </tr>
                </thead>
                <tbody>
                  {editorQuery.data.events.map((ev) => (
                    <tr key={ev.name}>
                      <td><code>{ev.name}</code></td>
                      <td>{ev.level}</td>
                      <td>{ev.description || "—"}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </section>

      <section className="editor-section">
        <button type="button" className="section-toggle" onClick={() => toggleSection("functions")}>
          <span>{openSections.functions ? "▾" : "▸"}</span> Функции ({editorQuery.data.functions.length})
        </button>
        {openSections.functions && (
          <div className="section-body">
            {editorQuery.data.functions.length === 0 ? (
              <p className="hint">Нет функций</p>
            ) : (
              <table className="data-table">
                <thead>
                  <tr>
                    <th>Имя</th>
                    <th>Описание</th>
                    <th>Вход</th>
                    <th>Выход</th>
                  </tr>
                </thead>
                <tbody>
                  {editorQuery.data.functions.map((fn) => (
                    <tr key={fn.name}>
                      <td><code>{fn.name}</code></td>
                      <td>{fn.description || "—"}</td>
                      <td className="mono small">{fn.inputSchema.name}</td>
                      <td className="mono small">{fn.outputSchema.name}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}
      </section>
    </div>
  );
}
