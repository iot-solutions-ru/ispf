import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Space, Table, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { fetchMcpHealth } from "../../api/mcpHealth";

export default function McpHealthCard() {
  const { t } = useTranslation(["system", "common"]);
  const healthQuery = useQuery({
    queryKey: ["mcp-health"],
    queryFn: fetchMcpHealth,
    refetchInterval: 30_000,
  });
  const rows = healthQuery.data
    ? [
        {
          key: "enabled",
          label: t("mcpHealth.enabled"),
          value: healthQuery.data.enabled ? t("common:action.yes") : t("common:action.no"),
        },
        {
          key: "stdioEnabled",
          label: t("mcpHealth.stdioEnabled"),
          value: healthQuery.data.stdioEnabled ? t("common:action.yes") : t("common:action.no"),
        },
        {
          key: "serverName",
          label: t("mcpHealth.serverName"),
          value: <Typography.Text code>{healthQuery.data.serverName}</Typography.Text>,
        },
        {
          key: "protocolVersion",
          label: t("mcpHealth.protocolVersion"),
          value: <Typography.Text code>{healthQuery.data.protocolVersion}</Typography.Text>,
        },
        ...(healthQuery.data.enabled
          ? [
              {
                key: "toolCount",
                label: t("mcpHealth.toolCount"),
                value: healthQuery.data.toolCount,
              },
              ...(healthQuery.data.httpEndpoint
                ? [{
                    key: "httpEndpoint",
                    label: t("mcpHealth.httpEndpoint"),
                    value: <Typography.Text code>{healthQuery.data.httpEndpoint}</Typography.Text>,
                  }]
                : []),
            ]
          : []),
      ]
    : [];
  const columns: TableColumnsType<(typeof rows)[number]> = [
    { title: "", dataIndex: "label", key: "label" },
    { title: "", dataIndex: "value", key: "value" },
  ];

  return (
    <section className="system-metrics-card mcp-health-card">
      <Typography.Title level={3}>{t("mcpHealth.title")}</Typography.Title>
      {healthQuery.isLoading && <Typography.Text type="secondary">{t("mcpHealth.loading")}</Typography.Text>}
      {healthQuery.error && (
        <Alert type="error" showIcon message={t("mcpHealth.loadError")} />
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
          <Typography.Paragraph type="secondary">{t("mcpHealth.hint")}</Typography.Paragraph>
        </Space>
      )}
    </section>
  );
}
