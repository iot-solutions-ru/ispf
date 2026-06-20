import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { login } from "../auth/login";
import { fetchAuthConfig, startOidcLogin } from "../auth/oidc";
import type { AuthSession } from "../auth/session";

interface LoginViewProps {
  onLoggedIn: (session: AuthSession) => void;
}

export default function LoginView({ onLoggedIn }: LoginViewProps) {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("admin");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const authConfigQuery = useQuery({
    queryKey: ["auth-config"],
    queryFn: fetchAuthConfig,
  });

  const authConfig = authConfigQuery.data;
  const showLocalLogin = authConfig?.mode !== "oidc" && authConfig?.localLoginEnabled !== false;
  const showOidcLogin = authConfig?.mode === "oidc";

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      const session = await login(username, password);
      onLoggedIn(session);
    } catch (err) {
      setError(String(err));
    } finally {
      setPending(false);
    }
  };

  const loginWithKeycloak = async () => {
    if (!authConfig) {
      return;
    }
    setPending(true);
    setError(null);
    try {
      await startOidcLogin(authConfig);
    } catch (err) {
      setError(String(err));
      setPending(false);
    }
  };

  return (
    <div className="login-shell">
      <form className="login-card" onSubmit={(event) => void submit(event)}>
        <h1>ISPF</h1>
        <p className="login-sub">Вход в платформу</p>

        {authConfigQuery.isLoading && <p className="login-hint">Загрузка конфигурации auth…</p>}
        {authConfigQuery.error && (
          <div className="op-alert op-alert-error">{String(authConfigQuery.error)}</div>
        )}

        {showLocalLogin && (
          <>
            <label>
              Логин
              <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" />
            </label>
            <label>
              Пароль
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
              />
            </label>
            <button type="submit" className="btn primary" disabled={pending}>
              {pending ? "Вход…" : "Войти"}
            </button>
            <p className="login-hint">По умолчанию: admin/admin или operator/operator</p>
          </>
        )}

        {showOidcLogin && (
          <>
            <button type="button" className="btn primary" disabled={pending} onClick={() => void loginWithKeycloak()}>
              {pending ? "Переход…" : "Войти через Keycloak"}
            </button>
            <p className="login-hint">
              OIDC realm <code>{authConfig?.oidc?.issuer ?? "—"}</code>
            </p>
          </>
        )}

        {error && <div className="op-alert op-alert-error">{error}</div>}
      </form>
    </div>
  );
}
