import { useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { createAlertRule, fetchVariables, validateExpression } from "../../api";
import type { AlertRuleFormValues } from "../../types/automation";
import AlertRuleFormFields, { toCreateAlertRulePayload } from "./AlertRuleFormFields";

interface CreateAlertRuleDialogProps {
  onClose: () => void;
  onCreated: () => void;
}

const DEFAULT: AlertRuleFormValues = {
  name: "",
  objectPath: "root.platform.devices.demo-sensor-01",
  watchVariable: "temperature",
  conditionExpr: 'self.temperature["value"] > 80.0',
  eventName: "thresholdExceeded",
  payloadVariable: "",
  enabled: true,
  edgeTrigger: true,
  delaySeconds: 0,
  sustainWhileTrue: false,
  priority: "HIGH",
  ackRequired: false,
  rateLimitSeconds: 0,
  deactivateDelaySeconds: 0,
  pollIntervalMs: 0,
};

export default function CreateAlertRuleDialog({ onClose, onCreated }: CreateAlertRuleDialogProps) {
  const { t } = useTranslation(["automation", "common"]);
  const [form, setForm] = useState<AlertRuleFormValues>({ ...DEFAULT });
  const [exprError, setExprError] = useState<string | null>(null);

  const targetPath = form.objectPath.trim();
  const targetVariablesQuery = useQuery({
    queryKey: ["variables", targetPath],
    queryFn: () => fetchVariables(targetPath),
    enabled: targetPath.length > 0,
  });

  const targetVariableNames = useMemo(() => {
    const list = targetVariablesQuery.data ?? [];
    return list.map((item) => item.name).sort((a, b) => a.localeCompare(b));
  }, [targetVariablesQuery.data]);

  const createMutation = useMutation({
    mutationFn: () => createAlertRule(toCreateAlertRulePayload(form, (form.name ?? "").trim())),
    onSuccess: () => onCreated(),
  });

  const validateMutation = useMutation({
    mutationFn: () => validateExpression(form.conditionExpr),
    onSuccess: (result) => setExprError(result.valid ? null : result.error),
  });

  return (
    <div className="modal-backdrop" role="presentation" onClick={onClose}>
      <div className="modal modal-wide alert-rule-modal" onClick={(e) => e.stopPropagation()}>
        <header>
          <h3>{t("automation:alertRule.newTitle")}</h3>
          <button type="button" className="icon-btn" onClick={onClose}>
            ✕
          </button>
        </header>
        <form
          onSubmit={(e) => {
            e.preventDefault();
            if (!form.name?.trim() || !!exprError) {
              return;
            }
            createMutation.mutate();
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
            canManage
            showName
            creating
            targetVariableNames={targetVariableNames}
            alertEventNames={[]}
            exprError={exprError}
            onValidateCel={() => {
              if (form.conditionExpr.trim()) {
                validateMutation.mutate();
              }
            }}
          />
          {createMutation.error && (
            <p className="hint error">{(createMutation.error as Error).message}</p>
          )}
          <footer className="form-actions">
            <button type="button" className="btn" onClick={onClose}>
              {t("common:action.cancel")}
            </button>
            <button
              type="submit"
              className="btn primary"
              disabled={
                createMutation.isPending
                || !form.name?.trim()
                || !form.objectPath.trim()
                || !form.watchVariable.trim()
                || !form.eventName.trim()
                || !form.conditionExpr.trim()
                || !!exprError
              }
            >
              {t("common:action.create")}
            </button>
          </footer>
        </form>
      </div>
    </div>
  );
}
