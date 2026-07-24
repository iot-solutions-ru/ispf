import { useEffect, useMemo, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { Alert, Button } from "antd";
import { fetchObject, fetchVariables, updateAlertRule, validateExpression } from "../../api";
import type { AlertRuleFormValues } from "../../types/automation";
import { variableBoolean, variableNumber, variableString } from "../../utils/object/variableFieldValue";
import { inspectorQueryLoading, useInspectorVariables } from "../../hooks/useInspectorQueries";
import ObjectFederationBindSection from "../federation/ObjectFederationBindSection";
import AlertRuleFormFields, { toCreateAlertRulePayload } from "./AlertRuleFormFields";

interface AlertRuleInspectorProps {
  path: string;
  canManage?: boolean;
}

function formFromVariables(
  variables: Parameters<typeof variableString>[0]
): AlertRuleFormValues {
  return {
    objectPath: variableString(variables, "targetObjectPath"),
    watchVariable: variableString(variables, "watchVariable"),
    conditionExpr: variableString(variables, "conditionExpr"),
    eventName: variableString(variables, "eventName"),
    payloadVariable: variableString(variables, "payloadVariable"),
    enabled: variableBoolean(variables, "enabled", true),
    edgeTrigger: variableBoolean(variables, "edgeTrigger", true),
    delaySeconds: variableNumber(variables, "delaySeconds", 0),
    sustainWhileTrue: variableBoolean(variables, "sustainWhileTrue", false),
    notificationWebhookUrl: variableString(variables, "notificationWebhookUrl"),
    notificationEmailTarget: variableString(variables, "notificationEmailTarget"),
    priority: (variableString(variables, "priority") || "HIGH") as AlertRuleFormValues["priority"],
    ackRequired: variableBoolean(variables, "ackRequired", false),
    rateLimitSeconds: variableNumber(variables, "rateLimitSeconds", 0),
    deactivateExpr: variableString(variables, "deactivateExpr"),
    deactivateDelaySeconds: variableNumber(variables, "deactivateDelaySeconds", 0),
    pollIntervalMs: variableNumber(variables, "pollIntervalMs", 0),
    triggerMessage: variableString(variables, "triggerMessage"),
    clearEventName: variableString(variables, "clearEventName"),
    anomalyModelId: variableString(variables, "anomalyModelId"),
  };
}

export default function AlertRuleInspector({ path, canManage = false }: AlertRuleInspectorProps) {
  const { t } = useTranslation(["automation", "common"]);
  const queryClient = useQueryClient();
  const variablesQuery = useInspectorVariables(path);
  const variables = variablesQuery.data ?? [];

  const [form, setForm] = useState<AlertRuleFormValues>(() => formFromVariables(variables));
  const [exprError, setExprError] = useState<string | null>(null);

  useEffect(() => {
    if (variablesQuery.data) {
      setForm(formFromVariables(variablesQuery.data));
      setExprError(null);
    }
  }, [path, variablesQuery.data]);

  const targetPath = form.objectPath.trim();
  const targetVariablesQuery = useQuery({
    queryKey: ["variables", targetPath],
    queryFn: () => fetchVariables(targetPath),
    enabled: targetPath.length > 0,
  });
  const alertObjectQuery = useQuery({
    queryKey: ["object", path],
    queryFn: () => fetchObject(path),
    enabled: path.length > 0,
  });

  const targetVariableNames = useMemo(() => {
    const list = targetVariablesQuery.data ?? [];
    return list.map((item) => item.name).sort((a, b) => a.localeCompare(b));
  }, [targetVariablesQuery.data]);

  const alertEventNames = useMemo(() => {
    const names = alertObjectQuery.data?.eventNames ?? [];
    return [...names].sort((a, b) => a.localeCompare(b));
  }, [alertObjectQuery.data]);

  const status = {
    lastFiredAt: variableString(variables, "lastFiredAt") || undefined,
    lastConditionMet: variables.some((item) => item.name === "lastConditionMet")
      ? variableBoolean(variables, "lastConditionMet", false)
      : undefined,
    latchedActive: variableBoolean(variables, "latchedActive", false),
    conditionTrueSince: variableString(variables, "conditionTrueSince") || undefined,
    deactivateTrueSince: variableString(variables, "deactivateTrueSince") || undefined,
  };

  const saveMutation = useMutation({
    mutationFn: () => {
      const payload = toCreateAlertRulePayload(form, path.split(".").pop() ?? "rule");
      const { name: _name, ...update } = payload;
      return updateAlertRule(path, update);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["variables", path] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["alert-rules"] });
    },
  });

  const validateMutation = useMutation({
    mutationFn: () => validateExpression(form.conditionExpr),
    onSuccess: (result) => setExprError(result.valid ? null : result.error ?? "CEL invalid"),
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
        className="alert-rule-inspector-form"
        onSubmit={(e) => {
          e.preventDefault();
          if (!canManage) {
            return;
          }
          if (!form.objectPath.trim() || !form.watchVariable.trim() || !form.eventName.trim() || !form.conditionExpr.trim()) {
            return;
          }
          saveMutation.mutate();
        }}
      >
        <AlertRuleFormFields
          value={form}
          onChange={(patch) => {
            setForm((current) => ({ ...current, ...patch }));
            if (patch.conditionExpr !== undefined) {
              setExprError(null);
            }
          }}
          canManage={canManage}
          targetVariableNames={targetVariableNames}
          alertEventNames={alertEventNames}
          status={status}
          exprError={exprError}
          onValidateCel={() => {
            if (form.conditionExpr.trim()) {
              validateMutation.mutate();
            }
          }}
        />
        {validateMutation.data?.valid && !exprError && (
          <p className="hint">{t("automation:alertRule.celOk")}</p>
        )}
        {canManage && (
          <div className="form-actions">
            <Button htmlType="submit" type="primary" loading={saveMutation.isPending} disabled={!!exprError}>
              {t("common:action.save")}
            </Button>
          </div>
        )}
        {saveMutation.error && (
          <Alert type="error" message={String(saveMutation.error)} showIcon />
        )}
      </form>
      <ObjectFederationBindSection path={path} canManage={canManage} />
    </section>
  );
}
