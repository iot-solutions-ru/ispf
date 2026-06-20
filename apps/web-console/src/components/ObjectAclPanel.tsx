import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { fetchObjectAcl, saveObjectAcl, type ObjectAclEntry } from "../api/objectAcl";

interface ObjectAclPanelProps {
  objectPath: string;
  canManage: boolean;
}

const EMPTY_ENTRY: ObjectAclEntry = {
  principalType: "ROLE",
  principalId: "operator",
  permission: "READ",
};

export default function ObjectAclPanel({ objectPath, canManage }: ObjectAclPanelProps) {
  const queryClient = useQueryClient();
  const [entries, setEntries] = useState<ObjectAclEntry[]>([]);

  const aclQuery = useQuery({
    queryKey: ["object-acl", objectPath],
    queryFn: () => fetchObjectAcl(objectPath),
  });

  useEffect(() => {
    if (aclQuery.data) {
      setEntries(aclQuery.data);
    }
  }, [aclQuery.data]);

  const saveMutation = useMutation({
    mutationFn: () => saveObjectAcl(objectPath, entries),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["object-acl", objectPath] });
    },
  });

  const updateEntry = (index: number, patch: Partial<ObjectAclEntry>) => {
    setEntries((current) =>
      current.map((entry, entryIndex) => (entryIndex === index ? { ...entry, ...patch } : entry))
    );
  };

  const removeEntry = (index: number) => {
    setEntries((current) => current.filter((_, entryIndex) => entryIndex !== index));
  };

  return (
    <section className="security-users-panel">
      <header className="security-users-header">
        <div>
          <h3>Доступ к объекту</h3>
          <p className="op-muted">
            Per-object ACL для <code>{objectPath}</code>. Если правил нет — действует глобальный RBAC.
            Admin всегда имеет полный доступ.
          </p>
        </div>
        {canManage && (
          <button type="button" className="btn" onClick={() => setEntries((current) => [...current, { ...EMPTY_ENTRY }])}>
            + Правило
          </button>
        )}
      </header>

      {aclQuery.isLoading && <p className="op-muted">Загрузка…</p>}
      {aclQuery.error && <div className="op-alert op-alert-error">{String(aclQuery.error)}</div>}

      {entries.length === 0 && !aclQuery.isLoading && (
        <p className="op-muted">Ограничений нет — объект виден всем ролям с глобальным доступом.</p>
      )}

      {entries.length > 0 && (
        <table className="op-table security-users-table security-users-table-compact">
          <thead>
            <tr>
              <th>Тип</th>
              <th>Principal</th>
              <th>Разрешение</th>
              {canManage && <th />}
            </tr>
          </thead>
          <tbody>
            {entries.map((entry, index) => (
              <tr key={`${entry.principalType}-${entry.principalId}-${entry.permission}-${index}`}>
                <td>
                  <select
                    value={entry.principalType}
                    disabled={!canManage}
                    onChange={(event) =>
                      updateEntry(index, { principalType: event.target.value as ObjectAclEntry["principalType"] })
                    }
                  >
                    <option value="ROLE">ROLE</option>
                    <option value="USER">USER</option>
                  </select>
                </td>
                <td>
                  <input
                    value={entry.principalId}
                    disabled={!canManage}
                    onChange={(event) => updateEntry(index, { principalId: event.target.value })}
                  />
                </td>
                <td>
                  <select
                    value={entry.permission}
                    disabled={!canManage}
                    onChange={(event) =>
                      updateEntry(index, { permission: event.target.value as ObjectAclEntry["permission"] })
                    }
                  >
                    <option value="READ">READ</option>
                    <option value="WRITE">WRITE</option>
                    <option value="INVOKE">INVOKE</option>
                  </select>
                </td>
                {canManage && (
                  <td>
                    <button type="button" className="btn danger" onClick={() => removeEntry(index)}>
                      Удалить
                    </button>
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {canManage && (
        <div className="operator-apps-actions">
          <button
            type="button"
            className="btn primary"
            disabled={saveMutation.isPending}
            onClick={() => saveMutation.mutate()}
          >
            {saveMutation.isPending ? "Сохранение…" : "Сохранить ACL"}
          </button>
          {saveMutation.error && <div className="op-alert op-alert-error">{String(saveMutation.error)}</div>}
        </div>
      )}
    </section>
  );
}
