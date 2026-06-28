import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchRedisHealth } from "../api/redisHealth";

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

  return (
    <section className="system-metrics-card redis-health-card">
      <h3>{t("redisHealth.title")}</h3>
      {healthQuery.isLoading && <p className="hint">{t("redisHealth.loading")}</p>}
      {healthQuery.error && (
        <div className="op-alert op-alert-error">{t("redisHealth.loadError")}</div>
      )}
      {healthQuery.data && (
        <>
          <table className="op-table system-metrics-table">
            <tbody>
              <tr>
                <th>{t("redisHealth.enabled")}</th>
                <td>{healthQuery.data.enabled ? t("common:action.yes") : t("common:action.no")}</td>
              </tr>
              <tr>
                <th>{t("redisHealth.connection")}</th>
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
                    {statusLabel(healthQuery.data.connected, healthQuery.data.enabled)}
                  </span>
                </td>
              </tr>
              {healthQuery.data.enabled && healthQuery.data.host && (
                <tr>
                  <th>{t("redisHealth.endpoint")}</th>
                  <td>{healthQuery.data.host}:{healthQuery.data.port}</td>
                </tr>
              )}
              <tr>
                <th>{t("redisHealth.correlatorWindows")}</th>
                <td>
                  {healthQuery.data.correlatorWindowsEnabled
                    ? t("common:action.yes")
                    : t("common:action.no")}
                </td>
              </tr>
              <tr>
                <th>{t("redisHealth.correlatorStore")}</th>
                <td>{storeLabel(healthQuery.data.correlatorWindowStore)}</td>
              </tr>
              <tr>
                <th>{t("redisHealth.aclCacheBackend")}</th>
                <td>{storeLabel(healthQuery.data.aclCacheBackend)}</td>
              </tr>
              {healthQuery.data.aclCacheBackend === "redis" && (
                <>
                  <tr>
                    <th>{t("redisHealth.objectAclTtl")}</th>
                    <td>{formatTtl(healthQuery.data.objectAclTtlSeconds, t)}</td>
                  </tr>
                  <tr>
                    <th>{t("redisHealth.contextPackTtl")}</th>
                    <td>{formatTtl(healthQuery.data.contextPackTtlSeconds, t)}</td>
                  </tr>
                  <tr>
                    <th>{t("redisHealth.platformBriefingTtl")}</th>
                    <td>{formatTtl(healthQuery.data.platformBriefingTtlSeconds, t)}</td>
                  </tr>
                </>
              )}
              {healthQuery.data.correlatorWindowKeys != null && (
                <tr>
                  <th>{t("redisHealth.correlatorWindowKeys")}</th>
                  <td>{healthQuery.data.correlatorWindowKeys}</td>
                </tr>
              )}
            </tbody>
          </table>
          {healthQuery.data.connectionError && (
            <p className="hint system-health-error">{healthQuery.data.connectionError}</p>
          )}
          <p className="hint">{t("redisHealth.hint")}</p>
        </>
      )}
    </section>
  );
}
