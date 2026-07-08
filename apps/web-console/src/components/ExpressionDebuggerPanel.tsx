import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation } from "@tanstack/react-query";
import { evaluateExpression, type EvaluateExpressionStep } from "../api";
import BindingExpressionField from "./BindingExpressionField";
import ExpressionDebuggerWatchList from "./ExpressionDebuggerWatchList";
import type { VariableDto } from "../types";

interface ExpressionDebuggerPanelProps {
  objectPath: string;
  variables?: VariableDto[];
  functionNames?: string[];
  compact?: boolean;
}

function isBindingsStep(phase: string): boolean {
  return phase === "cel-bindings" || phase === "variable-context";
}

function renderStepDetail(step: EvaluateExpressionStep): string {
  if (step.detail === undefined || step.detail === null) {
    return "";
  }
  if (typeof step.detail === "string") {
    return step.detail;
  }
  return JSON.stringify(step.detail, null, 2);
}

function BindingsTable({ detail }: { detail: unknown }) {
  if (!detail || typeof detail !== "object") {
    return null;
  }
  const record = detail as Record<string, unknown>;
  const self = record.self;
  const parent = record.parent;
  const context = record.context;

  if (self !== undefined || parent !== undefined || context !== undefined) {
    return (
      <div className="expression-debugger-bindings">
        {self !== undefined && (
          <div className="expression-debugger-binding-group">
            <span className="expression-debugger-binding-label">self</span>
            <pre className="mono compact">{JSON.stringify(self, null, 2)}</pre>
          </div>
        )}
        {parent !== undefined && (
          <div className="expression-debugger-binding-group">
            <span className="expression-debugger-binding-label">parent</span>
            <pre className="mono compact">{JSON.stringify(parent, null, 2)}</pre>
          </div>
        )}
        {context !== undefined && (
          <div className="expression-debugger-binding-group">
            <span className="expression-debugger-binding-label">context</span>
            <pre className="mono compact">{JSON.stringify(context, null, 2)}</pre>
          </div>
        )}
      </div>
    );
  }

  return (
    <div className="expression-debugger-bindings">
      {Object.entries(record).map(([name, value]) => (
        <div key={name} className="expression-debugger-binding-row">
          <code>{name}</code>
          <span className="expression-debugger-binding-value">{JSON.stringify(value)}</span>
        </div>
      ))}
    </div>
  );
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
  const [revealedSteps, setRevealedSteps] = useState(0);
  const [pinnedWatch, setPinnedWatch] = useState<string[]>([]);

  const toggleWatchPin = useCallback((name: string) => {
    setPinnedWatch((prev) =>
      prev.includes(name) ? prev.filter((item) => item !== name) : [...prev, name]
    );
  }, []);

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
    onSuccess: (data) => {
      setRevealedSteps(data.steps.length > 0 ? 1 : 0);
    },
  });

  const steps = evaluateMutation.data?.steps ?? [];
  const visibleSteps = steps.slice(0, revealedSteps);
  const canRevealMore = revealedSteps < steps.length;

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

      <ExpressionDebuggerWatchList
        pinned={pinnedWatch}
        onTogglePin={toggleWatchPin}
        variables={variables}
        steps={steps}
        revealedSteps={revealedSteps}
      />

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
          <div className="expression-debugger-steps-header">
            <h5>{t("expressionDebugger.steps")}</h5>
            <div className="expression-debugger-step-actions">
              {canRevealMore && (
                <button
                  type="button"
                  className="btn small"
                  onClick={() => setRevealedSteps((count) => Math.min(count + 1, steps.length))}
                >
                  {t("expressionDebugger.stepNext")}
                </button>
              )}
              {revealedSteps < steps.length && (
                <button
                  type="button"
                  className="btn small"
                  onClick={() => setRevealedSteps(steps.length)}
                >
                  {t("expressionDebugger.stepShowAll")}
                </button>
              )}
            </div>
          </div>
          <ol className="expression-debugger-step-list">
            {visibleSteps.map((step, index) => (
              <li
                key={`${step.phase}-${index}`}
                className={`expression-debugger-step${
                  step.status === "error" ? " error" : step.status === "ok" ? " ok" : ""
                }${index === visibleSteps.length - 1 ? " active" : ""}`}
              >
                <div className="expression-debugger-step-head">
                  <span className="expression-debugger-step-index">{index + 1}</span>
                  <code>{t(`expressionDebugger.phase.${step.phase}`, { defaultValue: step.phase })}</code>
                  <span className="inline-badge">{step.status}</span>
                </div>
                {step.detail !== undefined && step.detail !== null && (
                  isBindingsStep(step.phase) ? (
                    <BindingsTable detail={step.detail} />
                  ) : (
                    <pre className="mono compact">{renderStepDetail(step)}</pre>
                  )
                )}
              </li>
            ))}
          </ol>
        </section>
      )}
    </div>
  );
}
