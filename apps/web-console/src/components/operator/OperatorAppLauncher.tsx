import { useQueries, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Badge, Button, Space, Typography } from "antd";
import { useTranslation } from "react-i18next";
import {
  fetchOperatorApps,
  fetchOperatorAppUi,
  fetchOperatorStarters,
  installOperatorStarters,
  type OperatorAppEntry,
} from "../../api/operatorApps";
import ShellPreferences from "../ui/ShellPreferences";

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
        <Space direction="vertical" size={0}>
          <Typography.Text strong>{t("operator:launcher.title")}</Typography.Text>
          <span className="brand-sub">{t("operator:launcher.subtitle")}</span>
        </Space>
        <Space className="topbar-actions" wrap>
          <ShellPreferences />
          {onSwitchAdmin && (
            <Button onClick={onSwitchAdmin}>
              {t("operator:launcher.switchAdmin")}
            </Button>
          )}
        </Space>
      </header>
      <main className="op-launcher">
        {appsQuery.isLoading && <Typography.Paragraph className="op-muted">{t("common:action.loading")}</Typography.Paragraph>}
        {appsQuery.error && <Alert type="error" message={String(appsQuery.error)} />}
        <div className="op-launcher-grid">
          {apps.map((app: OperatorAppEntry, index) => {
            const ui = uiQueries[index]?.data ?? null;
            const ready = Boolean(ui?.defaultDashboard);
            const alarmsOn = Boolean(ui?.alarmBar?.enabled);
            const dashCount = ui?.dashboards?.length ?? 0;
            return (
              <Button
                key={app.appId}
                type="text"
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
                      <Badge
                        className="op-launcher-alarm-badge"
                        title={t("launcher.alarmsEnabled")}
                        count={t("launcher.alarmsOn")}
                      />
                    )}
                    {dashCount > 0 && (
                      <span className="op-muted">{t("launcher.dashboards", { count: dashCount })}</span>
                    )}
                  </span>
                </span>
              </Button>
            );
          })}
        </div>

        {missingStarters.length > 0 && (
          <section className="op-launcher-starters" data-testid="operator-starters">
            <Typography.Title level={3}>{t("operator:launcher.startersTitle")}</Typography.Title>
            <Typography.Paragraph className="op-muted">{t("operator:launcher.startersHint")}</Typography.Paragraph>
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
            <Button
              type="primary"
              disabled={installMutation.isPending}
              onClick={() => installMutation.mutate()}
            >
              {installMutation.isPending
                ? t("common:action.loading")
                : t("operator:launcher.installStarters")}
            </Button>
            {installMutation.error && (
              <Alert type="error" message={String(installMutation.error)} />
            )}
          </section>
        )}

        <Typography.Paragraph className="op-muted op-launcher-hint">{t("operator:launcher.hint")}</Typography.Paragraph>
      </main>
    </div>
  );
}
