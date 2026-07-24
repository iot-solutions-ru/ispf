import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Space, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { fetchRedisHealth } from "../../api/redisHealth";

function formatTtl(seconds: number, t: (key: string, opts?: { count: number }) => string): string {
  if (seconds >= 3600 && seconds % 3600 === 0) {
    return t("redisHealth.ttlHours", { count: seconds / 3600 });
  }
  if (seconds >= 60 && seconds % 60 === 0) {
    return t("redisHealth.ttlMinutes", { count: seconds / 60 });
  }
  return t("redisHealth.ttlSeconds", { count: seconds });
}

export default function RedisHealthCard() {
  const { t } = useTranslation(["system", "common"]);
  const healthQuery = useQuery({
    queryKey: ["redis-health"],
    queryFn: fetchRedisHealth,
    refetchInterval: 30_000,
  });

  const statusLabel = (connected: boolean, enabled: boolean) => {
    if (!enabled) return t("redisHealth.statusDisabled");
    return connected ? t("redisHealth.statusConnected") : t("redisHealth.statusDisconnected");
  };

  const storeLabel = (store: "redis" | "jdbc" | "local") => {
    if (store === "redis") return t("redisHealth.backendRedis");
    if (store === "jdbc") return t("redisHealth.backendJdbc");
    return t("redisHealth.backendLocal");
  };
  const rows = healthQuery.data
    ? [
        {
          key: "enabled",
          label: t("redisHealth.enabled"),
          value: healthQuery.data.enabled ? t("common:action.yes") : t("common:action.no"),
        },
        {
          key: "connection",
          label: t("redisHealth.connection"),
          value: (
            <Tag color={!healthQuery.data.enabled ? "default" : healthQuery.data.connected ? "success" : "error"}>
              {statusLabel(healthQuery.data.connected, healthQuery.data.enabled)}
            </Tag>
          ),
        },
        ...(healthQuery.data.enabled && healthQuery.data.host
          ? [{
              key: "endpoint",
              label: t("redisHealth.endpoint"),
              value: `${healthQuery.data.host}:${healthQuery.data.port}`,
            }]
          : []),
        {
          key: "correlatorWindows",
          label: t("redisHealth.correlatorWindows"),
          value: healthQuery.data.correlatorWindowsEnabled
            ? t("common:action.yes")
            : t("common:action.no"),
        },
        {
          key: "correlatorStore",
          label: t("redisHealth.correlatorStore"),
          value: storeLabel(healthQuery.data.correlatorWindowStore),
        },
        {
          key: "aclCacheBackend",
          label: t("redisHealth.aclCacheBackend"),
          value: storeLabel(healthQuery.data.aclCacheBackend),
        },
        ...(healthQuery.data.aclCacheBackend === "redis"
          ? [
              {
                key: "objectAclTtl",
                label: t("redisHealth.objectAclTtl"),
                value: formatTtl(healthQuery.data.objectAclTtlSeconds, t),
              },
              {
                key: "contextPackTtl",
                label: t("redisHealth.contextPackTtl"),
                value: formatTtl(healthQuery.data.contextPackTtlSeconds, t),
              },
              {
                key: "platformBriefingTtl",
                label: t("redisHealth.platformBriefingTtl"),
                value: formatTtl(healthQuery.data.platformBriefingTtlSeconds, t),
              },
            ]
          : []),
        ...(healthQuery.data.correlatorWindowKeys != null
          ? [{
              key: "correlatorWindowKeys",
              label: t("redisHealth.correlatorWindowKeys"),
              value: healthQuery.data.correlatorWindowKeys,
            }]
          : []),
      ]
    : [];
  const columns: TableColumnsType<(typeof rows)[number]> = [
    { title: "", dataIndex: "label", key: "label" },
    { title: "", dataIndex: "value", key: "value" },
  ];

  return (
    <section className="system-metrics-card redis-health-card">
      <Typography.Title level={3}>{t("redisHealth.title")}</Typography.Title>
      {healthQuery.isLoading && <Typography.Text type="secondary">{t("redisHealth.loading")}</Typography.Text>}
      {healthQuery.error && (
        <Alert type="error" showIcon message={t("redisHealth.loadError")} />
      )}
      {healthQuery.data && (
        <Space orientation="vertical" style={{ width: "100%" }}>
          <Table
            className="system-metrics-table"
            size="small"
            pagination={false}
            showHeader={false}
            columns={columns}
            dataSource={rows}
          />
          {healthQuery.data.connectionError && (
            <Alert type="error" showIcon message={healthQuery.data.connectionError} />
          )}
          <Typography.Paragraph type="secondary">{t("redisHealth.hint")}</Typography.Paragraph>
        </Space>
      )}
    </section>
  );
}
