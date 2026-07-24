import { Button } from "antd";
import { lazy, Suspense, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { AiAgentStep } from "../../api/ai";

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
  const { toolSteps, diagnosticSteps } = useMemo(() => ({
    toolSteps: steps.filter((step) => step.type === "tool"),
    diagnosticSteps: steps.filter((step) => step.type === "error" || step.type === "guard"),
  }), [steps]);
  const isRunning = status === "RUNNING";
  const detailCount = toolSteps.length + diagnosticSteps.length;

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
      {placeholder && toolSteps.length === 0 && diagnosticSteps.length === 0 && (
        <p className="op-muted ai-agent-step-placeholder">{t("agent.details.waiting")}</p>
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
          {diagnosticSteps.map((step) => (
            <li key={step.step} className="ai-agent-diagnostic-item">
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
      {detailsView === "list" && toolSteps.length > 0 && (
        <ol className="ai-agent-step-list">
          {toolSteps.map((step) => (
            <li key={step.step}>
              <code>{step.tool}</code>
              {" "}
              {step.label || ""}
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
          ))}
        </ol>
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
