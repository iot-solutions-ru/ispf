import { useMemo, useState } from "react";
import DashboardBuilder from "../dashboard/DashboardBuilder";
import type { DashboardSession } from "../dashboard/DashboardContext";
import {
  parseManifestSelectionJson,
  type OperatorManifestDashboard,
} from "../../types/operatorManifest";
interface ManifestEmbeddedDashboardProps {
  config: OperatorManifestDashboard;
}

export default function ManifestEmbeddedDashboard({ config }: ManifestEmbeddedDashboardProps) {
  const initialSelection = useMemo(
    () => parseManifestSelectionJson(config.selectionJson),
    [config.selectionJson]
  );
  const [session, setSession] = useState<DashboardSession>(() => ({
    selection: initialSelection,
    params: {},
  }));

  return (
    <div className="op-manifest-embed op-manifest-embed-dashboard">
      <DashboardBuilder
        path={config.dashboardPath}
        operatorMode
        session={session}
        onSessionChange={setSession}
      />
    </div>
  );
}
