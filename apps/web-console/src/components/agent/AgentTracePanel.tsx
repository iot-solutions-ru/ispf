import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchAgentSessionTrace } from "../../api/ai";
import type { AiAgentStep } from "../../api/ai";

function formatMs(value: unknown): string {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return "—";
  }
  return `${value} ms`;
}

function formatTokens(value: unknown): string {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return "—";
  }
  return String(value);
}

export default function AgentTracePanel({
  sessionId,
  turnId,
  steps,
}: {
  sessionId?: string | null;
  turnId?: string | null;
  steps: AiAgentStep[];
}) {
  const { t } = useTranslation("ai");
  const traceQuery = useQuery({
    queryKey: ["agent-trace", sessionId, turnId],
    queryFn: () => fetchAgentSessionTrace(sessionId!, turnId ?? undefined),
    enabled: Boolean(sessionId && turnId),
  });

  const traceSteps = (traceQuery.data?.steps as AiAgentStep[] | undefined) ?? steps;
  const totals = traceQuery.data?.totals as Record<string, unknown> | undefined;

  return (
    <div className="ai-agent-trace-panel">
      {totals && (
        <p className="ai-agent-trace-totals">
          {t("agent.trace.totals", {
            latency: formatMs(totals.latencyMs),
            prompt: formatTokens(totals.promptTokens),
            completion: formatTokens(totals.completionTokens),
          })}
        </p>
      )}
      <table className="ai-agent-trace-table">
        <thead>
          <tr>
            <th>{t("agent.trace.colStep")}</th>
            <th>{t("agent.trace.colType")}</th>
            <th>{t("agent.trace.colTool")}</th>
            <th>{t("agent.trace.colStatus")}</th>
            <th>{t("agent.trace.colLatency")}</th>
            <th>{t("agent.trace.colTokens")}</th>
          </tr>
        </thead>
        <tbody>
          {traceSteps.map((step) => {
            const traceStep = step as AiAgentStep & {
              latencyMs?: number;
              promptTokens?: number;
              completionTokens?: number;
              truncated?: boolean;
            };
            const status =
              traceStep.type === "tool"
                ? String(traceStep.result?.status ?? "")
                : traceStep.type === "error"
                  ? "ERROR"
                  : traceStep.type === "guard"
                    ? "GUARD"
                    : traceStep.type === "finish"
                      ? "OK"
                      : traceStep.type;
            const tokens =
              traceStep.promptTokens != null || traceStep.completionTokens != null
                ? `${formatTokens(traceStep.promptTokens)} / ${formatTokens(traceStep.completionTokens)}`
                : "—";
            return (
              <tr key={`${traceStep.step}-${traceStep.type}-${traceStep.tool ?? ""}`}>
                <td>{traceStep.step}</td>
                <td>{traceStep.type}</td>
                <td>
                  <code>{traceStep.tool ?? "—"}</code>
                  {traceStep.truncated && (
                    <span className="badge danger"> {t("agent.details.truncated")}</span>
                  )}
                </td>
                <td>{status || "—"}</td>
                <td>{formatMs(traceStep.latencyMs)}</td>
                <td>{tokens}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {(traceSteps.some((s) => s.type === "guard" && s.hint) ||
        traceSteps.some((s) => s.type === "guard" && s.error)) && (
        <ul className="ai-agent-trace-hints">
          {traceSteps
            .filter((s) => s.type === "guard")
            .map((s) => (
              <li key={`guard-${s.step}`}>
                <strong>{s.label ?? t("agent.details.guard")}</strong>
                {s.error && <p className="ai-agent-step-error">{s.error}</p>}
                {s.hint && <p className="hint">{s.hint}</p>}
              </li>
            ))}
        </ul>
      )}
    </div>
  );
}
