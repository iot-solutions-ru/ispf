import { useEffect, useMemo, useRef, useState, type MouseEvent } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { useVirtualizer } from "@tanstack/react-virtual";
import { fetchObjects } from "../../../api";
import type { ObjectTableColumn, ObjectTableWidget } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import type { VariableDto } from "../../../types";
import { useVariablesBatchQuery } from "../../../hooks/useVariablesQuery";
import { useDashboardContext, triggerDashboardOpen } from "../DashboardContext";
import { parseJsonObject, parseWidgetJsonArray, matchesNamePattern, objectTableValueField, formatObjectTableCell } from "../dashboardUtils";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import MultiPenTrendModal from "../../MultiPenTrendModal";
import { createTrendPen, type TrendPen } from "../../../types/trendPen";

const VIRTUALIZE_ROW_THRESHOLD = 50;
const TABLE_ROW_ESTIMATE_PX = 36;

interface ObjectTableWidgetViewProps {
  widget: ObjectTableWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function ObjectTableWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: ObjectTableWidgetViewProps) {
  const { t } = useTranslation(["widgets", "common"]);
  const styles = useWidgetStyles(widget.stylesJson);
  const { selection, setSelection, navigateToDashboard, openDashboardModal } = useDashboardContext();
  const parsedColumns = useMemo(
    () => parseWidgetJsonArray<ObjectTableColumn>(widget.columnsJson),
    [widget.columnsJson]
  );

  const children = useQuery({
    queryKey: ["objects", widget.parentPath],
    queryFn: () => fetchObjects(widget.parentPath),
    enabled: Boolean(widget.parentPath),
    refetchInterval: refreshIntervalMs,
  });

  const rows = useMemo(() => {
    const list = children.data ?? [];
    return list.filter((obj) => {
      const leaf = obj.path.split(".").pop() ?? "";
      if (widget.namePattern && !matchesNamePattern(leaf, widget.namePattern)) {
        return false;
      }
      if (widget.objectType && obj.type !== widget.objectType) {
        return false;
      }
      return true;
    });
  }, [children.data, widget.namePattern, widget.objectType]);

  const selectedPath = widget.selectionKey ? selection[widget.selectionKey] : undefined;

  useEffect(() => {
    if (!widget.selectionKey || editable || selectedPath) return;
    const first = rows[0];
    if (first) {
      setSelection(widget.selectionKey, first.path);
    }
  }, [rows, editable, selectedPath, setSelection, widget.selectionKey]);

  const tableWrapRef = useRef<HTMLDivElement>(null);
  const shouldVirtualize = rows.length >= VIRTUALIZE_ROW_THRESHOLD;
  const columnCount = parsedColumns.length + 1;

  const rowVirtualizer = useVirtualizer({
    count: rows.length,
    getScrollElement: () => tableWrapRef.current,
    estimateSize: () => TABLE_ROW_ESTIMATE_PX,
    overscan: 10,
    enabled: shouldVirtualize,
  });

  const virtualRows = shouldVirtualize ? rowVirtualizer.getVirtualItems() : [];
  const rowPaths = useMemo(() => {
    if (!shouldVirtualize) {
      return rows.map((row) => row.path);
    }
    const visibleIndices = new Set(virtualRows.map((virtualRow) => virtualRow.index));
    if (selectedPath) {
      const selectedIndex = rows.findIndex((row) => row.path === selectedPath);
      if (selectedIndex >= 0) {
        visibleIndices.add(selectedIndex);
      }
    }
    return [...visibleIndices]
      .sort((a, b) => a - b)
      .map((index) => rows[index]?.path)
      .filter((path): path is string => Boolean(path));
  }, [rows, selectedPath, shouldVirtualize, virtualRows]);
  const variablesBatch = useVariablesBatchQuery(rowPaths, refreshIntervalMs, Boolean(widget.parentPath));
  const [trendModal, setTrendModal] = useState<{
    pens: TrendPen[];
    availablePens: TrendPen[];
  } | null>(null);
  const [contextMenu, setContextMenu] = useState<{
    x: number;
    y: number;
    path: string;
    displayName: string;
    variables?: VariableDto[];
  } | null>(null);

  const trendColumns = useMemo(
    () => parsedColumns.filter((column) => Boolean(column.variable)),
    [parsedColumns]
  );

  useEffect(() => {
    if (!contextMenu) {
      return;
    }
    const close = () => setContextMenu(null);
    window.addEventListener("click", close);
    window.addEventListener("scroll", close, true);
    return () => {
      window.removeEventListener("click", close);
      window.removeEventListener("scroll", close, true);
    };
  }, [contextMenu]);

  const openTrendForRow = (
    path: string,
    displayName: string,
    columnIndex: number
  ) => {
    const column = trendColumns[columnIndex];
    if (!column?.variable) {
      return;
    }
    const availablePens = trendColumns.map((col, index) =>
      createTrendPen(
        path,
        col.variable!,
        `${displayName} · ${col.label}`,
        objectTableValueField(col),
        index
      )
    );
    setTrendModal({
      pens: [availablePens[columnIndex]],
      availablePens,
    });
    setContextMenu(null);
  };
  const paddingTop = virtualRows.length > 0 ? virtualRows[0].start : 0;
  const paddingBottom =
    virtualRows.length > 0
      ? rowVirtualizer.getTotalSize() - virtualRows[virtualRows.length - 1].end
      : 0;

  const renderRow = (obj: (typeof rows)[number]) => (
    <ObjectTableRow
      key={obj.path}
      path={obj.path}
      displayName={obj.displayName}
      columns={parsedColumns}
      variables={variablesBatch.data?.[obj.path]}
      selected={selectedPath === obj.path}
      trendEnabled={!editable && trendColumns.length > 0}
      onSelect={() => {
        if (editable) {
          return;
        }
        const targetKey = widget.rowSelectionKey ?? widget.selectionKey;
        const openOptions = {
          selection: targetKey ? { [targetKey]: obj.path } : undefined,
          params: parseJsonObject(widget.rowParamsJson),
        };
        if (widget.selectionKey) {
          setSelection(widget.selectionKey, obj.path);
        }
        triggerDashboardOpen(
          widget.rowOpenMode,
          widget.rowTargetDashboard,
          widget.title,
          { navigateToDashboard, openDashboardModal },
          openOptions
        );
      }}
      onContextMenu={
        editable || trendColumns.length === 0
          ? undefined
          : (event) => {
              event.preventDefault();
              setContextMenu({
                x: event.clientX,
                y: event.clientY,
                path: obj.path,
                displayName: obj.displayName,
                variables: variablesBatch.data?.[obj.path],
              });
            }
      }
    />
  );

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-table"
      editable={editable}
    >
      {!widget.parentPath ? (
        <p className="hint">{t("view.specifyParentPath")}</p>
      ) : children.isLoading ? (
        <p className="hint">{t("common:action.loading")}</p>
      ) : (
        <div className="dash-table-wrap" ref={tableWrapRef} style={styles.body}>
          <table className="dash-object-table" style={styles.table}>
            <thead>
              <tr>
                <th>{t("view.objectColumn")}</th>
                {parsedColumns.map((col) => (
                  <th key={col.variable ?? col.objectField ?? col.label}>{col.label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {shouldVirtualize && paddingTop > 0 && (
                <tr aria-hidden="true" className="dash-table-spacer">
                  <td colSpan={columnCount} style={{ height: paddingTop, padding: 0, border: 0 }} />
                </tr>
              )}
              {shouldVirtualize
                ? virtualRows.map((virtualRow) => renderRow(rows[virtualRow.index]))
                : rows.map((obj) => renderRow(obj))}
              {shouldVirtualize && paddingBottom > 0 && (
                <tr aria-hidden="true" className="dash-table-spacer">
                  <td colSpan={columnCount} style={{ height: paddingBottom, padding: 0, border: 0 }} />
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
      {contextMenu && (
        <ul
          className="scada-mimic-context-menu dash-object-table-context-menu"
          style={{ left: contextMenu.x, top: contextMenu.y }}
          onClick={(event) => event.stopPropagation()}
        >
          {trendColumns.map((column, index) => (
            <li key={column.variable ?? column.label}>
              <button
                type="button"
                onClick={() =>
                  openTrendForRow(
                    contextMenu.path,
                    contextMenu.displayName,
                    index
                  )
                }
              >
                {t("view.showTrend", { label: column.label })}
              </button>
            </li>
          ))}
        </ul>
      )}
      {trendModal && (
        <MultiPenTrendModal
          pens={trendModal.pens}
          availablePens={trendModal.availablePens}
          onClose={() => setTrendModal(null)}
        />
      )}
    </DashWidgetShell>
  );
}

function ObjectTableRow({
  path,
  displayName,
  columns,
  variables,
  selected,
  trendEnabled,
  onSelect,
  onContextMenu,
}: {
  path: string;
  displayName: string;
  columns: ObjectTableColumn[];
  variables?: VariableDto[];
  selected: boolean;
  trendEnabled?: boolean;
  onSelect: () => void;
  onContextMenu?: (event: MouseEvent<HTMLTableRowElement>) => void;
}) {
  return (
    <tr
      className={selected ? "selected" : ""}
      onClick={onSelect}
      onContextMenu={onContextMenu}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => e.key === "Enter" && onSelect()}
      data-testid={trendEnabled ? `object-table-row-${path.split(".").pop()}` : undefined}
    >
      <td>{displayName}</td>
      {columns.map((col) => {
        let raw: unknown;
        if (!col.variable) {
          if (col.objectField === "path" || col.field === "path") {
            raw = path;
          } else {
            raw = displayName;
          }
        } else {
          const variable = variables?.find((v) => v.name === col.variable);
          raw = readFieldValue(
            variable?.value?.rows[0],
            objectTableValueField(col)
          );
        }
        const text = formatObjectTableCell(raw, col);
        return (
          <td key={`${col.variable ?? col.objectField ?? col.label}-${col.field ?? "value"}`}>
            {text}
          </td>
        );
      })}
    </tr>
  );
}
