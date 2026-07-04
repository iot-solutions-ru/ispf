import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchClusterHealth, type ClusterNode, type ClusterNodeStatus } from "../api/clusterHealth";

function statusClass(status: ClusterNodeStatus): string {
  if (status === "UP") return "system-health-ok";
  if (status === "STALE") return "system-health-warn";
  return "system-health-bad";
}

function formatInstant(value: string | null): string {
  if (!value) return "—";
  return new Date(value).toLocaleString();
}

function NodeRow({ node, t }: { node: ClusterNode; t: (key: string) => string }) {
  return (
    <tr className={node.self ? "cluster-node-self" : undefined}>
      <td className="mono">
        {node.replicaId}
        {node.self && (
          <span className="cluster-node-self-badge" title={t("clusterHealth.thisNode")}>
            {" "}
            *
          </span>
        )}
      </td>
      <td>
        <span className={statusClass(node.status)}>
          {t(`clusterHealth.status.${node.status}`)}
        </span>
      </td>
      <td className="mono">{node.version ?? "—"}</td>
      <td>{node.environment ?? "—"}</td>
      <td>{node.heldDriverLocks}</td>
      <td>{formatInstant(node.lastHeartbeatAt)}</td>
    </tr>
  );
}

export default function ClusterHealthCard() {
  const { t } = useTranslation(["system", "common"]);
  const healthQuery = useQuery({
    queryKey: ["cluster-health"],
    queryFn: fetchClusterHealth,
    refetchInterval: 15_000,
  });

  const data = healthQuery.data;

  return (
    <section className="system-metrics-card cluster-health-card system-metrics-card-wide">
      <h3>{t("clusterHealth.title")}</h3>
      {healthQuery.isLoading && <p className="hint">{t("clusterHealth.loading")}</p>}
      {healthQuery.error && (
        <div className="op-alert op-alert-error">{t("clusterHealth.loadError")}</div>
      )}
      {data && (
        <>
          <table className="op-table system-metrics-table">
            <tbody>
              <tr>
                <th>{t("clusterHealth.clusterEnabled")}</th>
                <td>{data.clusterEnabled ? t("common:action.yes") : t("common:action.no")}</td>
              </tr>
              <tr>
                <th>{t("clusterHealth.driverOwnership")}</th>
                <td>
                  {data.driverOwnershipEnabled ? t("common:action.yes") : t("common:action.no")}
                </td>
              </tr>
              <tr>
                <th>{t("clusterHealth.thisReplica")}</th>
                <td className="mono">{data.replicaId}</td>
              </tr>
              <tr>
                <th>{t("clusterHealth.nodesSummary")}</th>
                <td>
                  <span className={data.nodesUp === data.nodesTotal ? "system-health-ok" : "system-health-warn"}>
                    {t("clusterHealth.nodesUpOfTotal", {
                      up: data.nodesUp,
                      total: data.nodesTotal,
                    })}
                  </span>
                </td>
              </tr>
              <tr>
                <th>{t("clusterHealth.natsReplicaEvents")}</th>
                <td>
                  {data.natsEnabled && data.natsReplicaEventsEnabled
                    ? t("common:action.yes")
                    : t("common:action.no")}
                </td>
              </tr>
              <tr>
                <th>{t("clusterHealth.driverLockTtl")}</th>
                <td>{t("clusterHealth.ttlSeconds", { count: data.driverLockTtlSeconds })}</td>
              </tr>
            </tbody>
          </table>

          <h4 className="cluster-health-nodes-title">{t("clusterHealth.nodesTitle")}</h4>
          {data.nodes.length === 0 ? (
            <p className="hint">{t("clusterHealth.nodesEmpty")}</p>
          ) : (
            <div className="cluster-health-nodes-wrap">
              <table className="op-table cluster-health-nodes-table">
                <thead>
                  <tr>
                    <th>{t("clusterHealth.col.replicaId")}</th>
                    <th>{t("clusterHealth.col.status")}</th>
                    <th>{t("clusterHealth.col.version")}</th>
                    <th>{t("clusterHealth.col.environment")}</th>
                    <th>{t("clusterHealth.col.driverLocks")}</th>
                    <th>{t("clusterHealth.col.lastHeartbeat")}</th>
                  </tr>
                </thead>
                <tbody>
                  {data.nodes.map((node) => (
                    <NodeRow key={node.replicaId} node={node} t={t} />
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <p className="hint">{t("clusterHealth.hint")}</p>
        </>
      )}
    </section>
  );
}
