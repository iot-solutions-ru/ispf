import { useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Form, Input, Select, Space, Table, Typography } from "antd";
import type { TableColumnsType } from "antd";
import {
  fetchPlatformAppSchedules,
  upsertPlatformAppSchedule,
  type PlatformAppSchedule,
} from "../../api/platformAppSchedules";
import { ObjectPathField } from "../../ui";

const DEFAULT_ACTION_JSON = '{"objectPath":"root.platform.devices.demo-sensor-01","functionName":"poll"}';

function parseInvokeAction(json: string): { objectPath: string; functionName: string; hasObjectPath: boolean } {
  try {
    const parsed = JSON.parse(json) as Record<string, unknown>;
    return {
      objectPath: typeof parsed.objectPath === "string" ? parsed.objectPath : "",
      functionName: typeof parsed.functionName === "string" ? parsed.functionName : "",
      hasObjectPath: Object.prototype.hasOwnProperty.call(parsed, "objectPath"),
    };
  } catch {
    return { objectPath: "", functionName: "", hasObjectPath: false };
  }
}

function patchActionJson(
  json: string,
  patch: Partial<{ objectPath: string; functionName: string }>,
): string {
  try {
    const parsed = JSON.parse(json) as Record<string, unknown>;
    if (patch.objectPath !== undefined) parsed.objectPath = patch.objectPath;
    if (patch.functionName !== undefined) parsed.functionName = patch.functionName;
    return JSON.stringify(parsed);
  } catch {
    return JSON.stringify({
      objectPath: patch.objectPath ?? "",
      functionName: patch.functionName ?? "",
    });
  }
}

