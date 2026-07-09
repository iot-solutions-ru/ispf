import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { updateAlertRule, validateExpression } from "../../api";
import type { CreateAlertRulePayload } from "../../types/automation";
import { variableBoolean, variableNumber, variableString } from "../../utils/variableFieldValue";
import { inspectorQueryLoading, useInspectorVariables } from "../../hooks/useInspectorQueries";
import ObjectFederationBindSection from "../ObjectFederationBindSection";

interface AlertRuleInspectorProps {
  path: string;
  canManage?: boolean;
}

export default function AlertRuleInspector({ path, canManage = false }: AlertRuleInspectorProps) {
  const { t } = useTranslation(["automation", "common"]);
  const queryClient = useQueryClient();
  const variablesQuery = useInspectorVariables(path);

  const variables = variablesQuery.data ?? [];
  const form = {
    objectPath: variableString(variables, "targetObjectPath"),
    watchVariable: variableString(variables, "watchVariable"),
    conditionExpr: variableString(variables, "conditionExpr"),
    eventName: variableString(variables, "eventName"),
    payloadVariable: variableString(variables, "payloadVariable"),
    enabled: variableBoolean(variables, "enabled", true),
    edgeTrigger: variableBoolean(variables, "edgeTrigger", true),
    notificationWebhookUrl: variableString(variables, "notificationWebhookUrl"),
    notificationEmailTarget: variableString(variables, "notificationEmailTarget"),
    priority: variableString(variables, "priority") || "HIGH",
    ackRequired: variableBoolean(variables, "ackRequired", false),
    rateLimitSeconds: variableNumber(variables, "rateLimitSeconds", 0),
    deactivateExpr: variableString(variables, "deactivateExpr"),
    deactivateDelaySeconds: variableNumber(variables, "deactivateDelaySeconds", 0),
    pollIntervalMs: variableNumber(variables, "pollIntervalMs", 0),
    triggerMessage: variableString(variables, "triggerMessage"),
    clearEventName: variableString(variables, "clearEventName"),
    latchedActive: variableBoolean(variables, "latchedActive", false),
  };

  const saveMutation = useMutation({
    mutationFn: (payload: Partial<CreateAlertRulePayload>) =>
      updateAlertRule(path, {
        ...payload,
        payloadVariable: payload.payloadVariable?.trim() || undefined,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["variables", path] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
    },
  });

  const validateMutation = useMutation({
    mutationFn: (expression: string) => validateExpression(expression),
  });

  if (inspectorQueryLoading(variablesQuery)) {
    return <p className="hint">{t("automation:alertRule.loading")}</p>;
  }

  return (
    <section className="automation-inspector">
      <header className="automation-panel-head">
        <div>
          <h3>{t("automation:alertRule.title")}</h3>
          <p className="hint">{t("automation:alertRule.subtitle")}</p>
          <p className="hint">
            <code>{path}</code>
          </p>
        </div>
      </header>
      <form
        key={path}
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault();
          if (!canManage) {
            return;
          }
          const data = new FormData(e.currentTarget);
          saveMutation.mutate({
            objectPath: String(data.get("objectPath") ?? ""),
            watchVariable: String(data.get("watchVariable") ?? ""),
            conditionExpr: String(data.get("conditionExpr") ?? ""),
            eventName: String(data.get("eventName") ?? ""),
            payloadVariable: String(data.get("payloadVariable") ?? ""),
            enabled: data.get("enabled") === "on",
            edgeTrigger: data.get("edgeTrigger") === "on",
            notificationWebhookUrl: String(data.get("notificationWebhookUrl") ?? "").trim() || undefined,
            notificationEmailTarget: String(data.get("notificationEmailTarget") ?? "").trim() || undefined,
            priority: String(data.get("priority") ?? "HIGH") as CreateAlertRulePayload["priority"],
            ackRequired: data.get("ackRequired") === "on",
            rateLimitSeconds: Number(data.get("rateLimitSeconds") ?? 0),
            deactivateExpr: String(data.get("deactivateExpr") ?? ""),
            deactivateDelaySeconds: Number(data.get("deactivateDelaySeconds") ?? 0),
            pollIntervalMs: Number(data.get("pollIntervalMs") ?? 0),
            triggerMessage: String(data.get("triggerMessage") ?? ""),
            clearEventName: String(data.get("clearEventName") ?? ""),
          });
        }}
      >
        <label className="full">
          {t("automation:alertRule.targetObject")}
          <input name="objectPath" defaultValue={form.objectPath} required readOnly={!canManage} />
        </label>
        <label>
          {t("automation:alertRule.variable")}
          <input name="watchVariable" defaultValue={form.watchVariable} required readOnly={!canManage} />
        </label>
        <label>
          {t("automation:alertRule.event")}
          <input name="eventName" defaultValue={form.eventName} required readOnly={!canManage} />
        </label>
        <label className="full">
          {t("automation:alertRule.celCondition")}
          <textarea
            name="conditionExpr"
            defaultValue={form.conditionExpr}
            rows={3}
            required
            readOnly={!canManage}
          />
        </label>
        <label className="full">
          {t("automation:alertRule.deactivateExpr")}
          <textarea
            name="deactivateExpr"
            defaultValue={form.deactivateExpr}
            rows={2}
            readOnly={!canManage}
            placeholder={t("automation:alertRule.deactivateExprPlaceholder")}
          />
        </label>
        <label>
          {t("automation:alertRule.clearEvent")}
          <input name="clearEventName" defaultValue={form.clearEventName} readOnly={!canManage} />
        </label>
        <label>
          {t("automation:alertRule.deactivateDelaySeconds")}
          <input
            name="deactivateDelaySeconds"
            type="number"
            min={0}
            step={1}
            defaultValue={form.deactivateDelaySeconds}
            readOnly={!canManage}
          />
        </label>
        <label>
          {t("automation:alertRule.pollIntervalMs")}
          <input
            name="pollIntervalMs"
            type="number"
            min={0}
            step={100}
            defaultValue={form.pollIntervalMs}
            readOnly={!canManage}
          />
        </label>
        <label className="full">
          {t("automation:alertRule.triggerMessage")}
          <input name="triggerMessage" defaultValue={form.triggerMessage} readOnly={!canManage} />
        </label>
        {form.latchedActive && (
          <p className="hint full">{t("automation:alertRule.latchedActive")}</p>
        )}
        <label className="full">
          {t("automation:alertRule.payloadVariable")}
          <input name="payloadVariable" defaultValue={form.payloadVariable} readOnly={!canManage} />
        </label>
        <label>
          {t("automation:alertRule.priority")}
          <select name="priority" defaultValue={form.priority} disabled={!canManage}>
            <option value="CRITICAL">CRITICAL</option>
            <option value="HIGH">HIGH</option>
            <option value="MEDIUM">MEDIUM</option>
            <option value="LOW">LOW</option>
          </select>
        </label>
        <label>
          {t("automation:alertRule.rateLimitSeconds")}
          <input
            name="rateLimitSeconds"
            type="number"
            min={0}
            step={1}
            defaultValue={form.rateLimitSeconds}
            readOnly={!canManage}
          />
        </label>
        <p className="hint full">{t("automation:alertRule.notificationHint")}</p>
        <label className="full">
          {t("automation:alertRule.notificationWebhook")}
          <input
            name="notificationWebhookUrl"
            defaultValue={form.notificationWebhookUrl}
            readOnly={!canManage}
            placeholder="https://hooks.example.com/alerts"
          />
        </label>
        <label className="full">
          {t("automation:alertRule.notificationEmail")}
          <input
            name="notificationEmailTarget"
            defaultValue={form.notificationEmailTarget}
            readOnly={!canManage}
            placeholder="ops@example.com|Alert|Threshold exceeded"
          />
        </label>
        <label className="checkbox">
          <input
            type="checkbox"
            name="enabled"
            defaultChecked={form.enabled}
            disabled={!canManage}
          />
          {t("automation:alertRule.enabled")}
        </label>
        <label className="checkbox">
          <input
            type="checkbox"
            name="edgeTrigger"
            defaultChecked={form.edgeTrigger}
            disabled={!canManage}
          />
          {t("automation:alertRule.edgeTrigger")}
        </label>
        <label className="checkbox">
          <input
            type="checkbox"
            name="ackRequired"
            defaultChecked={form.ackRequired}
            disabled={!canManage}
          />
          {t("automation:alertRule.ackRequired")}
        </label>
        {canManage && (
          <div className="form-actions full">
            <button
              type="button"
              className="btn"
              onClick={() => {
                const expression = (
                  document.querySelector(
                    'textarea[name="conditionExpr"]'
                  ) as HTMLTextAreaElement | null
                )?.value;
                if (expression) {
                  validateMutation.mutate(expression);
                }
              }}
            >
              {t("automation:alertRule.validateCel")}
            </button>
            <button type="submit" className="btn primary" disabled={saveMutation.isPending}>
              {t("common:action.save")}
            </button>
          </div>
        )}
        {validateMutation.data && (
          <p className={`hint full ${validateMutation.data.valid ? "" : "error"}`}>
            {validateMutation.data.valid ? t("automation:alertRule.celOk") : validateMutation.data.error}
          </p>
        )}
        {saveMutation.error && (
          <p className="hint error full">{String(saveMutation.error)}</p>
        )}
      </form>
      <ObjectFederationBindSection path={path} canManage={canManage} />
    </section>
  );
}
