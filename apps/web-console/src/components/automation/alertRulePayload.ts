// Extracted from AlertRuleFormFields.tsx so that the component module only exports components
// (react-refresh/only-export-components).
import type { AlertRuleFormValues, CreateAlertRulePayload } from "../../types/automation";

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
