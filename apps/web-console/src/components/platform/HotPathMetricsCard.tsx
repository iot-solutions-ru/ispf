import { useTranslation } from "react-i18next";
import { Button, Space, Typography } from "antd";
import type { PlatformMetricSection, PlatformMetricsResponse } from "../../api/platformMetrics";

const HOT_PATH_KEYS = [
  "telemetryCoalesceDropsTotal",
  "telemetryBindingBypassTotal",
  "telemetryHistorianOnlyTotal",
  "objectChangeQueueSize",
  "eventJournalQueueSize",
  "variableHistoryQueueSize",
  "objectChangeDroppedTotal",
  "websocketClients",
] as const;

function sectionValues(
  data: PlatformMetricsResponse,
  id: string
): Record<string, unknown> {
  const section = data.sections.find((s: PlatformMetricSection) => s.id === id);
  return section?.values ?? {};
}

function formatValue(value: unknown): string {
  if (value == null) return "—";
  if (typeof value === "number") {
    return Number.isInteger(value) ? String(value) : value.toLocaleString();
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return String(value);
}

export default function HotPathMetricsCard({ data }: { data: PlatformMetricsResponse }) {
  const { t } = useTranslation("system");
  const automation = sectionValues(data, "automation");
  const connections = sectionValues(data, "connections");
  const merged: Record<string, unknown> = { ...automation, ...connections };

  const dashboardHref = `/?path=${encodeURIComponent("root.platform.dashboards.platform-metrics")}`;

  return (
    <section className="system-metrics-card system-hot-path-card">
      <header className="system-hot-path-header">
        <div>
          <Typography.Title level={3}>{t("metrics.hotPath.title")}</Typography.Title>
          <Typography.Paragraph type="secondary" className="hint">
            {t("metrics.hotPath.subtitle")}
          </Typography.Paragraph>
        </div>
        <Button href={dashboardHref}>
          {t("metrics.hotPath.openDashboard")}
        </Button>
      </header>
      <Space orientation="vertical" className="system-hot-path-grid">
        {HOT_PATH_KEYS.map((key) => (
          <article key={key} className="system-hot-path-stat">
            <div className="system-hot-path-label">{t(`metrics.${key}`)}</div>
            <div className="system-hot-path-value">{formatValue(merged[key])}</div>
          </article>
        ))}
      </Space>
    </section>
  );
}
