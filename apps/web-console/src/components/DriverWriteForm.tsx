import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Alert, Button, Form, Input, Select, Space, Typography } from "antd";
import { fetchVariables } from "../api";
import { pollDriver, writeDriverPoint } from "../api/drivers";
import { parseDriverPointMappings, parseDriverWriteValue } from "../utils/driverPointMappings";
import { variableString } from "../utils/object/variableFieldValue";

interface DriverWriteFormProps {
  devicePath: string;
  canManage: boolean;
  supportsWrite?: boolean;
  showPoll?: boolean;
  onSuccess?: () => void;
}

export default function DriverWriteForm({
  devicePath,
  canManage,
  supportsWrite = true,
  showPoll = true,
  onSuccess,
}: DriverWriteFormProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const queryClient = useQueryClient();
  const [pointId, setPointId] = useState("");
  const [valueText, setValueText] = useState("");
  const [showJson, setShowJson] = useState(false);
  const [valueJson, setValueJson] = useState('{\n  "rows": [{ "value": 0 }]\n}');
  const [formError, setFormError] = useState<string | null>(null);

  const variablesQuery = useQuery({
    queryKey: ["variables", devicePath],
    queryFn: () => fetchVariables(devicePath),
  });

  const mappings = useMemo(
    () => parseDriverPointMappings(variableString(variablesQuery.data ?? [], "driverPointMappingsJson")),
    [variablesQuery.data],
  );

  const mappingEntries = useMemo(
    () => Object.entries(mappings).sort(([a], [b]) => a.localeCompare(b)),
    [mappings],
  );

  useEffect(() => {
    if (!pointId && mappingEntries.length > 0) {
      setPointId(mappingEntries[0][0]);
    }
  }, [mappingEntries, pointId]);

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ["driver-status", devicePath] });
    queryClient.invalidateQueries({ queryKey: ["variables", devicePath] });
    onSuccess?.();
  };

  const pollMutation = useMutation({
    mutationFn: () => pollDriver(devicePath),
    onSuccess: invalidate,
  });

  const writeMutation = useMutation({
    mutationFn: () => {
      setFormError(null);
      if (!pointId.trim()) {
        throw new Error(t("inspector:driver.write.pointRequired"));
      }
      let payload: { rows: Array<Record<string, unknown>> };
      if (showJson) {
        try {
          const parsed = JSON.parse(valueJson) as { rows?: Array<Record<string, unknown>> };
          if (!parsed.rows || parsed.rows.length === 0) {
            throw new Error(t("inspector:driver.write.valueRequired"));
          }
          payload = { rows: parsed.rows };
        } catch (error) {
          if (error instanceof Error && error.message === t("inspector:driver.write.valueRequired")) {
            throw error;
          }
          throw new Error(t("common:error.invalidJsonInput"));
        }
      } else {
        if (!valueText.trim()) {
          throw new Error(t("inspector:driver.write.valueRequired"));
        }
        payload = { rows: [parseDriverWriteValue(valueText)] };
      }
      return writeDriverPoint(devicePath, pointId.trim(), payload);
    },
    onSuccess: invalidate,
    onError: (error: Error) => setFormError(error.message),
  });

  const selectedMapping = mappings[pointId];
  const isBusy = pollMutation.isPending || writeMutation.isPending;
  const actionError =
    pollMutation.error
    ?? (writeMutation.error && !formError ? writeMutation.error : null);

  if (variablesQuery.isLoading) {
    return <Typography.Paragraph type="secondary">{t("inspector:driver.write.loading")}</Typography.Paragraph>;
  }

  if (!canManage) {
    return <Typography.Paragraph type="secondary">{t("common:hint.adminOnly")}</Typography.Paragraph>;
  }

  if (!supportsWrite) {
    return <Alert type="warning" showIcon message={t("inspector:driver.write.readOnlyDriver")} />;
  }

  return (
    <Space orientation="vertical" size="middle" style={{ width: "100%" }} className="driver-write-form">
      <div className="driver-write-head">
        <div>
          <Typography.Title level={4} style={{ margin: 0 }}>
            {t("inspector:driver.write.title")}
          </Typography.Title>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            {t("inspector:driver.write.subtitle")}
          </Typography.Paragraph>
        </div>
        {showPoll && (
          <Button
            disabled={isBusy}
            loading={pollMutation.isPending}
            onClick={() => pollMutation.mutate()}
          >
            {pollMutation.isPending ? t("inspector:driver.write.polling") : t("inspector:driver.write.pollNow")}
          </Button>
        )}
      </div>

      {mappingEntries.length === 0 ? (
        <Alert type="warning" showIcon message={t("inspector:driver.write.noMappings")} />
      ) : (
        <Form
          layout="vertical"
          className="driver-write-fields"
          onFinish={() => {
            writeMutation.mutate();
          }}
        >
          <Space orientation="vertical" size="middle" style={{ width: "100%" }}>
            <Form.Item label={t("inspector:driver.write.pointLabel")} style={{ marginBottom: 0 }}>
              <Select
                value={pointId}
                onChange={setPointId}
                options={mappingEntries.map(([variableName]) => ({
                  value: variableName,
                  label: variableName,
                }))}
              />
            </Form.Item>
            {selectedMapping && (
              <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                {t("inspector:driver.write.mappingHint", { mapping: selectedMapping })}
              </Typography.Paragraph>
            )}
            <Space style={{ justifyContent: "space-between", width: "100%" }}>
              <Typography.Text strong>{t("inspector:driver.write.valueLabel")}</Typography.Text>
              <Button size="small" onClick={() => setShowJson((value) => !value)}>
                JSON
              </Button>
            </Space>
            {showJson ? (
              <Form.Item label={t("inspector:driver.write.valueJsonLabel")} style={{ marginBottom: 0 }}>
                <Input.TextArea
                  className="mono"
                  rows={6}
                  value={valueJson}
                  onChange={(event) => setValueJson(event.target.value)}
                  spellCheck={false}
                />
              </Form.Item>
            ) : (
              <Form.Item label={t("inspector:driver.write.valueLabel")} style={{ marginBottom: 0 }}>
                <Input
                  value={valueText}
                  onChange={(event) => setValueText(event.target.value)}
                  placeholder={t("inspector:driver.write.valuePlaceholder")}
                />
              </Form.Item>
            )}
            <Button htmlType="submit" type="primary" disabled={isBusy} loading={writeMutation.isPending}>
              {writeMutation.isPending ? t("inspector:driver.write.writing") : t("inspector:driver.write.submit")}
            </Button>
            {formError && <Alert type="error" showIcon message={formError} />}
          </Space>
        </Form>
      )}

      {actionError && (
        <Alert type="error" showIcon message={(actionError as Error).message} />
      )}
    </Space>
  );
}
