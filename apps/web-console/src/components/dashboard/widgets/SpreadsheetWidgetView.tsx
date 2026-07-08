import type { SpreadsheetWidget } from "../../../types/dashboard";
import { resolveSheetMode } from "../sheet/sheetConfig";
import SpreadsheetConfiguredGridView from "./SpreadsheetConfiguredGridView";
import SpreadsheetFreeGridView from "./SpreadsheetFreeGridView";

/** BL-150: column resize + frozen header live in grid views via `useSpreadsheetColumnResize`. */
export { useSpreadsheetColumnResize } from "./spreadsheet/useSpreadsheetColumnResize";

export { default as SpreadsheetImportNotice } from "./SpreadsheetImportNotice";
export type { SpreadsheetImportNoticeState } from "./SpreadsheetImportNotice";

interface SpreadsheetWidgetViewProps {
  widget: SpreadsheetWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function SpreadsheetWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: SpreadsheetWidgetViewProps) {
  const mode = resolveSheetMode(widget);

  if (mode === "configured") {
    return (
      <SpreadsheetConfiguredGridView
        widget={widget}
        refreshIntervalMs={refreshIntervalMs}
        editable={editable}
      />
    );
  }

  return (
    <SpreadsheetFreeGridView
      widget={widget}
      refreshIntervalMs={refreshIntervalMs}
      editable={editable}
    />
  );
}
