import type { DashboardWidget, SheetCellConfig, SheetCellKind, SheetConfig } from "../../types/dashboard";
import { parseJsonArray, stringifyJson } from "./widgetEditorJson";

export interface PopupVariableRow {
  name: string;
  label?: string;
  field?: string;
}

export interface SlideRow {
  id?: string;
  label?: string;
  children?: DashboardWidget[];
}

export interface SheetCellRow {
  address: string;
  cell: SheetCellConfig;
}

export function childWidgetsFromJson(raw: string | undefined): DashboardWidget[] {
  return parseJsonArray<DashboardWidget>(raw, []).filter(
    (item) => item != null && typeof item === "object" && typeof item.type === "string"
  );
}

export function childWidgetsToJson(children: DashboardWidget[]): string {
  return stringifyJson(children);
}

export function slidesFromJson(raw: string | undefined): SlideRow[] {
  return parseJsonArray<SlideRow>(raw, []).filter((item) => item != null && typeof item === "object");
}

export function slidesToJson(slides: SlideRow[]): string {
  return stringifyJson(slides);
}

export function popupRowsFromJson(raw: string | undefined): PopupVariableRow[] {
  return parseJsonArray<Record<string, unknown>>(raw, [])
    .filter((row) => row != null && typeof row === "object")
    .map((row) => ({
      name: String(row.name ?? row.variable ?? ""),
      label: row.label != null ? String(row.label) : undefined,
      field: row.field != null ? String(row.field) : undefined,
    }));
}

export function popupRowsToJson(rows: PopupVariableRow[]): string {
  const cleaned = rows
    .map((row) => {
      const name = row.name.trim();
      const label = row.label?.trim();
      const field = row.field?.trim();
      return {
        name,
        ...(label ? { label } : {}),
        ...(field ? { field } : {}),
      };
    })
    .filter((row) => row.name.length > 0);
  return stringifyJson(cleaned);
}

export function sheetCellRows(config: SheetConfig): SheetCellRow[] {
  return Object.entries(config.cells).map(([address, cell]) => ({ address, cell }));
}

/** Normalized addresses that appear more than once (empty addresses ignored). */
export function duplicateSheetAddresses(rows: SheetCellRow[]): string[] {
  const seen = new Set<string>();
  const dupes = new Set<string>();
  for (const row of rows) {
    const address = row.address.trim().toUpperCase();
    if (!address) {
      continue;
    }
    if (seen.has(address)) {
      dupes.add(address);
    } else {
      seen.add(address);
    }
  }
  return [...dupes];
}

/**
 * Rebuild `cells` from editor rows. Duplicate A1 keys keep the **first** row;
 * later collisions are skipped so a typo does not wipe an existing cell.
 */
export function sheetConfigWithCells(config: SheetConfig, rows: SheetCellRow[]): SheetConfig {
  const cells: Record<string, SheetCellConfig> = {};
  for (const row of rows) {
    const address = row.address.trim().toUpperCase();
    if (!address || address in cells) {
      continue;
    }
    cells[address] = row.cell;
  }
  return { ...config, cells };
}

export function sheetCellDraftValue(cell: SheetCellConfig): string {
  if (cell.kind === "formula") {
    return cell.expr ?? "";
  }
  if (cell.kind === "binding") {
    return cell.variableName ?? "";
  }
  if (cell.kind === "label") {
    return cell.text ?? "";
  }
  return cell.default ?? "";
}

export function sheetCellWithDraft(cell: SheetCellConfig, value: string): SheetCellConfig {
  if (cell.kind === "formula") {
    return { ...cell, expr: value };
  }
  if (cell.kind === "binding") {
    return { ...cell, variableName: value };
  }
  if (cell.kind === "label") {
    return { ...cell, text: value };
  }
  return { ...cell, default: value };
}

export function sheetCellWithKind(cell: SheetCellConfig, kind: SheetCellKind): SheetCellConfig {
  const value = sheetCellDraftValue(cell);
  const next: SheetCellConfig = { ...cell, kind };
  delete next.text;
  delete next.expr;
  delete next.default;
  delete next.variableName;
  return sheetCellWithDraft(next, value);
}
