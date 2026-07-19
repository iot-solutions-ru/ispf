import type { AuthSession } from "../../auth/session";
import { useTranslation } from "react-i18next";
import { useOperatorUi } from "../../hooks/useOperatorUi";
import OperatorAppLauncher from "./OperatorAppLauncher";
import OperatorDashboardApp from "./OperatorDashboardApp";
import OperatorManifestView from "./OperatorManifestView";

interface OperatorViewProps {
  operatorId?: string;
  appId?: string | null;
  onSwitchAdmin?: () => void;
  onSelectApp?: (appId: string) => void;
  session?: AuthSession;
  onLogout?: () => void;
}

export default function OperatorView({
  operatorId = "operator",
  appId = null,
  onSwitchAdmin,
  onSelectApp,
  session,
  onLogout,
}: OperatorViewProps) {
  if (!appId) {
    return (
      <OperatorAppLauncher
        onOpenApp={(nextAppId) => onSelectApp?.(nextAppId)}
        onSwitchAdmin={onSwitchAdmin}
      />
    );
  }

  return (
    <OperatorAppEntry
      appId={appId}
      operatorId={operatorId}
      onSwitchAdmin={onSwitchAdmin}
      session={session}
      onLogout={onLogout}
      onSelectApp={onSelectApp}
    />
  );
}

function OperatorAppEntry({
  appId,
  operatorId,
  onSwitchAdmin,
  session,
  onLogout,
  onSelectApp,
}: {
  appId: string;
  operatorId: string;
  onSwitchAdmin?: () => void;
  session?: AuthSession;
  onLogout?: () => void;
  onSelectApp?: (appId: string) => void;
}) {
  const { t } = useTranslation("operator");
  const uiQuery = useOperatorUi(appId);

  if (uiQuery.isLoading) {
    return <div className="operator-shell op-loading">{t("loadingUi")}</div>;
  }

  if (uiQuery.data) {
    return (
      <OperatorDashboardApp
        appId={appId}
        operatorId={operatorId}
        onSwitchAdmin={onSwitchAdmin}
        session={session}
        onLogout={onLogout}
      />
    );
  }

  // No platform dashboard UI (missing or offline fetch error) — try public/legacy manifest.
  // OperatorManifestView shows OperatorAppMissing when the manifest is also gone.
  return (
    <OperatorManifestView
      appId={appId}
      operatorId={operatorId}
      onSwitchAdmin={onSwitchAdmin}
      session={session}
      onLogout={onLogout}
      onMissingApp={onSelectApp ? () => onSelectApp("") : undefined}
    />
  );
}
