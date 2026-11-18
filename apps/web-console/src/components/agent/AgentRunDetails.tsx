import { Button } from "antd";
import { lazy, Suspense, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { AiAgentStep } from "../../api/ai";
import { collapseConsecutiveToolSteps } from "../../utils/agent/collapseConsecutiveToolSteps";

const AgentTurnGraph = lazy(() => import("./AgentTurnGraph"));
const AgentTracePanel = lazy(() => import("./AgentTracePanel"));

function stepStatusBadge(status: string | undefined): React.ReactNode {
  if (!status) {
    return null;
  }
  const ok = status === "OK" || status === "SUCCESS";
  const running = status === "RUNNING";
  const cancelled = status === "CANCELLED" || status === "STOPPED";
  const badgeClass = ok ? "ok" : running || cancelled ? "hist" : "danger";
  const label = ok ? "OK" : running ? "…" : status;
  return <span className={`badge ${badgeClass}`}>{label}</span>;
}

function dashboardLink(path: string | undefined): string | null {
  if (!path) {
    return null;
  }
  return `/?path=${encodeURIComponent(path)}`;
}

type TimelineRow =
  | { kind: "tool"; key: string; step: AiAgentStep; count: number }
  | { kind: "llm"; key: string; step: AiAgentStep }
  | { kind: "status"; key: string; step: AiAgentStep };

/**
 * Chronological tool + LLM dialogue for the live “current steps” panel.
 * Consecutive identical OK tools stay collapsed; LLM rounds stay expanded with reply text.
 */
function buildTimeline(steps: AiAgentStep[], isRunning: boolean): TimelineRow[] {
  const rows: TimelineRow[] = [];
  let i = 0;
  while (i < steps.length) {
    const step = steps[i];
    if (step.type === "tool") {
      const run: AiAgentStep[] = [];
      let j = i;
      while (j < steps.length && steps[j].type === "tool") {
        run.push(steps[j]);
        j += 1;
      }
      for (const collapsed of collapseConsecutiveToolSteps(run)) {
        rows.push({
          kind: "tool",
          key: collapsed.key,
          step: collapsed.step,
          count: collapsed.count,
        });
      }
      i = j;
      continue;
    }
    if (step.type === "llm") {
      rows.push({ kind: "llm", key: `llm-${step.step}-${i}`, step });
      i += 1;
      continue;
    }
    if (step.type === "status" && isRunning) {
      // Keep only the latest waiting/status cue at the end (dedupe below).
      rows.push({ kind: "status", key: `status-${step.step}-${i}`, step });
      i += 1;
      continue;
    }
    i += 1;
  }
  if (!isRunning) {
    return rows.filter((row) => row.kind !== "status");
  }
  // Keep a waiting cue only when it is the trailing live row (not above completed LLM/tool rows).
  const lastStatusIdx = rows.reduce((acc, row, idx) => (row.kind === "status" ? idx : acc), -1);
  if (lastStatusIdx < 0 || lastStatusIdx !== rows.length - 1) {
    return rows.filter((row) => row.kind !== "status");
  }
  return rows.filter((row, idx) => row.kind !== "status" || idx === lastStatusIdx);
}

export function AgentRunDetails({
  steps,
  status,
  result,
  open = false,
  placeholder = false,
  sessionId,
  turnId,
}: {
  steps: AiAgentStep[];
  status?: string;
  result?: Record<string, unknown>;
  open?: boolean;
  placeholder?: boolean;
  sessionId?: string | null;
  turnId?: string | null;
}) {
  const { t } = useTranslation("ai");
  const [detailsView, setDetailsView] = useState<"list" | "graph" | "trace">("list");
  const devicePath = typeof result?.devicePath === "string" ? result.devicePath : undefined;
  const dashboardPath =
    typeof result?.dashboardPath === "string" ? result.dashboardPath : undefined;
  const isRunning = status === "RUNNING";
  const { diagnosticSteps, statusSteps } = useMemo(() => ({
    diagnosticSteps: steps.filter((step) => step.type === "error" || step.type === "guard"),
    statusSteps: steps.filter((step) => step.type === "status"),
  }), [steps]);
  const timeline = useMemo(() => buildTimeline(steps, isRunning), [steps, isRunning]);
  const detailCount = timeline.filter((row) => row.kind === "tool" || row.kind === "llm").length
    + diagnosticSteps.length;
  const latestStatusLabel = statusSteps.length > 0
    ? statusSteps[statusSteps.length - 1]?.label
    : undefined;
  const hasVisibleProgress = timeline.length > 0 || diagnosticSteps.length > 0;

  return (
    <details className="ai-agent-run-details" open={open || undefined}>
      <summary>
        {isRunning
          ? t("agent.details.running")
          : status === "OK"
            ? t("agent.details.ok")
            : t("agent.details.error")}
        {detailCount > 0 ? ` (${detailCount})` : ""}
      </summary>
      {placeholder && !hasVisibleProgress && (
        <p className="op-muted ai-agent-step-placeholder">
          {latestStatusLabel || t("agent.details.waiting")}
        </p>
      )}
      {steps.length > 0 && !isRunning && (
        <div className="ai-agent-details-tabs" role="tablist" aria-label={t("agent.trace.viewAria")}>
          <button
            type="button"
            role="tab"
            className={detailsView === "list" ? "active" : ""}
            aria-selected={detailsView === "list"}
            onClick={() => setDetailsView("list")}
          >
            {t("agent.trace.tabList")}
          </button>
          <button
            type="button"
            role="tab"
            className={detailsView === "graph" ? "active" : ""}
            aria-selected={detailsView === "graph"}
            onClick={() => setDetailsView("graph")}
          >
            {t("agent.trace.tabGraph")}
          </button>
          <button
            type="button"
            role="tab"
            className={detailsView === "trace" ? "active" : ""}
            aria-selected={detailsView === "trace"}
            onClick={() => setDetailsView("trace")}
          >
            {t("agent.trace.tabTrace")}
          </button>
        </div>
      )}
      {detailsView === "graph" && steps.length > 0 && (
        <Suspense fallback={<p className="op-muted">{t("agent.loadingChat")}</p>}>
          <AgentTurnGraph steps={steps} />
        </Suspense>
      )}
      {detailsView === "trace" && steps.length > 0 && (
        <Suspense fallback={<p className="op-muted">{t("agent.loadingChat")}</p>}>
          <AgentTracePanel sessionId={sessionId} turnId={turnId} steps={steps} />
        </Suspense>
      )}
      {detailsView === "list" && diagnosticSteps.length > 0 && (
        <ul className="ai-agent-diagnostic-list">
          {diagnosticSteps.map((step, index) => (
            <li key={`diag-${step.step}-${index}`} className="ai-agent-diagnostic-item">
              <strong>
                {step.label
                  ?? (step.type === "guard" ? t("agent.details.guard") : t("agent.details.parseError"))}
              </strong>
              {step.truncated && (
                <span className="badge danger"> {t("agent.details.truncated")}</span>
              )}
              {step.error && step.type !== "guard" && (
                <p className="ai-agent-step-error">{step.error}</p>
              )}
              {step.hint && step.type !== "guard" && <p className="hint">{step.hint}</p>}
              {step.rawPreview && (
                <details className="ai-agent-raw-preview">
                  <summary>{t("agent.details.rawPreview")}</summary>
                  <pre>{step.rawPreview}</pre>
                </details>
              )}
            </li>
          ))}
        </ul>
      )}
      {detailsView === "list" && timeline.some((row) => row.kind !== "status") && (
        <ol className="ai-agent-step-list">
          {timeline.filter((row) => row.kind !== "status").map((row) => {
            if (row.kind === "llm") {
              const step = row.step;
              const latency =
                typeof step.latencyMs === "number" && step.latencyMs > 0
                  ? ` · ${(step.latencyMs / 1000).toFixed(1)}s`
                  : "";
              return (
                <li key={row.key} className="ai-agent-step-llm">
                  <strong>{step.label || t("agent.details.llmRound")}</strong>
                  {latency ? <span className="op-muted">{latency}</span> : null}
                  {" "}
                  {stepStatusBadge(
                    step.result?.status === "ERROR"
                      ? "ERROR"
                      : step.result?.status === "OK"
                        ? "OK"
                        : isRunning
                          ? "RUNNING"
                          : undefined
                  )}
                  {step.error && (
                    <p className="ai-agent-step-error">{step.error}</p>
                  )}
                  {step.rawPreview ? (
                    <pre className="ai-agent-llm-preview">{step.rawPreview}</pre>
                  ) : null}
                </li>
              );
            }
            const step = row.step;
            return (
              <li key={row.key}>
                <code>{step.tool}</code>
                {" "}
                {step.label || ""}
                {row.count > 1 ? (
                  <span className="badge hist" title={t("agent.details.collapsedCount", { count: row.count })}>
                    {" "}
                    ×{row.count}
                  </span>
                ) : null}
                {" "}
                {stepStatusBadge(
                  step.result?.status === "ERROR"
                    ? "ERROR"
                    : step.result?.status === "OK"
                      ? "OK"
                      : isRunning
                        ? "RUNNING"
                        : undefined
                )}
                {step.result?.status === "ERROR" && (
                  <span className="ai-agent-step-error">
                    {" "}
                    — {String(step.result.error ?? t("agent.stepError"))}
                  </span>
                )}
              </li>
            );
          })}
        </ol>
      )}
      {detailsView === "list" && timeline.some((row) => row.kind === "status") && (
        <p className="op-muted ai-agent-step-placeholder">
          {timeline.filter((row) => row.kind === "status").at(-1)?.step.label
            || t("agent.details.waiting")}
          {" "}
          {stepStatusBadge("RUNNING")}
        </p>
      )}
      {!isRunning && (
        <div className="ai-agent-run-links">
          {devicePath && (
            <Button size="small" href={dashboardLink(devicePath) ?? "#"}>
              {t("agent.openDevice")}
            </Button>
          )}
          {dashboardPath && (
            <Button size="small" href={dashboardLink(dashboardPath) ?? "#"}>
              {t("agent.openDashboard")}
            </Button>
          )}
        </div>
      )}
    </details>
  );
}
