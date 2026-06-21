import type { DashboardWidget } from "../../types/dashboard";
import DashboardWidgetContent from "./DashboardWidgetContent";
import CompositeWidgetView from "./widgets/CompositeWidgetView";

interface RenderDashboardWidgetProps {
  widget: DashboardWidget;
  refreshIntervalMs: number;
  editable: boolean;
}

export default function renderDashboardWidget({
  widget,
  refreshIntervalMs,
  editable,
}: RenderDashboardWidgetProps) {
  if (widget.type === "composite-widget") {
    return (
      <CompositeWidgetView
        widget={widget}
        refreshIntervalMs={refreshIntervalMs}
        editable={editable}
      />
    );
  }
  return (
    <DashboardWidgetContent
      widget={widget}
      refreshIntervalMs={refreshIntervalMs}
      editable={editable}
    />
  );
}
