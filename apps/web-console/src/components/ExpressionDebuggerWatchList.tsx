import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { EvaluateExpressionStep } from "../api";
import type { VariableDto } from "../types";

interface ExpressionDebuggerWatchListProps {
  pinned: string[];
  onTogglePin: (name: string) => void;
  variables: VariableDto[];
  steps: EvaluateExpressionStep[];
  revealedSteps: number;
}

function lookupInRecord(record: Record<string, unknown>, name: string): unknown {
  if (name in record) {
    return record[name];
  }
  const lower = name.toLowerCase();
  for (const [key, value] of Object.entries(record)) {
    if (key.toLowerCase() === lower) {
      return value;
    }
  }
  return undefined;
}

function extractFromBindingsDetail(detail: unknown, name: string): unknown {
  if (!detail || typeof detail !== "object") {
    return undefined;
  }
  const record = detail as Record<string, unknown>;
  const direct = lookupInRecord(record, name);
  if (direct !== undefined) {
    return direct;
  }
  for (const bucket of ["self", "parent", "context"] as const) {
    const nested = record[bucket];
    if (nested && typeof nested === "object") {
      const hit = lookupInRecord(nested as Record<string, unknown>, name);
      if (hit !== undefined) {
        return hit;
      }
    }
  }
  return undefined;
}

/** Resolve pinned variable values from revealed steps and live variable DTOs (BL-149). */
export function resolvePinnedWatchValues(
  pinned: string[],
  steps: EvaluateExpressionStep[],
  revealedSteps: number,
  variables: VariableDto[]
): Record<string, unknown> {
  const liveByName = new Map(variables.map((v) => [v.name, v.value]));
  const result: Record<string, unknown> = {};

  for (const name of pinned) {
    if (liveByName.has(name)) {
      result[name] = liveByName.get(name);
      continue;
    }
    const visible = steps.slice(0, revealedSteps);
    for (let i = visible.length - 1; i >= 0; i -= 1) {
      const step = visible[i];
      if (step.phase !== "variable-context" && step.phase !== "cel-bindings") {
        continue;
      }
      const hit = extractFromBindingsDetail(step.detail, name);
      if (hit !== undefined) {
        result[name] = hit;
        break;
      }
    }
  }

  return result;
}

function formatWatchValue(value: unknown): string {
  if (value === undefined) {
    return "—";
  }
  if (typeof value === "string") {
    return value;
  }
  return JSON.stringify(value);
}

export default function ExpressionDebuggerWatchList({
  pinned,
  onTogglePin,
  variables,
  steps,
  revealedSteps,
}: ExpressionDebuggerWatchListProps) {
  const { t } = useTranslation("inspector");
  const values = useMemo(
    () => resolvePinnedWatchValues(pinned, steps, revealedSteps, variables),
    [pinned, steps, revealedSteps, variables]
  );

  if (pinned.length === 0 && variables.length === 0) {
    return null;
  }

  return (
    <section className="expression-debugger-watch" data-testid="expression-debugger-watch">
      <div className="expression-debugger-watch-header">
        <h5>{t("expressionDebugger.watchTitle")}</h5>
        <span className="hint">{t("expressionDebugger.watchHint")}</span>
      </div>
      {variables.length > 0 && (
        <div className="expression-debugger-watch-pins">
          {variables.map((variable) => {
            const isPinned = pinned.includes(variable.name);
            return (
              <button
                key={variable.name}
                type="button"
                className={`btn small expression-debugger-watch-pin${isPinned ? " primary" : ""}`}
                aria-pressed={isPinned}
                onClick={() => onTogglePin(variable.name)}
              >
                {isPinned ? "★" : "☆"} {variable.name}
              </button>
            );
          })}
        </div>
      )}
      {pinned.length > 0 ? (
        <table className="expression-debugger-watch-table">
          <thead>
            <tr>
              <th>{t("expressionDebugger.watchVariable")}</th>
              <th>{t("expressionDebugger.watchValue")}</th>
              <th aria-label={t("expressionDebugger.watchUnpin")} />
            </tr>
          </thead>
          <tbody>
            {pinned.map((name) => (
              <tr key={name}>
                <td>
                  <code>{name}</code>
                </td>
                <td className="expression-debugger-watch-value">
                  {formatWatchValue(values[name])}
                </td>
                <td>
                  <button
                    type="button"
                    className="btn small"
                    aria-label={t("expressionDebugger.watchUnpinNamed", { name })}
                    onClick={() => onTogglePin(name)}
                  >
                    ×
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        <p className="hint">{t("expressionDebugger.watchEmpty")}</p>
      )}
    </section>
  );
}
