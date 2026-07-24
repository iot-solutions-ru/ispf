import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Button, Space, Table, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { fetchPlatformMetrics, type PlatformMetricSection } from "../../api/platformMetrics";
import { usePersistentTab } from "../../hooks/usePersistentTab";
import AutomationIndexStatsCard from "../automation/AutomationIndexStatsCard";
import DiagnosticsPressureCard from "./DiagnosticsPressureCard";
import RedisHealthCard from "./RedisHealthCard";
import NatsJetStreamHealthCard from "./NatsJetStreamHealthCard";
import YargHealthCard from "./YargHealthCard";
import McpHealthCard from "./McpHealthCard";
import PlatformLicenseCard from "./PlatformLicenseCard";
import StorageHealthCard from "./StorageHealthCard";
import HotPathMetricsCard from "./HotPathMetricsCard";
import { useUserTimeZone } from "../../context/UserTimeZoneContext";

const METRIC_KEYS = [
  "uptimeMs", "uptimeHuman", "heapUsedBytes", "heapMaxBytes", "heapUsedMb", "heapMaxMb",
  "nonHeapUsedMb", "processors", "threadCount", "peakThreadCount", "poolName",
  "activeConnections", "idleConnections", "totalConnections", "threadsAwaitingConnection",
  "maxPoolSize", "poolAvailable", "objectNodes", "variables", "devices", "dashboards",
  "workflows", "applications", "blueprints", "alerts", "deviceObjects", "activeDrivers",
  "connectedDrivers", "driversWithError", "stoppedDrivers", "websocketClients",
  "platformUsers", "enabledUsers", "activeAuthSessions", "enabled", "minIntervalMs",
  "defaultRetentionDays", "historizedVariables", "sampleCount", "store", "oldestSampleAt",
  "newestSampleAt", "eventHistoryRecords", "workflowInstancesTotal", "workflowInstancesRunning",
  "workflowInstancesCompleted", "workflowInstancesFailed", "workflowInstancesCancelled",
  "alertRules", "eventCorrelators", "applicationFunctions", "applicationFunctionVersions",
  "platformSchedules", "platformSchedulesEnabled",
  "telemetryCoalesceDropsTotal", "telemetryBindingBypassTotal", "telemetryHistorianOnlyTotal",
  "objectChangeQueueSize", "eventJournalQueueSize", "variableHistoryQueueSize",
  "objectChangeDroppedTotal", "objectChangeProcessedTotal", "eventsFiredTotal", "alertFiresTotal",
  "correlatorTriggersTotal", "workflowStartsTotal", "eventJournalFlushedTotal",
  "eventJournalSyncFallbackTotal", "variableHistoryFlushedTotal", "variableHistorySyncFallbackTotal",
  "processCpuPercent", "systemCpuPercent", "heapUsedPercent", "pressureScore", "topSuspect",
  "turnsStartedTotal", "turnsCompletedTotal", "turnsRateLimitedTotal", "turnsLastHour",
  "avgStepsPerTurn", "guardBlocksByType",
  "asyncEnabled", "queueSize", "flushedTotal", "droppedTotal", "processedTotal",
] as const;

type MetricsSubTab = "overview" | "diagnostics";

const METRICS_SUB_TABS: readonly MetricsSubTab[] = ["overview", "diagnostics"];

