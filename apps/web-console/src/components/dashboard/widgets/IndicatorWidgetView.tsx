import { useMutation, useQueryClient } from "@tanstack/react-query";
import { setVariable } from "../../../api";
import type { IndicatorWidget } from "../../../types/dashboard";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import { cloneRecord, setFieldValue } from "../../../utils/record";
import WidgetDragHandle from "../WidgetDragHandle";

interface IndicatorWidgetViewProps {
  widget: IndicatorWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function IndicatorWidgetView({
  widget,
  refreshIntervalMs,
  editable = false,
}: IndicatorWidgetViewProps) {
  const { rawValue, isLoading, isError } = useBoundVariable(
    widget.objectPath,
    widget.variableName,
    widget.valueField,
    refreshIntervalMs
  );

  const active = rawValue === true || rawValue === "true" || rawValue === 1;
  const label = active
    ? (widget.trueLabel ?? "Активно")
    : (widget.falseLabel ?? "Норма");

  return (
    <div
      className={`dash-widget dash-widget-indicator ${active ? "active" : "inactive"}`}
      style={{
        borderColor: active
          ? (widget.trueColor ?? "var(--danger)")
          : (widget.falseColor ?? "var(--success)"),
      }}
    >
      <WidgetDragHandle visible={editable} />
      <div className="dash-widget-title">{widget.title}</div>
      <div className="dash-widget-indicator-body">
        <span className={`dash-indicator-dot ${active ? "on" : "off"}`} />
        <span>{isLoading ? "…" : isError ? "Ошибка" : label}</span>
      </div>
    </div>
  );
}

interface ToggleWidgetViewProps {
  widget: import("../../../types/dashboard").ToggleWidget;
  refreshIntervalMs: number;
}

export function ToggleWidgetView({
  widget,
  refreshIntervalMs,
  editable = false,
}: ToggleWidgetViewProps & { editable?: boolean }) {
  const queryClient = useQueryClient();
  const { rawValue, variable, writable, isLoading } = useBoundVariable(
    widget.objectPath,
    widget.variableName,
    widget.valueField,
    refreshIntervalMs
  );

  const active = rawValue === true || rawValue === "true";

  const mutation = useMutation({
    mutationFn: async (next: boolean) => {
      if (!variable?.value) {
        throw new Error("Variable not loaded");
      }
      const field = widget.valueField ?? "value";
      const record = setFieldValue(cloneRecord(variable.value), field, next);
      return setVariable(widget.objectPath, widget.variableName, record);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["variables", widget.objectPath] });
    },
  });

  return (
    <div className="dash-widget dash-widget-toggle">
      <WidgetDragHandle visible={editable} />
      <div className="dash-widget-title">{widget.title}</div>
      <button
        type="button"
        className={`dash-toggle-btn ${active ? "on" : "off"}`}
        disabled={editable || !writable || isLoading || mutation.isPending}
        onClick={() => mutation.mutate(!active)}
      >
        {active ? (widget.trueLabel ?? "Вкл") : (widget.falseLabel ?? "Выкл")}
      </button>
      {!writable && <div className="dash-widget-meta">только чтение</div>}
    </div>
  );
}
