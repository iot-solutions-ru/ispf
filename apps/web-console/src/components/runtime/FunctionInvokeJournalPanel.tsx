import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchFunctionInvocations } from "../../api";
import type { FunctionInvokeAuditEntry } from "../../types/runtime";

interface FunctionInvokeJournalPanelProps {
  objectPath?: string;
  functionName?: string;
  limit?: number;
  showFilters?: boolean;
}

export default function FunctionInvokeJournalPanel({
  objectPath: initialObjectPath,
  functionName: initialFunctionName,
  limit = 50,
  showFilters = false,
}: FunctionInvokeJournalPanelProps) {
  const [objectPath, setObjectPath] = useState(initialObjectPath ?? "");
  const [functionName, setFunctionName] = useState(initialFunctionName ?? "");
  const [successFilter, setSuccessFilter] = useState<"" | "true" | "false">("");

  const query = useQuery({
    queryKey: ["function-invocations", objectPath, functionName, successFilter, limit],
    queryFn: () =>
      fetchFunctionInvocations({
        objectPath: objectPath.trim() || undefined,
        functionName: functionName.trim() || undefined,
        success: successFilter === "" ? undefined : successFilter === "true",
        limit,
      }),
    refetchInterval: 10_000,
  });

  const items = query.data ?? [];

  return (
    <section className="event-journal-panel function-invoke-journal">
      <header className="event-journal-head">
        <div>
          <h3>Журнал вызовов функций</h3>
          <p className="hint">Все invoke через API, workflow, scheduler и UI</p>
        </div>
        <span className="badge">{items.length}</span>
      </header>

      {showFilters && (
        <div className="runtime-journal-filters form-grid">
          <label>
            objectPath
            <input
              value={objectPath}
              onChange={(e) => setObjectPath(e.target.value)}
              placeholder="root.platform.devices…"
            />
          </label>
          <label>
            functionName
            <input
              value={functionName}
              onChange={(e) => setFunctionName(e.target.value)}
              placeholder="acknowledgeAlarm"
            />
          </label>
          <label>
            success
            <select
              value={successFilter}
              onChange={(e) => setSuccessFilter(e.target.value as "" | "true" | "false")}
            >
              <option value="">все</option>
              <option value="true">успех</option>
              <option value="false">ошибка</option>
            </select>
          </label>
        </div>
      )}

      {query.isLoading && <p className="hint">Загрузка…</p>}
      {query.error && <p className="hint error">Не удалось загрузить журнал</p>}
      {items.length === 0 && !query.isLoading && <p className="hint">Вызовов пока нет</p>}

      <ul className="event-journal-list">
        {items.map((entry) => (
          <InvokeRow key={entry.id} entry={entry} />
        ))}
      </ul>
    </section>
  );
}

function InvokeRow({ entry }: { entry: FunctionInvokeAuditEntry }) {
  return (
    <li className={`event-journal-item ${entry.success ? "level-info" : "level-error"}`}>
      <div className="event-journal-row-top">
        <strong>{entry.functionName}</strong>
        <span className="event-level-pill">{entry.success ? "OK" : "FAIL"}</span>
      </div>
      <p className="hint">{entry.objectPath}</p>
      {entry.appId && <p className="hint">app: {entry.appId}</p>}
      {entry.errorMessage && <p className="event-journal-detail">{entry.errorMessage}</p>}
      <time className="hint event-journal-time">
        {new Date(entry.invokedAt).toLocaleString()}
      </time>
    </li>
  );
}
