import { useTranslation } from "react-i18next";
import type {
  AlertRuleFormValues,
  AlertRuleRuntimeStatus,
  CreateAlertRulePayload,
} from "../../types/automation";
import { ObjectPathField } from "../../ui";
import { VariableSelect } from "../dashboard/widgetEditorStructured";
import { formatUserDateTime } from "../../utils/formatDateTime";

function RequiredMark() {
  const { t } = useTranslation("automation");
  return (
    <span className="alert-rule-required" aria-hidden="true">
      {t("alertRule.requiredMark")}
    </span>
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
      <p className="hint alert-rule-roles-hint">{t("automation:alertRule.rolesHint")}</p>

      <fieldset className="alert-rule-fieldset" disabled={!canManage}>
        <legend>{t("automation:alertRule.sectionTarget")}</legend>
        <div className="form-grid">
          {showName && (
            <label>
              <span className="field-caption">
                {t("common:table.name")} <RequiredMark />
              </span>
              <input
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
            <select
              value={value.priority ?? "HIGH"}
              onChange={(e) =>
                patch({ priority: e.target.value as CreateAlertRulePayload["priority"] })
              }
              disabled={!canManage}
            >
              <option value="CRITICAL">CRITICAL</option>
              <option value="HIGH">HIGH</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="LOW">LOW</option>
            </select>
          </label>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={value.enabled}
              onChange={(e) => patch({ enabled: e.target.checked })}
              disabled={!canManage}
            />
            {t("automation:alertRule.enabled")}
          </label>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={value.ackRequired ?? false}
              onChange={(e) => patch({ ackRequired: e.target.checked })}
              disabled={!canManage}
            />
            {t("automation:alertRule.ackRequired")}
          </label>
        </div>
      </fieldset>

      <fieldset className="alert-rule-fieldset" disabled={!canManage}>
        <legend>{t("automation:alertRule.sectionCondition")}</legend>
        <div className="form-grid">
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
            <textarea
              rows={3}
              value={value.conditionExpr}
              onChange={(e) => patch({ conditionExpr: e.target.value })}
              required
              readOnly={!canManage}
            />
            {exprError && <span className="hint error">{exprError}</span>}
            {canManage && onValidateCel && (
              <div className="form-actions" style={{ marginTop: "0.5rem" }}>
                <button type="button" className="btn small" onClick={onValidateCel}>
                  {t("automation:alertRule.validateCel")}
                </button>
              </div>
            )}
          </label>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={value.edgeTrigger}
              onChange={(e) => patch({ edgeTrigger: e.target.checked })}
              disabled={!canManage}
            />
            {t("automation:alertRule.edgeTriggerHint")}
          </label>
          <label className="checkbox-row">
            <input
              type="checkbox"
              checked={value.sustainWhileTrue ?? false}
              onChange={(e) => patch({ sustainWhileTrue: e.target.checked })}
              disabled={!canManage}
            />
            {t("automation:alertRule.sustainWhileTrue")}
          </label>
          <label>
            <span className="field-caption">{t("automation:alertRule.delaySeconds")}</span>
            <input
              type="number"
              min={0}
              step={1}
              value={value.delaySeconds ?? 0}
              onChange={(e) => patch({ delaySeconds: Number(e.target.value) })}
              readOnly={!canManage}
            />
          </label>
          <label>
            <span className="field-caption">{t("automation:alertRule.pollIntervalMs")}</span>
            <input
              type="number"
              min={0}
              step={100}
              value={value.pollIntervalMs ?? 0}
              onChange={(e) => patch({ pollIntervalMs: Number(e.target.value) })}
              readOnly={!canManage}
            />
          </label>
          <label>
            <span className="field-caption">{t("automation:alertRule.rateLimitSeconds")}</span>
            <input
              type="number"
              min={0}
              step={1}
              value={value.rateLimitSeconds ?? 0}
              onChange={(e) => patch({ rateLimitSeconds: Number(e.target.value) })}
              readOnly={!canManage}
            />
          </label>
          <label className="full">
            <span className="field-caption">{t("automation:alertRule.anomalyModelId")}</span>
            <input
              value={value.anomalyModelId ?? ""}
              onChange={(e) => patch({ anomalyModelId: e.target.value })}
              placeholder={t("automation:alertRule.optionalPlaceholder")}
              readOnly={!canManage}
            />
          </label>
        </div>
      </fieldset>

      <fieldset className="alert-rule-fieldset" disabled={!canManage}>
        <legend>{t("automation:alertRule.sectionPublish")}</legend>
        <div className="form-grid">
          <p className="hint full">{t("automation:alertRule.publishHint")}</p>
          {hasEvents ? (
            <>
              <label>
                <span className="field-caption">
                  {t("automation:alertRule.event")} <RequiredMark />
                </span>
                <select
                  value={value.eventName}
                  onChange={(e) => patch({ eventName: e.target.value })}
                  required
                  disabled={!canManage}
                >
                  <option value="">—</option>
                  {eventOptions.map((name) => (
                    <option key={name} value={name}>
                      {name}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                <span className="field-caption">{t("automation:alertRule.eventManualFallback")}</span>
                <input
                  value={value.eventName}
                  onChange={(e) => patch({ eventName: e.target.value })}
                  readOnly={!canManage}
                />
              </label>
            </>
          ) : (
            <label className="full">
              <span className="field-caption">
                {t("automation:alertRule.event")} <RequiredMark />
              </span>
              <input
                value={value.eventName}
                onChange={(e) => patch({ eventName: e.target.value })}
                required
                readOnly={!canManage}
                placeholder={creating ? "virtClusterError" : t("automation:alertRule.eventManualFallback")}
              />
              {creating && (
                <span className="hint">{t("automation:alertRule.createEventHint")}</span>
              )}
            </label>
          )}
          <label>
            <span className="field-caption">{t("automation:alertRule.payloadVariable")}</span>
            <input
              value={value.payloadVariable ?? ""}
              onChange={(e) => patch({ payloadVariable: e.target.value })}
              placeholder={t("automation:alertRule.optionalPlaceholder")}
              readOnly={!canManage}
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
            <input
              value={value.triggerMessage ?? ""}
              onChange={(e) => patch({ triggerMessage: e.target.value })}
              readOnly={!canManage}
            />
          </label>
        </div>
      </fieldset>

      <fieldset className="alert-rule-fieldset" disabled={!canManage}>
        <legend>{t("automation:alertRule.sectionLatch")}</legend>
        <div className="form-grid">
          <label className="full">
            <span className="field-caption">{t("automation:alertRule.deactivateExpr")}</span>
            <textarea
              rows={2}
              value={value.deactivateExpr ?? ""}
              onChange={(e) => patch({ deactivateExpr: e.target.value })}
              placeholder={t("automation:alertRule.deactivateExprPlaceholder")}
              readOnly={!canManage}
            />
          </label>
          {hasEvents ? (
            <label>
              <span className="field-caption">{t("automation:alertRule.clearEvent")}</span>
              <select
                value={value.clearEventName ?? ""}
                onChange={(e) => patch({ clearEventName: e.target.value })}
                disabled={!canManage}
              >
                <option value="">—</option>
                {clearEventOptions.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </select>
            </label>
          ) : (
            <label>
              <span className="field-caption">{t("automation:alertRule.clearEvent")}</span>
              <input
                value={value.clearEventName ?? ""}
                onChange={(e) => patch({ clearEventName: e.target.value })}
                readOnly={!canManage}
              />
            </label>
          )}
          <label>
            <span className="field-caption">{t("automation:alertRule.deactivateDelaySeconds")}</span>
            <input
              type="number"
              min={0}
              step={1}
              value={value.deactivateDelaySeconds ?? 0}
              onChange={(e) => patch({ deactivateDelaySeconds: Number(e.target.value) })}
              readOnly={!canManage}
            />
          </label>
        </div>
      </fieldset>

      <fieldset className="alert-rule-fieldset" disabled={!canManage}>
        <legend>{t("automation:alertRule.sectionNotifications")}</legend>
        <div className="form-grid">
          <p className="hint full">{t("automation:alertRule.notificationHint")}</p>
          <label className="full">
            <span className="field-caption">{t("automation:alertRule.notificationWebhook")}</span>
            <input
              value={value.notificationWebhookUrl ?? ""}
              onChange={(e) => patch({ notificationWebhookUrl: e.target.value })}
              placeholder="https://hooks.example.com/alerts"
              readOnly={!canManage}
            />
          </label>
          <label className="full">
            <span className="field-caption">{t("automation:alertRule.notificationEmail")}</span>
            <input
              value={value.notificationEmailTarget ?? ""}
              onChange={(e) => patch({ notificationEmailTarget: e.target.value })}
              placeholder="ops@example.com|Alert|Threshold exceeded"
              readOnly={!canManage}
            />
          </label>
        </div>
      </fieldset>

      {status && (
        <fieldset className="alert-rule-fieldset alert-rule-fieldset-status">
          <legend>{t("automation:alertRule.sectionStatus")}</legend>
          <div className="form-grid alert-rule-status-grid">
            <div>
              <span className="field-caption">{t("automation:alertRule.lastFiredAt")}</span>
              <p className="hint">{formatStatusInstant(status.lastFiredAt, t("automation:alertRule.valueUnknown"))}</p>
            </div>
            <div>
              <span className="field-caption">{t("automation:alertRule.lastConditionMet")}</span>
              <p className="hint">{formatBool(status.lastConditionMet, t)}</p>
            </div>
            <div>
              <span className="field-caption">{t("automation:alertRule.conditionTrueSince")}</span>
              <p className="hint">
                {formatStatusInstant(status.conditionTrueSince, t("automation:alertRule.valueUnknown"))}
              </p>
            </div>
            <div>
              <span className="field-caption">{t("automation:alertRule.deactivateTrueSince")}</span>
              <p className="hint">
                {formatStatusInstant(status.deactivateTrueSince, t("automation:alertRule.valueUnknown"))}
              </p>
            </div>
            {status.latchedActive && (
              <p className="hint full">{t("automation:alertRule.latchedActive")}</p>
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
