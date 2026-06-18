import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { deleteAlertRule, fetchAlertRules, updateAlertRule } from "../../api";
import type { AlertRule } from "../../types/event";
import CreateAlertRuleDialog from "./CreateAlertRuleDialog";

interface AlertRulesPanelProps {
  readOnly?: boolean;
}

export default function AlertRulesPanel({ readOnly = false }: AlertRulesPanelProps) {
  const queryClient = useQueryClient();
  const [showCreate, setShowCreate] = useState(false);
  const rules = useQuery({ queryKey: ["alert-rules"], queryFn: fetchAlertRules });

  const toggleMutation = useMutation({
    mutationFn: (rule: AlertRule) => updateAlertRule(rule.id, { enabled: !rule.enabled }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["alert-rules"] }),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteAlertRule(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["alert-rules"] }),
  });

  return (
    <section className="automation-panel">
      <header className="automation-panel-head">
        <div>
          <h2>Правила алертов</h2>
          <p className="hint">CEL-условия на изменение переменных → публикация событий</p>
        </div>
        {!readOnly && (
          <button type="button" className="btn primary" onClick={() => setShowCreate(true)}>
            + Правило
          </button>
        )}
      </header>
      {rules.isLoading && <p className="hint">Загрузка…</p>}
      {rules.error && <p className="hint error">Не удалось загрузить правила</p>}
      <div className="automation-table-wrap">
        <table className="automation-table">
          <thead>
            <tr>
              <th>Имя</th>
              <th>Объект</th>
              <th>Переменная</th>
              <th>Событие</th>
              <th>CEL</th>
              <th>Статус</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {(rules.data ?? []).map((rule) => (
              <tr key={rule.id}>
                <td>{rule.name}</td>
                <td><code>{rule.objectPath}</code></td>
                <td>{rule.watchVariable}</td>
                <td>{rule.eventName}</td>
                <td><code className="expr-cell">{rule.conditionExpr}</code></td>
                <td>
                  <span className={`badge ${rule.enabled ? "ok" : ""}`}>
                    {rule.enabled ? "Вкл" : "Выкл"}
                  </span>
                </td>
                <td className="automation-actions">
                  {!readOnly && (
                    <>
                      <button
                        type="button"
                        className="btn small"
                        disabled={toggleMutation.isPending}
                        onClick={() => toggleMutation.mutate(rule)}
                      >
                        {rule.enabled ? "Выкл" : "Вкл"}
                      </button>
                      <button
                        type="button"
                        className="btn small danger"
                        disabled={deleteMutation.isPending}
                        onClick={() => {
                          if (window.confirm(`Удалить правило «${rule.name}»?`)) {
                            deleteMutation.mutate(rule.id);
                          }
                        }}
                      >
                        Удалить
                      </button>
                    </>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {showCreate && (
        <CreateAlertRuleDialog
          onClose={() => setShowCreate(false)}
          onCreated={() => {
            setShowCreate(false);
            queryClient.invalidateQueries({ queryKey: ["alert-rules"] });
          }}
        />
      )}
    </section>
  );
}
