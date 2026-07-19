import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation } from "@tanstack/react-query";
import { evaluateExpression, type EvaluateExpressionStep } from "../../api";
import ExpressionDebuggerWatchList from "./ExpressionDebuggerWatchList";
import type { VariableDto } from "../../types";

export interface ExpressionDebuggerSectionProps {
  objectPath: string;
  expression: string;
  variables?: VariableDto[];
  variableNames?: string[];
  disabled?: boolean;
  embedded?: boolean;
}

/** Phases operators can set breakpoints on (BL-149). */
export const EXPRESSION_DEBUG_BREAKPOINT_PHASES = [
  "validate",
  "load-object",
  "variable-context",
  "compile-cel",
  "cel-bindings",
  "compile",
  "platform-binding",
  "evaluate",
  "map-result",
] as const;

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
  const input = record.input;

  if (self !== undefined || parent !== undefined || context !== undefined || input !== undefined) {
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
        {input !== undefined && (
          <div className="expression-debugger-binding-group">
            <span className="expression-debugger-binding-label">input</span>
            <pre className="mono compact">{JSON.stringify(input, null, 2)}</pre>
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

function resolveWatchVariables(
  variables: VariableDto[] | undefined,
  variableNames: string[] | undefined
): VariableDto[] {
  if (variables?.length) {
    return variables;
  }
  return (variableNames ?? []).map((name) => ({
    name,
    value: null,
    readable: true,
    writable: false,
    updatedAt: null,
    historyEnabled: false,
    historyRetentionDays: null,
  }));
}

export default function ExpressionDebuggerSection({
  objectPath,
  expression,
  variables,
  variableNames,
  disabled = false,
  embedded = false,
}: ExpressionDebuggerSectionProps) {
  const { t } = useTranslation("inspector");
  const [targetVariable, setTargetVariable] = useState("");
  const [revealedSteps, setRevealedSteps] = useState(0);
  const [pinnedWatch, setPinnedWatch] = useState<string[]>([]);
  const [breakpoints, setBreakpoints] = useState<string[]>(["evaluate"]);
  const [pausedAt, setPausedAt] = useState<string | null>(null);

  const toggleWatchPin = useCallback((name: string) => {
    setPinnedWatch((prev) =>
      prev.includes(name) ? prev.filter((item) => item !== name) : [...prev, name]
    );
  }, []);

  const toggleBreakpoint = useCallback((phase: string) => {
    setBreakpoints((prev) =>
      prev.includes(phase) ? prev.filter((item) => item !== phase) : [...prev, phase]
    );
  }, []);

  const watchVariables = useMemo(
    () => resolveWatchVariables(variables, variableNames),
    [variables, variableNames]
  );

  const nameOptions = useMemo(
    () => watchVariables.map((variable) => variable.name),
    [watchVariables]
  );

  const evaluateMutation = useMutation({
    mutationFn: (resumeFrom?: string) =>
      evaluateExpression({
        objectPath,
        expression: expression.trim(),
        targetVariable: targetVariable.trim() || undefined,
        breakpoints,
        resumeFrom,
      }),
    onSuccess: (data) => {
      setRevealedSteps(data.steps.length > 0 ? data.steps.length : 0);
      setPausedAt(data.paused ? data.pausedAt ?? null : null);
    },
  });

  const steps = evaluateMutation.data?.steps ?? [];
  const visibleSteps = steps.slice(0, revealedSteps);
  const canRevealMore = revealedSteps < steps.length;
  const isPaused = Boolean(evaluateMutation.data?.paused && pausedAt);

  return (
    <div
      className={`expression-debugger-section${embedded ? " embedded" : ""}`}
      data-testid="expression-debugger-section"
    >
      {!embedded && (
        <header className="expression-debugger-header">
          <h4>{t("expressionDebugger.title")}</h4>
          <p className="hint">{t("expressionDebugger.hint")}</p>
        </header>
      )}

      <div className="expression-debugger-form">
        {nameOptions.length > 0 && (
          <label>
            <span>{t("expressionDebugger.targetVariable")}</span>
            <select
              value={targetVariable}
              disabled={disabled}
              onChange={(e) => setTargetVariable(e.target.value)}
            >
              <option value="">{t("expressionDebugger.targetNone")}</option>
              {nameOptions.map((name) => (
                <option key={name} value={name}>
                  {name}
                </option>
              ))}
            </select>
          </label>
        )}

        <fieldset className="expression-debugger-breakpoints" data-testid="expression-debugger-breakpoints">
          <legend>{t("expressionDebugger.breakpoints")}</legend>
          <p className="hint">{t("expressionDebugger.breakpointsHint")}</p>
          <div className="expression-debugger-breakpoint-list">
            {EXPRESSION_DEBUG_BREAKPOINT_PHASES.map((phase) => (
              <label key={phase} className="expression-debugger-breakpoint-item">
                <input
                  type="checkbox"
                  checked={breakpoints.includes(phase)}
                  disabled={disabled}
                  data-testid={`expression-debugger-bp-${phase}`}
                  onChange={() => toggleBreakpoint(phase)}
                />
                <code>{t(`expressionDebugger.phase.${phase}`, { defaultValue: phase })}</code>
              </label>
            ))}
          </div>
        </fieldset>

        <div className="expression-debugger-actions">
          <button
            type="button"
            className="btn primary small"
            disabled={disabled || !expression.trim() || evaluateMutation.isPending}
            onClick={() => {
              setPausedAt(null);
              evaluateMutation.mutate(undefined);
            }}
          >
            {evaluateMutation.isPending
              ? t("expressionDebugger.evaluating")
              : t("expressionDebugger.evaluate")}
          </button>
          {isPaused && pausedAt && (
            <button
              type="button"
              className="btn small"
              data-testid="expression-debugger-continue"
              disabled={disabled || evaluateMutation.isPending}
              onClick={() => evaluateMutation.mutate(pausedAt)}
            >
              {t("expressionDebugger.continue")}
            </button>
          )}
        </div>
      </div>

      {evaluateMutation.error && (
        <p className="hint error">{(evaluateMutation.error as Error).message}</p>
      )}

      {isPaused && pausedAt && (
        <p className="hint" data-testid="expression-debugger-paused">
          {t("expressionDebugger.pausedAt", {
            phase: t(`expressionDebugger.phase.${pausedAt}`, { defaultValue: pausedAt }),
          })}
        </p>
      )}

      <ExpressionDebuggerWatchList
        pinned={pinnedWatch}
        onTogglePin={toggleWatchPin}
        variables={watchVariables}
        steps={steps}
        revealedSteps={revealedSteps}
      />

      {evaluateMutation.data && !evaluateMutation.data.paused && (
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
                  step.status === "error"
                    ? " error"
                    : step.status === "paused"
                      ? " paused"
                      : step.status === "ok"
                        ? " ok"
                        : ""
                }${index === visibleSteps.length - 1 ? " active" : ""}`}
              >
                <div className="expression-debugger-step-head">
                  <span className="expression-debugger-step-index">{index + 1}</span>
                  <code>{t(`expressionDebugger.phase.${step.phase}`, { defaultValue: step.phase })}</code>
                  <span className="inline-badge">{step.status}</span>
                  {breakpoints.includes(step.phase) && (
                    <span className="inline-badge" title={t("expressionDebugger.breakpointActive")}>
                      BP
                    </span>
                  )}
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
