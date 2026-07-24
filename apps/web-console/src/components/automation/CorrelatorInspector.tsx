import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Button, Form, Input, InputNumber, Select, Switch } from "antd";
import { updateCorrelator } from "../../api";
import type { CorrelatorActionType, CorrelatorPatternType, CreateCorrelatorPayload } from "../../types/automation";
import {
  CORRELATOR_ACTION_LABEL_KEYS,
  CORRELATOR_ACTION_TYPES,
  correlatorActionTargetLabel,
} from "../../utils/automation/correlatorAction";
import { variableBoolean, variableNumber, variableString } from "../../utils/object/variableFieldValue";
import { inspectorQueryLoading, useInspectorVariables } from "../../hooks/useInspectorQueries";
import ObjectFederationBindSection from "../federation/ObjectFederationBindSection";

interface CorrelatorInspectorProps {
  path: string;
  canManage?: boolean;
}

export default function CorrelatorInspector({ path, canManage = false }: CorrelatorInspectorProps) {
  const { t } = useTranslation(["automation", "common"]);
  const queryClient = useQueryClient();
  const variablesQuery = useInspectorVariables(path);
  const [antdForm] = Form.useForm();

  const variables = variablesQuery.data ?? [];
  const patternType = (variableString(variables, "patternType") || "COUNT") as CorrelatorPatternType;
  const actionType = (variableString(variables, "actionType") || "RUN_WORKFLOW") as CorrelatorActionType;
  const needsSecondEvent =
    patternType === "SEQUENCE" || patternType === "EVENT_CHAIN";

  const saveMutation = useMutation({
    mutationFn: (payload: Partial<CreateCorrelatorPayload>) => updateCorrelator(path, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["variables", path] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
  });

  if (inspectorQueryLoading(variablesQuery)) {
    return <p className="hint">{t("automation:correlator.loading")}</p>;
  }

  return (
    <section className="automation-inspector">
      <header className="automation-panel-head">
        <div>
          <h3>{t("automation:correlator.title")}</h3>
          <p className="hint">{t("automation:correlator.subtitle")}</p>
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
        initialValues={{
          objectPath: variableString(variables, "targetObjectPath"),
          patternType,
          actionType,
          eventName: variableString(variables, "eventName"),
          secondEventName: variableString(variables, "secondEventName"),
          windowSeconds: variableNumber(variables, "windowSeconds"),
          minOccurrences: variableNumber(variables, "minOccurrences", 1),
          cooldownSeconds: variableNumber(variables, "cooldownSeconds", 120),
          actionTarget: variableString(variables, "actionTarget"),
          enabled: variableBoolean(variables, "enabled", true),
        }}
        disabled={!canManage}
        onFinish={(values: {
          objectPath?: string;
          patternType?: CorrelatorPatternType;
          actionType?: CorrelatorActionType;
          eventName?: string;
          secondEventName?: string;
          windowSeconds?: number;
          minOccurrences?: number;
          cooldownSeconds?: number;
          actionTarget?: string;
          enabled?: boolean;
        }) => {
          if (!canManage) {
            return;
          }
          saveMutation.mutate({
            objectPath: String(values.objectPath ?? "") || undefined,
            patternType: (values.patternType ?? "COUNT") as CorrelatorPatternType,
            eventName: String(values.eventName ?? ""),
            secondEventName: String(values.secondEventName ?? "") || undefined,
            windowSeconds: Number(values.windowSeconds ?? 0),
            minOccurrences: Number(values.minOccurrences ?? 1),
            cooldownSeconds: Number(values.cooldownSeconds ?? 120),
            actionType: (values.actionType ?? "RUN_WORKFLOW") as CorrelatorActionType,
            actionTarget: String(values.actionTarget ?? ""),
            enabled: Boolean(values.enabled),
          });
        }}
      >
        <Form.Item className="full" name="objectPath" label={t("automation:correlator.objectFilter")}>
          <Input />
        </Form.Item>
        <Form.Item name="patternType" label={t("automation:correlator.pattern")}>
          <Select
            options={[
              { value: "COUNT", label: "COUNT" },
              { value: "SEQUENCE", label: "SEQUENCE" },
              { value: "EVENT_CHAIN", label: "EVENT_CHAIN" },
            ]}
          />
        </Form.Item>
        <Form.Item name="actionType" label={t("automation:correlator.action")}>
          <Select
            options={CORRELATOR_ACTION_TYPES.map((type) => ({
              value: type,
              label: t(CORRELATOR_ACTION_LABEL_KEYS[type]),
            }))}
          />
        </Form.Item>
        <Form.Item name="eventName" label={t("automation:correlator.event")} rules={[{ required: true }]} required>
          <Input />
        </Form.Item>
        <Form.Item
          name="secondEventName"
          label={
            needsSecondEvent
              ? patternType === "EVENT_CHAIN"
                ? t("automation:correlator.eventChain")
                : t("automation:correlator.eventB")
              : t("automation:correlator.secondEventChain")
          }
        >
          <Input placeholder={t("automation:correlator.secondEventPlaceholder")} />
        </Form.Item>
        <Form.Item name="windowSeconds" label={t("automation:correlator.windowSeconds")}>
          <InputNumber min={0} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item name="minOccurrences" label={t("automation:correlator.minOccurrences")}>
          <InputNumber min={1} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item name="cooldownSeconds" label={t("automation:correlator.cooldownSeconds")}>
          <InputNumber min={0} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item className="full" name="actionTarget" label={correlatorActionTargetLabel(actionType, t)} rules={[{ required: true }]} required>
          <Input />
        </Form.Item>
        <Form.Item name="enabled" valuePropName="checked" label={t("automation:alertRule.enabled")}>
          <Switch />
        </Form.Item>
        {canManage && (
          <div className="form-actions full">
            <Button htmlType="submit" type="primary" loading={saveMutation.isPending}>
              {t("common:action.save")}
            </Button>
          </div>
        )}
        {saveMutation.error && (
          <Alert className="full" type="error" message={String(saveMutation.error)} showIcon />
        )}
      </Form>
      <ObjectFederationBindSection path={path} canManage={canManage} />
    </section>
  );
}
