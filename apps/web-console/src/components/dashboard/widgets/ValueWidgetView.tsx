import type { ValueWidget } from "../../../types/dashboard";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import WidgetDragHandle from "../WidgetDragHandle";

interface ValueWidgetViewProps {
  widget: ValueWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function ValueWidgetView({
  widget,
  refreshIntervalMs,
  editable = false,
}: ValueWidgetViewProps) {
  const { rawValue, variable, isLoading, isError } = useBoundVariable(
    widget.objectPath ?? "",
    widget.variableName ?? "",
    widget.valueField,
    refreshIntervalMs
  );

  const unitRow = variable?.value?.rows[0];
  const unit =
    widget.unit ??
    (widget.unitField && unitRow ? String(unitRow[widget.unitField] ?? "") : "");

  let display = "—";
  if (isLoading) {
    display = "…";
  } else if (isError) {
    display = "!";
  } else if (typeof rawValue === "number") {
    const decimals = widget.decimals ?? 1;
    display = rawValue.toFixed(decimals);
  } else if (rawValue != null) {
    display = String(rawValue);
  }

  return (
    <div className="dash-widget dash-widget-value">
      <WidgetDragHandle visible={editable} />
      <div className="dash-widget-title">{widget.title}</div>
      <div className="dash-widget-value-body">
        <span className="dash-widget-metric">{display}</span>
        {unit ? <span className="dash-widget-unit">{unit}</span> : null}
      </div>
      <div className="dash-widget-meta mono">
        {(widget.objectPath ?? "—").split(".").pop()}.{widget.variableName}
      </div>
    </div>
  );
}
