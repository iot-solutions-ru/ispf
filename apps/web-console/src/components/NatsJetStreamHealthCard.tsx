import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchNatsHealth } from "../api/natsHealth";

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

  return (
    <section className="system-metrics-card nats-health-card">
      <h3>{t("natsHealth.title")}</h3>
      {healthQuery.isLoading && <p className="hint">{t("natsHealth.loading")}</p>}
      {healthQuery.error && (
        <div className="op-alert op-alert-error">{t("natsHealth.loadError")}</div>
      )}
      {healthQuery.data && (
        <>
          <table className="op-table system-metrics-table">
            <tbody>
              <tr>
                <th>{t("natsHealth.enabled")}</th>
                <td>
                  {healthQuery.data.enabled ? t("common:action.yes") : t("common:action.no")}
                </td>
              </tr>
              <tr>
                <th>{t("natsHealth.connection")}</th>
                <td>
                  <span
                    className={
                      healthQuery.data.enabled && healthQuery.data.connected
                        ? "system-health-ok"
                        : healthQuery.data.enabled
                          ? "system-health-bad"
                          : undefined
                    }
                  >
                    {connectionStatus(healthQuery.data.connected, healthQuery.data.enabled)}
                  </span>
                </td>
              </tr>
              {healthQuery.data.url && (
                <tr>
                  <th>{t("natsHealth.url")}</th>
                  <td>{healthQuery.data.url}</td>
                </tr>
              )}
              <tr>
                <th>{t("natsHealth.replicaId")}</th>
                <td className="mono">{healthQuery.data.replicaId}</td>
              </tr>
              <tr>
                <th>{t("natsHealth.replicaEvents")}</th>
                <td>
                  {healthQuery.data.replicaEventsEnabled
                    ? t("common:action.yes")
                    : t("common:action.no")}
                </td>
              </tr>
              <tr>
                <th>{t("natsHealth.jetStreamEnabled")}</th>
                <td>
                  {healthQuery.data.jetStreamEnabled
                    ? t("common:action.yes")
                    : t("common:action.no")}
                </td>
              </tr>
              {healthQuery.data.jetStreamEnabled && (
                <>
                  <tr>
                    <th>{t("natsHealth.jetStreamActive")}</th>
                    <td>
                      {healthQuery.data.jetStreamActive
                        ? t("common:action.yes")
                        : t("common:action.no")}
                    </td>
                  </tr>
                  {healthQuery.data.streamName && (
                    <tr>
                      <th>{t("natsHealth.streamName")}</th>
                      <td className="mono">{healthQuery.data.streamName}</td>
                    </tr>
                  )}
                  <tr>
                    <th>{t("natsHealth.streamReady")}</th>
                    <td>
                      <span
                        className={
                          healthQuery.data.streamReady ? "system-health-ok" : "system-health-bad"
                        }
                      >
                        {healthQuery.data.streamReady
                          ? t("common:action.yes")
                          : t("common:action.no")}
                      </span>
                    </td>
                  </tr>
                  {healthQuery.data.streamMessages != null && (
                    <tr>
                      <th>{t("natsHealth.streamMessages")}</th>
                      <td>{healthQuery.data.streamMessages.toLocaleString()}</td>
                    </tr>
                  )}
                  {healthQuery.data.streamBytes != null && (
                    <tr>
                      <th>{t("natsHealth.streamBytes")}</th>
                      <td>{formatBytes(healthQuery.data.streamBytes)}</td>
                    </tr>
                  )}
                  {healthQuery.data.consumerDurable && (
                    <tr>
                      <th>{t("natsHealth.consumerDurable")}</th>
                      <td className="mono">{healthQuery.data.consumerDurable}</td>
                    </tr>
                  )}
                  {healthQuery.data.consumerPending != null && (
                    <tr>
                      <th>{t("natsHealth.consumerPending")}</th>
                      <td>{healthQuery.data.consumerPending.toLocaleString()}</td>
                    </tr>
                  )}
                </>
              )}
              <tr>
                <th>{t("natsHealth.publishNats")}</th>
                <td>
                  <span
                    className={
                      healthQuery.data.publishNatsAvailable ? "system-health-ok" : undefined
                    }
                  >
                    {healthQuery.data.publishNatsAvailable
                      ? t("natsHealth.publishNatsReady")
                      : t("natsHealth.publishNatsUnavailable")}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
          {healthQuery.data.connectionError && (
            <p className="hint system-health-error">{healthQuery.data.connectionError}</p>
          )}
          <p className="hint">{t("natsHealth.hint")}</p>
          <p className="hint">{t("natsHealth.publishNatsSmokeHint")}</p>
        </>
      )}
    </section>
  );
}
