import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { deleteBindingRule, fetchBindingRules, saveBindingRules } from "../api";
import type { BindingActivators, BindingRule } from "../types";
import BindingExpressionField from "./BindingExpressionField";

interface BindingRulesPanelProps {
  path: string;
  canManage: boolean;
}

function defaultActivators(): BindingActivators {
  return {
    onStartup: false,
    onVariableChange: [{ objectPath: "self", variableName: "*" }],
    onEvent: null,
    periodicMs: 0,
  };
}

function activatorsSummary(rule: BindingRule): string {
  const parts: string[] = [];
  if (rule.activators.onStartup) {
    parts.push("startup");
  }
  for (const ref of rule.activators.onVariableChange ?? []) {
    parts.push(`${ref.objectPath}:${ref.variableName}`);
  }
  if (rule.activators.periodicMs > 0) {
    parts.push(`${rule.activators.periodicMs}ms`);
  }
  return parts.length > 0 ? parts.join(", ") : "—";
}

function emptyRule(): BindingRule {
  return {
    id: "",
    name: "",
    enabled: true,
    order: 0,
    activators: defaultActivators(),
    condition: "",
    expression: "",
    target: { variableName: "", field: "value" },
  };
}

export default function BindingRulesPanel({ path, canManage }: BindingRulesPanelProps) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<BindingRule | null>(null);

  const rulesQuery = useQuery({
    queryKey: ["binding-rules", path],
    queryFn: () => fetchBindingRules(path),
  });

  const saveMutation = useMutation({
    mutationFn: (rule: BindingRule) => {
      const current = rulesQuery.data ?? [];
      const next = current.filter((r) => r.id !== rule.id);
      next.push(rule);
      next.sort((a, b) => a.order - b.order || a.id.localeCompare(b.id));
      return saveBindingRules(path, next);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["binding-rules", path] });
      queryClient.invalidateQueries({ queryKey: ["variables", path] });
      setEditing(null);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (ruleId: string) => deleteBindingRule(path, ruleId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["binding-rules", path] });
      queryClient.invalidateQueries({ queryKey: ["variables", path] });
    },
  });

  if (rulesQuery.isLoading) {
    return <p>Загрузка привязок…</p>;
  }

  const rules = rulesQuery.data ?? [];

  return (
    <section className="panel">
      {canManage && (
        <div className="panel-toolbar">
          <button type="button" className="btn primary small" onClick={() => setEditing(emptyRule())}>
            + Правило
          </button>
        </div>
      )}

      {rules.length === 0 && <p className="hint">Нет правил привязки</p>}

      {rules.length > 0 && (
        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Цель</th>
                <th>Выражение</th>
                <th>Активаторы</th>
                <th>Вкл</th>
                {canManage && <th aria-label="Действия" />}
              </tr>
            </thead>
            <tbody>
              {rules.map((rule) => (
                <tr key={rule.id}>
                  <td><code>{rule.id}</code></td>
                  <td><code>{rule.target.variableName}</code></td>
                  <td className="mono small" title={rule.expression}>{rule.expression || "—"}</td>
                  <td className="small">{activatorsSummary(rule)}</td>
                  <td>{rule.enabled ? "да" : "нет"}</td>
                  {canManage && (
                    <td>
                      <button type="button" className="btn small" onClick={() => setEditing(rule)}>Изменить</button>
                      <button
                        type="button"
                        className="btn small danger"
                        disabled={deleteMutation.isPending}
                        onClick={() => deleteMutation.mutate(rule.id)}
                      >
                        Удалить
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing && (
        <div className="modal-backdrop" onClick={() => setEditing(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <header>
              <h3>{editing.id ? `Правило: ${editing.id}` : "Новое правило"}</h3>
              <button type="button" className="icon-btn" onClick={() => setEditing(null)}>✕</button>
            </header>
            <section className="modal-section form-grid">
              <label className="full">
                ID
                <input
                  value={editing.id}
                  disabled={Boolean(rules.find((r) => r.id === editing.id))}
                  onChange={(e) => setEditing({ ...editing, id: e.target.value })}
                  pattern="[A-Za-z0-9_-]+"
                  required
                />
              </label>
              <label className="full">
                Целевая переменная
                <input
                  value={editing.target.variableName}
                  onChange={(e) =>
                    setEditing({
                      ...editing,
                      target: { ...editing.target, variableName: e.target.value },
                    })
                  }
                  required
                />
              </label>
              <label className="full">
                Выражение
                <BindingExpressionField
                  value={editing.expression}
                  onChange={(expression) => setEditing({ ...editing, expression })}
                />
              </label>
              <label className="full">
                Условие (CEL, необяз.)
                <BindingExpressionField
                  value={editing.condition}
                  onChange={(condition) => setEditing({ ...editing, condition })}
                  placeholder="Пусто = всегда"
                />
              </label>
              <label className="full">
                Remote object path (cross-object)
                <input
                  value={
                    editing.activators.onVariableChange.find((r) => r.objectPath !== "self")?.objectPath ?? ""
                  }
                  placeholder="root.platform.devices.foo"
                  onChange={(e) => {
                    const remotePath = e.target.value.trim();
                    const remoteVar =
                      editing.activators.onVariableChange.find((r) => r.objectPath !== "self")?.variableName ?? "";
                    const activators: BindingActivators = remotePath
                      ? {
                          onStartup: editing.activators.onStartup,
                          onEvent: editing.activators.onEvent,
                          periodicMs: editing.activators.periodicMs,
                          onVariableChange: remoteVar
                            ? [{ objectPath: remotePath, variableName: remoteVar }]
                            : [{ objectPath: remotePath, variableName: "*" }],
                        }
                      : defaultActivators();
                    setEditing({ ...editing, activators });
                  }}
                />
              </label>
              <label className="full">
                Remote variable
                <input
                  value={
                    editing.activators.onVariableChange.find((r) => r.objectPath !== "self")?.variableName ?? ""
                  }
                  onChange={(e) => {
                    const remoteVar = e.target.value.trim();
                    const remotePath =
                      editing.activators.onVariableChange.find((r) => r.objectPath !== "self")?.objectPath ?? "";
                    if (!remotePath) {
                      return;
                    }
                    setEditing({
                      ...editing,
                      activators: {
                        ...editing.activators,
                        onVariableChange: [{ objectPath: remotePath, variableName: remoteVar || "*" }],
                      },
                    });
                  }}
                />
              </label>
              <label className="checkbox-label inline full">
                <input
                  type="checkbox"
                  checked={editing.enabled}
                  onChange={(e) => setEditing({ ...editing, enabled: e.target.checked })}
                />
                Включено
              </label>
            </section>
            {saveMutation.error && (
              <p className="hint error">{(saveMutation.error as Error).message}</p>
            )}
            <footer>
              <button type="button" className="btn" onClick={() => setEditing(null)}>Отмена</button>
              <button
                type="button"
                className="btn primary"
                disabled={
                  !editing.id.trim()
                  || !editing.target.variableName.trim()
                  || !editing.expression.trim()
                  || saveMutation.isPending
                }
                onClick={() => saveMutation.mutate({
                  ...editing,
                  id: editing.id.trim(),
                  name: editing.name?.trim() || editing.id.trim(),
                  target: { ...editing.target, field: editing.target.field ?? "value" },
                })}
              >
                Сохранить
              </button>
            </footer>
          </div>
        </div>
      )}
    </section>
  );
}
