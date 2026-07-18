import { validateAnalyticsExpression, validateExpression } from "../../api";
import type { BindingRuleKind } from "../../types";

export type BindingExpressionValidationResult = { valid: boolean; error: string | null };
export type BindingExpressionValidator = (expression: string) => Promise<BindingExpressionValidationResult>;

const HISTORIAN_BUILTIN_RE = /^(avg|rateOfChange|oee)\s*\(/i;
const HISTORIAN_CEL_RE = /\b(avg|min|max|last|sum|live)\s*\(/i;

export async function validateFunctionExpression(
  expression: string,
  objectPath: string
): Promise<BindingExpressionValidationResult> {
  const trimmed = expression.trim();
  if (!trimmed) {
    return { valid: false, error: "Expression is empty" };
  }
  if (HISTORIAN_BUILTIN_RE.test(trimmed) || HISTORIAN_CEL_RE.test(trimmed)) {
    return validateHistorianExpression(trimmed, objectPath);
  }
  const reactive = await validateExpression(trimmed);
  if (reactive.valid) {
    return reactive;
  }
  return validateHistorianExpression(trimmed, objectPath);
}

export async function validateHistorianExpression(
  expression: string,
  objectPath: string
): Promise<BindingExpressionValidationResult> {
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
): Promise<BindingExpressionValidationResult> {
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
