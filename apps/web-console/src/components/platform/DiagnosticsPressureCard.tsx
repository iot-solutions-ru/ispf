import { useEffect, useState } from "react";
import { useQuery, useMutation } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Button, Select, Space, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import {
  fetchClusterDiagnostics,
  fetchDiagnosticsMetricsProbe,
  setDiagnosticsMetricsProbe,
  type ClusterDiagnosticsNode,
  type DiagnosticsSeverity,
  type DriverDiagnosticsRow,
} from "../../api/clusterDiagnostics";

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

function severityColor(severity: DiagnosticsSeverity): "error" | "warning" | "processing" {
  if (severity === "critical") return "error";
  if (severity === "warning") return "warning";
  return "processing";
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
  const threadGroupColumns: TableColumnsType<(typeof threadGroups)[number]> = [
    {
      title: t("diagnostics.col.threadGroup"),
      dataIndex: "prefix",
      key: "prefix",
      render: (prefix: string) => <Typography.Text code>{prefix}</Typography.Text>,
    },
    { title: t("diagnostics.col.threadCount"), dataIndex: "threadCount", key: "threadCount" },
    {
      title: t("diagnostics.col.cpuDelta"),
      dataIndex: "cpuPercentDelta",
      key: "cpuPercentDelta",
      render: (value: number | null | undefined) => formatPercent(value),
    },
  ];
  const topThreadColumns: TableColumnsType<(typeof topThreads)[number]> = [
    {
      title: t("diagnostics.col.threadName"),
      dataIndex: "name",
      key: "name",
      render: (name: string) => <Typography.Text code>{name}</Typography.Text>,
    },
    {
      title: t("diagnostics.col.cpuDelta"),
      dataIndex: "cpuPercentDelta",
      key: "cpuPercentDelta",
      render: (value: number | null | undefined) => formatPercent(value),
    },
  ];
  const driverColumns: TableColumnsType<DriverDiagnosticsRow> = [
    {
      title: t("diagnostics.col.devicePath"),
      dataIndex: "devicePath",
      key: "devicePath",
      render: (devicePath: string) => <Typography.Text code>{devicePath}</Typography.Text>,
    },
    { title: t("diagnostics.col.driverId"), dataIndex: "driverId", key: "driverId" },
    { title: t("diagnostics.col.pollMs"), dataIndex: "pollIntervalMs", key: "pollIntervalMs" },
    {
      title: t("diagnostics.col.pointCount"),
      dataIndex: "pointMappingCount",
      key: "pointMappingCount",
      render: (value: number | null | undefined) => value ?? "—",
    },
    {
      title: t("diagnostics.col.ingressPending"),
      dataIndex: "ingressPending",
      key: "ingressPending",
      render: (value: number | null | undefined) => value ?? "—",
    },
    {
      title: t("diagnostics.col.pressure"),
      dataIndex: "pressureScore",
      key: "pressureScore",
      render: (value: number, driver) => <span title={driver.lastError ?? undefined}>{value}</span>,
    },
  ];
  const jobColumns: TableColumnsType<(typeof runningJobs)[number]> = [
    { title: t("diagnostics.col.jobType"), dataIndex: "jobType", key: "jobType", render: (value) => String(value ?? "—") },
    {
      title: t("diagnostics.col.runningSeconds"),
      dataIndex: "runningSeconds",
      key: "runningSeconds",
      render: (value) => String(value ?? "—"),
    },
  ];
  const workflowColumns: TableColumnsType<(typeof runningWorkflows)[number]> = [
    {
      title: t("diagnostics.col.workflowPath"),
      dataIndex: "workflowPath",
      key: "workflowPath",
      render: (value) => <Typography.Text code>{String(value ?? "—")}</Typography.Text>,
    },
    {
      title: t("diagnostics.col.runningSeconds"),
      dataIndex: "runningSeconds",
      key: "runningSeconds",
      render: (value) => String(value ?? "—"),
    },
  ];

  return (
    <div className="diagnostics-node-detail">
      {node.suspects.length > 0 && (
        <section>
          <Typography.Title level={4}>{t("diagnostics.suspectsTitle")}</Typography.Title>
          <ul className="diagnostics-suspect-list">
            {node.suspects.map((suspect) => (
              <li key={`${node.replicaId}-${suspect.id}`} className={severityClass(suspect.severity)}>
                <Tag color={severityColor(suspect.severity)}>{t(`diagnostics.kind.${suspect.kind}`)}</Tag>
                <Typography.Text strong>{suspect.title}</Typography.Text>
                <Typography.Text type="secondary">{suspect.detail}</Typography.Text>
              </li>
            ))}
          </ul>
        </section>
      )}

      {threadGroups.length > 0 && (
        <section>
          <Typography.Title level={4}>{t("diagnostics.threadsTitle")}</Typography.Title>
          {detail.threadSampleReady === false && (
            <Typography.Paragraph type="secondary" className="diagnostics-thread-hint">
              {t("diagnostics.threadSampleWarmup")}
            </Typography.Paragraph>
          )}
          {detail.threadSampleReady !== false && detail.threadCpuAttributedPercent != null && (
            <Typography.Paragraph type="secondary" className="diagnostics-thread-hint">
              {t("diagnostics.threadCpuAttributed", {
                attributed: formatPercent(detail.threadCpuAttributedPercent),
                process: formatPercent(node.processCpuPercent),
                window: detail.threadSampleWindowSeconds ?? "—",
              })}
            </Typography.Paragraph>
          )}
          <Table
            className="diagnostics-detail-table"
            size="small"
            pagination={false}
            rowKey="prefix"
            columns={threadGroupColumns}
            dataSource={threadGroups}
          />
          {topThreads.length > 0 && (
            <Table
              className="diagnostics-detail-table"
              size="small"
              pagination={false}
              rowKey="name"
              columns={topThreadColumns}
              dataSource={topThreads}
            />
          )}
        </section>
      )}

      {drivers.length > 0 && (
        <section>
          <Typography.Title level={4}>{t("diagnostics.driversTitle")}</Typography.Title>
          <Table
            className="diagnostics-detail-table"
            size="small"
            pagination={false}
            rowKey="devicePath"
            rowClassName={(driver) => driver.pressureScore >= 100 ? "diagnostics-driver-hot" : ""}
            columns={driverColumns}
            dataSource={drivers}
          />
        </section>
      )}

      {runningJobs.length > 0 && (
        <section>
          <Typography.Title level={4}>{t("diagnostics.jobsTitle")}</Typography.Title>
          <Table
            className="diagnostics-detail-table"
            size="small"
            pagination={false}
            rowKey={(job) => String(job.jobId)}
            columns={jobColumns}
            dataSource={runningJobs}
          />
        </section>
      )}

      {runningWorkflows.length > 0 && (
        <section>
          <Typography.Title level={4}>{t("diagnostics.workflowsTitle")}</Typography.Title>
          <Table
            className="diagnostics-detail-table"
            size="small"
            pagination={false}
            rowKey={(workflow) => String(workflow.instanceId)}
            columns={workflowColumns}
            dataSource={runningWorkflows}
          />
        </section>
      )}
    </div>
  );
}

