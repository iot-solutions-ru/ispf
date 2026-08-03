import { lazy, Suspense, useMemo } from "react";
import { DashboardProvider } from "../dashboard/DashboardContext";
import {
  manifestMapToWidget,
  type OperatorManifestMap,
  type OperatorManifestScreen,
} from "../../types/operatorManifest";

const MapWidgetView = lazy(() => import("../dashboard/widgets/MapWidgetView"));

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
        <Suspense fallback={null}>
          <MapWidgetView widget={widget} refreshIntervalMs={refreshIntervalMs} />
        </Suspense>
      </DashboardProvider>
    </div>
  );
}
