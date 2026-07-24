import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Form, Input, Select, Space, Table, Tag, Typography } from "antd";
import type { TableColumnsType } from "antd";
import {
  applyChangeSet,
  createChangeSet,
  fetchChangeSet,
  fetchChangeSets,
  previewChangeSet,
  type ChangeSetOp,
  type ChangeSetPreview,
  type ChangeSetSummary,
} from "../../api/platformChangeSets";
import { useUserTimeZone } from "../../context/UserTimeZoneContext";

const SAMPLE_OPS = `[
  {
    "op": "UPDATE_INFO",
    "path": "root.platform",
    "expectedRevision": 1,
    "payload": { "displayName": "Platform", "description": "Updated via change-set" }
  }
]`;

export default function PlatformChangeSetsPanel() {
  const { t } = useTranslation(["system", "common"]);
  const { formatDate } = useUserTimeZone();
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState("");
  const [createTitle, setCreateTitle] = useState("");
  const [createOpsJson, setCreateOpsJson] = useState(SAMPLE_OPS);
  const [createError, setCreateError] = useState<string | null>(null);
  const [preview, setPreview] = useState<ChangeSetPreview | null>(null);
  const [applyForce, setApplyForce] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);

  const listQuery = useQuery({
    queryKey: ["platform-change-sets", statusFilter],
    queryFn: () => fetchChangeSets(statusFilter || undefined),
  });

  const detailQuery = useQuery({
    queryKey: ["platform-change-set", selectedId],
    queryFn: () => fetchChangeSet(selectedId as string),
    enabled: Boolean(selectedId),
  });

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["platform-change-sets"] });
    if (selectedId) {
      queryClient.invalidateQueries({ queryKey: ["platform-change-set", selectedId] });
    }
  };

  const createMutation = useMutation({
    mutationFn: async () => {
      let ops: ChangeSetOp[];
      try {
        ops = JSON.parse(createOpsJson) as ChangeSetOp[];
      } catch {
        throw new Error(t("changeSets.invalidOpsJson"));
      }
      if (!Array.isArray(ops) || ops.length === 0) {
        throw new Error(t("changeSets.opsRequired"));
      }
      return createChangeSet(createTitle.trim(), ops);
    },
    onSuccess: (created) => {
      setCreateTitle("");
      setCreateError(null);
      setSelectedId(created.id);
      invalidate();
    },
    onError: (error) => setCreateError((error as Error).message),
  });

  const previewMutation = useMutation({
    mutationFn: () => previewChangeSet(selectedId as string),
    onSuccess: (result) => {
      setPreview(result);
      setActionError(null);
    },
    onError: (error) => setActionError((error as Error).message),
  });

  const applyMutation = useMutation({
    mutationFn: () => applyChangeSet(selectedId as string, applyForce),
    onSuccess: () => {
      setPreview(null);
      setActionError(null);
      invalidate();
    },
    onError: (error) => setActionError((error as Error).message),
  });

  const selectedSummary = useMemo(
    () => listQuery.data?.find((item) => item.id === selectedId) ?? null,
    [listQuery.data, selectedId]
  );
  const listColumns: TableColumnsType<ChangeSetSummary> = [
    { title: t("changeSets.column.title"), dataIndex: "title", key: "title" },
    {
      title: t("changeSets.column.status"),
      dataIndex: "status",
      key: "status",
      render: (status: string) => <Tag>{status}</Tag>,
    },
    { title: t("changeSets.column.author"), dataIndex: "author", key: "author" },
    {
      title: t("changeSets.column.updated"),
      dataIndex: "updatedAt",
      key: "updatedAt",
      render: (updatedAt: string) => formatDate(updatedAt),
    },
  ];
  const opColumns: TableColumnsType<ChangeSetOp> = [
    {
      title: t("changeSets.column.op"),
      dataIndex: "op",
      key: "op",
      render: (op: string) => <Typography.Text code>{op}</Typography.Text>,
    },
    {
      title: t("changeSets.column.path"),
      dataIndex: "path",
      key: "path",
      render: (path: string) => <Typography.Text code>{path}</Typography.Text>,
    },
    {
      title: t("changeSets.column.revision"),
      dataIndex: "expectedRevision",
      key: "expectedRevision",
      render: (value: number | null | undefined) => value ?? "—",
    },
  ];
  const conflictColumns: TableColumnsType<Record<string, unknown>> = [
    {
      title: t("changeSets.column.path"),
      dataIndex: "path",
      key: "path",
      render: (value) => <Typography.Text code>{String(value ?? "")}</Typography.Text>,
    },
    {
      title: t("changeSets.column.op"),
      dataIndex: "op",
      key: "op",
      render: (value) => <Typography.Text code>{String(value ?? "")}</Typography.Text>,
    },
    {
      title: t("changeSets.column.expected"),
      dataIndex: "expectedRevision",
      key: "expectedRevision",
      render: (value) => String(value ?? ""),
    },
    {
      title: t("changeSets.column.current"),
      dataIndex: "currentRevision",
      key: "currentRevision",
      render: (value) => String(value ?? ""),
    },
  ];

  return (
    <section className="system-panel platform-change-sets-panel">
      <Typography.Paragraph type="secondary">{t("changeSets.intro")}</Typography.Paragraph>

      <Space className="platform-change-sets-toolbar">
        <Form layout="vertical">
          <Form.Item label={t("changeSets.statusFilter")}>
            <Select
              value={statusFilter}
              onChange={setStatusFilter}
              options={[
                { value: "", label: t("changeSets.statusAll") },
                { value: "DRAFT", label: "DRAFT" },
                { value: "APPLIED", label: "APPLIED" },
              ]}
            />
          </Form.Item>
        </Form>
        <Button onClick={() => listQuery.refetch()}>
          {t("common:action.refresh")}
        </Button>
      </Space>

      {listQuery.isLoading ? (
        <Typography.Text type="secondary">{t("changeSets.loading")}</Typography.Text>
      ) : listQuery.isError ? (
        <Alert type="error" showIcon message={t("changeSets.loadError")} />
      ) : (
        <div className="platform-change-sets-layout">
          <div className="platform-change-sets-list">
            <Table
              className="data-table"
              size="small"
              pagination={false}
              rowKey="id"
              rowClassName={(item) => selectedId === item.id ? "selected" : ""}
              columns={listColumns}
              dataSource={listQuery.data ?? []}
              locale={{ emptyText: t("changeSets.empty") }}
              onRow={(item) => ({
                onClick: () => {
                  setSelectedId(item.id);
                  setPreview(null);
                  setActionError(null);
                },
              })}
            />
          </div>

          <div className="platform-change-sets-detail">
            <Typography.Title level={3}>{t("changeSets.createTitle")}</Typography.Title>
            <Form layout="vertical">
              <Form.Item label={t("changeSets.field.title")}>
                <Input
                  value={createTitle}
                  onChange={(e) => setCreateTitle(e.target.value)}
                  placeholder={t("changeSets.titlePlaceholder")}
                />
              </Form.Item>
              <Form.Item label={t("changeSets.field.opsJson")}>
                <Input.TextArea
                  className="mono"
                  rows={8}
                  value={createOpsJson}
                  onChange={(e) => setCreateOpsJson(e.target.value)}
                />
              </Form.Item>
            </Form>
            {createError && <Alert type="error" showIcon message={createError} />}
            <Button
              type="primary"
              disabled={!createTitle.trim() || createMutation.isPending}
              onClick={() => createMutation.mutate()}
            >
              {createMutation.isPending ? t("changeSets.creating") : t("changeSets.create")}
            </Button>

            {selectedId && (
              <>
                <Typography.Title level={3}>{selectedSummary?.title ?? selectedId}</Typography.Title>
                {detailQuery.isLoading ? (
                  <Typography.Text type="secondary">{t("common:action.loading")}</Typography.Text>
                ) : detailQuery.data ? (
                  <>
                    <Typography.Paragraph type="secondary">
                      <Typography.Text code>{detailQuery.data.status}</Typography.Text> · {detailQuery.data.author}
                    </Typography.Paragraph>
                    <Table
                      className="data-table compact"
                      size="small"
                      pagination={false}
                      rowKey={(_, index) => `${_.path}-${index}`}
                      columns={opColumns}
                      dataSource={detailQuery.data.ops}
                    />
                    <Space className="platform-change-sets-actions">
                      <Button
                        disabled={previewMutation.isPending}
                        onClick={() => previewMutation.mutate()}
                      >
                        {previewMutation.isPending ? t("changeSets.previewing") : t("changeSets.preview")}
                      </Button>
                      <Select
                        value={applyForce ? "true" : "false"}
                        onChange={(value) => setApplyForce(value === "true")}
                        options={[
                          { value: "false", label: t("changeSets.forceApply") },
                          { value: "true", label: t("changeSets.forceApply") },
                        ]}
                      />
                      <Button
                        type="primary"
                        disabled={
                          detailQuery.data.status === "APPLIED" || applyMutation.isPending
                        }
                        onClick={() => applyMutation.mutate()}
                      >
                        {applyMutation.isPending ? t("changeSets.applying") : t("changeSets.apply")}
                      </Button>
                    </Space>
                  </>
                ) : null}

                {preview && (
                  <div className="platform-change-sets-preview">
                    <Typography.Title level={4}>{t("changeSets.previewResult")}</Typography.Title>
                    <Typography.Paragraph>
                      {t("changeSets.conflictCount", { count: preview.conflictCount })}
                    </Typography.Paragraph>
                    {preview.conflicts.length > 0 && (
                      <Table
                        className="data-table compact"
                        size="small"
                        pagination={false}
                        rowKey={(_, index) => String(index)}
                        columns={conflictColumns}
                        dataSource={preview.conflicts}
                      />
                    )}
                    {preview.applicable.length > 0 && (
                      <Typography.Paragraph type="secondary">
                        {t("changeSets.applicableCount", { count: preview.applicable.length })}
                      </Typography.Paragraph>
                    )}
                  </div>
                )}
                {actionError && <Alert type="error" showIcon message={actionError} />}
                {applyMutation.isSuccess && (
                  <Alert type="success" showIcon message={t("changeSets.appliedSuccess")} />
                )}
              </>
            )}
          </div>
        </div>
      )}
    </section>
  );
}
