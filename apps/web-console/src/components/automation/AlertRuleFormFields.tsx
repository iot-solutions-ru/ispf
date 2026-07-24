import { useTranslation } from "react-i18next";
import { Button, Input, InputNumber, Select, Space, Switch, Typography, Alert } from "antd";
import type {
  AlertRuleFormValues,
  AlertRuleRuntimeStatus,
  CreateAlertRulePayload,
} from "../../types/automation";
import { ObjectPathField } from "../../ui";
import { VariableSelect } from "../dashboard/widgetEditorStructured";
import { formatUserDateTime } from "../../utils/ui/formatDateTime";

function RequiredMark() {
  const { t } = useTranslation("automation");
  return (
    <Typography.Text type="danger" className="alert-rule-required" aria-hidden="true">
      {t("alertRule.requiredMark")}
    </Typography.Text>
  );
}

function formatStatusInstant(value: string | undefined, unknown: string): string {
  if (!value?.trim()) {
    return unknown;
  }
  try {
    return formatUserDateTime(value);
  } catch {
    return value;
  }
}

function formatBool(
  value: boolean | undefined,
  t: (key: string) => string
): string {
  if (value === true) {
    return t("automation:alertRule.valueTrue");
  }
  if (value === false) {
    return t("automation:alertRule.valueFalse");
  }
  return t("automation:alertRule.valueUnknown");
}

export interface AlertRuleFormFieldsProps {
  value: AlertRuleFormValues;
  onChange: (patch: Partial<AlertRuleFormValues>) => void;
  canManage: boolean;
  showName?: boolean;
  /** Variables from the watched target object. */
  targetVariableNames: string[];
  /** Events already defined on the ALERT rule node (inspector). Empty on create. */
  alertEventNames: string[];
  /** When true, show create-time hint that events are auto-created on the ALERT node. */
  creating?: boolean;
  status?: AlertRuleRuntimeStatus;
  exprError?: string | null;
  onValidateCel?: () => void;
}