export default function DiagnosticsPressureCard() {
  const { t } = useTranslation("system");
  const [expandedReplicaId, setExpandedReplicaId] = useState<string | null>(null);
  const [probeToggle, setProbeToggle] = useState(false);

  const probeStatusQuery = useQuery({
    queryKey: ["diagnostics-metrics-probe"],
    queryFn: fetchDiagnosticsMetricsProbe,
  });

  const probeMutation = useMutation({
    mutationFn: setDiagnosticsMetricsProbe,
    onSuccess: (data) => setProbeToggle(data.enabled),
    onError: () => setProbeToggle(false),
  });

  useEffect(() => {
    if (probeStatusQuery.data != null) {
      setProbeToggle(probeStatusQuery.data.enabled);
    }
  }, [probeStatusQuery.data]);

  useEffect(() => {
    return () => {
      void setDiagnosticsMetricsProbe(false).catch(() => undefined);
    };
  }, []);

  const diagnosticsQuery = useQuery({
    queryKey: ["cluster-diagnostics"],
    queryFn: fetchClusterDiagnostics,
    refetchInterval: 20_000,
  });

  const top = diagnosticsQuery.data?.clusterTopSuspect;

  const handleProbeToggle = (enabled: boolean) => {
    setProbeToggle(enabled);
    probeMutation.mutate(enabled);
  };
  const nodeColumns: TableColumnsType<ClusterDiagnosticsNode> = [
    {
      title: t("diagnostics.col.replica"),
      dataIndex: "replicaId",
      key: "replicaId",
      render: (replicaId: string, node) => (
        <Typography.Text code>
          {replicaId}
          {node.self && (
            <span className="diagnostics-self-badge" title={t("diagnostics.thisNode")}>
              {" "}*
            </span>
          )}
        </Typography.Text>
      ),
    },
    {
      title: t("diagnostics.col.profile"),
      dataIndex: "replicaProfile",
      key: "replicaProfile",
      render: (value: string | null | undefined) => value ?? "—",
    },
    {
      title: t("diagnostics.col.status"),
      key: "status",
      render: (_, node) =>
        !node.reachable ? (
          <Tag color="error">{t("diagnostics.unreachable")}</Tag>
        ) : (
          node.status
        ),
    },
    {
      title: t("diagnostics.col.cpu"),
      dataIndex: "processCpuPercent",
      key: "processCpuPercent",
      render: (value: number) => <span className={cpuClass(value)}>{formatPercent(value)}</span>,
    },
    {
      title: t("diagnostics.col.heap"),
      dataIndex: "heapUsedPercent",
      key: "heapUsedPercent",
      render: (value: number | null | undefined) => formatPercent(value),
    },
    {
      title: t("diagnostics.col.topSuspect"),
      key: "topSuspect",
      render: (_, node) => node.topSuspect?.title ?? "—",
    },
  ];

  return (
    <section className="system-metrics-card diagnostics-pressure-card">
      <div className="diagnostics-pressure-header">
        <div>
          <Typography.Title level={3}>{t("diagnostics.title")}</Typography.Title>
          <Typography.Paragraph type="secondary">{t("diagnostics.subtitle")}</Typography.Paragraph>
        </div>
        <Space className="diagnostics-pressure-actions">
          <Select
            className="diagnostics-probe-toggle"
            value={probeToggle ? "true" : "false"}
            disabled={probeMutation.isPending}
            onChange={(value) => handleProbeToggle(value === "true")}
            options={[
              { value: "true", label: t("diagnostics.metricsProbeToggle") },
              { value: "false", label: t("diagnostics.metricsProbeToggle") },
            ]}
          />
          <Button
            disabled={diagnosticsQuery.isFetching}
            onClick={() => diagnosticsQuery.refetch()}
          >
            {t("metrics.refresh")}
          </Button>
        </Space>
      </div>

      {probeToggle && (
        <Typography.Paragraph type="secondary" className="diagnostics-probe-hint">
          {t("diagnostics.metricsProbeHint")}
        </Typography.Paragraph>
      )}

      {diagnosticsQuery.error && (
        <Alert type="error" showIcon message={String(diagnosticsQuery.error)} />
      )}

      {diagnosticsQuery.isLoading && <Typography.Text type="secondary">{t("diagnostics.loading")}</Typography.Text>}

      {top && top.title && (
        <div className="diagnostics-top-suspect">
          <span className="diagnostics-top-label">{t("diagnostics.clusterTopSuspect")}</span>
          <Typography.Text strong>
            {top.replicaId ? `${top.replicaId}: ` : ""}
            {top.title}
          </Typography.Text>
          <Typography.Text type="secondary">{top.detail}</Typography.Text>
        </div>
      )}

      {diagnosticsQuery.data && (
        <Table
          className="diagnostics-cluster-table"
          size="small"
          pagination={false}
          rowKey="replicaId"
          rowClassName={(node) => node.self ? "diagnostics-node-self" : ""}
          columns={nodeColumns}
          dataSource={diagnosticsQuery.data.nodes}
          expandable={{
            expandedRowKeys: expandedReplicaId ? [expandedReplicaId] : [],
            onExpand: (expanded, node) => setExpandedReplicaId(expanded ? node.replicaId : null),
            expandedRowRender: (node) =>
              !node.reachable ? (
                <Typography.Text type="secondary">
                  {node.error ?? t("diagnostics.unreachable")}
                </Typography.Text>
              ) : (
                <NodeDetailPanel node={node} />
              ),
          }}
        />
      )}
    </section>
  );
}
