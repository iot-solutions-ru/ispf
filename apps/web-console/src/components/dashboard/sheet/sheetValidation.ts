import type { SheetCellValidation } from "../../../types/dashboard";

export interface SheetValidationResult {
  valid: boolean;
  message?: string;
}

export function validateSheetCellValue(
  raw: string,
  validation?: SheetCellValidation
): SheetValidationResult {
  if (!validation) {
    return { valid: true };
  }
  if (validation.type === "range") {
    const num = Number.parseFloat(raw);
    if (!Number.isFinite(num)) {
      return { valid: false, message: validation.message ?? "Expected a number" };
    }
    if (validation.min !== undefined && num < validation.min) {
      return { valid: false, message: validation.message ?? `Min ${validation.min}` };
    }
    if (validation.max !== undefined && num > validation.max) {
      return { valid: false, message: validation.message ?? `Max ${validation.max}` };
    }
    return { valid: true };
  }
  if (validation.type === "pattern" && validation.pattern) {
    try {
      const re = new RegExp(validation.pattern);
      if (!re.test(raw)) {
        return { valid: false, message: validation.message ?? "Invalid format" };
      }
    } catch {
      return { valid: true };
    }
  }
  return { valid: true };
}