export default function AlertRuleFormFields({
  value,
  onChange,
  canManage,
  showName = false,
  targetVariableNames,
  alertEventNames,
  creating = false,
  status,
  exprError,
  onValidateCel,
}: AlertRuleFormFieldsProps) {
  const { t } = useTranslation(["automation", "common"]);
  const patch = (next: Partial<AlertRuleFormValues>) => onChange(next);
  const hasEvents = alertEventNames.length > 0;
  const eventOptions =
    value.eventName && !alertEventNames.includes(value.eventName)
      ? [value.eventName, ...alertEventNames]
      : alertEventNames;
  const clearEventOptions =
    value.clearEventName && !alertEventNames.includes(value.clearEventName)
      ? [value.clearEventName, ...alertEventNames]
      : alertEventNames;

  return (
    <div className="alert-rule-form">
      <Typography.Paragraph type="secondary" className="alert-rule-roles-hint">
        {t("automation:alertRule.rolesHint")}
      </Typography.Paragraph>

      <fieldset className="alert-rule-fieldset" disabled={!canManage}>
        <legend>{t("automation:alertRule.sectionTarget")}</legend>
        <div className="antd-control-grid">
          {showName && (
            <label>
              <span className="field-caption">
                {t("common:table.name")} <RequiredMark />
              </span>
              <Input
                value={value.name ?? ""}
                onChange={(e) => patch({ name: e.target.value })}
                required
                disabled={!canManage}
              />
            </label>
          )}
          <ObjectPathField
            className="full"
            label={`${t("automation:alertRule.targetObject")} ${t("automation:alertRule.requiredMark")}`}
            value={value.objectPath}
            onChange={(objectPath) => patch({ objectPath })}
            disabled={!canManage}
          />
          <label>
            <span className="field-caption">{t("automation:alertRule.priority")}</span>
            <Select
              value={value.priority ?? "HIGH"}
              onChange={(priority: CreateAlertRulePayload["priority"]) =>
                patch({ priority })
              }
              disabled={!canManage}
              options={[
                { value: "CRITICAL", label: "CRITICAL" },
                { value: "HIGH", label: "HIGH" },
                { value: "MEDIUM", label: "MEDIUM" },
                { value: "LOW", label: "LOW" },
              ]}
            />
          </label>
          <label className="checkbox-row">
            <Switch
              checked={value.enabled}
              onChange={(enabled) => patch({ enabled })}
              disabled={!canManage}
            />
            {t("automation:alertRule.enabled")}
          </label>
          <label className="checkbox-row">
            <Switch
              checked={value.ackRequired ?? false}
              onChange={(ackRequired) => patch({ ackRequired })}
              disabled={!canManage}
            />
            {t("automation:alertRule.ackRequired")}
          </label>
        </div>
      </fieldset>

      <fieldset className="alert-rule-fieldset" disabled={!canManage}>
        <legend>{t("automation:alertRule.sectionCondition")}</legend>
        <div className="antd-control-grid">
          <div className="full">
            <VariableSelect
              label={`${t("automation:alertRule.variable")} ${t("automation:alertRule.requiredMark")}`}
              value={value.watchVariable}
              onChange={(watchVariable) => patch({ watchVariable })}
              variables={targetVariableNames}
              disabled={!canManage}
            />
          </div>
          <label className="full">
            <span className="field-caption">
              {t("automation:alertRule.celCondition")} <RequiredMark />
            </span>
            <Input.TextArea
              rows={3}
              value={value.conditionExpr}
              onChange={(e) => patch({ conditionExpr: e.target.value })}
              required
              readOnly={!canManage}
            />
            {exprError && <Alert type="error" showIcon message={exprError} style={{ marginTop: 8 }} />}
            {canManage && onValidateCel && (
              <Space style={{ marginTop: 8 }}>
                <Button size="small" onClick={onValidateCel}>
                  {t("automation:alertRule.validateCel")}
                </Button>
              </Space>
            )}
          </label>
          <label className="checkbox-row">
            <Switch
              checked={value.edgeTrigger}
              onChange={(edgeTrigger) => patch({ edgeTrigger })}
              disabled={!canManage}
            />
            {t("automation:alertRule.edgeTriggerHint")}
          </label>
          <label className="checkbox-row">
            <Switch
              checked={value.sustainWhileTrue ?? false}
              onChange={(sustainWhileTrue) => patch({ sustainWhileTrue })}
              disabled={!canManage}
            />
            {t("automation:alertRule.sustainWhileTrue")}
          </label>
          <label>
            <span className="field-caption">{t("automation:alertRule.delaySeconds")}</span>
            <InputNumber
              min={0}
              step={1}
              value={value.delaySeconds ?? 0}
              onChange={(delaySeconds) => patch({ delaySeconds: Number(delaySeconds ?? 0) })}
              disabled={!canManage}
              style={{ width: "100%" }}
            />
          </label>
          <label>
            <span className="field-caption">{t("automation:alertRule.pollIntervalMs")}</span>
            <InputNumber
              min={0}
              step={100}
              value={value.pollIntervalMs ?? 0}
              onChange={(pollIntervalMs) => patch({ pollIntervalMs: Number(pollIntervalMs ?? 0) })}
              disabled={!canManage}
              style={{ width: "100%" }}
            />
          </label>
          <label>
            <span className="field-caption">{t("automation:alertRule.rateLimitSeconds")}</span>
            <InputNumber
              min={0}
              step={1}
              value={value.rateLimitSeconds ?? 0}
              onChange={(rateLimitSeconds) => patch({ rateLimitSeconds: Number(rateLimitSeconds ?? 0) })}
              disabled={!canManage}
              style={{ width: "100%" }}
            />
          </label>
          <label className="full">
            <span className="field-caption">{t("automation:alertRule.anomalyModelId")}</span>
            <Input
              value={value.anomalyModelId ?? ""}
              onChange={(e) => patch({ anomalyModelId: e.target.value })}
              placeholder={t("automation:alertRule.optionalPlaceholder")}
              disabled={!canManage}
            />
          </label>
        </div>
      </fieldset>

      <fieldset className="alert-rule-fieldset" disabled={!canManage}>
        <legend>{t("automation:alertRule.sectionPublish")}</legend>
        <div className="antd-control-grid">
          <Typography.Paragraph type="secondary" className="full">
            {t("automation:alertRule.publishHint")}
          </Typography.Paragraph>
          {hasEvents ? (
            <>
              <label>
                <span className="field-caption">
                  {t("automation:alertRule.event")} <RequiredMark />
                </span>
                <Select
                  value={value.eventName}
                  onChange={(eventName) => patch({ eventName })}
                  disabled={!canManage}
                  options={[
                    { value: "", label: "—" },
                    ...eventOptions.map((name) => ({ value: name, label: name })),
                  ]}
                />
              </label>
              <label>
                <span className="field-caption">{t("automation:alertRule.eventManualFallback")}</span>
                <Input
                  value={value.eventName}
                  onChange={(e) => patch({ eventName: e.target.value })}
                  disabled={!canManage}
                />
              </label>
            </>
          ) : (
            <label className="full">
              <span className="field-caption">
                {t("automation:alertRule.event")} <RequiredMark />
              </span>
              <Input
                value={value.eventName}
                onChange={(e) => patch({ eventName: e.target.value })}
                required
                disabled={!canManage}
                placeholder={creating ? "virtClusterError" : t("automation:alertRule.eventManualFallback")}
              />
              {creating && (
                <Typography.Text type="secondary">{t("automation:alertRule.createEventHint")}</Typography.Text>
              )}
            </label>
          )}
          <label>
            <span className="field-caption">{t("automation:alertRule.payloadVariable")}</span>
            <Input
              value={value.payloadVariable ?? ""}
              onChange={(e) => patch({ payloadVariable: e.target.value })}
              placeholder={t("automation:alertRule.optionalPlaceholder")}
              disabled={!canManage}
              list="alert-rule-payload-vars"
            />
            <datalist id="alert-rule-payload-vars">
              {targetVariableNames.map((name) => (
                <option key={name} value={name} />
              ))}
            </datalist>
          </label>
          <label className="full">
            <span className="field-caption">{t("automation:alertRule.triggerMessage")}</span>
            <Input
              value={value.triggerMessage ?? ""}
              onChange={(e) => patch({ triggerMessage: e.target.value })}
              disabled={!canManage}
            />
          </label>
        </div>
      </fieldset>

      <fieldset className="alert-rule-fieldset" disabled={!canManage}>
        <legend>{t("automation:alertRule.sectionLatch")}</legend>
        <div className="antd-control-grid">
          <label className="full">
            <span className="field-caption">{t("automation:alertRule.deactivateExpr")}</span>
            <Input.TextArea
              rows={2}
              value={value.deactivateExpr ?? ""}
              onChange={(e) => patch({ deactivateExpr: e.target.value })}
              placeholder={t("automation:alertRule.deactivateExprPlaceholder")}
              disabled={!canManage}
            />
          </label>
          {hasEvents ? (
            <label>
              <span className="field-caption">{t("automation:alertRule.clearEvent")}</span>
              <Select
                value={value.clearEventName ?? ""}
                onChange={(clearEventName) => patch({ clearEventName })}
                disabled={!canManage}
                options={[
                  { value: "", label: "—" },
                  ...clearEventOptions.map((name) => ({ value: name, label: name })),
                ]}
              />
            </label>
          ) : (
            <label>
              <span className="field-caption">{t("automation:alertRule.clearEvent")}</span>
              <Input
                value={value.clearEventName ?? ""}
                onChange={(e) => patch({ clearEventName: e.target.value })}
                disabled={!canManage}
              />
            </label>
          )}
          <label>
            <span className="field-caption">{t("automation:alertRule.deactivateDelaySeconds")}</span>
            <InputNumber
              min={0}
              step={1}
              value={value.deactivateDelaySeconds ?? 0}
              onChange={(deactivateDelaySeconds) =>
                patch({ deactivateDelaySeconds: Number(deactivateDelaySeconds ?? 0) })
              }
              disabled={!canManage}
              style={{ width: "100%" }}
            />
          </label>
        </div>
      </fieldset>

      <fieldset className="alert-rule-fieldset" disabled={!canManage}>
        <legend>{t("automation:alertRule.sectionNotifications")}</legend>
        <div className="antd-control-grid">
          <Typography.Paragraph type="secondary" className="full">
            {t("automation:alertRule.notificationHint")}
          </Typography.Paragraph>
          <label className="full">
            <span className="field-caption">{t("automation:alertRule.notificationWebhook")}</span>
            <Input
              value={value.notificationWebhookUrl ?? ""}
              onChange={(e) => patch({ notificationWebhookUrl: e.target.value })}
              placeholder="https://hooks.example.com/alerts"
              disabled={!canManage}
            />
          </label>
          <label className="full">
            <span className="field-caption">{t("automation:alertRule.notificationEmail")}</span>
            <Input
              value={value.notificationEmailTarget ?? ""}
              onChange={(e) => patch({ notificationEmailTarget: e.target.value })}
              placeholder="ops@example.com|Alert|Threshold exceeded"
              disabled={!canManage}
            />
          </label>
        </div>
      </fieldset>

      {status && (
        <fieldset className="alert-rule-fieldset alert-rule-fieldset-status">
          <legend>{t("automation:alertRule.sectionStatus")}</legend>
          <div className="antd-control-grid alert-rule-status-grid">
            <div>
              <span className="field-caption">{t("automation:alertRule.lastFiredAt")}</span>
              <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                {formatStatusInstant(status.lastFiredAt, t("automation:alertRule.valueUnknown"))}
              </Typography.Paragraph>
            </div>
            <div>
              <span className="field-caption">{t("automation:alertRule.lastConditionMet")}</span>
              <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                {formatBool(status.lastConditionMet, t)}
              </Typography.Paragraph>
            </div>
            <div>
              <span className="field-caption">{t("automation:alertRule.conditionTrueSince")}</span>
              <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                {formatStatusInstant(status.conditionTrueSince, t("automation:alertRule.valueUnknown"))}
              </Typography.Paragraph>
            </div>
            <div>
              <span className="field-caption">{t("automation:alertRule.deactivateTrueSince")}</span>
              <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                {formatStatusInstant(status.deactivateTrueSince, t("automation:alertRule.valueUnknown"))}
              </Typography.Paragraph>
            </div>
            {status.latchedActive && (
              <Typography.Paragraph type="secondary" className="full" style={{ marginBottom: 0 }}>
                {t("automation:alertRule.latchedActive")}
              </Typography.Paragraph>
            )}
          </div>
        </fieldset>
      )}
    </div>
  );
}

