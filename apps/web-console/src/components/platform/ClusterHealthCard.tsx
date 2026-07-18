import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  fetchClusterHealth,
  type ClusterHealth,
  type ClusterNode,
  type ClusterNodeStatus,
} from "../../api/clusterHealth";
import { formatUserDateTime } from "../../utils/ui/formatDateTime";

const KNOWN_PROFILES = ["unified", "edge-api", "hmi-read", "io", "compute", "all", "api", "worker"] as const;

function statusClass(status: ClusterNodeStatus): string {
  if (status === "UP") return "system-health-ok";
  if (status === "STALE") return "system-health-warn";
  return "system-health-bad";
}

function profileClass(profile: string | null | undefined): string {
  if (profile === "edge-api" || profile === "api") return "cluster-role-badge cluster-role-api";
  if (profile === "compute" || profile === "worker") return "cluster-role-badge cluster-role-worker";
  if (profile === "unified" || profile === "all") return "cluster-role-badge cluster-role-all";
  if (profile === "io") return "cluster-role-badge cluster-role-io";
  if (profile === "hmi-read") return "cluster-role-badge cluster-role-hmi-read";
  return "cluster-role-badge cluster-role-unknown";
}

function formatInstant(value: string | null): string {
  if (!value) return "—";
  return formatUserDateTime(value);
}

function ProfileBadge({
  profile,
  t,
}: {
  profile: string | null | undefined;
  t: (key: string) => string;
}) {
  const normalized = profile ?? "unknown";
  const labelKey = `clusterHealth.profile.${normalized}`;
  const label = KNOWN_PROFILES.includes(normalized as (typeof KNOWN_PROFILES)[number])
    ? t(labelKey)
    : profile ?? "—";
  return <span className={profileClass(profile)}>{label}</span>;
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
        <ProfileBadge profile={node.replicaProfile ?? node.replicaRole} t={t} />
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

export default function ClusterHealthPanel({ showTitle = true }: { showTitle?: boolean }) {
  const { t } = useTranslation(["system", "common"]);
  const healthQuery = useQuery({
    queryKey: ["cluster-health"],
    queryFn: fetchClusterHealth,
    refetchInterval: 15_000,
  });

  const data = healthQuery.data;

  return (
    <section className="system-metrics-card cluster-health-card system-metrics-card-wide">
      {showTitle && <h3>{t("clusterHealth.title")}</h3>}
      {healthQuery.isLoading && <p className="hint">{t("clusterHealth.loading")}</p>}
      {healthQuery.error && (
        <div className="op-alert op-alert-error">{t("clusterHealth.loadError")}</div>
      )}
      {data && <ClusterHealthContent data={data} t={t} />}
    </section>
  );
}

function ClusterHealthContent({
  data,
  t,
}: {
  data: ClusterHealth;
  t: (key: string, options?: Record<string, unknown>) => string;
}) {
  return (
    <>
      <table className="op-table system-metrics-table">
        <tbody>
          <tr>
            <th>{t("clusterHealth.clusterEnabled")}</th>
            <td>{data.clusterEnabled ? t("common:action.yes") : t("common:action.no")}</td>
          </tr>
          <tr>
            <th>{t("clusterHealth.thisReplica")}</th>
            <td className="mono">{data.replicaId}</td>
          </tr>
          <tr>
            <th>{t("clusterHealth.thisInstanceRole")}</th>
            <td>
              <ProfileBadge profile={data.replicaProfile ?? data.replicaRole} t={t} />
            </td>
          </tr>
          {data.replicaCapabilities?.length > 0 && (
            <tr>
              <th>{t("clusterHealth.capabilities")}</th>
              <td className="mono cluster-capabilities-list">
                {data.replicaCapabilities.join(", ")}
              </td>
            </tr>
          )}
          <tr>
            <th>{t("clusterHealth.jobConsumer")}</th>
            <td>
              {data.jobConsumerActive ? t("common:action.yes") : t("common:action.no")}
            </td>
          </tr>
          <tr>
            <th>{t("clusterHealth.driverOwnership")}</th>
            <td>
              {data.driverOwnershipEnabled ? t("common:action.yes") : t("common:action.no")}
            </td>
          </tr>
          <tr>
            <th>{t("clusterHealth.localDriverLocks")}</th>
            <td>{data.heldDriverLocks}</td>
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
            <th>{t("clusterHealth.liveVariableSync")}</th>
            <td>
              {data.liveVariableSyncEnabled ? t("common:action.yes") : t("common:action.no")}
            </td>
          </tr>
          <tr>
            <th>{t("clusterHealth.liveVariableSyncCoalesce")}</th>
            <td>{t("clusterHealth.coalesceMs", { count: data.liveVariableSyncCoalesceMs })}</td>
          </tr>
          <tr>
            <th>{t("clusterHealth.clusterPathInterest")}</th>
            <td>
              {data.clusterPathInterestEnabled ? t("common:action.yes") : t("common:action.no")}
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
                <th>{t("clusterHealth.col.role")}</th>
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
  );
}

export { ClusterHealthContent };
