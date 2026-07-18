import type { BindingFormulaLink } from "../../types";

export interface FunctionExpressionBodyDto {
  expression: string;
  formulaRef?: string | null;
  formulaParams?: Record<string, string> | null;
  formulaScope?: string | null;
  formulaAppId?: string | null;
}

export function parseFunctionExpressionBody(sourceBody: string | null | undefined): {
  expression: string;
  formulaLink: BindingFormulaLink | null;
} {
  if (!sourceBody?.trim()) {
    return { expression: "", formulaLink: null };
  }
  const trimmed = sourceBody.trim();
  if (!trimmed.startsWith("{")) {
    return { expression: trimmed, formulaLink: null };
  }
  try {
    const parsed = JSON.parse(trimmed) as FunctionExpressionBodyDto;
    const expression = parsed.expression?.trim() ?? "";
    if (!parsed.formulaRef?.trim()) {
      return { expression, formulaLink: null };
    }
    return {
      expression,
      formulaLink: {
        formulaRef: parsed.formulaRef.trim(),
        formulaParams: parsed.formulaParams ?? {},
        formulaScope: parsed.formulaScope ?? null,
        formulaAppId: parsed.formulaAppId ?? null,
      },
    };
  } catch {
    return { expression: trimmed, formulaLink: null };
  }
}

export function serializeFunctionExpressionBody(
  expression: string,
  formulaLink?: BindingFormulaLink | null
): string {
  const trimmed = expression.trim();
  if (!formulaLink?.formulaRef?.trim()) {
    return trimmed;
  }
  const payload: FunctionExpressionBodyDto = {
    expression: trimmed,
    formulaRef: formulaLink.formulaRef.trim(),
    formulaParams: formulaLink.formulaParams ?? {},
  };
  if (formulaLink.formulaScope?.trim()) {
    payload.formulaScope = formulaLink.formulaScope.trim();
  }
  if (formulaLink.formulaAppId?.trim()) {
    payload.formulaAppId = formulaLink.formulaAppId.trim();
  }
  return JSON.stringify(payload);
}
