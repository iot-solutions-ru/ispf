import { useMemo } from "react";
import { DashboardProvider } from "../dashboard/DashboardContext";
import MapWidgetView from "../dashboard/widgets/MapWidgetView";
import {
  manifestMapToWidget,
  type OperatorManifestMap,
  type OperatorManifestScreen,
} from "../../types/operatorManifest";

interface ManifestMapPanelProps {
  screen: OperatorManifestScreen;
  map: OperatorManifestMap;
  refreshIntervalMs?: number;
}

export default function ManifestMapPanel({
  screen,
  map,
  refreshIntervalMs = 5000,
}: ManifestMapPanelProps) {
  const widget = useMemo(() => manifestMapToWidget(screen, map), [screen, map]);

  return (
    <div className="op-manifest-embed op-manifest-embed-map">
      <DashboardProvider operatorMode>
        <MapWidgetView widget={widget} refreshIntervalMs={refreshIntervalMs} />
      </DashboardProvider>
    </div>
  );
}
