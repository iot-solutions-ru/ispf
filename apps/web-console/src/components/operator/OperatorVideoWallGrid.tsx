import { useCallback } from "react";
import type { DashboardLayoutPreset } from "../../types/dashboard";
import type { OperatorUiDashboard } from "../../types/operatorUi";
import DashboardBuilder from "../dashboard/DashboardBuilder";
import { emptySession, mergeSession, type DashboardSession } from "../dashboard/DashboardContext";
import { videoWallSlotCount } from "../dashboard/dashboardLayoutPresets";

interface OperatorVideoWallGridProps {
  dashboards: OperatorUiDashboard[];
  appId: string;
  layoutPreset: DashboardLayoutPreset;
  sessionsByDashboard: Record<string, DashboardSession>;
  onSessionChange: (path: string, session: DashboardSession) => void;
  onNavigateDashboard: (path: string) => void;
  federationPeerId: string | null;
}

/**
 * Multi-dashboard mosaic for control-room video walls (BL-148).
 */
export default function OperatorVideoWallGrid({
  dashboards,
  appId,
  layoutPreset,
  sessionsByDashboard,
  onSessionChange,
  onNavigateDashboard,
  federationPeerId,
}: OperatorVideoWallGridProps) {
  const slotCount = videoWallSlotCount(layoutPreset);
  const slots = dashboards.slice(0, slotCount);
  while (slots.length < slotCount) {
    slots.push({ path: "", title: "" });
  }

  const sessionForPath = useCallback(
    (path: string): DashboardSession => {
      const base = path ? (sessionsByDashboard[path] ?? emptySession()) : emptySession();
      if (!federationPeerId) {
        return base;
      }
      return mergeSession(base, { selection: { federationPeer: federationPeerId } });
    },
    [federationPeerId, sessionsByDashboard]
  );

  return (
    <div
      className={`operator-video-wall-grid operator-video-wall-grid--${layoutPreset}`}
      data-testid="operator-video-wall-grid"
      data-layout-preset={layoutPreset}
    >
      {slots.map((dashboard, index) => (
        <div
          key={dashboard.path || `empty-${index}`}
          className={`operator-video-wall-cell${dashboard.path ? "" : " operator-video-wall-cell--empty"}`}
        >
          {dashboard.path ? (
            <>
              <header className="operator-video-wall-cell-title">{dashboard.title}</header>
              <div className="operator-video-wall-cell-body">
                <DashboardBuilder
                  key={dashboard.path}
                  path={dashboard.path}
                  operatorMode
                  session={sessionForPath(dashboard.path)}
                  onSessionChange={(next) => onSessionChange(dashboard.path, next)}
                  onNavigateDashboard={onNavigateDashboard}
                />
              </div>
            </>
          ) : (
            <div className="operator-video-wall-cell-placeholder" aria-hidden />
          )}
        </div>
      ))}
      <span className="sr-only">{appId}</span>
    </div>
  );
}
