import type { SheetConfig } from "../../../types/dashboard";
import { a1ToRowCol } from "./sheetAddress";

export function rowPassesColumnFilters(
  rowIndex: number,
  config: SheetConfig,
  getCellText: (address: string) => string
): boolean {
  const filters = config.columnFilters;
  if (!filters?.length) {
    return true;
  }
  for (const filter of filters) {
    if (!filter.column?.trim()) {
      continue;
    }
    const col = filter.column.toUpperCase();
    const rc = a1ToRowCol(`${col}1`);
    if (!rc) {
      continue;
    }
    const addr = `${col}${rowIndex + 1}`;
    const cellText = getCellText(addr);
    if (filter.value !== undefined && filter.value !== "" && cellText !== filter.value) {
      return false;
    }
  }
  return true;
}

export function visibleRowIndices(config: SheetConfig): number[] {
  return Array.from({ length: config.rows }, (_, i) => i);
}
