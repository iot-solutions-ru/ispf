import type { StatusBadgeWidget } from "../../../types/dashboard";
import {
  DISPATCH_STATUS_COLORS,
  formatDispatchStatus,
  resolveWidgetPath,
} from "../dashboardUtils";
import { useDashboardContext } from "../DashboardContext";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import WidgetDragHandle from "../WidgetDragHandle";

interface StatusBadgeWidgetViewProps {
  widget: StatusBadgeWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function StatusBadgeWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: StatusBadgeWidgetViewProps) {
  const { selection } = useDashboardContext();
  const objectPath = resolveWidgetPath(widget.objectPath, widget.selectionKey, selection);
  const bound = useBoundVariable(
    objectPath,
    widget.variableName ?? "status",
    widget.valueField ?? "value",
    refreshIntervalMs
  );

  const status = bound.rawValue != null ? String(bound.rawValue) : "";
  const color = DISPATCH_STATUS_COLORS[status] ?? "#8b949e";

  return (
    <div className="dash-widget dash-widget-status-badge">
      <WidgetDragHandle visible={editable} />
      <div className="dash-widget-title">{widget.title}</div>
      {!objectPath ? (
        <p className="hint">Выберите наряд</p>
      ) : (
        <span className="dash-status-badge" style={{ borderColor: color, color }}>
          {formatDispatchStatus(status)}
        </span>
      )}
    </div>
  );
}
