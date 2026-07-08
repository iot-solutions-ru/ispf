/**
 * Analytics template helpers for dashboard chart widgets (BL-160).
 */

export interface AnalyticsTemplateRef {
  templateId: string;
  helper: string;
  windowBucket: string;
  sourceField?: string;
}

/** Build catalog expression, e.g. rollingAvg('temperature', '5m'). */
export function buildAnalyticsBindingExpression(
  helper: string,
  variableName: string,
  windowBucket: string
): string {
  const safeVar = variableName.trim() || "sourceVar";
  const safeBucket = windowBucket.trim() || "5m";
  return `${helper}('${safeVar}', '${safeBucket}')`;
}

/** Historian aggregate bucket for chart series when an analytics template is selected. */
export function resolveAnalyticsAggregateBucket(
  template: AnalyticsTemplateRef | null | undefined,
  fallbackBucket: string | null
): string | null {
  if (!template?.helper) {
    return fallbackBucket;
  }
  if (template.helper === "rollingAvg" || template.helper === "rateOfChange") {
    return template.windowBucket || fallbackBucket;
  }
  return fallbackBucket;
}

export function templateFromApiRow(row: {
  templateId: string;
  helper: string;
  windowBucket: string;
  sourceField?: string;
}): AnalyticsTemplateRef {
  return {
    templateId: row.templateId,
    helper: row.helper,
    windowBucket: row.windowBucket,
    sourceField: row.sourceField,
  };
}
