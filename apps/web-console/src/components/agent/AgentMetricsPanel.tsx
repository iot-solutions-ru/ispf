import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchAgentMetrics } from "../../api/ai";

export default function AgentMetricsPanel() {
  const { t } = useTranslation("ai");
  const metricsQuery = useQuery({
    queryKey: ["ai-agent-metrics", 7],
    queryFn: () => fetchAgentMetrics(7),
  });
  const metrics = metricsQuery.data;

  if (metricsQuery.isLoading) {
    return <p className="op-muted">{t("agent.metrics.loading")}</p>;
  }
  if (!metrics) {
    return null;
  }

  const turnsByStatus = (metrics.turnsByStatus ?? {}) as Record<string, number>;
  const topFailing = (metrics.topFailingTools ?? []) as Array<{ tool: string; errorCount: number }>;

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
    </section>
  );
}
