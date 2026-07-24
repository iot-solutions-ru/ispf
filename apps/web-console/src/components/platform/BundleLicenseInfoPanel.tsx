import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Button, Space, Table, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { fetchPlatformLicense } from "../../api/platformLicense";
import { parseManifestLicense } from "../../utils/platform/bundleLicenseUi";
import { useUserTimeZone } from "../../context/UserTimeZoneContext";

interface BundleLicenseInfoPanelProps {
  appId?: string;
  manifest?: unknown;
  compact?: boolean;
}

export default function BundleLicenseInfoPanel({
  appId,
  manifest,
  compact = false,
}: BundleLicenseInfoPanelProps) {
  const { t } = useTranslation(["platform", "common"]);
  const { formatDate } = useUserTimeZone();
  const licenseQuery = useQuery({
    queryKey: ["platform-license"],
    queryFn: fetchPlatformLicense,
    staleTime: 60_000,
  });

  const manifestLicense = useMemo(() => parseManifestLicense(manifest), [manifest]);

  const copyInstallationId = async () => {
    const id = licenseQuery.data?.installationId;
    if (!id) {
      return;
    }
    try {
      await navigator.clipboard.writeText(id);
    } catch {
      // Clipboard may be unavailable outside secure context.
    }
  };

  if (licenseQuery.isLoading) {
    return <Typography.Text type="secondary">{t("platform:bundle.license.loading")}</Typography.Text>;
  }

  if (licenseQuery.error) {
    return <Alert type="error" showIcon message={t("platform:bundle.license.loadError")} />;
  }

  const serverInstallationId = licenseQuery.data?.installationId ?? "";
  const installationMismatch = Boolean(
    manifestLicense.present
    && manifestLicense.installationId
    && serverInstallationId
    && manifestLicense.installationId.toLowerCase() !== serverInstallationId.toLowerCase(),
  );
  const appIdMismatch = Boolean(
    appId
    && manifestLicense.bundleId
    && manifestLicense.bundleId !== appId,
  );
  const rows = [
    {
      key: "installationId",
      label: t("platform:bundle.license.installationId"),
      value: (
        <Space>
          <Typography.Text code>{serverInstallationId}</Typography.Text>
          <Button size="small" onClick={() => void copyInstallationId()}>
            {t("platform:bundle.license.copyInstallationId")}
          </Button>
        </Space>
      ),
    },
    {
      key: "enforce",
      label: t("platform:bundle.license.enforce"),
      value: licenseQuery.data?.enforce ? t("common:action.yes") : t("common:action.no"),
    },
    {
      key: "manifestBlock",
      label: t("platform:bundle.license.manifestBlock"),
      value: manifestLicense.present
        ? t("platform:bundle.license.manifestPresent")
        : t("platform:bundle.license.manifestAbsent"),
    },
    ...(manifestLicense.present && manifestLicense.bundleId
      ? [{
          key: "bundleId",
          label: t("platform:bundle.license.bundleId"),
          value: (
            <Space>
              <Typography.Text code>{manifestLicense.bundleId}</Typography.Text>
              {appIdMismatch && (
                <Typography.Text type="warning" className="bundle-license-warn">
                  {t("platform:bundle.license.mismatchAppId", { appId })}
                </Typography.Text>
              )}
            </Space>
          ),
        }]
      : []),
    ...(manifestLicense.present && manifestLicense.expiresAt
      ? [{
          key: "expiresAt",
          label: t("platform:bundle.license.expiresAt"),
          value: (
            <time dateTime={manifestLicense.expiresAt}>
              {formatDate(manifestLicense.expiresAt)}
            </time>
          ),
        }]
      : []),
  ];
  const columns: TableColumnsType<(typeof rows)[number]> = [
    { title: "", dataIndex: "label", key: "label" },
    { title: "", dataIndex: "value", key: "value" },
  ];

  return (
    <section className={`bundle-license-info${compact ? " bundle-license-info-compact" : ""}`}>
      <Space orientation="vertical" style={{ width: "100%" }}>
        <Typography.Title level={4}>{t("platform:bundle.license.title")}</Typography.Title>
        <Typography.Paragraph type="secondary">{t("platform:bundle.license.hint")}</Typography.Paragraph>
        <Table
          className="bundle-license-kv"
          size="small"
          pagination={false}
          showHeader={false}
          columns={columns}
          dataSource={rows}
        />

        {installationMismatch && (
          <Alert
            type="info"
            showIcon
            message={t("platform:bundle.license.mismatchInstallation", {
              licensed: manifestLicense.installationId,
              server: serverInstallationId,
            })}
          />
        )}
      </Space>
    </section>
  );
}
