import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchSecurityRoles, updateSecurityRole } from "../api/securityRoles";
import { deleteObject } from "../api";
import { roleNameFromSecurityRolePath } from "../utils/securityRolePath";
import ObjectTreeIcon from "./icons/ObjectTreeIcon";

interface SecurityRoleInspectorProps {
  path: string;
  canManage: boolean;
  onDeleted: () => void;
}

export default function SecurityRoleInspector({
  path,
  canManage,
  onDeleted,
}: SecurityRoleInspectorProps) {
  const roleName = roleNameFromSecurityRolePath(path);
  const queryClient = useQueryClient();
  const [displayName, setDisplayName] = useState("");
  const [description, setDescription] = useState("");

  const rolesQuery = useQuery({
    queryKey: ["security-roles"],
    queryFn: fetchSecurityRoles,
  });

  const role = rolesQuery.data?.find((item) => item.name === roleName);

  useEffect(() => {
    if (!role) {
      return;
    }
    setDisplayName(role.displayName);
    setDescription(role.description);
  }, [role]);

  const saveMutation = useMutation({
    mutationFn: () =>
      updateSecurityRole(roleName, {
        displayName: displayName.trim(),
        description: description.trim(),
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["security-roles"] });
      queryClient.invalidateQueries({ queryKey: ["object", path] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteObject(path),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["security-roles"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      onDeleted();
    },
  });

  if (rolesQuery.isLoading) {
    return <div className="inspector-empty">Загрузка роли…</div>;
  }

  if (rolesQuery.error || !role) {
    return <div className="inspector-empty error">Роль не найдена</div>;
  }

  const dirty = displayName !== role.displayName || description !== role.description;

  return (
    <div className="inspector security-user-inspector">
      <header className="inspector-header security-user-header">
        <div className="inspector-title-row">
          <ObjectTreeIcon path={path} type="ROLE" size={28} />
          <div className="security-user-heading">
            <div className="security-user-title-line">
              <h2>{displayName.trim() || role.name}</h2>
              <span className={`security-user-pill security-user-pill--role-${role.name}`}>
                {role.name}
              </span>
              {role.builtIn && (
                <span className="security-user-pill security-user-pill--status is-active">
                  Встроенная
                </span>
              )}
            </div>
            <p className="security-user-meta">
              <code className="path-code">{path}</code>
            </p>
          </div>
        </div>
        {canManage && !role.builtIn && (
          <div className="inspector-actions">
            <button
              type="button"
              className="btn danger"
              disabled={deleteMutation.isPending}
              onClick={() => {
                if (confirm(`Удалить роль «${role.name}»?`)) {
                  deleteMutation.mutate();
                }
              }}
            >
              Удалить
            </button>
          </div>
        )}
      </header>

      {!canManage && (
        <p className="hint security-user-readonly-hint">Редактирование доступно только admin.</p>
      )}

      <div className="security-user-cards">
        <section className="security-user-card">
          <h3 className="security-user-card-title">Свойства роли</h3>
          <form
            className="security-user-form"
            onSubmit={(event) => {
              event.preventDefault();
              if (canManage && dirty) {
                saveMutation.mutate();
              }
            }}
          >
            <div className="security-user-form-grid">
              <label>
                <span className="field-label">Имя</span>
                <input value={role.name} readOnly className="readonly" />
              </label>
              <label>
                <span className="field-label">Отображаемое имя</span>
                <input
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  disabled={!canManage}
                  placeholder="Имя в дереве объектов"
                />
              </label>
              <label className="full">
                <span className="field-label">Описание</span>
                <textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  disabled={!canManage}
                  rows={3}
                  placeholder="Назначение роли"
                />
              </label>
            </div>

            {canManage && (
              <footer className="security-user-card-footer">
                <div className="security-user-card-actions">
                  <button
                    type="submit"
                    className="btn primary"
                    disabled={!dirty || saveMutation.isPending}
                  >
                    {saveMutation.isPending ? "Сохранение…" : "Сохранить"}
                  </button>
                </div>
                {saveMutation.isSuccess && !dirty && (
                  <span className="hint success">Изменения сохранены</span>
                )}
                {saveMutation.error && (
                  <span className="hint error">{String(saveMutation.error)}</span>
                )}
              </footer>
            )}
          </form>
        </section>
      </div>

      {deleteMutation.error && (
        <p className="hint error security-user-delete-error">{String(deleteMutation.error)}</p>
      )}
    </div>
  );
}
