import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { EvaluateExpressionStep } from "../../api";
import type { VariableDto } from "../../types";
import { resolvePinnedWatchValues } from "./expressionDebuggerWatchValues";

interface ExpressionDebuggerWatchListProps {
  pinned: string[];
  onTogglePin: (name: string) => void;
  variables: VariableDto[];
  steps: EvaluateExpressionStep[];
  revealedSteps: number;
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
