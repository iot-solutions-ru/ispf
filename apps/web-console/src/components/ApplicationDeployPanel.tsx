import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { fetchDeployHistory, rollbackDeploy } from "../api/applications";

interface ApplicationDeployPanelProps {
  appId: string;
  canManage: boolean;
}

function formatDeployedAt(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return date.toLocaleString();
}

export default function ApplicationDeployPanel({ appId, canManage }: ApplicationDeployPanelProps) {
  const queryClient = useQueryClient();
  const [rollbackVersion, setRollbackVersion] = useState<string | null>(null);

  const historyQuery = useQuery({
    queryKey: ["deploy-history", appId],
    queryFn: () => fetchDeployHistory(appId),
  });

  const rollbackMutation = useMutation({
    mutationFn: (version: string) => rollbackDeploy(appId, version),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["deploy-history", appId] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      setRollbackVersion(null);
    },
  });

  const history = historyQuery.data ?? [];
  const activeVersion = history.find((entry) => entry.active)?.version;

  return (
    <section className="security-users-panel application-deploy-panel">
      <header className="security-users-header">
        <div>
          <h3>История deploy bundle</h3>
          <p className="op-muted">
            Версии manifest для <code>{appId}</code>. Rollback повторно применяет сохранённый bundle
            (миграции пропускаются, функции и объекты обновляются).
          </p>
        </div>
      </header>

      {historyQuery.isLoading && <p className="op-muted">Загрузка истории…</p>}
      {historyQuery.error && (
        <div className="op-alert op-alert-error">{String(historyQuery.error)}</div>
      )}

      {!historyQuery.isLoading && !historyQuery.error && history.length === 0 && (
        <p className="op-muted">
          Deploy ещё не выполнялся. Отправьте bundle через{" "}
          <code>POST /api/v1/applications/{appId}/deploy</code>.
        </p>
      )}

      {history.length > 0 && (
        <table className="op-table security-users-table security-users-table-compact">
          <thead>
            <tr>
              <th>Версия</th>
              <th>Развёрнуто</th>
              <th>Активна</th>
              {canManage && <th>Действие</th>}
            </tr>
          </thead>
          <tbody>
            {history.map((entry) => {
              const isActive = entry.active;
              const isPending =
                rollbackMutation.isPending && rollbackVersion === entry.version;
              return (
                <tr key={`${entry.version}-${entry.deployedAt}`}>
                  <td>
                    <code>{entry.version}</code>
                  </td>
                  <td>{formatDeployedAt(entry.deployedAt)}</td>
                  <td>{isActive ? "да" : "—"}</td>
                  {canManage && (
                    <td>
                      {!isActive && (
                        <button
                          type="button"
                          className="btn"
                          disabled={rollbackMutation.isPending}
                          onClick={() => {
                            setRollbackVersion(entry.version);
                            rollbackMutation.mutate(entry.version);
                          }}
                        >
                          {isPending ? "Откат…" : "Откатить"}
                        </button>
                      )}
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      {rollbackMutation.error && (
        <div className="op-alert op-alert-error">{String(rollbackMutation.error)}</div>
      )}
      {rollbackMutation.isSuccess && (
        <p className="op-muted">
          Откат выполнен
          {rollbackMutation.data?.rolledBackTo
            ? ` → версия ${rollbackMutation.data.rolledBackTo}`
            : activeVersion
              ? ` → активна ${activeVersion}`
              : ""}
          .
        </p>
      )}
    </section>
  );
}
