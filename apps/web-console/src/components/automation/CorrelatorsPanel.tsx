import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { deleteCorrelator, fetchCorrelators, updateCorrelator } from "../../api";
import type { EventCorrelator } from "../../types/event";
import CreateCorrelatorDialog from "./CreateCorrelatorDialog";

interface CorrelatorsPanelProps {
  readOnly?: boolean;
}

export default function CorrelatorsPanel({ readOnly = false }: CorrelatorsPanelProps) {
  const queryClient = useQueryClient();
  const [showCreate, setShowCreate] = useState(false);
  const correlators = useQuery({ queryKey: ["correlators"], queryFn: fetchCorrelators });

  const toggleMutation = useMutation({
    mutationFn: (item: EventCorrelator) => updateCorrelator(item.id, { enabled: !item.enabled }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["correlators"] }),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => deleteCorrelator(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["correlators"] }),
  });

  return (
    <section className="automation-panel">
      <header className="automation-panel-head">
        <div>
          <h2>Корреляторы событий</h2>
          <p className="hint">События → workflow / автоматизация (окно, порог, cooldown)</p>
        </div>
        {!readOnly && (
          <button type="button" className="btn primary" onClick={() => setShowCreate(true)}>
            + Коррелятор
          </button>
        )}
      </header>
      {correlators.isLoading && <p className="hint">Загрузка…</p>}
      {correlators.error && <p className="hint error">Не удалось загрузить корреляторы</p>}
      <div className="automation-table-wrap">
        <table className="automation-table">
          <thead>
            <tr>
              <th>Имя</th>
              <th>Событие</th>
              <th>Объект</th>
              <th>Окно</th>
              <th>Действие</th>
              <th>Статус</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {(correlators.data ?? []).map((item) => (
              <tr key={item.id}>
                <td>{item.name}</td>
                <td>{item.eventName}</td>
                <td><code>{item.objectPath ?? "*"}</code></td>
                <td>
                  {item.minOccurrences}× / {item.windowSeconds}s
                  {item.cooldownSeconds > 0 ? ` · cd ${item.cooldownSeconds}s` : ""}
                </td>
                <td>
                  <code>{item.actionType}</code>
                  <div className="hint">{item.actionTarget}</div>
                </td>
                <td>
                  <span className={`badge ${item.enabled ? "ok" : ""}`}>
                    {item.enabled ? "Вкл" : "Выкл"}
                  </span>
                </td>
                <td className="automation-actions">
                  {!readOnly && (
                    <>
                      <button
                        type="button"
                        className="btn small"
                        disabled={toggleMutation.isPending}
                        onClick={() => toggleMutation.mutate(item)}
                      >
                        {item.enabled ? "Выкл" : "Вкл"}
                      </button>
                      <button
                        type="button"
                        className="btn small danger"
                        disabled={deleteMutation.isPending}
                        onClick={() => {
                          if (window.confirm(`Удалить коррелятор «${item.name}»?`)) {
                            deleteMutation.mutate(item.id);
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
        <CreateCorrelatorDialog
          onClose={() => setShowCreate(false)}
          onCreated={() => {
            setShowCreate(false);
            queryClient.invalidateQueries({ queryKey: ["correlators"] });
          }}
        />
      )}
    </section>
  );
}
