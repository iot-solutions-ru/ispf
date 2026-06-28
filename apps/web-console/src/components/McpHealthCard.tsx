import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchMcpHealth } from "../api/mcpHealth";

export default function McpHealthCard() {
  const { t } = useTranslation(["system", "common"]);
  const healthQuery = useQuery({
    queryKey: ["mcp-health"],
    queryFn: fetchMcpHealth,
    refetchInterval: 30_000,
  });

  return (
    <section className="system-metrics-card mcp-health-card">
      <h3>{t("mcpHealth.title")}</h3>
      {healthQuery.isLoading && <p className="hint">{t("mcpHealth.loading")}</p>}
      {healthQuery.error && (
        <div className="op-alert op-alert-error">{t("mcpHealth.loadError")}</div>
      )}
      {healthQuery.data && (
        <>
          <table className="op-table system-metrics-table">
            <tbody>
              <tr>
                <th>{t("mcpHealth.enabled")}</th>
                <td>{healthQuery.data.enabled ? t("common:action.yes") : t("common:action.no")}</td>
              </tr>
              <tr>
                <th>{t("mcpHealth.stdioEnabled")}</th>
                <td>{healthQuery.data.stdioEnabled ? t("common:action.yes") : t("common:action.no")}</td>
              </tr>
              <tr>
                <th>{t("mcpHealth.serverName")}</th>
                <td className="mono">{healthQuery.data.serverName}</td>
              </tr>
              <tr>
                <th>{t("mcpHealth.protocolVersion")}</th>
                <td className="mono">{healthQuery.data.protocolVersion}</td>
              </tr>
              {healthQuery.data.enabled && (
                <>
                  <tr>
                    <th>{t("mcpHealth.toolCount")}</th>
                    <td>{healthQuery.data.toolCount}</td>
                  </tr>
                  {healthQuery.data.httpEndpoint && (
                    <tr>
                      <th>{t("mcpHealth.httpEndpoint")}</th>
                      <td className="mono">{healthQuery.data.httpEndpoint}</td>
                    </tr>
                  )}
                </>
              )}
            </tbody>
          </table>
          <p className="hint">{t("mcpHealth.hint")}</p>
        </>
      )}
    </section>
  );
}
