import { validateAnalyticsExpression, validateExpression } from "../../api";
import type { FormalVerificationReport } from "../../api";
import type { BindingRuleKind } from "../../types";

export type BindingExpressionValidationResult = {
  valid: boolean;
  error: string | null;
  warnings?: string[];
  verification?: FormalVerificationReport | null;
};
export type BindingExpressionValidator = (expression: string) => Promise<BindingExpressionValidationResult>;

const HISTORIAN_BUILTIN_RE = /^(avg|rateOfChange|oee)\s*\(/i;
const HISTORIAN_CEL_RE = /\b(avg|min|max|last|sum|live)\s*\(/i;

function formatVerificationError(result: {
  valid: boolean;
  error: string | null;
  warnings?: string[];
  verification?: FormalVerificationReport | null;
}): BindingExpressionValidationResult {
  const findings = result.verification?.findings ?? result.warnings ?? [];
  const error = result.valid
    ? null
    : (result.error ?? findings[0] ?? "Formal verification failed");
  return {
    valid: result.valid,
    error,
    warnings: findings,
    verification: result.verification ?? null,
  };
}

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
    return formatVerificationError(reactive);
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
    warnings: result.verification?.findings,
    verification: result.verification ?? null,
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
    return formatVerificationError(result);
  }
  if (kind === "historian") {
    return validateHistorianExpression(trimmed, objectPath);
  }
  if (!trimmed) {
    return { valid: false, error: "Expression is empty" };
  }
  const result = await validateExpression(trimmed);
  return formatVerificationError(result);
}
