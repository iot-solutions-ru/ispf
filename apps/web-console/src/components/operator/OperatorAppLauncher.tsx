import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  fetchOperatorApps,
  fetchOperatorStarters,
  installOperatorStarters,
  type OperatorAppEntry,
} from "../../api/operatorApps";
import ShellPreferences from "../ShellPreferences";

interface OperatorAppLauncherProps {
  onOpenApp: (appId: string) => void;
  onSwitchAdmin?: () => void;
}

async function loadAppsIndex() {
  try {
    return await fetchOperatorApps();
  } catch {
    return [{ appId: "platform", title: "Platform HMI" } satisfies OperatorAppEntry];
  }
}

export default function OperatorAppLauncher({ onOpenApp, onSwitchAdmin }: OperatorAppLauncherProps) {
  const { t } = useTranslation(["operator", "common"]);
  const queryClient = useQueryClient();
  const appsQuery = useQuery({
    queryKey: ["operator-apps"],
    queryFn: loadAppsIndex,
  });
  const startersQuery = useQuery({
    queryKey: ["operator-starters"],
    queryFn: fetchOperatorStarters,
  });
  const installMutation = useMutation({
    mutationFn: installOperatorStarters,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["operator-apps"] });
    },
  });

  const appIds = new Set((appsQuery.data ?? []).map((app) => app.appId));
  const missingStarters = (startersQuery.data ?? []).filter((starter) => !appIds.has(starter.appId));

  return (
    <div className="operator-shell" data-testid="operator-shell">
      <header className="operator-topbar">
        <div>
          <strong>{t("operator:launcher.title")}</strong>
          <span className="brand-sub">{t("operator:launcher.subtitle")}</span>
        </div>
        <div className="topbar-actions">
          <ShellPreferences />
          {onSwitchAdmin && (
            <button type="button" className="btn" onClick={onSwitchAdmin}>
              {t("operator:launcher.switchAdmin")}
            </button>
          )}
        </div>
      </header>
      <main className="op-launcher">
        {appsQuery.isLoading && <p className="op-muted">{t("common:action.loading")}</p>}
        {appsQuery.error && <p className="op-alert op-alert-error">{String(appsQuery.error)}</p>}
        <div className="op-launcher-grid">
          {(appsQuery.data ?? []).map((app: OperatorAppEntry) => (
            <button
              key={app.appId}
              type="button"
              className="op-launcher-card"
              onClick={() => onOpenApp(app.appId)}
            >
              <strong>{app.title}</strong>
              <span className="op-muted">{app.appId}</span>
            </button>
          ))}
        </div>

        {missingStarters.length > 0 && (
          <section className="op-launcher-starters" data-testid="operator-starters">
            <h3>{t("operator:launcher.startersTitle")}</h3>
            <p className="op-muted">{t("operator:launcher.startersHint")}</p>
            <div className="op-launcher-grid">
              {missingStarters.map((starter) => (
                <div key={starter.appId} className="op-launcher-card op-launcher-card-static">
                  <strong>{starter.title}</strong>
                  <span className="op-muted">{starter.appId}</span>
                  {starter.description && <span className="op-muted">{starter.description}</span>}
                </div>
              ))}
            </div>
            <button
              type="button"
              className="btn primary"
              disabled={installMutation.isPending}
              onClick={() => installMutation.mutate()}
            >
              {installMutation.isPending
                ? t("common:action.loading")
                : t("operator:launcher.installStarters")}
            </button>
            {installMutation.error && (
              <p className="op-alert op-alert-error">{String(installMutation.error)}</p>
            )}
          </section>
        )}

        <p className="op-muted op-launcher-hint">{t("operator:launcher.hint")}</p>
      </main>
    </div>
  );
}
