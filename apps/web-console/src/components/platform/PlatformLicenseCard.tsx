import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Button, Space, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { fetchPlatformLicense } from "../../api/platformLicense";
import { useOptionalUserTimeZone } from "../../context/UserTimeZoneContext";
import { formatUserDateTime } from "../../utils/ui/formatDateTime";

export default function PlatformLicenseCard() {
  const { t } = useTranslation(["system", "common"]);
  const tz = useOptionalUserTimeZone();
  const formatDate = (value: string | number | Date | null | undefined) =>
    tz ? tz.formatDate(value) : formatUserDateTime(value);
  const licenseQuery = useQuery({
    queryKey: ["platform-license"],
    queryFn: fetchPlatformLicense,
    refetchInterval: 60_000,
  });

  const copyInstallationId = async () => {
    if (!licenseQuery.data?.installationId) {
      return;
    }
    try {
      await navigator.clipboard.writeText(licenseQuery.data.installationId);
    } catch {
      // Clipboard may be unavailable outside secure context.
    }
  };

  const rows = licenseQuery.data
    ? [
        { key: "mode", label: t("licenseHealth.mode"), value: licenseQuery.data.mode },
        ...(licenseQuery.data.tier
          ? [{ key: "tier", label: t("licenseHealth.tier"), value: licenseQuery.data.tier }]
          : []),
        {
          key: "valid",
          label: t("licenseHealth.valid"),
          value: (
            <Tag color={licenseQuery.data.valid ? "success" : "error"}>
              {licenseQuery.data.valid ? t("licenseHealth.validYes") : t("licenseHealth.validNo")}
            </Tag>
          ),
        },
        {
          key: "enforce",
          label: t("licenseHealth.enforce"),
          value: licenseQuery.data.enforce ? t("common:action.yes") : t("common:action.no"),
        },
        ...(licenseQuery.data.expiresAt
          ? [{
              key: "expiresAt",
              label: t("licenseHealth.expiresAt"),
              value: (
                <time dateTime={licenseQuery.data.expiresAt}>
                  {formatDate(licenseQuery.data.expiresAt)}
                </time>
              ),
            }]
          : []),
        {
          key: "installationId",
          label: t("licenseHealth.installationId"),
          value: (
            <Space>
              <Typography.Text code className="platform-license-id">
                {licenseQuery.data.installationId}
              </Typography.Text>
              <Button size="small" onClick={() => void copyInstallationId()}>
                {t("licenseHealth.copyInstallationId")}
              </Button>
            </Space>
          ),
        },
        { key: "message", label: t("licenseHealth.message"), value: licenseQuery.data.message },
      ]
    : [];
  const columns: TableColumnsType<(typeof rows)[number]> = [
    { title: "", dataIndex: "label", key: "label" },
    { title: "", dataIndex: "value", key: "value" },
  ];

  return (
    <section className="system-metrics-card platform-license-card">
      <Typography.Title level={3}>{t("licenseHealth.title")}</Typography.Title>
      {licenseQuery.isLoading && <Typography.Text type="secondary">{t("licenseHealth.loading")}</Typography.Text>}
      {licenseQuery.error && (
        <Alert type="error" showIcon message={t("licenseHealth.loadError")} />
      )}
      {licenseQuery.data && (
        <Space orientation="vertical" style={{ width: "100%" }}>
          <Table
            className="system-metrics-table"
            size="small"
            pagination={false}
            showHeader={false}
            columns={columns}
            dataSource={rows}
          />
          <Typography.Paragraph type="secondary">{t("licenseHealth.hint")}</Typography.Paragraph>
          {licenseQuery.data.enforce && !licenseQuery.data.valid && (
            <Alert type="warning" showIcon message={t("licenseHealth.enforceInvalidWarning")} />
          )}
        </Space>
      )}
    </section>
  );
}
