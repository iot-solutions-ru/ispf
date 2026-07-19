import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { login } from "../../auth/login";
import { fetchAuthConfig, startOidcLogin } from "../../auth/oidc";
import type { AuthSession } from "../../auth/session";
import ShellPreferences from "./ShellPreferences";

interface LoginViewProps {
  onLoggedIn: (session: AuthSession) => void;
}

export default function LoginView({ onLoggedIn }: LoginViewProps) {
  const { t } = useTranslation(["shell", "common"]);
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("admin");
  const [totpCode, setTotpCode] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const authConfigQuery = useQuery({
    queryKey: ["auth-config"],
    queryFn: fetchAuthConfig,
  });

  const authConfig = authConfigQuery.data;
  const showLocalLogin = authConfig?.mode !== "oidc" && authConfig?.localLoginEnabled !== false;
  const showOidcLogin = authConfig?.mode === "oidc";
  const showTotpField = authConfig?.mfaEnabled === true;

  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    setPending(true);
    setError(null);
    try {
      const session = await login(username, password, totpCode);
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
        <div className="login-card-head">
          <ShellPreferences />
        </div>
        <h1>{t("shell:login.title")}</h1>
        <p className="login-sub">{t("shell:login.subtitle")}</p>

        {authConfigQuery.isLoading && <p className="login-hint">{t("shell:login.loadingAuthConfig")}</p>}
        {authConfigQuery.error && (
          <div className="op-alert op-alert-error">{String(authConfigQuery.error)}</div>
        )}

        {showLocalLogin && (
          <>
            <label>
              {t("shell:login.username")}
              <input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" />
            </label>
            <label>
              {t("shell:login.password")}
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
              />
            </label>
            {showTotpField && (
              <label>
                {t("shell:login.totp")}
                <input
                  value={totpCode}
                  onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={6}
                  placeholder="000000"
                />
              </label>
            )}
            <button type="submit" className="btn primary" disabled={pending}>
              {pending ? t("shell:login.submitting") : t("shell:login.submit")}
            </button>
            <p className="login-hint">{t("shell:login.defaultHint")}</p>
            {showTotpField && <p className="login-hint">{t("shell:login.totpHint")}</p>}
          </>
        )}

        {showOidcLogin && (
          <>
            <button type="button" className="btn primary" disabled={pending} onClick={() => void loginWithKeycloak()}>
              {pending ? t("shell:login.oidcRedirecting") : t("shell:login.oidcSubmit")}
            </button>
            <p className="login-hint">
              {t("shell:login.oidcRealm")} <code>{authConfig?.oidc?.issuer ?? t("common:empty.dash")}</code>
            </p>
          </>
        )}

        {error && <div className="op-alert op-alert-error">{error}</div>}
      </form>
    </div>
  );
}
