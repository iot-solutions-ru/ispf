import { useEffect, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  fetchSecurityUsers,
  setSecurityUserPassword,
  updateSecurityUser,
} from "../api/securityUsers";
import { fetchSecurityRoles } from "../api/securityRoles";
import { fetchOperatorApps, type OperatorAppEntry } from "../api/operatorApps";
import { deleteObject } from "../api";
import { usernameFromSecurityUserPath } from "../utils/securityUserPath";
import SecurityUserAutoStartFields from "./SecurityUserAutoStartFields";
import ObjectTreeIcon from "./icons/ObjectTreeIcon";

async function loadOperatorApps(): Promise<OperatorAppEntry[]> {
  try {
    const apps = await fetchOperatorApps();
    return apps.length ? apps : [{ appId: "platform", title: "Platform HMI" }];
  } catch {
    return [{ appId: "platform", title: "Platform HMI" }];
  }
}

function serverSupportsAutoStart(users: Awaited<ReturnType<typeof fetchSecurityUsers>> | undefined): boolean {
  return Boolean(users?.some((user) => Object.prototype.hasOwnProperty.call(user, "autoStartEnabled")));
}

interface SecurityUserInspectorProps {
  path: string;
  canManage: boolean;
  onDeleted: () => void;
}

export default function SecurityUserInspector({
  path,
  canManage,
  onDeleted,
}: SecurityUserInspectorProps) {
  const username = usernameFromSecurityUserPath(path);
  const queryClient = useQueryClient();
  const [displayName, setDisplayName] = useState("");
  const [role, setRole] = useState("operator");
  const [enabled, setEnabled] = useState(true);
  const [showPasswordDialog, setShowPasswordDialog] = useState(false);
  const [newPassword, setNewPassword] = useState("");

  const usersQuery = useQuery({
    queryKey: ["security-users"],
    queryFn: fetchSecurityUsers,
  });

  const rolesQuery = useQuery({
    queryKey: ["security-roles"],
    queryFn: fetchSecurityRoles,
  });

  const user = usersQuery.data?.find((item) => item.username === username);

  const appsQuery = useQuery({
    queryKey: ["operator-apps"],
    queryFn: loadOperatorApps,
  });

  useEffect(() => {
    if (!user) {
      return;
    }
    setDisplayName(user.displayName);
    setRole(user.roles[0] ?? "operator");
    setEnabled(user.enabled);
  }, [user]);

  const saveMutation = useMutation({
    mutationFn: () =>
      updateSecurityUser(username, {
        displayName: displayName.trim(),
        roles: [role],
        enabled,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["security-users"] });
      queryClient.invalidateQueries({ queryKey: ["object", path] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["variables", path] });
    },
  });

  const passwordMutation = useMutation({
    mutationFn: () => setSecurityUserPassword(username, newPassword),
    onSuccess: () => {
      setShowPasswordDialog(false);
      setNewPassword("");
    },
  });

  const deleteMutation = useMutation({
    mutationFn: () => deleteObject(path),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["security-users"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      onDeleted();
    },
  });

  if (usersQuery.isLoading) {
    return <div className="inspector-empty">Загрузка пользователя…</div>;
  }

  if (usersQuery.error || !user) {
    return <div className="inspector-empty error">Пользователь не найден</div>;
  }

  const serverReady = serverSupportsAutoStart(usersQuery.data);
  const dirty =
    displayName !== user.displayName
    || role !== (user.roles[0] ?? "operator")
    || enabled !== user.enabled;

  return (
    <div className="inspector security-user-inspector">
      <header className="inspector-header security-user-header">
        <div className="inspector-title-row">
          <ObjectTreeIcon path={path} type="USER" size={28} />
          <div className="security-user-heading">
            <div className="security-user-title-line">
              <h2>{displayName.trim() || user.username}</h2>
              <span className={`security-user-pill security-user-pill--role-${role}`}>
                {role}
              </span>
              <span
                className={`security-user-pill security-user-pill--status ${
                  enabled ? "is-active" : "is-inactive"
                }`}
              >
                {enabled ? "Активен" : "Отключён"}
              </span>
            </div>
            <p className="security-user-meta">
              <code>@{user.username}</code>
              <span className="security-user-meta-sep">·</span>
              <code className="path-code">{path}</code>
            </p>
          </div>
        </div>
        {canManage && (
          <div className="inspector-actions">
            <button
              type="button"
              className="btn danger"
              disabled={deleteMutation.isPending}
              onClick={() => {
                if (confirm(`Удалить пользователя «${user.username}»?`)) {
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
          <h3 className="security-user-card-title">Учётная запись</h3>
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
                <span className="field-label">Логин</span>
                <input value={user.username} readOnly className="readonly" />
              </label>
              <label>
                <span className="field-label">Отображаемое имя</span>
                <input
                  value={displayName}
                  onChange={(e) => setDisplayName(e.target.value)}
                  disabled={!canManage}
                  placeholder="Имя в интерфейсе"
                />
              </label>
              <label>
                <span className="field-label">Роль</span>
                <select
                  value={role}
                  onChange={(e) => setRole(e.target.value)}
                  disabled={!canManage || (rolesQuery.data ?? []).length === 0}
                >
                  {(rolesQuery.data ?? []).map((item) => (
                    <option key={item.name} value={item.name}>
                      {item.name}{item.description ? ` — ${item.description}` : ""}
                    </option>
                  ))}
                </select>
              </label>
              <div className="security-user-switch-field">
                <div>
                  <span className="field-label">Статус</span>
                  <p className="security-user-switch-hint">
                    {enabled ? "Может входить в систему" : "Вход заблокирован"}
                  </p>
                </div>
                <label className="switch">
                  <input
                    type="checkbox"
                    checked={enabled}
                    disabled={!canManage}
                    onChange={(e) => setEnabled(e.target.checked)}
                  />
                  <span className="switch-slider" aria-hidden />
                </label>
              </div>
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
                  <button
                    type="button"
                    className="btn"
                    onClick={() => setShowPasswordDialog(true)}
                  >
                    Сменить пароль
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

        <section className="security-user-card">
          <h3 className="security-user-card-title">Автозапуск</h3>
          <p className="security-user-card-desc">
            Operator-приложение при входе:{" "}
            <code>?mode=operator&amp;app=…</code>
          </p>
          <SecurityUserAutoStartFields
            user={user}
            apps={appsQuery.data ?? []}
            serverReady={serverReady}
            disabled={!canManage}
          />
        </section>
      </div>

      {showPasswordDialog && (
        <div className="modal-backdrop" onClick={() => setShowPasswordDialog(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <header>
              <h3>Сменить пароль</h3>
              <button type="button" className="icon-btn" onClick={() => setShowPasswordDialog(false)}>
                ✕
              </button>
            </header>
            <p className="hint">Пользователь <code>{user.username}</code></p>
            <form
              className="form-grid"
              onSubmit={(event) => {
                event.preventDefault();
                passwordMutation.mutate();
              }}
            >
              <label className="full">
                Новый пароль
                <input
                  type="password"
                  value={newPassword}
                  onChange={(e) => setNewPassword(e.target.value)}
                  required
                  minLength={4}
                  autoFocus
                />
              </label>
              {passwordMutation.error && (
                <p className="hint error full">{String(passwordMutation.error)}</p>
              )}
              <footer className="full form-actions">
                <button type="button" className="btn" onClick={() => setShowPasswordDialog(false)}>
                  Отмена
                </button>
                <button type="submit" className="btn primary" disabled={passwordMutation.isPending}>
                  Сохранить
                </button>
              </footer>
            </form>
          </div>
        </div>
      )}

      {deleteMutation.error && (
        <p className="hint error security-user-delete-error">{String(deleteMutation.error)}</p>
      )}
    </div>
  );
}
