import { useRef } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { useVirtualizer } from "@tanstack/react-virtual";
import type { SpreadsheetWidget } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import { setVariable } from "../../../api";
import { cloneRecord } from "../../../utils/record";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import { useEditorDemoRows } from "../widgetDemoPreview";

const VIRTUALIZE_ROW_THRESHOLD = 50;
const TABLE_ROW_ESTIMATE_PX = 36;

interface SpreadsheetWidgetViewProps {
  widget: SpreadsheetWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function SpreadsheetWidgetView({
  widget,
  refreshIntervalMs,
  editable: editMode,
}: SpreadsheetWidgetViewProps) {
  const { t } = useTranslation(["widgets", "common"]);
  const styles = useWidgetStyles(widget.stylesJson);
  const queryClient = useQueryClient();
  const tableWrapRef = useRef<HTMLDivElement>(null);
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const { variable, isLoading } = useBoundVariable(
    objectPath,
    widget.variableName,
    widget.valueField,
    refreshIntervalMs
  );

  const rows = variable?.value?.rows ?? [];
  const { rows: displayRows, isDemo } = useEditorDemoRows(
    widget,
    rows,
    editMode
  );
  const columns =
    displayRows.length > 0
      ? Object.keys(displayRows[0]).filter((k) => k !== "schema")
      : [];

  const shouldVirtualize = displayRows.length >= VIRTUALIZE_ROW_THRESHOLD;
  const rowVirtualizer = useVirtualizer({
    count: displayRows.length,
    getScrollElement: () => tableWrapRef.current,
    estimateSize: () => TABLE_ROW_ESTIMATE_PX,
    overscan: 10,
    enabled: shouldVirtualize,
  });

  const virtualRows = shouldVirtualize ? rowVirtualizer.getVirtualItems() : [];
  const paddingTop = virtualRows.length > 0 ? virtualRows[0].start : 0;
  const paddingBottom =
    virtualRows.length > 0
      ? rowVirtualizer.getTotalSize() - virtualRows[virtualRows.length - 1].end
      : 0;

  const mutation = useMutation({
    mutationFn: async (nextRows: Array<Record<string, unknown>>) => {
      if (!variable?.value) throw new Error("no variable");
      const record = { ...cloneRecord(variable.value), rows: nextRows };
      return setVariable(objectPath, widget.variableName, record);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["variables", objectPath] });
    },
  });

  const renderRow = (row: Record<string, unknown>, ri: number) => (
    <tr key={ri}>
      {columns.map((col) => (
        <td key={col}>
          {widget.editable && !editMode && !isDemo ? (
            <input
              defaultValue={String(readFieldValue(row, col) ?? "")}
              onBlur={(e) => {
                const next = rows.map((r, i) =>
                  i === ri ? { ...r, [col]: e.target.value } : r
                );
                mutation.mutate(next);
              }}
            />
          ) : (
            String(readFieldValue(row, col) ?? "—")
          )}
        </td>
      ))}
    </tr>
  );

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-spreadsheet"
      editable={editMode}
      demo={isDemo}
    >
      {isLoading && !isDemo ? (
        <p className="hint">{t("common:action.loading")}</p>
      ) : (
        <div className="dash-table-wrap" ref={tableWrapRef} style={styles.body}>
          <table className="dash-object-table">
            <thead>
              <tr>
                {columns.map((col) => (
                  <th key={col}>{col}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {shouldVirtualize && paddingTop > 0 && (
                <tr aria-hidden="true" className="dash-table-spacer">
                  <td colSpan={columns.length || 1} style={{ height: paddingTop, padding: 0, border: 0 }} />
                </tr>
              )}
              {shouldVirtualize
                ? virtualRows.map((virtualRow) =>
                    renderRow(displayRows[virtualRow.index], virtualRow.index)
                  )
                : displayRows.map((row, ri) => renderRow(row, ri))}
              {shouldVirtualize && paddingBottom > 0 && (
                <tr aria-hidden="true" className="dash-table-spacer">
                  <td colSpan={columns.length || 1} style={{ height: paddingBottom, padding: 0, border: 0 }} />
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </DashWidgetShell>
  );
}
