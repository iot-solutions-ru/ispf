import type { SheetCellStyle, SheetConditionalStyle } from "../../../types/dashboard";

export function resolveConditionalStyle(
  rules: SheetConditionalStyle[] | undefined,
  getCellValue: (address: string) => unknown,
  configCells: Record<string, { style?: SheetCellStyle }>,
  address: string
): SheetCellStyle | undefined {
  const base = configCells[address]?.style;
  if (!rules?.length) {
    return base;
  }
  for (const rule of rules) {
    if (!rule.when?.trim()) {
      continue;
    }
    const expr = rule.when.trim();
    if (evaluateSimpleCondition(expr, address, getCellValue)) {
      return { ...base, ...rule.style };
    }
  }
  return base;
}

function evaluateSimpleCondition(
  expr: string,
  address: string,
  getCellValue: (address: string) => unknown
): boolean {
  const normalized = expr.startsWith("=") ? expr.slice(1).trim() : expr.trim();
  const gt = /^([A-Z]+\d+)\s*>\s*(-?\d+(?:\.\d+)?)$/.exec(normalized);
  if (gt) {
    const ref = gt[1].toUpperCase();
    const threshold = Number.parseFloat(gt[2]);
    const val = Number.parseFloat(String(getCellValue(ref)));
    return Number.isFinite(val) && val > threshold;
  }
  const lt = /^([A-Z]+\d+)\s*<\s*(-?\d+(?:\.\d+)?)$/.exec(normalized);
  if (lt) {
    const ref = lt[1].toUpperCase();
    const threshold = Number.parseFloat(lt[2]);
    const val = Number.parseFloat(String(getCellValue(ref)));
    return Number.isFinite(val) && val < threshold;
  }
  if (normalized === address) {
    const val = getCellValue(address);
    return Boolean(val) && val !== "" && val !== 0;
  }
  return false;
}
