import type { DataRecord } from "../../../types";
import type { SheetConfig, SheetMode } from "../../../types/dashboard";
import type { SheetValues } from "./sheetFormulaEngine";

export function loadValuesFromSession(
  params: Record<string, unknown>,
  sessionKey: string
): SheetValues {
  const raw = params[sessionKey];
  if (!raw || typeof raw !== "object" || Array.isArray(raw)) {
    return {};
  }
  const result: SheetValues = {};
  for (const [key, value] of Object.entries(raw as Record<string, unknown>)) {
    if (typeof value === "string" || typeof value === "number" || typeof value === "boolean") {
      result[key.toUpperCase()] = String(value);
    }
  }
  return result;
}

export function seedsFromConfig(config: SheetConfig): SheetValues {
  const result: SheetValues = {};
  for (const [addr, cell] of Object.entries(config.cells)) {
    if (cell.kind === "input" || cell.kind === "readonly") {
      if (cell.default !== undefined) {
        result[addr] = cell.default;
      }
    } else if (cell.kind === "formula" && cell.expr) {
      result[addr] = cell.expr.startsWith("=") ? cell.expr : `=${cell.expr}`;
    }
  }
  return result;
}

export function loadCellContents(
  record: DataRecord | undefined,
  config: SheetConfig,
  mode: SheetMode
): SheetValues {
  const seeds = mode === "free" ? seedsFromConfig(config) : defaultsFromConfig(config);
  if (!record?.rows?.length) {
    return seeds;
  }
  const result = { ...seeds };
  for (const row of record.rows) {
    const cell = String(row.cell ?? "").toUpperCase();
    if (cell) {
      result[cell] = String(row.value ?? "");
    }
  }
  return result;
}

function defaultsFromConfig(config: SheetConfig): SheetValues {
  const result: SheetValues = {};
  for (const [addr, cell] of Object.entries(config.cells)) {
    if (cell.kind === "input" && cell.default !== undefined) {
      result[addr] = cell.default;
    }
  }
  return result;
}

export function saveValuesToSessionPatch(values: SheetValues): Record<string, string> {
  return { ...values };
}

export function saveValuesToVariableRecord(
  values: SheetValues,
  existing: DataRecord | undefined
): DataRecord {
  const schema = existing?.schema ?? {
    name: "sheetValues",
    fields: [{ name: "rows", type: "RECORD_LIST" as const }],
  };
  const rows = Object.entries(values)
    .filter(([, value]) => value.trim() !== "")
    .map(([cell, value]) => ({ cell, value }));
  return { schema, rows };
}

export function loadValuesFromVariable(
  record: DataRecord | undefined,
  config: SheetConfig,
  mode: SheetMode = "configured"
): SheetValues {
  return loadCellContents(record, config, mode);
}
