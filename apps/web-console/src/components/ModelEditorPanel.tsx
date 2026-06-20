import { useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  applyModel,
  createModel,
  createModelFromObject,
  deleteModel,
  fetchModelByName,
  fetchModels,
  instantiateModel,
  updateModel,
} from "../api/models";
import type { ObjectType } from "../types";
import {
  BUILTIN_MODEL_NAMES,
  MODELS_ROOT,
  modelNameFromPath,
  type ModelDto,
} from "../types/models";
import { recordDisplayValue } from "../utils/tree";

interface ModelEditorPanelProps {
  selectedPath: string;
  canManage: boolean;
  onSelectPath?: (path: string) => void;
  onClose?: () => void;
  title?: string;
}

const OBJECT_TYPES: ObjectType[] = [
  "DEVICE",
  "DASHBOARD",
  "WORKFLOW",
  "CUSTOM",
  "APPLICATION",
  "USER",
];

function ModelDetail({
  model,
  canManage,
  onSelectPath,
}: {
  model: ModelDto;
  canManage: boolean;
  onSelectPath?: (path: string) => void;
}) {
  const queryClient = useQueryClient();
  const [description, setDescription] = useState(model.description);
  const [suitability, setSuitability] = useState(model.suitabilityExpression);
  const [applyPath, setApplyPath] = useState("");
  const [parentPath, setParentPath] = useState("root.platform.devices");
  const [instanceName, setInstanceName] = useState("");
  const isBuiltin = BUILTIN_MODEL_NAMES.has(model.name);

  const saveMutation = useMutation({
    mutationFn: () =>
      updateModel(model.id, {
        description,
        suitabilityExpression: suitability,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["model", model.name] });
      queryClient.invalidateQueries({ queryKey: ["models"] });
    },
  });

  const applyMutation = useMutation({
    mutationFn: () => applyModel(model.id, applyPath.trim()),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["variables"] });
      if (applyPath.trim() && onSelectPath) {
        onSelectPath(applyPath.trim());
      }
    },
  });

  const instantiateMutation = useMutation({
    mutationFn: () =>
      instantiateModel(model.id, parentPath.trim(), instanceName.trim()),
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      onSelectPath?.(created.path);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteModel(model.id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["models"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      onSelectPath?.(MODELS_ROOT);
    },
  });

  return (
    <div className="model-detail">
      <div className="model-meta-grid">
        <div>
          <span className="model-meta-label">Тип модели</span>
          <code>{model.type}</code>
        </div>
        <div>
          <span className="model-meta-label">Целевой ObjectType</span>
          <code>{model.targetObjectType}</code>
        </div>
        <div>
          <span className="model-meta-label">Путь в дереве</span>
          <code>{model.objectPath}</code>
        </div>
        <div>
          <span className="model-meta-label">Встроенная</span>
          <span>{isBuiltin ? "да" : "нет"}</span>
        </div>
      </div>

      {canManage && (
        <form
          className="model-edit-form"
          onSubmit={(e) => {
            e.preventDefault();
            saveMutation.mutate();
          }}
        >
          <label>
            Описание
            <textarea
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              readOnly={isBuiltin}
            />
          </label>
          <label>
            Условие применимости (CEL)
            <input
              value={suitability}
              onChange={(e) => setSuitability(e.target.value)}
              placeholder="пусто = по targetObjectType"
            />
          </label>
          {!isBuiltin && (
            <button type="submit" className="btn primary" disabled={saveMutation.isPending}>
              Сохранить метаданные
            </button>
          )}
          {saveMutation.error && (
            <p className="hint error">{String(saveMutation.error)}</p>
          )}
        </form>
      )}

      <section className="model-section">
        <h4>Переменные ({model.variables.length})</h4>
        {model.variables.length === 0 ? (
          <p className="hint">Нет переменных</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Имя</th>
                <th>Группа</th>
                <th>Запись</th>
                <th>По умолчанию</th>
                <th>Binding</th>
              </tr>
            </thead>
            <tbody>
              {model.variables.map((v) => (
                <tr key={v.name}>
                  <td>
                    <code>{v.name}</code>
                    {v.description && (
                      <span className="model-var-desc">{v.description}</span>
                    )}
                  </td>
                  <td>{v.group}</td>
                  <td>{v.writable ? "да" : "нет"}</td>
                  <td className="mono small">
                    {v.defaultValue ? recordDisplayValue(v.defaultValue) : "—"}
                  </td>
                  <td className="mono small">{v.defaultBinding ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="model-section">
        <h4>Bindings ({model.bindings.length})</h4>
        {model.bindings.length === 0 ? (
          <p className="hint">Нет вычисляемых привязок</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Переменная</th>
                <th>CEL-выражение</th>
              </tr>
            </thead>
            <tbody>
              {model.bindings.map((b) => (
                <tr key={b.targetVariable}>
                  <td><code>{b.targetVariable}</code></td>
                  <td className="mono small">{b.expression}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </section>

      <section className="model-section model-section-inline">
        <div>
          <h4>События ({model.events.length})</h4>
          {model.events.length === 0 ? (
            <p className="hint">Нет событий</p>
          ) : (
            <ul className="event-list">
              {model.events.map((e) => (
                <li key={e.name}>
                  <code>{e.name}</code>
                  <span className="model-var-desc">{e.description}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div>
          <h4>Функции ({model.functions.length})</h4>
          {model.functions.length === 0 ? (
            <p className="hint">Нет функций</p>
          ) : (
            <ul className="event-list">
              {model.functions.map((f) => (
                <li key={f.name}>
                  <code>{f.name}</code>
                  <span className="model-var-desc">{f.description}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>

      {canManage && (
        <section className="model-section model-actions">
          <h4>Действия</h4>
          <div className="model-action-block">
            <p className="hint">
              <strong>Apply</strong> — влить переменные, события и bindings в существующий объект (RELATIVE).
            </p>
            <div className="model-action-row">
              <input
                value={applyPath}
                onChange={(e) => setApplyPath(e.target.value)}
                placeholder="objectPath, напр. root.platform.devices.my-sensor"
              />
              <button
                type="button"
                className="btn primary"
                disabled={!applyPath.trim() || applyMutation.isPending}
                onClick={() => applyMutation.mutate()}
              >
                Применить
              </button>
            </div>
            {applyMutation.error && (
              <p className="hint error">{String(applyMutation.error)}</p>
            )}
          </div>

          {model.type === "INSTANCE" && (
            <div className="model-action-block">
              <p className="hint">
                <strong>Instantiate</strong> — создать новый дочерний объект по модели.
              </p>
              <div className="model-action-row">
                <input
                  value={parentPath}
                  onChange={(e) => setParentPath(e.target.value)}
                  placeholder="parentPath"
                />
                <input
                  value={instanceName}
                  onChange={(e) => setInstanceName(e.target.value)}
                  placeholder="имя экземпляра"
                />
                <button
                  type="button"
                  className="btn primary"
                  disabled={!parentPath.trim() || !instanceName.trim() || instantiateMutation.isPending}
                  onClick={() => instantiateMutation.mutate()}
                >
                  Создать экземпляр
                </button>
              </div>
              {instantiateMutation.error && (
                <p className="hint error">{String(instantiateMutation.error)}</p>
              )}
            </div>
          )}

          {!isBuiltin && (
            <div className="model-action-block">
              <button
                type="button"
                className="btn danger"
                disabled={deleteMutation.isPending}
                onClick={() => {
                  if (confirm(`Удалить модель «${model.name}»?`)) {
                    deleteMutation.mutate();
                  }
                }}
              >
                Удалить модель
              </button>
            </div>
          )}
        </section>
      )}
    </div>
  );
}

function ModelsCatalog({
  canManage,
  selectedPath,
  onSelectPath,
}: {
  canManage: boolean;
  selectedPath: string;
  onSelectPath?: (path: string) => void;
}) {
  const queryClient = useQueryClient();
  const modelsQuery = useQuery({
    queryKey: ["models"],
    queryFn: fetchModels,
  });

  const createMutation = useMutation({
    mutationFn: createModel,
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ["models"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      onSelectPath?.(created.objectPath);
    },
  });

  const fromObjectMutation = useMutation({
    mutationFn: ({
      sourcePath,
      modelName,
      description,
      type,
    }: {
      sourcePath: string;
      modelName: string;
      description: string;
      type: "RELATIVE" | "INSTANCE";
    }) => createModelFromObject(sourcePath, modelName, description, type),
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ["models"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      onSelectPath?.(created.objectPath);
    },
  });

  const sorted = useMemo(
    () => [...(modelsQuery.data ?? [])].sort((a, b) => a.name.localeCompare(b.name)),
    [modelsQuery.data]
  );

  return (
    <div className="models-catalog">
      <p className="hint">
        Модели — чертежи структуры объектов. Узлы в дереве — закладки; полное определение здесь.
      </p>
      {modelsQuery.error && (
        <p className="hint error">{String(modelsQuery.error)}</p>
      )}
      <table className="data-table">
        <thead>
          <tr>
            <th>Имя</th>
            <th>Тип</th>
            <th>ObjectType</th>
            <th>Переменные</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {sorted.map((m) => (
            <tr key={m.id}>
              <td>
                <button
                  type="button"
                  className="link-btn"
                  onClick={() => onSelectPath?.(m.objectPath)}
                >
                  <code>{m.name}</code>
                </button>
              </td>
              <td>{m.type}</td>
              <td>{m.targetObjectType}</td>
              <td>{m.variables.length}</td>
              <td className="mono small">{m.description}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {canManage && (
        <>
          <form
            className="model-create-form"
            onSubmit={(e) => {
              e.preventDefault();
              const form = e.currentTarget;
              const data = new FormData(form);
              createMutation.mutate({
                name: String(data.get("name") ?? ""),
                description: String(data.get("description") ?? ""),
                type: String(data.get("type") ?? "RELATIVE") as "RELATIVE" | "INSTANCE",
                targetObjectType: String(data.get("targetObjectType") ?? "CUSTOM") as ObjectType,
              });
              form.reset();
            }}
          >
            <h4>Новая пустая модель</h4>
            <div className="model-form-grid">
              <input name="name" placeholder="имя-модели" required pattern="[a-zA-Z0-9._-]+" />
              <input name="description" placeholder="описание" />
              <select name="type" defaultValue="RELATIVE">
                <option value="RELATIVE">RELATIVE</option>
                <option value="INSTANCE">INSTANCE</option>
                <option value="ABSOLUTE">ABSOLUTE</option>
              </select>
              <select name="targetObjectType" defaultValue="CUSTOM">
                {OBJECT_TYPES.map((t) => (
                  <option key={t} value={t}>
                    {t}
                  </option>
                ))}
              </select>
              <button type="submit" className="btn primary" disabled={createMutation.isPending}>
                Создать
              </button>
            </div>
            {createMutation.error && (
              <p className="hint error">{String(createMutation.error)}</p>
            )}
          </form>

          <form
            className="model-create-form"
            onSubmit={(e) => {
              e.preventDefault();
              const form = e.currentTarget;
              const data = new FormData(form);
              fromObjectMutation.mutate({
                sourcePath: String(data.get("sourcePath") ?? ""),
                modelName: String(data.get("modelName") ?? ""),
                description: String(data.get("description") ?? ""),
                type: String(data.get("type") ?? "RELATIVE") as "RELATIVE" | "INSTANCE",
              });
              form.reset();
            }}
          >
            <h4>Сохранить объект как модель</h4>
            <p className="hint">
              Снимок переменных, событий, функций и bindings с объекта{" "}
              {selectedPath !== MODELS_ROOT ? (
                <code>{selectedPath}</code>
              ) : (
                "(укажите путь)"
              )}
            </p>
            <div className="model-form-grid">
              <input
                name="sourcePath"
                placeholder="sourcePath"
                defaultValue={selectedPath !== MODELS_ROOT ? selectedPath : ""}
                required
              />
              <input name="modelName" placeholder="имя новой модели" required />
              <input name="description" placeholder="описание" />
              <select name="type" defaultValue="RELATIVE">
                <option value="RELATIVE">RELATIVE</option>
                <option value="INSTANCE">INSTANCE</option>
              </select>
              <button type="submit" className="btn" disabled={fromObjectMutation.isPending}>
                Экспортировать
              </button>
            </div>
            {fromObjectMutation.error && (
              <p className="hint error">{String(fromObjectMutation.error)}</p>
            )}
          </form>
        </>
      )}
    </div>
  );
}

export default function ModelEditorPanel({
  selectedPath,
  canManage,
  onSelectPath,
  onClose,
  title,
}: ModelEditorPanelProps) {
  const modelName = modelNameFromPath(selectedPath);
  const isCatalog = selectedPath === MODELS_ROOT;
  const headerTitle = isCatalog
    ? "Каталог моделей"
    : modelName
      ? `Модель ${modelName}`
      : (title ?? selectedPath.split(".").pop() ?? "Модель");

  const modelQuery = useQuery({
    queryKey: ["model", modelName],
    queryFn: () => fetchModelByName(modelName!),
    enabled: !!modelName,
  });

  return (
    <div className="model-editor-shell">
      <header className="model-editor-header">
        <div>
          <h2>{headerTitle}</h2>
          <code className="path-code">{selectedPath}</code>
        </div>
        {onClose && (
          <button type="button" className="btn" onClick={onClose}>
            Закрыть
          </button>
        )}
      </header>

      <div className="model-editor-body">
        {isCatalog ? (
          <ModelsCatalog
            canManage={canManage}
            selectedPath={selectedPath}
            onSelectPath={onSelectPath}
          />
        ) : !modelName ? (
          <p className="hint">Выберите модель в дереве</p>
        ) : (
          <>
            {!canManage && (
              <p className="hint">Редактирование моделей доступно только admin.</p>
            )}
            {modelQuery.isLoading && <p className="hint">Загрузка модели…</p>}
            {modelQuery.error && (
              <p className="hint error">
                Модель не найдена в реестре. Возможно, узел дерева не синхронизирован.
                <br />
                {String(modelQuery.error)}
              </p>
            )}
            {modelQuery.data && (
              <ModelDetail
                model={modelQuery.data}
                canManage={canManage}
                onSelectPath={onSelectPath}
              />
            )}
          </>
        )}
      </div>
    </div>
  );
}
