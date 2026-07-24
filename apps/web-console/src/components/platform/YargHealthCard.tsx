import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Space, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { fetchYargHealth } from "../../api/yargHealth";

export default function YargHealthCard() {
  const { t } = useTranslation(["system", "common"]);
  const healthQuery = useQuery({
    queryKey: ["yarg-health"],
    queryFn: fetchYargHealth,
    refetchInterval: 30_000,
  });
  const rows = healthQuery.data
    ? [
        {
          key: "libreOffice",
          label: t("yargHealth.libreOffice"),
          value: (
            <Tag color={healthQuery.data.libreOfficeAvailable ? "success" : "error"}>
              {healthQuery.data.libreOfficeAvailable
                ? t("yargHealth.available")
                : t("yargHealth.unavailable")}
            </Tag>
          ),
        },
        ...(healthQuery.data.configuredPath
          ? [{
              key: "configuredPath",
              label: t("yargHealth.configuredPath"),
              value: <Typography.Text code>{healthQuery.data.configuredPath}</Typography.Text>,
            }]
          : []),
        ...(healthQuery.data.resolvedPath
          ? [{
              key: "resolvedPath",
              label: t("yargHealth.resolvedPath"),
              value: <Typography.Text code>{healthQuery.data.resolvedPath}</Typography.Text>,
            }]
          : []),
        {
          key: "timeoutSeconds",
          label: t("yargHealth.timeoutSeconds"),
          value: healthQuery.data.timeoutSeconds,
        },
      ]
    : [];
  const columns: TableColumnsType<(typeof rows)[number]> = [
    { title: "", dataIndex: "label", key: "label" },
    { title: "", dataIndex: "value", key: "value" },
  ];

  return (
    <section className="system-metrics-card yarg-health-card">
      <Typography.Title level={3}>{t("yargHealth.title")}</Typography.Title>
      {healthQuery.isLoading && <Typography.Text type="secondary">{t("yargHealth.loading")}</Typography.Text>}
      {healthQuery.error && (
        <Alert type="error" showIcon message={t("yargHealth.loadError")} />
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
          <Typography.Paragraph type="secondary">{t("yargHealth.hint")}</Typography.Paragraph>
          {!healthQuery.data.libreOfficeAvailable && (
            <Alert type="warning" showIcon message={healthQuery.data.pdfHint} />
          )}
        </Space>
      )}
    </section>
  );
}
