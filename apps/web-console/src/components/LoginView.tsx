import { useState } from "react";
import { login } from "../auth/login";
import type { AuthSession } from "../auth/session";

interface LoginViewProps {
  onLoggedIn: (session: AuthSession) => void;
}

export default function LoginView({ onLoggedIn }: LoginViewProps) {
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("admin");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

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

  return (
    <div className="login-shell">
      <form className="login-card" onSubmit={(event) => void submit(event)}>
        <h1>ISPF</h1>
        <p className="login-sub">Вход в платформу</p>
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
        {error && <div className="op-alert op-alert-error">{error}</div>}
        <button type="submit" className="btn primary" disabled={pending}>
          {pending ? "Вход…" : "Войти"}
        </button>
        <p className="login-hint">По умолчанию: admin/admin или operator/operator</p>
      </form>
    </div>
  );
}
