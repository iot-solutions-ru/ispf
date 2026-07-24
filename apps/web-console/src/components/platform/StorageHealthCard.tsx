import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Space, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import { fetchStorageHealth, type StorageBackendInfo } from "../../api/storageHealth";

function statusBadgeColor(backend: StorageBackendInfo): "default" | "success" | "error" {
  if (backend.store === "disabled") {
    return "default";
  }
  return backend.connected ? "success" : "error";
}

function formatCount(value: number | null, t: (key: string) => string): string {
  if (value == null) {
    return t("common:empty.dash");
  }
  return value.toLocaleString();
}

function backendSummary(
  backend: StorageBackendInfo,
  storeLabel: (store: string) => string,
  engineLabel: (engine: string) => string,
): string {
  const store = storeLabel(backend.store);
  const engine = engineLabel(backend.engine);
  if (backend.store === "disabled") {
    return store;
  }
  if (store.toLowerCase() === engine.toLowerCase()) {
    return store;
  }
  return `${store} · ${engine}`;
}

function StorageBackendPanel({
  backend,
  roleLabel,
  storeLabel,
  engineLabel,
  statusLabel,
}: {
  backend: StorageBackendInfo;
  roleLabel: string;
  storeLabel: (store: string) => string;
  engineLabel: (engine: string) => string;
  statusLabel: string;
}) {
  const { t } = useTranslation(["system", "common"]);
  const rows = [
    {
      key: "backend",
      label: t("storageHealth.field.backend"),
      value: backendSummary(backend, storeLabel, engineLabel),
    },
    ...(backend.endpoint
      ? [{
          key: "endpoint",
          label: t("storageHealth.field.endpoint"),
          value: <Typography.Text code className="storage-health-endpoint">{backend.endpoint}</Typography.Text>,
        }]
      : []),
    ...(backend.recordCount != null
      ? [{
          key: "records",
          label: t("storageHealth.field.records"),
          value: formatCount(backend.recordCount, t),
        }]
      : []),
    ...(backend.retentionDays != null
      ? [{
          key: "retention",
          label: t("storageHealth.field.retention"),
          value: t("storageHealth.retentionDays", { count: backend.retentionDays }),
        }]
      : []),
  ];
  const columns: TableColumnsType<(typeof rows)[number]> = [
    { title: "", dataIndex: "label", key: "label" },
    { title: "", dataIndex: "value", key: "value" },
  ];

  return (
    <article className="storage-health-panel">
      <header className="storage-health-panel-header">
        <Typography.Title level={4} className="storage-health-panel-title">
          {roleLabel}
        </Typography.Title>
        <Tag color={statusBadgeColor(backend)}>{statusLabel}</Tag>
      </header>
      <Table
        className="system-metrics-table storage-health-panel-table"
        size="small"
        pagination={false}
        showHeader={false}
        columns={columns}
        dataSource={rows}
      />
      {backend.connectionError && (
        <Alert
          className="storage-health-panel-error"
          type="error"
          showIcon
          message={backend.connectionError}
        />
      )}
    </article>
  );
}

export default function StorageHealthCard() {
  const { t } = useTranslation(["system", "common"]);
  const healthQuery = useQuery({
    queryKey: ["storage-health"],
    queryFn: fetchStorageHealth,
    refetchInterval: 30_000,
  });

  const roleLabel = (role: string) => t(`storageHealth.roles.${role}`, role);
  const storeLabel = (store: string) => t(`storageHealth.stores.${store}`, store);
  const engineLabel = (engine: string) => t(`storageHealth.engines.${engine}`, engine);

  const statusLabel = (backend: StorageBackendInfo) => {
    if (backend.store === "disabled") {
      return t("storageHealth.statusDisabled");
    }
    return backend.connected
      ? t("storageHealth.statusConnected")
      : t("storageHealth.statusDisconnected");
  };

  return (
    <section className="system-metrics-card storage-health-card">
      <Typography.Title level={3}>{t("storageHealth.title")}</Typography.Title>
      {healthQuery.isLoading && <Typography.Text type="secondary">{t("storageHealth.loading")}</Typography.Text>}
      {healthQuery.error && (
        <Alert type="error" showIcon message={t("storageHealth.loadError")} />
      )}
      {healthQuery.data && (
        <Space orientation="vertical" style={{ width: "100%" }}>
          <div className="storage-health-grid">
            {healthQuery.data.backends.map((backend) => (
              <StorageBackendPanel
                key={backend.id}
                backend={backend}
                roleLabel={roleLabel(backend.role)}
                storeLabel={storeLabel}
                engineLabel={engineLabel}
                statusLabel={statusLabel(backend)}
              />
            ))}
          </div>
          <Typography.Paragraph type="secondary" className="storage-health-hint">
            {t("storageHealth.hint")}
          </Typography.Paragraph>
        </Space>
      )}
    </section>
  );
}
