import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Space, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
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

function statusColor(status: ClusterNodeStatus): "success" | "warning" | "error" {
  if (status === "UP") return "success";
  if (status === "STALE") return "warning";
  return "error";
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
  return <Tag className={profileClass(profile)}>{label}</Tag>;
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
      {showTitle && <Typography.Title level={3}>{t("clusterHealth.title")}</Typography.Title>}
      {healthQuery.isLoading && <Typography.Text type="secondary">{t("clusterHealth.loading")}</Typography.Text>}
      {healthQuery.error && (
        <Alert type="error" showIcon message={t("clusterHealth.loadError")} />
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
  const summaryRows = [
    {
      key: "clusterEnabled",
      label: t("clusterHealth.clusterEnabled"),
      value: data.clusterEnabled ? t("common:action.yes") : t("common:action.no"),
    },
    {
      key: "thisReplica",
      label: t("clusterHealth.thisReplica"),
      value: <Typography.Text code>{data.replicaId}</Typography.Text>,
    },
    {
      key: "thisInstanceRole",
      label: t("clusterHealth.thisInstanceRole"),
      value: <ProfileBadge profile={data.replicaProfile ?? data.replicaRole} t={t} />,
    },
    ...(data.replicaCapabilities?.length > 0
      ? [{
          key: "capabilities",
          label: t("clusterHealth.capabilities"),
          value: <Typography.Text code>{data.replicaCapabilities.join(", ")}</Typography.Text>,
        }]
      : []),
    {
      key: "jobConsumer",
      label: t("clusterHealth.jobConsumer"),
      value: data.jobConsumerActive ? t("common:action.yes") : t("common:action.no"),
    },
    {
      key: "driverOwnership",
      label: t("clusterHealth.driverOwnership"),
      value: data.driverOwnershipEnabled ? t("common:action.yes") : t("common:action.no"),
    },
    { key: "localDriverLocks", label: t("clusterHealth.localDriverLocks"), value: data.heldDriverLocks },
    {
      key: "nodesSummary",
      label: t("clusterHealth.nodesSummary"),
      value: (
        <Tag color={data.nodesUp === data.nodesTotal ? "success" : "warning"}>
          {t("clusterHealth.nodesUpOfTotal", { up: data.nodesUp, total: data.nodesTotal })}
        </Tag>
      ),
    },
    {
      key: "natsReplicaEvents",
      label: t("clusterHealth.natsReplicaEvents"),
      value: data.natsEnabled && data.natsReplicaEventsEnabled ? t("common:action.yes") : t("common:action.no"),
    },
    {
      key: "liveVariableSync",
      label: t("clusterHealth.liveVariableSync"),
      value: data.liveVariableSyncEnabled ? t("common:action.yes") : t("common:action.no"),
    },
    {
      key: "liveVariableSyncCoalesce",
      label: t("clusterHealth.liveVariableSyncCoalesce"),
      value: t("clusterHealth.coalesceMs", { count: data.liveVariableSyncCoalesceMs }),
    },
    {
      key: "clusterPathInterest",
      label: t("clusterHealth.clusterPathInterest"),
      value: data.clusterPathInterestEnabled ? t("common:action.yes") : t("common:action.no"),
    },
    {
      key: "driverLockTtl",
      label: t("clusterHealth.driverLockTtl"),
      value: t("clusterHealth.ttlSeconds", { count: data.driverLockTtlSeconds }),
    },
  ];
  const summaryColumns: TableColumnsType<(typeof summaryRows)[number]> = [
    { title: "", dataIndex: "label", key: "label" },
    { title: "", dataIndex: "value", key: "value" },
  ];
  const nodeColumns: TableColumnsType<ClusterNode> = [
    {
      title: t("clusterHealth.col.replicaId"),
      dataIndex: "replicaId",
      key: "replicaId",
      render: (replicaId: string, node) => (
        <Typography.Text code>
          {replicaId}
          {node.self && (
            <span className="cluster-node-self-badge" title={t("clusterHealth.thisNode")}>
              {" "}*
            </span>
          )}
        </Typography.Text>
      ),
    },
    {
      title: t("clusterHealth.col.role"),
      key: "role",
      render: (_, node) => <ProfileBadge profile={node.replicaProfile ?? node.replicaRole} t={t} />,
    },
    {
      title: t("clusterHealth.col.status"),
      dataIndex: "status",
      key: "status",
      render: (status: ClusterNodeStatus) => (
        <Tag className={statusClass(status)} color={statusColor(status)}>
          {t(`clusterHealth.status.${status}`)}
        </Tag>
      ),
    },
    {
      title: t("clusterHealth.col.version"),
      dataIndex: "version",
      key: "version",
      render: (version: string | null) => <Typography.Text code>{version ?? "—"}</Typography.Text>,
    },
    {
      title: t("clusterHealth.col.environment"),
      dataIndex: "environment",
      key: "environment",
      render: (environment: string | null) => environment ?? "—",
    },
    { title: t("clusterHealth.col.driverLocks"), dataIndex: "heldDriverLocks", key: "heldDriverLocks" },
    {
      title: t("clusterHealth.col.lastHeartbeat"),
      dataIndex: "lastHeartbeatAt",
      key: "lastHeartbeatAt",
      render: (value: string | null) => formatInstant(value),
    },
  ];

  return (
    <Space orientation="vertical" style={{ width: "100%" }}>
      <Table
        className="system-metrics-table"
        size="small"
        pagination={false}
        showHeader={false}
        columns={summaryColumns}
        dataSource={summaryRows}
      />

      <Typography.Title level={4} className="cluster-health-nodes-title">
        {t("clusterHealth.nodesTitle")}
      </Typography.Title>
      {data.nodes.length === 0 ? (
        <Typography.Text type="secondary">{t("clusterHealth.nodesEmpty")}</Typography.Text>
      ) : (
        <div className="cluster-health-nodes-wrap">
          <Table
            className="cluster-health-nodes-table"
            size="small"
            pagination={false}
            rowKey="replicaId"
            rowClassName={(node) => node.self ? "cluster-node-self" : ""}
            columns={nodeColumns}
            dataSource={data.nodes}
          />
        </div>
      )}

      <Typography.Paragraph type="secondary">{t("clusterHealth.hint")}</Typography.Paragraph>
    </Space>
  );
}

export { ClusterHealthContent };
