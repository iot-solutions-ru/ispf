import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { AiAgentStep } from "../../api/ai";
import { buildAgentTurnGraph } from "../../utils/agent/agentTurnGraph";

export default function AgentTurnGraph({ steps }: { steps: AiAgentStep[] }) {
  const { t } = useTranslation("ai");
  const graph = useMemo(() => buildAgentTurnGraph(steps), [steps]);

  if (graph.nodes.length === 0) {
    return <p className="op-muted">{t("agent.trace.noSteps")}</p>;
  }

  return (
    <div className="ai-agent-turn-graph" role="list" aria-label={t("agent.trace.graphAria")}>
      {graph.nodes.map((node, index) => (
        <div key={node.id} className="ai-agent-turn-graph-item" role="listitem">
          <div className={`ai-agent-turn-graph-node ai-agent-turn-graph-node--${node.type}`}>
            <span className="ai-agent-turn-graph-step">{node.step}</span>
            <span className="ai-agent-turn-graph-label">{node.label}</span>
            {node.status && (
              <span className={`badge ${node.status === "OK" ? "ok" : node.status === "ERROR" ? "danger" : "hist"}`}>
                {node.status}
              </span>
            )}
          </div>
          {index < graph.nodes.length - 1 && (
            <span className="ai-agent-turn-graph-arrow" aria-hidden>
              →
            </span>
          )}
        </div>
      ))}
    </div>
  );
}
