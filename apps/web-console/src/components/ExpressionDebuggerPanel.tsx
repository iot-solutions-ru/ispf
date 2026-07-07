import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation } from "@tanstack/react-query";
import { evaluateExpression } from "../api";
import BindingExpressionField from "./BindingExpressionField";
import type { VariableDto } from "../types";

interface ExpressionDebuggerPanelProps {
  objectPath: string;
  variables?: VariableDto[];
  functionNames?: string[];
  compact?: boolean;
}

export default function ExpressionDebuggerPanel({
  objectPath,
  variables = [],
  functionNames = [],
  compact = false,
}: ExpressionDebuggerPanelProps) {
  const { t } = useTranslation("inspector");
  const [expression, setExpression] = useState("");
  const [targetVariable, setTargetVariable] = useState("");

  const variableNames = useMemo(
    () => variables.map((variable) => variable.name),
    [variables]
  );

  const evaluateMutation = useMutation({
    mutationFn: () =>
      evaluateExpression({
        objectPath,
        expression: expression.trim(),
        targetVariable: targetVariable.trim() || undefined,
      }),
  });

  const steps = evaluateMutation.data?.steps ?? [];

  return (
    <div className={`expression-debugger-panel${compact ? " compact" : ""}`}>
      <header className="expression-debugger-header">
        <h4>{t("expressionDebugger.title")}</h4>
        <p className="hint">{t("expressionDebugger.hint")}</p>
      </header>

      <div className="expression-debugger-form">
        <label>
          <span>{t("expressionDebugger.expression")}</span>
          <BindingExpressionField
            id="expression-debugger-expr"
            value={expression}
            onChange={setExpression}
            objectPath={objectPath}
            variableNames={variableNames}
            functionNames={functionNames}
          />
        </label>

        {variableNames.length > 0 && (
          <label>
            <span>{t("expressionDebugger.targetVariable")}</span>
            <select
              value={targetVariable}
              onChange={(e) => setTargetVariable(e.target.value)}
            >
              <option value="">{t("expressionDebugger.targetNone")}</option>
              {variableNames.map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </select>
          </label>
        )}

        <div className="expression-debugger-actions">
          <button
            type="button"
            className="btn primary small"
            disabled={!expression.trim() || evaluateMutation.isPending}
            onClick={() => evaluateMutation.mutate()}
          >
            {evaluateMutation.isPending
              ? t("expressionDebugger.evaluating")
              : t("expressionDebugger.evaluate")}
          </button>
        </div>
      </div>

      {evaluateMutation.error && (
        <p className="hint error">{(evaluateMutation.error as Error).message}</p>
      )}

      {evaluateMutation.data && (
        <section className="expression-debugger-result">
          {evaluateMutation.data.valid ? (
            <p className="hint success">{t("expressionDebugger.success")}</p>
          ) : (
            <p className="hint error">
              {evaluateMutation.data.error ?? t("expressionDebugger.failed")}
            </p>
          )}
          {evaluateMutation.data.result !== undefined && evaluateMutation.data.result !== null && (
            <div className="expression-debugger-output">
              <span className="hint">
                {t("expressionDebugger.resultType", {
                  type: evaluateMutation.data.resultType ?? "unknown",
                })}
              </span>
              <pre className="mono">{JSON.stringify(evaluateMutation.data.result, null, 2)}</pre>
            </div>
          )}
        </section>
      )}

      {steps.length > 0 && (
        <section className="expression-debugger-steps">
          <h5>{t("expressionDebugger.steps")}</h5>
          <ol className="expression-debugger-step-list">
            {steps.map((step, index) => (
              <li
                key={`${step.phase}-${index}`}
                className={step.status === "error" ? "error" : step.status === "ok" ? "ok" : ""}
              >
                <code>{step.phase}</code>
                <span className="inline-badge">{step.status}</span>
                {step.detail !== undefined && step.detail !== null && (
                  <pre className="mono compact">
                    {typeof step.detail === "string"
                      ? step.detail
                      : JSON.stringify(step.detail, null, 2)}
                  </pre>
                )}
              </li>
            ))}
          </ol>
        </section>
      )}
    </div>
  );
}
