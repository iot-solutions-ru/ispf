import { useQuery } from "@tanstack/react-query";
import { Alert, Button, Form, Input, Typography } from "antd";
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

  const submit = async () => {
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
      <div className="login-card">
        <div className="login-card-head">
          <ShellPreferences />
        </div>
        <Typography.Title level={2} style={{ marginTop: 0 }}>
          {t("shell:login.title")}
        </Typography.Title>
        <Typography.Paragraph type="secondary">{t("shell:login.subtitle")}</Typography.Paragraph>

        {authConfigQuery.isLoading && (
          <Typography.Paragraph type="secondary">{t("shell:login.loadingAuthConfig")}</Typography.Paragraph>
        )}
        {authConfigQuery.error && (
          <Alert type="error" showIcon message={String(authConfigQuery.error)} style={{ marginBottom: 12 }} />
        )}

        {showLocalLogin && (
          <Form layout="vertical" onFinish={() => void submit()}>
            <Form.Item label={t("shell:login.username")}>
              <Input
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                autoComplete="username"
              />
            </Form.Item>
            <Form.Item label={t("shell:login.password")}>
              <Input.Password
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
              />
            </Form.Item>
            {showTotpField && (
              <Form.Item label={t("shell:login.totp")}>
                <Input
                  value={totpCode}
                  onChange={(e) => setTotpCode(e.target.value.replace(/\D/g, "").slice(0, 6))}
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  maxLength={6}
                  placeholder="000000"
                />
              </Form.Item>
            )}
            <Button type="primary" htmlType="submit" block loading={pending}>
              {pending ? t("shell:login.submitting") : t("shell:login.submit")}
            </Button>
            <Typography.Paragraph type="secondary" style={{ marginTop: 12 }}>
              {t("shell:login.defaultHint")}
            </Typography.Paragraph>
            {showTotpField && (
              <Typography.Paragraph type="secondary">{t("shell:login.totpHint")}</Typography.Paragraph>
            )}
          </Form>
        )}

        {showOidcLogin && (
          <>
            <Button type="primary" block loading={pending} onClick={() => void loginWithKeycloak()}>
              {pending ? t("shell:login.oidcRedirecting") : t("shell:login.oidcSubmit")}
            </Button>
            <Typography.Paragraph type="secondary" style={{ marginTop: 12 }}>
              {t("shell:login.oidcRealm")}{" "}
              <Typography.Text code>{authConfig?.oidc?.issuer ?? t("common:empty.dash")}</Typography.Text>
            </Typography.Paragraph>
          </>
        )}

        {error && <Alert type="error" showIcon message={error} style={{ marginTop: 12 }} />}
      </div>
    </div>
  );
}
