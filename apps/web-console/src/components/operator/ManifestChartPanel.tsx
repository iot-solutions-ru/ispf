import { useMemo } from "react";
import ChartWidgetView from "../dashboard/widgets/ChartWidgetView";
import {
  manifestChartToWidget,
  type OperatorManifestChart,
  type OperatorManifestScreen,
} from "../../types/operatorManifest";

interface ManifestChartPanelProps {
  screen: OperatorManifestScreen;
  chart: OperatorManifestChart;
  refreshIntervalMs?: number;
}

export default function ManifestChartPanel({
  screen,
  chart,
  refreshIntervalMs = 5000,
}: ManifestChartPanelProps) {
  const widget = useMemo(() => manifestChartToWidget(screen, chart), [screen, chart]);

  return (
    <div className="op-manifest-embed op-manifest-embed-chart">
      <ChartWidgetView widget={widget} refreshIntervalMs={refreshIntervalMs} />
    </div>
  );
}