function MetricSectionCard({ section }: { section: PlatformMetricSection }) {
  const { t } = useTranslation(["system", "common"]);
  const { formatDate } = useUserTimeZone();
  const entries = Object.entries(section.values ?? {});

  const metricLabel = (key: string) => {
    const labelKey = `metrics.${key}`;
    if (METRIC_KEYS.includes(key as (typeof METRIC_KEYS)[number])) {
      return t(labelKey);
    }
    const translated = t(labelKey, { defaultValue: "" });
    return translated || key;
  };

  const formatScalar = (value: unknown): string => {
    if (value == null) {
      return t("common:empty.dash");
    }
    if (typeof value === "boolean") {
      return value ? t("common:action.yes") : t("common:action.no");
    }
    if (typeof value === "number") {
      return Number.isInteger(value) ? String(value) : value.toLocaleString();
    }
    return String(value);
  };

  const formatMetricValue = (value: unknown): string => {
    if (value == null) {
      return t("common:empty.dash");
    }
    if (typeof value === "boolean" || typeof value === "number") {
      return formatScalar(value);
    }
    if (typeof value === "object") {
      const record = value as Record<string, unknown>;
      if (typeof record.title === "string" && record.title.trim()) {
        const detail = typeof record.detail === "string" ? record.detail.trim() : "";
        return detail ? `${record.title} — ${detail}` : record.title;
      }
      if (!Array.isArray(value)) {
        const parts = Object.entries(record).map(
          ([k, v]) => `${metricLabel(k)}: ${formatScalar(v)}`,
        );
        if (parts.length > 0) {
          return parts.join(" · ");
        }
      }
      try {
        return JSON.stringify(value);
      } catch {
        return t("common:empty.dash");
      }
    }
    return String(value);
  };
  const rows = entries.map(([key, value]) => {
    let tone = "";
    if (typeof value === "number") {
      if (key === "pressureScore" || key === "heapUsedPercent" || key === "processCpuPercent") {
        if (value >= 85) tone = "metric-tone-bad";
        else if (value >= 65) tone = "metric-tone-warn";
        else tone = "metric-tone-ok";
      } else if (key === "driversWithError" || key === "objectChangeDroppedTotal") {
        tone = value > 0 ? "metric-tone-bad" : "metric-tone-ok";
      }
    }
    return {
      key,
      label: metricLabel(key),
      value,
      tone,
    };
  });
  const columns: TableColumnsType<(typeof rows)[number]> = [
    { title: "", dataIndex: "label", key: "label" },
    {
      title: "",
      dataIndex: "value",
      key: "value",
      render: (value, row) => (
        <span className={row.tone || undefined}>
          {typeof value === "string" && value.includes("T") && value.endsWith("Z") ? (
            <time dateTime={value}>{formatDate(value)}</time>
          ) : (
            formatMetricValue(value)
          )}
        </span>
      ),
    },
  ];

  return (
    <section className="system-metrics-card">
      <Typography.Title level={3}>{section.title}</Typography.Title>
      <Table
        className="system-metrics-table"
        size="small"
        pagination={false}
        showHeader={false}
        columns={columns}
        dataSource={rows}
      />
    </section>
  );
}

export default function SystemMetricsView({ embedded = false }: { embedded?: boolean }) {
  const { t } = useTranslation("system");
  const { formatDate } = useUserTimeZone();
  const [subTab, setSubTab] = usePersistentTab<MetricsSubTab>(
    "system-metrics",
    "overview",
    METRICS_SUB_TABS
  );
  const metricsQuery = useQuery({
    queryKey: ["platform-metrics"],
    queryFn: fetchPlatformMetrics,
    refetchInterval: 30_000,
    enabled: subTab === "overview",
  });

  const subTabs: { id: MetricsSubTab; labelKey: string }[] = [
    { id: "overview", labelKey: "metrics.tab.overview" },
    { id: "diagnostics", labelKey: "metrics.tab.diagnostics" },
  ];

  const content = (
    <>
      {!embedded && (
        <header className="system-metrics-header">
          <div>
            <h2>{t("title")}</h2>
            <p className="op-muted">{t("metrics.standaloneSubtitle")}</p>
          </div>
        </header>
      )}

      <div className="tabs-scroll system-metrics-subtabs">
        <Space role="tablist" aria-label={t("metrics.tabsAria")}>
          {subTabs.map((item) => (
            <Button
              key={item.id}
              type={subTab === item.id ? "primary" : "default"}
              onClick={() => setSubTab(item.id)}
            >
              {t(item.labelKey)}
            </Button>
          ))}
        </Space>
      </div>

      {subTab === "diagnostics" && (
        <div className="system-metrics-diagnostics-pane">
          <DiagnosticsPressureCard />
        </div>
      )}

      {subTab === "overview" && (
        <>
          <div className="system-embedded-toolbar">
            <Button
              disabled={metricsQuery.isFetching}
              onClick={() => metricsQuery.refetch()}
            >
              {t(embedded ? "metrics.refreshMetrics" : "metrics.refresh")}
            </Button>
          </div>

          {metricsQuery.error && (
            <Alert type="error" showIcon message={String(metricsQuery.error)} />
          )}

          {metricsQuery.isLoading && <Typography.Text type="secondary">{t("metrics.loading")}</Typography.Text>}

          {metricsQuery.data && (
            <>
              <Typography.Paragraph type="secondary" className="system-metrics-updated hint">
                {t("metrics.updatedAt", {
                  time: formatDate(metricsQuery.data.timestamp),
                })}
              </Typography.Paragraph>
              <HotPathMetricsCard data={metricsQuery.data} />
              <AutomationIndexStatsCard />
              <StorageHealthCard />
              <div className="system-metrics-grid system-backend-health-grid">
                <RedisHealthCard />
                <NatsJetStreamHealthCard />
                <YargHealthCard />
                <McpHealthCard />
                <PlatformLicenseCard />
              </div>
              <div className="system-metrics-grid">
                {metricsQuery.data.sections.map((section) => (
                  <MetricSectionCard key={section.id} section={section} />
                ))}
              </div>
            </>
          )}
        </>
      )}
    </>
  );

  if (embedded) {
    return <div className="system-metrics-embedded">{content}</div>;
  }

  return (
    <main className="main system-metrics-view">
      {content}
    </main>
  );
}
