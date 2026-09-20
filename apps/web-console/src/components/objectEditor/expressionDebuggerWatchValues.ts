// Extracted from ExpressionDebuggerWatchList.tsx so that component modules only export components
// (react-refresh/only-export-components keeps Fast Refresh state-preserving).
import type { EvaluateExpressionStep } from "../../api";
import type { VariableDto } from "../../types";

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

function extractFromBindingsDetail(detail: unknown, name: string): unknown {
  if (!detail || typeof detail !== "object") {
    return undefined;
  }
  const record = detail as Record<string, unknown>;
  const direct = lookupInRecord(record, name);
  if (direct !== undefined) {
    return direct;
  }
  for (const bucket of ["self", "parent", "context", "input"] as const) {
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
