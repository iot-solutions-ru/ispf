import type { SheetCellConfig } from "../../../types/dashboard";
import type { VariableHistorySample } from "../../../api";
import type { IspfFormulaVarRef } from "./sheetIspfFormulaDeps";

export interface SheetHistoryRef {
  objectPath: string;
  variableName: string;
  field: string;
  histMinutes: number;
}

export function resolveBindingHistoryMinutes(cell: SheetCellConfig): number | undefined {
  const raw = cell.historyMinutes;
  if (raw == null || !Number.isFinite(raw) || raw <= 0) {
    return undefined;
  }
  return Math.min(10_080, Math.round(raw));
}

export function bindingCellToHistoryRef(
  objectPath: string,
  variableName: string,
  valueField: string | undefined,
  historyMinutes: number
): SheetHistoryRef {
  return {
    objectPath,
    variableName,
    field: valueField?.trim() || "value",
    histMinutes: historyMinutes,
  };
}

export function historyRefKey(ref: SheetHistoryRef): string {
  return `${ref.objectPath}|${ref.variableName}|${ref.field}|${ref.histMinutes}`;
}

export function mergeSheetHistoryRefs(
  formulaRefs: IspfFormulaVarRef[],
  bindingRefs: SheetHistoryRef[]
): SheetHistoryRef[] {
  const merged = new Map<string, SheetHistoryRef>();
  for (const ref of formulaRefs) {
    if (ref.histMinutes == null) {
      continue;
    }
    const entry: SheetHistoryRef = {
      objectPath: ref.objectPath,
      variableName: ref.variableName,
      field: ref.field,
      histMinutes: ref.histMinutes,
    };
    merged.set(historyRefKey(entry), entry);
  }
  for (const ref of bindingRefs) {
    merged.set(historyRefKey(ref), ref);
  }
  return [...merged.values()];
}

export function pickLatestHistorySample(
  samples: VariableHistorySample[] | undefined,
  _field: string
): number | undefined {
  if (!samples?.length) {
    return undefined;
  }
  let best: { t: number; value: number } | null = null;
  for (const sample of samples) {
    const t = Date.parse(sample.ts);
    const raw = sample.value;
    const numeric = typeof raw === "number" ? raw : Number(raw);
    if (!Number.isFinite(numeric)) {
      continue;
    }
    if (!best || t >= best.t) {
      best = { t: Number.isFinite(t) ? t : 0, value: numeric };
    }
  }
  return best?.value;
}
