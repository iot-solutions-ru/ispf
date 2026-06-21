import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { updateAlertRule, validateExpression, fetchVariables } from "../../api";
import type { CreateAlertRulePayload } from "../../types/automation";
import { variableBoolean, variableString } from "../../utils/variableFieldValue";
import ObjectFederationBindSection from "../ObjectFederationBindSection";

interface AlertRuleInspectorProps {
  path: string;
  canManage?: boolean;
}

export default function AlertRuleInspector({ path, canManage = false }: AlertRuleInspectorProps) {
  const queryClient = useQueryClient();
  const variablesQuery = useQuery({
    queryKey: ["variables", path],
    queryFn: () => fetchVariables(path),
  });

  const variables = variablesQuery.data ?? [];
  const form = {
    objectPath: variableString(variables, "targetObjectPath"),
    watchVariable: variableString(variables, "watchVariable"),
    conditionExpr: variableString(variables, "conditionExpr"),
    eventName: variableString(variables, "eventName"),
    payloadVariable: variableString(variables, "payloadVariable"),
    enabled: variableBoolean(variables, "enabled", true),
    edgeTrigger: variableBoolean(variables, "edgeTrigger", true),
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

  if (variablesQuery.isLoading) {
    return <p className="hint">Загрузка правила…</p>;
  }

  return (
    <section className="automation-inspector">
      <header className="automation-panel-head">
        <div>
          <h3>Правило алерта</h3>
          <p className="hint">CEL-условие на изменение переменной → публикация события</p>
          <p className="hint">
            <code>{path}</code>
          </p>
        </div>
      </header>
      <form
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
          });
        }}
      >
        <label className="full">
          Объект (targetObjectPath) *
          <input name="objectPath" defaultValue={form.objectPath} required readOnly={!canManage} />
        </label>
        <label>
          Переменная *
          <input name="watchVariable" defaultValue={form.watchVariable} required readOnly={!canManage} />
        </label>
        <label>
          Событие *
          <input name="eventName" defaultValue={form.eventName} required readOnly={!canManage} />
        </label>
        <label className="full">
          CEL-условие *
          <textarea
            name="conditionExpr"
            defaultValue={form.conditionExpr}
            rows={3}
            required
            readOnly={!canManage}
          />
        </label>
        <label className="full">
          Payload variable
          <input name="payloadVariable" defaultValue={form.payloadVariable} readOnly={!canManage} />
        </label>
        <label className="checkbox">
          <input
            type="checkbox"
            name="enabled"
            defaultChecked={form.enabled}
            disabled={!canManage}
          />
          Включено
        </label>
        <label className="checkbox">
          <input
            type="checkbox"
            name="edgeTrigger"
            defaultChecked={form.edgeTrigger}
            disabled={!canManage}
          />
          Edge trigger
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
              Проверить CEL
            </button>
            <button type="submit" className="btn primary" disabled={saveMutation.isPending}>
              Сохранить
            </button>
          </div>
        )}
        {validateMutation.data && (
          <p className={`hint full ${validateMutation.data.valid ? "" : "error"}`}>
            {validateMutation.data.valid ? "CEL OK" : validateMutation.data.error}
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
