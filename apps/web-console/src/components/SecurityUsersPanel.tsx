import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { fetchSecurityUsers } from "../api/securityUsers";
import CreateSecurityUserDialog from "./CreateSecurityUserDialog";

interface SecurityUsersPanelProps {
  canManage: boolean;
  onSelectUser: (path: string) => void;
}

export default function SecurityUsersPanel({ canManage, onSelectUser }: SecurityUsersPanelProps) {
  const [showCreate, setShowCreate] = useState(false);

  const usersQuery = useQuery({
    queryKey: ["security-users"],
    queryFn: fetchSecurityUsers,
  });

  if (!canManage) {
    return <p className="op-muted">Управление пользователями доступно только admin.</p>;
  }

  return (
    <section className="security-users-panel">
      <header className="security-users-header">
        <div>
          <h3>Пользователи платформы</h3>
          <p className="op-muted">
            Учётные записи в <code>root.platform.security.users</code>. Выберите пользователя в
            дереве или в списке — свойства редактируются в инспекторе объекта.
          </p>
        </div>
        <button type="button" className="btn primary" onClick={() => setShowCreate(true)}>
          + Создать пользователя
        </button>
      </header>

      {usersQuery.error && <div className="op-alert op-alert-error">{String(usersQuery.error)}</div>}

      <table className="op-table security-users-table security-users-table-compact">
        <thead>
          <tr>
            <th>Логин</th>
            <th>Имя</th>
            <th>Роли</th>
            <th>Активен</th>
          </tr>
        </thead>
        <tbody>
          {(usersQuery.data ?? []).map((user) => (
            <tr key={user.username}>
              <td>
                <button
                  type="button"
                  className="link-btn"
                  onClick={() => onSelectUser(user.objectPath)}
                >
                  <code>{user.username}</code>
                </button>
              </td>
              <td>{user.displayName}</td>
              <td>{user.roles.join(", ")}</td>
              <td>{user.enabled ? "да" : "нет"}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {showCreate && (
        <CreateSecurityUserDialog
          onClose={() => setShowCreate(false)}
          onCreated={(objectPath) => {
            setShowCreate(false);
            onSelectUser(objectPath);
          }}
        />
      )}
    </section>
  );
}
