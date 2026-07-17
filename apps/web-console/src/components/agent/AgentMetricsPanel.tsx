import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchAgentMetrics, fetchAgentToolMetrics } from "../../api/ai";

export default function AgentMetricsPanel() {
  const { t } = useTranslation("ai");
  const metricsQuery = useQuery({
    queryKey: ["ai-agent-metrics", 7],
    queryFn: () => fetchAgentMetrics(7),
  });
  const toolMetricsQuery = useQuery({
    queryKey: ["ai-agent-tool-metrics", 7],
    queryFn: () => fetchAgentToolMetrics(7),
  });
  const metrics = metricsQuery.data;
  const toolMetrics = toolMetricsQuery.data;

  if (metricsQuery.isLoading) {
    return <p className="op-muted">{t("agent.metrics.loading")}</p>;
  }
  if (!metrics) {
    return null;
  }

  const turnsByStatus = (metrics.turnsByStatus ?? {}) as Record<string, number>;
  const topFailing = (metrics.topFailingTools ?? []) as Array<{ tool: string; errorCount: number }>;
  const tools = toolMetrics?.tools ?? [];

  return (
    <section className="ai-agent-metrics-panel">
      <h4>{t("agent.metrics.title")}</h4>
      <div className="ai-agent-metrics-grid">
        <div className="ai-agent-metrics-card">
          <span className="ai-agent-metrics-label">{t("agent.metrics.turnsOk")}</span>
          <strong>{turnsByStatus.OK ?? 0}</strong>
        </div>
        <div className="ai-agent-metrics-card">
          <span className="ai-agent-metrics-label">{t("agent.metrics.turnsError")}</span>
          <strong>{turnsByStatus.ERROR ?? 0}</strong>
        </div>
        <div className="ai-agent-metrics-card">
          <span className="ai-agent-metrics-label">{t("agent.metrics.avgSteps")}</span>
          <strong>{Number(metrics.avgStepsPerTurn ?? 0).toFixed(1)}</strong>
        </div>
        <div className="ai-agent-metrics-card">
          <span className="ai-agent-metrics-label">{t("agent.metrics.tokens")}</span>
          <strong>
            {Number(metrics.promptTokensSum ?? 0) + Number(metrics.completionTokensSum ?? 0)}
          </strong>
        </div>
      </div>
      {topFailing.length > 0 && (
        <>
          <h5>{t("agent.metrics.topFailing")}</h5>
          <ul className="ai-agent-metrics-failures">
            {topFailing.map((row) => (
              <li key={row.tool}>
                <code>{row.tool}</code> — {row.errorCount}
              </li>
            ))}
          </ul>
        </>
      )}
      <h5>{t("agent.metrics.toolsTitle")}</h5>
      {toolMetricsQuery.isLoading && (
        <p className="op-muted">{t("agent.metrics.toolsLoading")}</p>
      )}
      {!toolMetricsQuery.isLoading && tools.length === 0 && (
        <p className="op-muted">{t("agent.metrics.toolsEmpty")}</p>
      )}
      {tools.length > 0 && (
        <div className="ai-agent-metrics-table-wrap">
          <table className="ai-agent-metrics-table">
            <thead>
              <tr>
                <th>{t("agent.metrics.colTool")}</th>
                <th>{t("agent.metrics.colCalls")}</th>
                <th>{t("agent.metrics.colAvgLatency")}</th>
                <th>{t("agent.metrics.colTokens")}</th>
                <th>{t("agent.metrics.colErrorRate")}</th>
              </tr>
            </thead>
            <tbody>
              {tools.slice(0, 30).map((row) => (
                <tr key={row.tool}>
                  <td>
                    <code>{row.tool}</code>
                  </td>
                  <td>{row.callCount}</td>
                  <td>{Number(row.avgLatencyMs ?? 0).toFixed(1)}</td>
                  <td>
                    {Number(row.promptTokens ?? 0) + Number(row.completionTokens ?? 0)}
                  </td>
                  <td>{Number(row.errorRate ?? 0).toFixed(1)}%</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
