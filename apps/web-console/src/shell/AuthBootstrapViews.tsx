import { useTranslation } from "react-i18next";
import LoginView from "../components/LoginView";
import ShellPreferences from "../components/ShellPreferences";
import type { AuthSession } from "../auth/session";

export function AuthLoadingCard() {
  const { t } = useTranslation("shell");
  return (
    <div className="login-shell">
      <div className="login-card">
        <div className="login-card-head">
          <ShellPreferences />
        </div>
        <p className="login-sub">{t("login.oidcCompleting")}</p>
      </div>
    </div>
  );
}

export function AuthLoginGate({ onLoggedIn }: { onLoggedIn: (session: AuthSession) => void }) {
  return <LoginView onLoggedIn={onLoggedIn} />;
}
