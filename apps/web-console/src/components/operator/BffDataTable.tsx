import type { ReactNode } from "react";
import { useTranslation } from "react-i18next";
import type { TFunction } from "i18next";

interface BffDataTableProps {
  rows: Array<Record<string, unknown>>;
  labels?: Record<string, string>;
  emptyMessage?: string;
  selectable?: boolean;
  selectionKey?: string;
  selectedKey?: string | null;
  onSelect?: (row: Record<string, unknown> | null) => void;
  /** Render ok/warn dots instead of text for these columns */
  statusColumns?: string[];
}

function formatCell(
  value: unknown,
  t: TFunction<["operator", "common"]>,
  column: string,
  statusColumns: string[]
): ReactNode {
  if (statusColumns.includes(column)) {
    const status = String(value ?? "").toLowerCase();
    if (status === "ok" || status === "warn" || status === "warning") {
      const tone = status === "ok" ? "ok" : "warn";
      return <span className={`bff-status-dot ${tone}`} aria-label={status} />;
    }
  }
  if (value === null || value === undefined) {
    return t("common:empty.dash");
  }
  if (typeof value === "boolean") {
    return value ? t("common:action.yes") : t("common:action.no");
  }
  if (typeof value === "object") {
    return JSON.stringify(value);
  }
  return String(value);
}

export default function BffDataTable({
  rows,
  labels,
  emptyMessage,
  selectable = false,
  selectionKey = "order_id",
  selectedKey = null,
  onSelect,
  statusColumns = [],
}: BffDataTableProps) {
  const { t } = useTranslation(["operator", "common"]);
  if (rows.length === 0) {
    return <p className="op-muted">{emptyMessage ?? t("common:empty.noData")}</p>;
  }

  const columns = Object.keys(rows[0]);

  return (
    <div className="op-table-wrap">
      <table className="op-table">
        <thead>
          <tr>
            {selectable && <th className="op-col-select"> </th>}
            {columns.map((column) => (
              <th key={column}>{labels?.[column] ?? column}</th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, index) => {
            const rowKey = String(row[selectionKey] ?? index);
            const selected = selectable && selectedKey === rowKey;
            return (
              <tr
                key={rowKey}
                className={selected ? "op-row-selected" : selectable ? "op-row-clickable" : undefined}
                onClick={
                  selectable
                    ? () => {
                        onSelect?.(selected ? null : row);
                      }
                    : undefined
                }
              >
                {selectable && (
                  <td className="op-col-select">
                    <input
                      type="radio"
                      checked={selected}
                      readOnly
                      aria-label={t("bff.selectRow", { rowKey })}
                    />
                  </td>
                )}
                {columns.map((column) => (
                  <td key={column}>{formatCell(row[column], t, column, statusColumns)}</td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
