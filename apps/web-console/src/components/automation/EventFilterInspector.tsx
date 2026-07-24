import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Button, Form, Input, InputNumber, Switch } from "antd";
import { updateEventFilter, validateExpression } from "../../api";
import type { EventFilterPayload } from "../../types/automation";
import { variableBoolean, variableNumber, variableString } from "../../utils/object/variableFieldValue";
import { inspectorQueryLoading, useInspectorVariables } from "../../hooks/useInspectorQueries";
import ObjectFederationBindSection from "../federation/ObjectFederationBindSection";

interface EventFilterInspectorProps {
  path: string;
  canManage?: boolean;
}

export default function EventFilterInspector({ path, canManage = false }: EventFilterInspectorProps) {
  const { t } = useTranslation(["automation", "common"]);
  const queryClient = useQueryClient();
  const variablesQuery = useInspectorVariables(path);

  const variables = variablesQuery.data ?? [];
  const [antdForm] = Form.useForm();
  const form = {
    filterId: variableString(variables, "filterId"),
    eventNamePattern: variableString(variables, "eventNamePattern") || "*",
    sourceObjectPathPattern: variableString(variables, "sourceObjectPathPattern") || "root.platform.**",
    minSeverity: variableNumber(variables, "minSeverity", 0),
    maxSeverity: variableNumber(variables, "maxSeverity", 100),
    timeWindowMs: variableNumber(variables, "timeWindowMs", 0),
    filterExpression: variableString(variables, "filterExpression"),
    enabled: variableBoolean(variables, "enabled", true),
  };

  const saveMutation = useMutation({
    mutationFn: (payload: Partial<EventFilterPayload>) => updateEventFilter(path, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["variables", path] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["event-filters"] });
    },
  });

  const validateMutation = useMutation({
    mutationFn: (expression: string) => validateExpression(expression),
  });

  if (inspectorQueryLoading(variablesQuery)) {
    return <p className="hint">{t("automation:eventFilter.loading")}</p>;
  }

  return (
    <section className="automation-inspector">
      <header className="automation-panel-head">
        <div>
          <h3>{t("automation:eventFilter.title")}</h3>
          <p className="hint">{t("automation:eventFilter.subtitle")}</p>
          <p className="hint">
            <code>{path}</code>
          </p>
        </div>
      </header>
      <Form
        form={antdForm}
        key={path}
        layout="vertical"
        className="antd-control-grid"
        initialValues={form}
        disabled={!canManage}
        onFinish={(values: typeof form) => {
          if (!canManage) {
            return;
          }
          saveMutation.mutate({
            filterId: String(values.filterId ?? ""),
            eventNamePattern: String(values.eventNamePattern ?? "*"),
            sourceObjectPathPattern: String(values.sourceObjectPathPattern ?? "root.platform.**"),
            minSeverity: Number(values.minSeverity ?? 0),
            maxSeverity: Number(values.maxSeverity ?? 100),
            timeWindowMs: Number(values.timeWindowMs ?? 0),
            filterExpression: String(values.filterExpression ?? ""),
            enabled: Boolean(values.enabled),
          });
        }}
      >
        <Form.Item name="filterId" label={t("automation:eventFilter.filterId")} rules={[{ required: true }]} required>
          <Input />
        </Form.Item>
        <Form.Item name="eventNamePattern" label={t("automation:eventFilter.eventNamePattern")} rules={[{ required: true }]} required>
          <Input />
        </Form.Item>
        <Form.Item className="full" name="sourceObjectPathPattern" label={t("automation:eventFilter.sourceObjectPathPattern")} rules={[{ required: true }]} required>
          <Input />
        </Form.Item>
        <Form.Item name="minSeverity" label={t("automation:eventFilter.minSeverity")}>
          <InputNumber min={0} max={100} step={1} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item name="maxSeverity" label={t("automation:eventFilter.maxSeverity")}>
          <InputNumber min={0} max={100} step={1} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item name="timeWindowMs" label={t("automation:eventFilter.timeWindowMs")}>
          <InputNumber min={0} step={1000} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item className="full" name="filterExpression" label={t("automation:eventFilter.filterExpression")}>
          <Input.TextArea rows={3} placeholder={t("automation:eventFilter.filterExpressionPlaceholder")} />
        </Form.Item>
        <Form.Item name="enabled" valuePropName="checked" label={t("automation:eventFilter.enabled")}>
          <Switch />
        </Form.Item>
        {canManage && (
          <div className="form-actions full">
            <Button
              onClick={() => {
                const expression = antdForm.getFieldValue("filterExpression") as string | undefined;
                if (expression) {
                  validateMutation.mutate(expression);
                }
              }}
            >
              {t("automation:eventFilter.validateCel")}
            </Button>
            <Button htmlType="submit" type="primary" loading={saveMutation.isPending}>
              {t("common:action.save")}
            </Button>
          </div>
        )}
        {validateMutation.data && (
          <p className={`hint full ${validateMutation.data.valid ? "" : "error"}`}>
            {validateMutation.data.valid ? t("automation:eventFilter.celOk") : validateMutation.data.error}
          </p>
        )}
        {saveMutation.error && <Alert className="full" type="error" message={String(saveMutation.error)} showIcon />}
      </Form>
      <ObjectFederationBindSection path={path} canManage={canManage} />
    </section>
  );
}
