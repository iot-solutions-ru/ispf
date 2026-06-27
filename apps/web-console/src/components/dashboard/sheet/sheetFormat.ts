import type { SheetCellFormat } from "../../../types/dashboard";

export function formatSheetCellValue(
  raw: unknown,
  format?: SheetCellFormat
): string {
  if (raw === null || raw === undefined) {
    return "";
  }
  if (typeof raw === "object" && raw !== null && "type" in raw) {
    const err = raw as { type?: string; value?: string };
    if (err.type === "CELL_ERROR") {
      return String(err.value ?? "#ERROR!");
    }
  }
  if (format?.type === "number") {
    const num = typeof raw === "number" ? raw : Number.parseFloat(String(raw));
    if (!Number.isFinite(num)) {
      return String(raw);
    }
    const decimals = format.decimals ?? 2;
    const text = num.toFixed(decimals);
    return `${format.prefix ?? ""}${text}${format.suffix ?? ""}`;
  }
  const text = String(raw);
  return `${format?.prefix ?? ""}${text}${format?.suffix ?? ""}`;
}
