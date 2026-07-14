import { useMemo, useState, type CSSProperties, type ReactNode } from "react";
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
  /** Show per-column text filters in the header */
  filterable?: boolean;
  /** Columns with filters; default = all data columns */
  filterColumns?: string[];
  tableStyle?: CSSProperties;
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

function matchesFilters(
  row: Record<string, unknown>,
  columns: string[],
  filters: Record<string, string>
): boolean {
  for (const column of columns) {
    const needle = filters[column]?.trim().toLowerCase();
    if (!needle) continue;
    const hay = String(row[column] ?? "").toLowerCase();
    if (!hay.includes(needle)) {
      return false;
    }
  }
  return true;
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
  filterable = false,
  filterColumns,
  tableStyle,
}: BffDataTableProps) {
  const { t } = useTranslation(["operator", "common"]);
  const [filterDraft, setFilterDraft] = useState<Record<string, string>>({});
  const columns = rows.length > 0 ? Object.keys(rows[0]) : [];
  const activeFilterColumns = useMemo(() => {
    if (!filterable || columns.length === 0) return [];
    const requested = (filterColumns ?? []).filter((column) => columns.includes(column));
    return requested.length > 0 ? requested : columns;
  }, [filterable, filterColumns, columns]);

  const filteredRows = useMemo(() => {
    if (rows.length === 0) return [];
    if (!filterable || activeFilterColumns.length === 0) {
      return rows;
    }
    return rows.filter((row) => matchesFilters(row, activeFilterColumns, filterDraft));
  }, [rows, filterable, activeFilterColumns, filterDraft]);

  if (rows.length === 0) {
    return <p className="op-muted">{emptyMessage ?? t("common:empty.noData")}</p>;
  }

  return (
    <div className="op-table-wrap">
      <table className="op-table" style={tableStyle}>
        <thead>
          <tr>
            {selectable && <th className="op-col-select"> </th>}
            {columns.map((column) => (
              <th key={column}>{labels?.[column] ?? column}</th>
            ))}
          </tr>
          {filterable && activeFilterColumns.length > 0 && (
            <tr className="op-table-filter-row">
              {selectable && <th className="op-col-select" />}
              {columns.map((column) => (
                <th key={`filter-${column}`} className="op-table-filter-cell">
                  {activeFilterColumns.includes(column) ? (
                    <input
                      type="search"
                      className="op-table-filter-input"
                      value={filterDraft[column] ?? ""}
                      placeholder={t("bff.filterColumn")}
                      aria-label={t("bff.filterColumnNamed", {
                        column: labels?.[column] ?? column,
                      })}
                      onChange={(event) =>
                        setFilterDraft((prev) => ({
                          ...prev,
                          [column]: event.target.value,
                        }))
                      }
                    />
                  ) : null}
                </th>
              ))}
            </tr>
          )}
        </thead>
        <tbody>
          {filteredRows.length === 0 && (
            <tr>
              <td
                className="op-table-empty-filtered"
                colSpan={columns.length + (selectable ? 1 : 0)}
              >
                {t("bff.noFilterMatches")}
              </td>
            </tr>
          )}
          {filteredRows.map((row, index) => {
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
