import { useEffect, useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchObjects, fetchVariables } from "../../../api";
import type { ObjectTableColumn, ObjectTableWidget } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import { useDashboardContext } from "../DashboardContext";
import WidgetDragHandle from "../WidgetDragHandle";

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
  const { selection, setSelection } = useDashboardContext();
  const parsedColumns = useMemo(() => {
    try {
      return widget.columnsJson ? (JSON.parse(widget.columnsJson) as ObjectTableColumn[]) : [];
    } catch {
      return [] as ObjectTableColumn[];
    }
  }, [widget.columnsJson]);

  const children = useQuery({
    queryKey: ["objects", widget.parentPath],
    queryFn: () => fetchObjects(widget.parentPath),
    enabled: Boolean(widget.parentPath),
    refetchInterval: refreshIntervalMs,
  });

  const selectedPath = widget.selectionKey ? selection[widget.selectionKey] : undefined;

  useEffect(() => {
    if (!widget.selectionKey || editable || selectedPath) return;
    const first = children.data?.[0];
    if (first) {
      setSelection(widget.selectionKey, first.path);
    }
  }, [children.data, editable, selectedPath, setSelection, widget.selectionKey]);

  return (
    <div className="dash-widget dash-widget-table">
      <WidgetDragHandle visible={editable} />
      <div className="dash-widget-title">{widget.title}</div>
      {!widget.parentPath ? (
        <p className="hint">Укажите parentPath</p>
      ) : children.isLoading ? (
        <p className="hint">Загрузка…</p>
      ) : (
        <div className="dash-table-wrap">
          <table className="dash-object-table">
            <thead>
              <tr>
                <th>Объект</th>
                {parsedColumns.map((col) => (
                  <th key={col.variable}>{col.label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {(children.data ?? []).map((obj) => (
                <ObjectTableRow
                  key={obj.path}
                  path={obj.path}
                  displayName={obj.displayName}
                  columns={parsedColumns}
                  selected={selectedPath === obj.path}
                  refreshIntervalMs={refreshIntervalMs}
                  onSelect={() => {
                    if (widget.selectionKey && !editable) {
                      setSelection(widget.selectionKey, obj.path);
                    }
                  }}
                />
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

function ObjectTableRow({
  path,
  displayName,
  columns,
  selected,
  refreshIntervalMs,
  onSelect,
}: {
  path: string;
  displayName: string;
  columns: ObjectTableColumn[];
  selected: boolean;
  refreshIntervalMs: number;
  onSelect: () => void;
}) {
  const vars = useQuery({
    queryKey: ["variables", path],
    queryFn: () => fetchVariables(path),
    refetchInterval: refreshIntervalMs,
  });

  return (
    <tr
      className={selected ? "selected" : ""}
      onClick={onSelect}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => e.key === "Enter" && onSelect()}
    >
      <td>{displayName}</td>
      {columns.map((col) => {
        const variable = vars.data?.find((v) => v.name === col.variable);
        const raw = readFieldValue(variable?.value?.rows[0], "value");
        return <td key={col.variable}>{raw != null ? String(raw) : "—"}</td>;
      })}
    </tr>
  );
}
