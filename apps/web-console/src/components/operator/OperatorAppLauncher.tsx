import { useQueries, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  fetchOperatorApps,
  fetchOperatorAppUi,
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
  return fetchOperatorApps();
}

function appInitial(title: string, appId: string): string {
  const source = (title || appId).trim();
  return (source.charAt(0) || "?").toUpperCase();
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

  const apps = appsQuery.data ?? [];
  const uiQueries = useQueries({
    queries: apps.map((app) => ({
      queryKey: ["operator-app-ui", app.appId],
      queryFn: () => fetchOperatorAppUi(app.appId),
      staleTime: 60_000,
    })),
  });

  const appIds = new Set(apps.map((app) => app.appId));
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
          {apps.map((app: OperatorAppEntry, index) => {
            const ui = uiQueries[index]?.data ?? null;
            const ready = Boolean(ui?.defaultDashboard);
            const alarmsOn = Boolean(ui?.alarmBar?.enabled);
            const dashCount = ui?.dashboards?.length ?? 0;
            return (
              <button
                key={app.appId}
                type="button"
                className={`op-launcher-card${ready ? " op-launcher-card--ready" : ""}`}
                onClick={() => onOpenApp(app.appId)}
              >
                <span className="op-launcher-card-icon" aria-hidden>
                  {appInitial(app.title, app.appId)}
                </span>
                <span className="op-launcher-card-body">
                  <strong>{app.title}</strong>
                  <span className="op-muted">{app.appId}</span>
                  <span className="op-launcher-card-meta">
                    <span
                      className={`op-launcher-status ${ready ? "op-launcher-status--ok" : "op-launcher-status--idle"}`}
                    >
                      {ready ? t("launcher.statusReady") : t("launcher.statusSetup")}
                    </span>
                    {alarmsOn && (
                      <span className="op-launcher-alarm-badge" title={t("launcher.alarmsEnabled")}>
                        {t("launcher.alarmsOn")}
                      </span>
                    )}
                    {dashCount > 0 && (
                      <span className="op-muted">{t("launcher.dashboards", { count: dashCount })}</span>
                    )}
                  </span>
                </span>
              </button>
            );
          })}
        </div>

        {missingStarters.length > 0 && (
          <section className="op-launcher-starters" data-testid="operator-starters">
            <h3>{t("operator:launcher.startersTitle")}</h3>
            <p className="op-muted">{t("operator:launcher.startersHint")}</p>
            <div className="op-launcher-grid">
              {missingStarters.map((starter) => (
                <div key={starter.appId} className="op-launcher-card op-launcher-card-static">
                  <span className="op-launcher-card-icon" aria-hidden>
                    {appInitial(starter.title, starter.appId)}
                  </span>
                  <span className="op-launcher-card-body">
                    <strong>{starter.title}</strong>
                    <span className="op-muted">{starter.appId}</span>
                    {starter.description && <span className="op-muted">{starter.description}</span>}
                  </span>
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