export function toCreateAlertRulePayload(
  form: AlertRuleFormValues,
  name: string
): CreateAlertRulePayload {
  return {
    name,
    objectPath: form.objectPath.trim(),
    watchVariable: form.watchVariable.trim(),
    conditionExpr: form.conditionExpr.trim(),
    eventName: form.eventName.trim(),
    payloadVariable: form.payloadVariable?.trim() || undefined,
    enabled: form.enabled,
    edgeTrigger: form.edgeTrigger,
    delaySeconds: form.delaySeconds ?? 0,
    sustainWhileTrue: form.sustainWhileTrue ?? false,
    priority: form.priority ?? "HIGH",
    ackRequired: form.ackRequired ?? false,
    rateLimitSeconds: form.rateLimitSeconds ?? 0,
    deactivateExpr: form.deactivateExpr?.trim() || undefined,
    deactivateDelaySeconds: form.deactivateDelaySeconds ?? 0,
    pollIntervalMs: form.pollIntervalMs ?? 0,
    triggerMessage: form.triggerMessage?.trim() || undefined,
    clearEventName: form.clearEventName?.trim() || undefined,
    notificationWebhookUrl: form.notificationWebhookUrl?.trim() || undefined,
    notificationEmailTarget: form.notificationEmailTarget?.trim() || undefined,
    anomalyModelId: form.anomalyModelId?.trim() || undefined,
  };
}
