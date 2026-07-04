import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchStorageHealth, type StorageBackendInfo } from "../api/storageHealth";

function statusBadgeClass(backend: StorageBackendInfo): string {
  if (backend.store === "disabled") {
    return "storage-health-badge is-muted";
  }
  return backend.connected
    ? "storage-health-badge is-ok"
    : "storage-health-badge is-bad";
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

  return (
    <article className="storage-health-panel">
      <header className="storage-health-panel-header">
        <h4 className="storage-health-panel-title">{roleLabel}</h4>
        <span className={statusBadgeClass(backend)}>{statusLabel}</span>
      </header>
      <table className="op-table system-metrics-table storage-health-panel-table">
        <tbody>
          <tr>
            <th>{t("storageHealth.field.backend")}</th>
            <td>{backendSummary(backend, storeLabel, engineLabel)}</td>
          </tr>
          {backend.endpoint && (
            <tr>
              <th>{t("storageHealth.field.endpoint")}</th>
              <td>
                <code className="storage-health-endpoint">{backend.endpoint}</code>
              </td>
            </tr>
          )}
          {backend.recordCount != null && (
            <tr>
              <th>{t("storageHealth.field.records")}</th>
              <td>{formatCount(backend.recordCount, t)}</td>
            </tr>
          )}
          {backend.retentionDays != null && (
            <tr>
              <th>{t("storageHealth.field.retention")}</th>
              <td>{t("storageHealth.retentionDays", { count: backend.retentionDays })}</td>
            </tr>
          )}
        </tbody>
      </table>
      {backend.connectionError && (
        <p className="hint system-health-error storage-health-panel-error">
          {backend.connectionError}
        </p>
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
      <h3>{t("storageHealth.title")}</h3>
      {healthQuery.isLoading && <p className="hint">{t("storageHealth.loading")}</p>}
      {healthQuery.error && (
        <div className="op-alert op-alert-error">{t("storageHealth.loadError")}</div>
      )}
      {healthQuery.data && (
        <>
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
          <p className="hint storage-health-hint">{t("storageHealth.hint")}</p>
        </>
      )}
    </section>
  );
}
