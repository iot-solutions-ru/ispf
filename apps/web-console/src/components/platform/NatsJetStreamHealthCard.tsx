import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Space, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { fetchNatsHealth } from "../../api/natsHealth";

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export default function NatsJetStreamHealthCard() {
  const { t } = useTranslation(["system", "common"]);
  const healthQuery = useQuery({
    queryKey: ["nats-health"],
    queryFn: fetchNatsHealth,
    refetchInterval: 30_000,
  });

  const connectionStatus = (connected: boolean, enabled: boolean) => {
    if (!enabled) return t("natsHealth.statusDisabled");
    return connected ? t("natsHealth.statusConnected") : t("natsHealth.statusDisconnected");
  };
  const rows = healthQuery.data
    ? [
        {
          key: "enabled",
          label: t("natsHealth.enabled"),
          value: healthQuery.data.enabled ? t("common:action.yes") : t("common:action.no"),
        },
        {
          key: "connection",
          label: t("natsHealth.connection"),
          value: (
            <Tag color={!healthQuery.data.enabled ? "default" : healthQuery.data.connected ? "success" : "error"}>
              {connectionStatus(healthQuery.data.connected, healthQuery.data.enabled)}
            </Tag>
          ),
        },
        ...(healthQuery.data.url
          ? [{ key: "url", label: t("natsHealth.url"), value: healthQuery.data.url }]
          : []),
        {
          key: "replicaId",
          label: t("natsHealth.replicaId"),
          value: <Typography.Text code>{healthQuery.data.replicaId}</Typography.Text>,
        },
        {
          key: "replicaEvents",
          label: t("natsHealth.replicaEvents"),
          value: healthQuery.data.replicaEventsEnabled ? t("common:action.yes") : t("common:action.no"),
        },
        {
          key: "jetStreamEnabled",
          label: t("natsHealth.jetStreamEnabled"),
          value: healthQuery.data.jetStreamEnabled ? t("common:action.yes") : t("common:action.no"),
        },
        ...(healthQuery.data.jetStreamEnabled
          ? [
              {
                key: "jetStreamActive",
                label: t("natsHealth.jetStreamActive"),
                value: healthQuery.data.jetStreamActive ? t("common:action.yes") : t("common:action.no"),
              },
              ...(healthQuery.data.streamName
                ? [{
                    key: "streamName",
                    label: t("natsHealth.streamName"),
                    value: <Typography.Text code>{healthQuery.data.streamName}</Typography.Text>,
                  }]
                : []),
              {
                key: "streamReady",
                label: t("natsHealth.streamReady"),
                value: (
                  <Tag color={healthQuery.data.streamReady ? "success" : "error"}>
                    {healthQuery.data.streamReady ? t("common:action.yes") : t("common:action.no")}
                  </Tag>
                ),
              },
              ...(healthQuery.data.streamMessages != null
                ? [{
                    key: "streamMessages",
                    label: t("natsHealth.streamMessages"),
                    value: healthQuery.data.streamMessages.toLocaleString(),
                  }]
                : []),
              ...(healthQuery.data.streamBytes != null
                ? [{
                    key: "streamBytes",
                    label: t("natsHealth.streamBytes"),
                    value: formatBytes(healthQuery.data.streamBytes),
                  }]
                : []),
              ...(healthQuery.data.consumerDurable
                ? [{
                    key: "consumerDurable",
                    label: t("natsHealth.consumerDurable"),
                    value: <Typography.Text code>{healthQuery.data.consumerDurable}</Typography.Text>,
                  }]
                : []),
              ...(healthQuery.data.consumerPending != null
                ? [{
                    key: "consumerPending",
                    label: t("natsHealth.consumerPending"),
                    value: healthQuery.data.consumerPending.toLocaleString(),
                  }]
                : []),
            ]
          : []),
        {
          key: "publishNats",
          label: t("natsHealth.publishNats"),
          value: (
            <Tag color={healthQuery.data.publishNatsAvailable ? "success" : "default"}>
              {healthQuery.data.publishNatsAvailable
                ? t("natsHealth.publishNatsReady")
                : t("natsHealth.publishNatsUnavailable")}
            </Tag>
          ),
        },
      ]
    : [];
  const columns: TableColumnsType<(typeof rows)[number]> = [
    { title: "", dataIndex: "label", key: "label" },
    { title: "", dataIndex: "value", key: "value" },
  ];

  return (
    <section className="system-metrics-card nats-health-card">
      <Typography.Title level={3}>{t("natsHealth.title")}</Typography.Title>
      {healthQuery.isLoading && <Typography.Text type="secondary">{t("natsHealth.loading")}</Typography.Text>}
      {healthQuery.error && (
        <Alert type="error" showIcon message={t("natsHealth.loadError")} />
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
          <Typography.Paragraph type="secondary">{t("natsHealth.hint")}</Typography.Paragraph>
          <Typography.Paragraph type="secondary">{t("natsHealth.publishNatsSmokeHint")}</Typography.Paragraph>
        </Space>
      )}
    </section>
  );
}
