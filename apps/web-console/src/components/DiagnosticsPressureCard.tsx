import { Fragment, useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  fetchClusterDiagnostics,
  type ClusterDiagnosticsNode,
  type DiagnosticsSeverity,
  type DriverDiagnosticsRow,
} from "../api/clusterDiagnostics";

function cpuClass(percent: number): string {
  if (percent >= 80) return "diagnostics-cpu-critical";
  if (percent >= 50) return "diagnostics-cpu-warn";
  return "diagnostics-cpu-ok";
}

function severityClass(severity: DiagnosticsSeverity): string {
  if (severity === "critical") return "diagnostics-severity-critical";
  if (severity === "warning") return "diagnostics-severity-warning";
  return "diagnostics-severity-info";
}

function formatPercent(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return "—";
  return `${value.toLocaleString(undefined, { maximumFractionDigits: 1 })}%`;
}

function NodeDetailPanel({ node }: { node: ClusterDiagnosticsNode }) {
  const { t } = useTranslation("system");
  const detail = node.detail ?? {};
  const drivers = detail.drivers ?? [];
  const threadGroups = detail.threadGroups ?? [];
  const topThreads = detail.topThreads ?? [];
  const runningJobs = detail.runningJobs ?? [];
  const runningWorkflows = detail.runningWorkflows ?? [];

  return (
    <div className="diagnostics-node-detail">
      {node.suspects.length > 0 && (
        <section>
          <h4>{t("diagnostics.suspectsTitle")}</h4>
          <ul className="diagnostics-suspect-list">
            {node.suspects.map((suspect) => (
              <li key={`${node.replicaId}-${suspect.id}`} className={severityClass(suspect.severity)}>
                <span className="diagnostics-suspect-kind">{t(`diagnostics.kind.${suspect.kind}`)}</span>
                <strong>{suspect.title}</strong>
                <span className="op-muted">{suspect.detail}</span>
              </li>
            ))}
          </ul>
        </section>
      )}

      {threadGroups.length > 0 && (
        <section>
          <h4>{t("diagnostics.threadsTitle")}</h4>
          <table className="op-table diagnostics-detail-table">
            <thead>
              <tr>
                <th>{t("diagnostics.col.threadGroup")}</th>
                <th>{t("diagnostics.col.threadCount")}</th>
                <th>{t("diagnostics.col.cpuDelta")}</th>
              </tr>
            </thead>
            <tbody>
              {threadGroups.map((group) => (
                <tr key={group.prefix}>
                  <td className="mono">{group.prefix}</td>
                  <td>{group.threadCount}</td>
                  <td>{formatPercent(group.cpuPercentDelta)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {topThreads.length > 0 && (
            <table className="op-table diagnostics-detail-table">
              <thead>
                <tr>
                  <th>{t("diagnostics.col.threadName")}</th>
                  <th>{t("diagnostics.col.cpuDelta")}</th>
                </tr>
              </thead>
              <tbody>
                {topThreads.map((thread) => (
                  <tr key={thread.name}>
                    <td className="mono">{thread.name}</td>
                    <td>{formatPercent(thread.cpuPercentDelta)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>
      )}

      {drivers.length > 0 && (
        <section>
          <h4>{t("diagnostics.driversTitle")}</h4>
          <table className="op-table diagnostics-detail-table">
            <thead>
              <tr>
                <th>{t("diagnostics.col.devicePath")}</th>
                <th>{t("diagnostics.col.driverId")}</th>
                <th>{t("diagnostics.col.pollMs")}</th>
                <th>{t("diagnostics.col.ingressPending")}</th>
                <th>{t("diagnostics.col.pressure")}</th>
              </tr>
            </thead>
            <tbody>
              {drivers.map((driver: DriverDiagnosticsRow) => (
                <tr
                  key={driver.devicePath}
                  className={driver.pressureScore >= 100 ? "diagnostics-driver-hot" : undefined}
                >
                  <td className="mono">{driver.devicePath}</td>
                  <td>{driver.driverId}</td>
                  <td>{driver.pollIntervalMs}</td>
                  <td>{driver.ingressPending ?? "—"}</td>
                  <td>{driver.pressureScore}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {runningJobs.length > 0 && (
        <section>
          <h4>{t("diagnostics.jobsTitle")}</h4>
          <table className="op-table diagnostics-detail-table">
            <thead>
              <tr>
                <th>{t("diagnostics.col.jobType")}</th>
                <th>{t("diagnostics.col.runningSeconds")}</th>
              </tr>
            </thead>
            <tbody>
              {runningJobs.map((job) => (
                <tr key={String(job.jobId)}>
                  <td>{String(job.jobType ?? "—")}</td>
                  <td>{String(job.runningSeconds ?? "—")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {runningWorkflows.length > 0 && (
        <section>
          <h4>{t("diagnostics.workflowsTitle")}</h4>
          <table className="op-table diagnostics-detail-table">
            <thead>
              <tr>
                <th>{t("diagnostics.col.workflowPath")}</th>
                <th>{t("diagnostics.col.runningSeconds")}</th>
              </tr>
            </thead>
            <tbody>
              {runningWorkflows.map((workflow) => (
                <tr key={String(workflow.instanceId)}>
                  <td className="mono">{String(workflow.workflowPath ?? "—")}</td>
                  <td>{String(workflow.runningSeconds ?? "—")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}
    </div>
  );
}

export default function DiagnosticsPressureCard() {
  const { t } = useTranslation("system");
  const [expandedReplicaId, setExpandedReplicaId] = useState<string | null>(null);
  const diagnosticsQuery = useQuery({
    queryKey: ["cluster-diagnostics"],
    queryFn: fetchClusterDiagnostics,
    refetchInterval: 20_000,
  });

  const top = diagnosticsQuery.data?.clusterTopSuspect;

  return (
    <section className="system-metrics-card diagnostics-pressure-card">
      <div className="diagnostics-pressure-header">
        <div>
          <h3>{t("diagnostics.title")}</h3>
          <p className="op-muted">{t("diagnostics.subtitle")}</p>
        </div>
        <button
          type="button"
          className="btn"
          disabled={diagnosticsQuery.isFetching}
          onClick={() => diagnosticsQuery.refetch()}
        >
          {t("metrics.refresh")}
        </button>
      </div>

      {diagnosticsQuery.error && (
        <div className="op-alert op-alert-error">{String(diagnosticsQuery.error)}</div>
      )}

      {diagnosticsQuery.isLoading && <p className="hint">{t("diagnostics.loading")}</p>}

      {top && top.title && (
        <div className="diagnostics-top-suspect">
          <span className="diagnostics-top-label">{t("diagnostics.clusterTopSuspect")}</span>
          <strong>
            {top.replicaId ? `${top.replicaId}: ` : ""}
            {top.title}
          </strong>
          <span className="op-muted">{top.detail}</span>
        </div>
      )}

      {diagnosticsQuery.data && (
        <table className="op-table diagnostics-cluster-table">
          <thead>
            <tr>
              <th />
              <th>{t("diagnostics.col.replica")}</th>
              <th>{t("diagnostics.col.profile")}</th>
              <th>{t("diagnostics.col.status")}</th>
              <th>{t("diagnostics.col.cpu")}</th>
              <th>{t("diagnostics.col.heap")}</th>
              <th>{t("diagnostics.col.topSuspect")}</th>
            </tr>
          </thead>
          <tbody>
            {diagnosticsQuery.data.nodes.map((node) => {
              const expanded = expandedReplicaId === node.replicaId;
              return (
                <Fragment key={node.replicaId}>
                  <tr
                    className={node.self ? "diagnostics-node-self" : undefined}
                  >
                    <td>
                      <button
                        type="button"
                        className="btn btn-ghost diagnostics-expand-btn"
                        aria-expanded={expanded}
                        onClick={() =>
                          setExpandedReplicaId(expanded ? null : node.replicaId)
                        }
                      >
                        {expanded ? "−" : "+"}
                      </button>
                    </td>
                    <td className="mono">
                      {node.replicaId}
                      {node.self && (
                        <span className="diagnostics-self-badge" title={t("diagnostics.thisNode")}>
                          {" "}*
                        </span>
                      )}
                    </td>
                    <td>{node.replicaProfile ?? "—"}</td>
                    <td>
                      {!node.reachable ? (
                        <span className="diagnostics-severity-critical">{t("diagnostics.unreachable")}</span>
                      ) : (
                        node.status
                      )}
                    </td>
                    <td className={cpuClass(node.processCpuPercent)}>
                      {formatPercent(node.processCpuPercent)}
                    </td>
                    <td>{formatPercent(node.heapUsedPercent)}</td>
                    <td>{node.topSuspect?.title ?? "—"}</td>
                  </tr>
                  {expanded && (
                    <tr className="diagnostics-detail-row">
                      <td colSpan={7}>
                        {!node.reachable ? (
                          <p className="op-muted">{node.error ?? t("diagnostics.unreachable")}</p>
                        ) : (
                          <NodeDetailPanel node={node} />
                        )}
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
          </tbody>
        </table>
      )}
    </section>
  );
}
