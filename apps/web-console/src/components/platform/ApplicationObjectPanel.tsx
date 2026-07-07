import { useTranslation } from "react-i18next";
import ApplicationDeployPanel from "../ApplicationDeployPanel";
import { resolveApplicationAppId } from "../../utils/applicationPath";

interface ApplicationObjectPanelProps {
  path: string;
  displayName?: string;
  description?: string;
  canManage: boolean;
}

export default function ApplicationObjectPanel({
  path,
  displayName,
  description,
  canManage,
}: ApplicationObjectPanelProps) {
  const { t } = useTranslation(["platform", "inspector"]);
  const appId = resolveApplicationAppId(path, description);

  return (
    <section className="security-users-panel application-object-panel">
      <header className="security-users-header">
        <div>
          <h3>{displayName ?? path.split(".").pop()}</h3>
          <p className="op-muted">{t("platform:application.explorerSubtitle")}</p>
          <p className="hint mono small">{path}</p>
        </div>
      </header>
      {appId ? (
        <ApplicationDeployPanel appId={appId} displayName={displayName} canManage={canManage} />
      ) : (
        <p className="hint">{t("inspector:deploy.appIdMissing")}</p>
      )}
    </section>
  );
}
