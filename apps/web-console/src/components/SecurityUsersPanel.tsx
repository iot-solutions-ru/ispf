import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { createSecurityUser, fetchSecurityUsers, setSecurityUserPassword } from "../api/securityUsers";

interface SecurityUsersPanelProps {
  canManage: boolean;
}

export default function SecurityUsersPanel({ canManage }: SecurityUsersPanelProps) {
  const queryClient = useQueryClient();
  const usersQuery = useQuery({
    queryKey: ["security-users"],
    queryFn: fetchSecurityUsers,
  });

  const createMutation = useMutation({
    mutationFn: createSecurityUser,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["security-users"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
  });

  const passwordMutation = useMutation({
    mutationFn: ({ username, password }: { username: string; password: string }) =>
      setSecurityUserPassword(username, password),
  });

  if (!canManage) {
    return <p className="op-muted">Управление пользователями доступно только admin.</p>;
  }

  return (
    <section className="security-users-panel">
      <h3>Пользователи платформы</h3>
      <p className="op-muted">
        Учётные записи синхронизированы с деревом: <code>root.platform.security.users</code>
      </p>
      {usersQuery.error && <div className="op-alert op-alert-error">{String(usersQuery.error)}</div>}
      <table className="op-table">
        <thead>
          <tr>
            <th>Логин</th>
            <th>Имя</th>
            <th>Роли</th>
            <th>Активен</th>
            <th>Пароль</th>
          </tr>
        </thead>
        <tbody>
          {(usersQuery.data ?? []).map((user) => (
            <tr key={user.username}>
              <td>{user.username}</td>
              <td>{user.displayName}</td>
              <td>{user.roles.join(", ")}</td>
              <td>{user.enabled ? "да" : "нет"}</td>
              <td>
                <button
                  type="button"
                  className="btn small"
                  onClick={() => {
                    const password = window.prompt(`Новый пароль для ${user.username}`);
                    if (password) {
                      passwordMutation.mutate({ username: user.username, password });
                    }
                  }}
                >
                  Сменить
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <form
        className="security-user-form"
        onSubmit={(event) => {
          event.preventDefault();
          const form = event.currentTarget;
          const data = new FormData(form);
          createMutation.mutate({
            username: String(data.get("username") ?? ""),
            displayName: String(data.get("displayName") ?? ""),
            password: String(data.get("password") ?? ""),
            roles: [String(data.get("role") ?? "operator")],
          });
          form.reset();
        }}
      >
        <h4>Добавить пользователя</h4>
        <div className="security-user-form-grid">
          <input name="username" placeholder="Логин" required />
          <input name="displayName" placeholder="Отображаемое имя" />
          <input name="password" type="password" placeholder="Пароль" required />
          <select name="role" defaultValue="operator">
            <option value="operator">operator</option>
            <option value="admin">admin</option>
          </select>
          <button type="submit" className="btn primary" disabled={createMutation.isPending}>
            Создать
          </button>
        </div>
      </form>
    </section>
  );
}
