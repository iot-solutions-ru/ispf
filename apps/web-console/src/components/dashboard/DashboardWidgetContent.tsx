import type { DashboardWidget } from "../../types/dashboard";
import ValueWidgetView from "./widgets/ValueWidgetView";
import IndicatorWidgetView, { ToggleWidgetView } from "./widgets/IndicatorWidgetView";
import ChartWidgetView from "./widgets/ChartWidgetView";
import SparklineWidgetView from "./widgets/SparklineWidgetView";
import FunctionFormWidgetView from "./widgets/FunctionFormWidgetView";
import ProgressWidgetView from "./widgets/ProgressWidgetView";
import ObjectTableWidgetView from "./widgets/ObjectTableWidgetView";
import EventFeedWidgetView from "./widgets/EventFeedWidgetView";
import WorkQueueWidgetView from "./widgets/WorkQueueWidgetView";
import StatusBadgeWidgetView from "./widgets/StatusBadgeWidgetView";
import GaugeWidgetView from "./widgets/GaugeWidgetView";
import CardGridWidgetView from "./widgets/CardGridWidgetView";
import FunctionWidgetView from "./widgets/FunctionWidgetView";
import DashboardLinkWidgetView from "./widgets/DashboardLinkWidgetView";
import ReportWidgetView from "./widgets/ReportWidgetView";
import PieChartWidgetView from "./widgets/PieChartWidgetView";
import HistoryTableWidgetView from "./widgets/HistoryTableWidgetView";
import VariableEditorWidgetView from "./widgets/VariableEditorWidgetView";
import SvgWidgetView from "./widgets/SvgWidgetView";

interface DashboardWidgetContentProps {
  widget: DashboardWidget;
  refreshIntervalMs: number;
  editable: boolean;
}

/** Renders a dashboard widget body (excluding composite containers). */
export default function DashboardWidgetContent({
  widget,
  refreshIntervalMs,
  editable,
}: DashboardWidgetContentProps) {
  switch (widget.type) {
    case "value":
      return (
        <ValueWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "indicator":
      return (
        <IndicatorWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "toggle":
      return (
        <ToggleWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "chart":
      return (
        <ChartWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "sparkline":
      return (
        <SparklineWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "function":
      return <FunctionWidgetView widget={widget} editable={editable} />;
    case "function-form":
      return <FunctionFormWidgetView widget={widget} editable={editable} />;
    case "progress":
      return (
        <ProgressWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "object-table":
      return (
        <ObjectTableWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "event-feed":
      return (
        <EventFeedWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "work-queue":
      return <WorkQueueWidgetView widget={widget} editable={editable} />;
    case "status-badge":
      return (
        <StatusBadgeWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "gauge":
      return (
        <GaugeWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "card-grid":
      return (
        <CardGridWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "dashboard-link":
      return <DashboardLinkWidgetView widget={widget} editable={editable} />;
    case "report":
      return (
        <ReportWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "pie-chart":
      return (
        <PieChartWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "history-table":
      return (
        <HistoryTableWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "variable-editor":
      return (
        <VariableEditorWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "svg-widget":
      return (
        <SvgWidgetView
          widget={widget}
          refreshIntervalMs={refreshIntervalMs}
          editable={editable}
        />
      );
    case "composite-widget":
      return <div className="hint">Nested composite widgets are not supported</div>;
    default:
      return <div className="dash-widget">Неизвестный виджет</div>;
  }
}