export default function PlatformSchedulesPanel() {
  const { t } = useTranslation(["system", "platform"]);
  const tp = (key: string) => t(key, { ns: "platform" });
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<PlatformAppSchedule | null>(null);
  const [scheduleId, setScheduleId] = useState("");
  const [appId, setAppId] = useState("");
  const [intervalMs, setIntervalMs] = useState("60000");
  const [enabled, setEnabled] = useState(true);
  const [actionType, setActionType] = useState("invoke_function");
  const [actionJson, setActionJson] = useState(DEFAULT_ACTION_JSON);

  const schedulesQuery = useQuery({
    queryKey: ["platform-app-schedules"],
    queryFn: fetchPlatformAppSchedules,
  });

  const loadIntoForm = (row: PlatformAppSchedule) => {
    setEditing(row);
    setScheduleId(row.scheduleId);
    setAppId(row.appId);
    setIntervalMs(String(row.intervalMs));
    setEnabled(row.enabled);
    setActionType(row.actionType ?? "invoke_function");
    setActionJson(row.actionJson ?? DEFAULT_ACTION_JSON);
  };

  const resetForm = () => {
    setEditing(null);
    setScheduleId("");
    setAppId("");
    setIntervalMs("60000");
    setEnabled(true);
    setActionType("invoke_function");
    setActionJson(DEFAULT_ACTION_JSON);
  };

  const saveMutation = useMutation({
    mutationFn: () =>
      upsertPlatformAppSchedule({
        scheduleId: scheduleId.trim(),
        appId: appId.trim(),
        enabled,
        intervalMs: Number(intervalMs) || 60_000,
        action: { type: actionType.trim(), json: actionJson.trim() },
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["platform-app-schedules"] });
      resetForm();
    },
  });

  const invokeAction = parseInvokeAction(actionJson);
  const showObjectPathField =
    actionType.trim() === "invoke_function" || invokeAction.hasObjectPath;
  const columns: TableColumnsType<PlatformAppSchedule> = [
    {
      title: t("appSchedules.column.id"),
      dataIndex: "scheduleId",
      key: "scheduleId",
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    {
      title: t("appSchedules.column.app"),
      dataIndex: "appId",
      key: "appId",
      render: (value: string) => <Typography.Text code>{value}</Typography.Text>,
    },
    {
      title: t("appSchedules.column.interval"),
      dataIndex: "intervalMs",
      key: "intervalMs",
      render: (value: number) => `${value.toLocaleString()} ms`,
    },
    {
      title: t("appSchedules.column.enabled"),
      dataIndex: "enabled",
      key: "enabled",
      render: (value: boolean) => value ? t("appSchedules.yes") : t("appSchedules.no"),
    },
    {
      title: t("appSchedules.column.action"),
      dataIndex: "actionType",
      key: "actionType",
      render: (value: string | null | undefined) => <Typography.Text code>{value ?? "—"}</Typography.Text>,
    },
    {
      title: "",
      key: "actions",
      render: (_, row) => (
        <Button size="small" onClick={() => loadIntoForm(row)}>
          {t("appSchedules.edit")}
        </Button>
      ),
    },
  ];

  return (
    <section className="system-panel platform-schedules-panel">
      <header>
        <Typography.Title level={3}>{t("appSchedules.title")}</Typography.Title>
        <Typography.Paragraph type="secondary">{t("appSchedules.subtitle")}</Typography.Paragraph>
      </header>

      {schedulesQuery.isLoading && <Typography.Text type="secondary">{t("appSchedules.loading")}</Typography.Text>}
      {schedulesQuery.error && (
        <Alert type="error" showIcon message={String(schedulesQuery.error)} />
      )}

      {schedulesQuery.data && (
        <div className="panel-card">
          <Table
            size="small"
            pagination={false}
            rowKey="scheduleId"
            columns={columns}
            dataSource={schedulesQuery.data}
            locale={{ emptyText: t("appSchedules.empty") }}
          />
        </div>
      )}

      <div className="platform-schedules-form panel-card">
        <Typography.Title level={4}>{editing ? t("appSchedules.editTitle") : t("appSchedules.createTitle")}</Typography.Title>
        <Form layout="vertical">
          <Form.Item label={`${t("appSchedules.column.id")} *`}>
            <Input value={scheduleId} onChange={(e) => setScheduleId(e.target.value)} required />
          </Form.Item>
          <Form.Item label={`${t("appSchedules.column.app")} *`}>
            <Input value={appId} onChange={(e) => setAppId(e.target.value)} required />
          </Form.Item>
          <Form.Item label={`${tp("schedule.intervalMs")} *`}>
            <Input value={intervalMs} onChange={(e) => setIntervalMs(e.target.value)} />
          </Form.Item>
          <Form.Item label={tp("schedule.enabled")}>
            <Select
              value={enabled ? "true" : "false"}
              onChange={(value) => setEnabled(value === "true")}
              options={[
                { value: "true", label: t("appSchedules.yes") },
                { value: "false", label: t("appSchedules.no") },
              ]}
            />
          </Form.Item>
          <Form.Item label={t("appSchedules.column.action")}>
            <Input value={actionType} onChange={(e) => setActionType(e.target.value)} />
          </Form.Item>
          {showObjectPathField && (
            <>
              <ObjectPathField
                className="full"
                label={tp("schedule.objectPath")}
                value={invokeAction.objectPath}
                onChange={(objectPath) =>
                  setActionJson(patchActionJson(actionJson, { objectPath }))
                }
              />
              <Form.Item label={tp("schedule.functionName")} className="full">
                <Input
                  value={invokeAction.functionName}
                  onChange={(e) =>
                    setActionJson(patchActionJson(actionJson, { functionName: e.target.value }))
                  }
                  placeholder="poll"
                />
              </Form.Item>
            </>
          )}
          <Form.Item label={`${t("appSchedules.column.action")} JSON`} className="full">
            <Input.TextArea
              className="mono"
              rows={4}
              value={actionJson}
              onChange={(e) => setActionJson(e.target.value)}
            />
          </Form.Item>
        </Form>
        <Space className="form-actions">
          <Button
            type="primary"
            disabled={saveMutation.isPending || !scheduleId.trim() || !appId.trim()}
            onClick={() => saveMutation.mutate()}
          >
            {saveMutation.isPending ? t("appSchedules.saving") : t("appSchedules.save")}
          </Button>
          {editing && (
            <Button onClick={resetForm}>
              {t("appSchedules.cancel")}
            </Button>
          )}
        </Space>
        {saveMutation.error && (
          <Alert type="error" showIcon message={String(saveMutation.error)} />
        )}
        {saveMutation.isSuccess && (
          <Alert type="success" showIcon message={t("appSchedules.saved")} />
        )}
      </div>
    </section>
  );
}
