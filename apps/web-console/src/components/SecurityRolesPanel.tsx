import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { fetchSecurityRoles } from "../api/securityRoles";
import CreateSecurityRoleDialog from "./CreateSecurityRoleDialog";

interface SecurityRolesPanelProps {
  canManage: boolean;
  onSelectRole: (path: string) => void;
}

export default function SecurityRolesPanel({ canManage, onSelectRole }: SecurityRolesPanelProps) {
  const [showCreate, setShowCreate] = useState(false);
  const queryClient = useQueryClient();

  const rolesQuery = useQuery({
    queryKey: ["security-roles"],
    queryFn: fetchSecurityRoles,
  });

  if (!canManage) {
    return <p className="op-muted">Управление ролями доступно только admin.</p>;
  }

  return (
    <section className="security-users-panel">
      <header className="security-users-header">
        <div>
          <h3>Роли платформы</h3>
          <p className="op-muted">
            RBAC-роли в <code>root.platform.security.roles</code>. Выберите роль в дереве или в
            списке — свойства редактируются в инспекторе объекта.
          </p>
        </div>
        <button type="button" className="btn primary" onClick={() => setShowCreate(true)}>
          + Создать роль
        </button>
      </header>

      {rolesQuery.error && <div className="op-alert op-alert-error">{String(rolesQuery.error)}</div>}

      <table className="op-table security-users-table security-users-table-compact">
        <thead>
          <tr>
            <th>Имя</th>
            <th>Отображаемое имя</th>
            <th>Описание</th>
            <th>Тип</th>
          </tr>
        </thead>
        <tbody>
          {(rolesQuery.data ?? []).map((role) => (
            <tr key={role.name}>
              <td>
                <button
                  type="button"
                  className="link-btn"
                  onClick={() => onSelectRole(role.objectPath)}
                >
                  <code>{role.name}</code>
                </button>
              </td>
              <td>{role.displayName}</td>
              <td>{role.description || "—"}</td>
              <td>{role.builtIn ? "встроенная" : "пользовательская"}</td>
            </tr>
          ))}
        </tbody>
      </table>

      {showCreate && (
        <CreateSecurityRoleDialog
          onClose={() => setShowCreate(false)}
          onCreated={(objectPath) => {
            queryClient.invalidateQueries({ queryKey: ["security-roles"] });
            queryClient.invalidateQueries({ queryKey: ["objects"] });
            setShowCreate(false);
            onSelectRole(objectPath);
          }}
        />
      )}
    </section>
  );
}
