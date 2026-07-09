import { validateAnalyticsExpression, validateExpression } from "../api";
import type { BindingRuleKind } from "../types";

const HISTORIAN_BUILTIN_RE = /^(rollingAvg|rateOfChange|oee)\s*\(/i;

export async function validateHistorianExpression(
  expression: string,
  objectPath: string
): Promise<{ valid: boolean; error: string | null }> {
  const trimmed = expression.trim();
  if (!trimmed) {
    return { valid: false, error: "Expression is empty" };
  }
  if (HISTORIAN_BUILTIN_RE.test(trimmed)) {
    return { valid: true, error: null };
  }
  const result = await validateAnalyticsExpression(trimmed, objectPath);
  return {
    valid: result.valid,
    error: result.errors[0] ?? null,
  };
}

export async function validateBindingRuleExpression(
  expression: string,
  objectPath: string,
  kind: BindingRuleKind,
  field: "expression" | "condition"
): Promise<{ valid: boolean; error: string | null }> {
  const trimmed = expression.trim();
  if (field === "condition") {
    if (!trimmed) {
      return { valid: true, error: null };
    }
    const result = await validateExpression(trimmed);
    return { valid: result.valid, error: result.error };
  }
  if (kind === "historian") {
    return validateHistorianExpression(trimmed, objectPath);
  }
  if (!trimmed) {
    return { valid: false, error: "Expression is empty" };
  }
  const result = await validateExpression(trimmed);
  return { valid: result.valid, error: result.error };
}
