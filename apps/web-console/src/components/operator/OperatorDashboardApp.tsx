import { useCallback, useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import type { AuthSession } from "../../auth/session";
import { isAdminSession } from "../../auth/session";
import { useOperatorUi } from "../../hooks/useOperatorUi";
import { useOperatorConnectivity } from "../../hooks/useOperatorConnectivity";
import { useOperatorAlarmBar } from "../../hooks/useOperatorAlarmBar";
import {
  resolveOperatorDashboard,
  resolveOperatorReport,
  type OperatorUi,
} from "../../types/operatorUi";
import type { DashboardLayoutPreset } from "../../types/dashboard";
import { isVideoWallPreset } from "../dashboard/dashboardLayoutPresets";
import { emptySession, mergeSession, type DashboardSession } from "../dashboard/DashboardContext";
import OperatorPreferences from "./OperatorPreferences";
import DashboardBuilder from "../dashboard/DashboardBuilder";
import ReportBuilder from "../report/ReportBuilder";
import AlarmBarOverlay from "./AlarmBarOverlay";
import AlarmShelfPanel from "./AlarmShelfPanel";
import AlarmShelfApprovalPanel from "./AlarmShelfApprovalPanel";
import OperatorShellFrame from "./OperatorShellFrame";
import OperatorSidebar from "./OperatorSidebar";
import OperatorSidebarToggle from "./OperatorSidebarToggle";
import OperatorAgentFab from "./OperatorAgentFab";
import OperatorOfflineBanner from "./OperatorOfflineBanner";
import OperatorOfflineBadge from "./OperatorOfflineBadge";
import OperatorFederationPeerSelector from "./OperatorFederationPeerSelector";
import OperatorVideoWallGrid from "./OperatorVideoWallGrid";
import { useOperatorSidebarDrawer } from "../../hooks/useOperatorSidebarDrawer";
import { cachedAtForOperatorUi } from "../../utils/operator/operatorOfflineCache";
import { syncOperatorCachesOnReconnect } from "../../utils/operator/operatorOfflineSync";

interface OperatorDashboardAppProps {
  appId: string;
  operatorId?: string;
  onSwitchAdmin?: () => void;
  session?: AuthSession;
  onLogout?: () => void;
}

type OperatorViewKind = "dashboard" | "report";

function resolveDashboardFromUrl(): string | null {
  return new URLSearchParams(window.location.search).get("dashboard");
}

function resolveReportFromUrl(): string | null {
  return new URLSearchParams(window.location.search).get("report");
}

function resolveSessionFromUrl(): DashboardSession {
  const params = new URLSearchParams(window.location.search);
  const session = emptySession();
  for (const [key, value] of params.entries()) {
    if (key.startsWith("ctx.")) {
      session.selection[key.slice(4)] = value;
    }
  }
  const rawCtx = params.get("ctx");
  if (rawCtx) {
    try {
      const parsed = JSON.parse(rawCtx) as {
        selection?: Record<string, string>;
        params?: Record<string, unknown>;
      };
      if (parsed.selection) {
        session.selection = { ...session.selection, ...parsed.selection };
      }
      if (parsed.params) {
        session.params = { ...session.params, ...parsed.params };
      }
    } catch {
      // ignore malformed ctx
    }
  }
  return session;
}

function resolveViewKindFromUrl(): OperatorViewKind {
  return new URLSearchParams(window.location.search).get("report") ? "report" : "dashboard";
}

function operatorDashboardSessionKey(appId: string, dashboardPath: string): string {
  return `ispf-operator-dashboard-session:${appId}:${dashboardPath}`;
}

function loadStoredDashboardSession(appId: string, dashboardPath: string): DashboardSession | null {
  try {
    const raw = sessionStorage.getItem(operatorDashboardSessionKey(appId, dashboardPath));
    if (!raw) {
      return null;
    }
    const parsed = JSON.parse(raw) as DashboardSession;
    return {
      selection: parsed.selection ?? {},
      params: parsed.params ?? {},
    };
  } catch {
    return null;
  }
}

function saveStoredDashboardSession(
  appId: string,
  dashboardPath: string,
  session: DashboardSession
): void {
  try {
    sessionStorage.setItem(
      operatorDashboardSessionKey(appId, dashboardPath),
      JSON.stringify(session)
    );
  } catch {
    // ignore quota / private mode
  }
}

function resolveFederationPeerFromUrl(): string | null {
  const params = new URLSearchParams(window.location.search);
  return params.get("federationPeer") ?? params.get("ctx.federationPeer");
}

export default function OperatorDashboardApp({
  appId,
  operatorId = "operator",
  onSwitchAdmin,
  session,
  onLogout,
}: OperatorDashboardAppProps) {
  const { t } = useTranslation(["operator", "common"]);
  const queryClient = useQueryClient();
  const uiQuery = useOperatorUi(appId);
  const { showStaleBanner, reconnecting, offline } = useOperatorConnectivity(() =>
    syncOperatorCachesOnReconnect(queryClient, appId)
  );
  const cachedAt = cachedAtForOperatorUi(appId);
  const [viewKind, setViewKind] = useState<OperatorViewKind>(resolveViewKindFromUrl);
  const [dashboardPath, setDashboardPath] = useState<string | null>(resolveDashboardFromUrl);
  const [reportPath, setReportPath] = useState<string | null>(resolveReportFromUrl);
  const [sessionsByDashboard, setSessionsByDashboard] = useState<
    Record<string, DashboardSession>
  >(() => {
    const path = resolveDashboardFromUrl();
    if (!path) {
      return {};
    }
    const stored = loadStoredDashboardSession(appId, path);
    const urlSession = resolveSessionFromUrl();
    return {
      [path]: stored ? mergeSession(stored, urlSession) : urlSession,
    };
  });

  const ui = uiQuery.data;
  const activeDashboardPath = useMemo(
    () => (ui ? resolveOperatorDashboard(ui, dashboardPath) : ""),
    [ui, dashboardPath]
  );
  const activeReportPath = useMemo(
    () => (ui ? resolveOperatorReport(ui, reportPath) : ""),
    [ui, reportPath]
  );
  const activePath = viewKind === "report" ? activeReportPath : activeDashboardPath;
  const sidebarDrawer = useOperatorSidebarDrawer([viewKind, activePath]);
  const { open: drawerOpen, close: closeSidebar, toggle: toggleSidebar } = sidebarDrawer;
  const hideTasksAndEvents = ui?.hideTasksAndEvents === true;
  const hideDashboardNav = ui?.hideDashboardNav === true;
  const sidebarOpen = hideTasksAndEvents ? false : drawerOpen;
  const [layoutPreset, setLayoutPreset] = useState<DashboardLayoutPreset | undefined>();
  const [federationPeerId, setFederationPeerId] = useState<string | null>(resolveFederationPeerFromUrl);
  const videoWallMode = viewKind === "dashboard" && isVideoWallPreset(layoutPreset);

  useEffect(() => {
    if (hideTasksAndEvents && drawerOpen) {
      closeSidebar();
    }
  }, [hideTasksAndEvents, drawerOpen, closeSidebar]);

  useEffect(() => {
    setLayoutPreset(undefined);
  }, [activeDashboardPath, viewKind]);

  const handleFederationPeerChange = useCallback(
    (peerId: string | null) => {
      setFederationPeerId(peerId);
      if (!activeDashboardPath) {
        const url = new URL(window.location.href);
        if (peerId) {
          url.searchParams.set("federationPeer", peerId);
          url.searchParams.set("ctx.federationPeer", peerId);
        } else {
          url.searchParams.delete("federationPeer");
          url.searchParams.delete("ctx.federationPeer");
        }
        window.history.replaceState({}, "", url.toString());
        return;
      }
      setSessionsByDashboard((prev) => {
        const current = prev[activeDashboardPath] ?? loadStoredDashboardSession(appId, activeDashboardPath) ?? emptySession();
        const next: DashboardSession = {
          ...current,
          selection: { ...current.selection },
        };
        if (peerId) {
          next.selection.federationPeer = peerId;
        } else {
          delete next.selection.federationPeer;
        }
        saveStoredDashboardSession(appId, activeDashboardPath, next);
        return { ...prev, [activeDashboardPath]: next };
      });
      const url = new URL(window.location.href);
      if (peerId) {
        url.searchParams.set("federationPeer", peerId);
        url.searchParams.set("ctx.federationPeer", peerId);
      } else {
        url.searchParams.delete("federationPeer");
        url.searchParams.delete("ctx.federationPeer");
      }
      window.history.replaceState({}, "", url.toString());
    },
    [activeDashboardPath, appId]
  );

  const handleLayoutPresetChange = useCallback((preset: DashboardLayoutPreset | undefined) => {
    setLayoutPreset(preset);
  }, []);

  const dashboardSession = useMemo(() => {
    const base = activeDashboardPath
      ? (sessionsByDashboard[activeDashboardPath] ?? emptySession())
      : emptySession();
    if (!federationPeerId) {
      return base;
    }
    return mergeSession(base, { selection: { federationPeer: federationPeerId } });
  }, [activeDashboardPath, federationPeerId, sessionsByDashboard]);

  const handleDashboardSessionChange = useCallback(
    (next: DashboardSession) => {
      if (!activeDashboardPath) {
        return;
      }
      setSessionsByDashboard((prev) => ({ ...prev, [activeDashboardPath]: next }));
      saveStoredDashboardSession(appId, activeDashboardPath, next);
    },
    [activeDashboardPath, appId]
  );

  const handleVideoWallSessionChange = useCallback(
    (path: string, next: DashboardSession) => {
      setSessionsByDashboard((prev) => ({ ...prev, [path]: next }));
      saveStoredDashboardSession(appId, path, next);
    },
    [appId]
  );

  useEffect(() => {
    if (!activeDashboardPath || viewKind !== "dashboard") {
      return;
    }
    setSessionsByDashboard((prev) => {
      if (prev[activeDashboardPath]) {
        return prev;
      }
      const stored = loadStoredDashboardSession(appId, activeDashboardPath);
      return { ...prev, [activeDashboardPath]: stored ?? emptySession() };
    });
  }, [activeDashboardPath, appId, viewKind]);

  const navigateDashboard = useCallback(
    (path: string, options?: import("../dashboard/DashboardContext").OpenDashboardOptions) => {
      setSessionsByDashboard((prev) => {
        const current = prev[path] ?? loadStoredDashboardSession(appId, path) ?? emptySession();
        const next = options ? mergeSession(current, options) : current;
        saveStoredDashboardSession(appId, path, next);
        return { ...prev, [path]: next };
      });
      setViewKind("dashboard");
      setDashboardPath(path);
      const url = new URL(window.location.href);
      url.searchParams.set("mode", "operator");
      url.searchParams.set("app", appId);
      url.searchParams.set("dashboard", path);
      url.searchParams.delete("report");
      url.searchParams.delete("screen");
      window.history.replaceState({}, "", url.toString());
    },
    [appId]
  );

  const navigateReport = useCallback(
    (path: string) => {
      setViewKind("report");
      setReportPath(path);
      const url = new URL(window.location.href);
      url.searchParams.set("mode", "operator");
      url.searchParams.set("app", appId);
      url.searchParams.set("report", path);
      url.searchParams.delete("dashboard");
      url.searchParams.delete("screen");
      window.history.replaceState({}, "", url.toString());
    },
    [appId]
  );

  const alarmBar = useOperatorAlarmBar(ui?.alarmBar, {
    ui: ui ?? undefined,
    navigateDashboard,
    navigateReport,
  });
  const showShelfApproval = isAdminSession(session ?? null) && alarmBar.enabled;

  useEffect(() => {
    if (!ui) {
      return;
    }
    if (viewKind === "report") {
      const resolved = resolveOperatorReport(ui, reportPath);
      if (resolved && resolved !== reportPath) {
        navigateReport(resolved);
      }
      return;
    }
    const resolved = resolveOperatorDashboard(ui, dashboardPath);
    if (resolved && resolved !== dashboardPath) {
      navigateDashboard(resolved);
    }
  }, [ui, viewKind, dashboardPath, reportPath, navigateDashboard, navigateReport]);

  if (uiQuery.isLoading) {
    return <div className="operator-shell op-loading" data-testid="operator-shell">{t("operator:loadingUi")}</div>;
  }

  const hasDashboards = Boolean(ui?.dashboards?.length);
  const hasReports = Boolean(ui?.reports?.length);

  if (uiQuery.error || !ui || (!hasDashboards && !hasReports)) {
    return (
      <div className="operator-shell op-loading" data-testid="operator-shell">
        {uiQuery.error
          ? String(uiQuery.error)
          : t("operator:uiNotFound", { appId })}
      </div>
    );
  }

  if (!activePath) {
    return (
      <div className="operator-shell op-loading" data-testid="operator-shell">
        {t("operator:uiNoDashboards", { appId })}
      </div>
    );
  }

  return (
    <div
      className={`operator-shell${alarmBar.hasActiveAlarm ? " operator-alarm-active" : ""}${
        sidebarOpen ? " operator-shell--sidebar-open" : ""
      }${videoWallMode && layoutPreset ? ` operator-shell--${layoutPreset}` : ""}`}
      data-testid="operator-shell"
    >
      {!videoWallMode && (
        <OperatorDashboardChrome
          ui={ui}
          viewKind={viewKind}
          activePath={activePath}
          appId={appId}
          session={session}
          offline={offline}
          federationPeerId={federationPeerId}
          onFederationPeerChange={handleFederationPeerChange}
          onSwitchAdmin={onSwitchAdmin}
          onLogout={onLogout}
          onSelectDashboard={navigateDashboard}
          onSelectReport={navigateReport}
          sidebarOpen={sidebarOpen}
          onToggleSidebar={toggleSidebar}
          hideTasksAndEvents={hideTasksAndEvents}
          hideDashboardNav={hideDashboardNav}
        />
      )}
      <OperatorOfflineBanner
        visible={showStaleBanner}
        cachedAt={cachedAt}
        reconnecting={reconnecting}
      />
      {alarmBar.position === "top" && <AlarmBarOverlay {...alarmBar} />}
      <OperatorShellFrame
        layoutClassName="operator-dashboard-layout"
        mainClassName="operator-dashboard operator-dashboard-shell-host"
        sidebarOpen={sidebarOpen}
        onSidebarClose={closeSidebar}
        main={
          viewKind === "report" ? (
            <ReportBuilder key={activeReportPath} path={activeReportPath} operatorMode />
          ) : videoWallMode ? (
            <OperatorVideoWallGrid
              dashboards={ui.dashboards}
              appId={appId}
              layoutPreset={layoutPreset!}
              sessionsByDashboard={sessionsByDashboard}
              onSessionChange={handleVideoWallSessionChange}
              onNavigateDashboard={navigateDashboard}
              federationPeerId={federationPeerId}
            />
          ) : (
            <DashboardBuilder
              key={activeDashboardPath}
              path={activeDashboardPath}
              operatorMode
              session={dashboardSession}
              onSessionChange={handleDashboardSessionChange}
              onNavigateDashboard={navigateDashboard}
              onLayoutPresetChange={handleLayoutPresetChange}
            />
          )
        }
        sidebar={
          hideTasksAndEvents ? null : (
            <OperatorSidebar appId={appId} operatorId={operatorId} ui={ui} />
          )
        }
      />
      {alarmBar.position === "bottom" && <AlarmBarOverlay {...alarmBar} />}
      {alarmBar.enabled && (
        <AlarmShelfPanel shelves={alarmBar.shelves} onUnshelve={alarmBar.onUnshelveShelf} />
      )}
      <AlarmShelfApprovalPanel enabled={showShelfApproval} />
      {!videoWallMode && (
        <OperatorAgentFab
          appId={appId}
          onOpenDashboard={navigateDashboard}
          onOpenReport={navigateReport}
        />
      )}
    </div>
  );
}

function OperatorDashboardChrome({
  ui,
  viewKind,
  activePath,
  appId,
  session,
  offline,
  federationPeerId,
  onFederationPeerChange,
  onSwitchAdmin,
  onLogout,
  onSelectDashboard,
  onSelectReport,
  sidebarOpen,
  onToggleSidebar,
  hideTasksAndEvents,
  hideDashboardNav,
}: {
  ui: OperatorUi;
  viewKind: OperatorViewKind;
  activePath: string;
  appId: string;
  session?: AuthSession;
  offline?: boolean;
  federationPeerId: string | null;
  onFederationPeerChange: (peerId: string | null) => void;
  onSwitchAdmin?: () => void;
  onLogout?: () => void;
  onSelectDashboard: (path: string) => void;
  onSelectReport: (path: string) => void;
  sidebarOpen: boolean;
  onToggleSidebar: () => void;
  hideTasksAndEvents: boolean;
  hideDashboardNav: boolean;
}) {
  const { t } = useTranslation(["operator", "common"]);
  const activeTitle =
    viewKind === "report"
      ? (ui.reports?.find((item) => item.path === activePath)?.title ?? activePath)
      : (ui.dashboards.find((item) => item.path === activePath)?.title ?? activePath);

  return (
    <>
      <header className="operator-topbar">
        <div>
          <strong>{ui.title}</strong>
          <OperatorOfflineBadge visible={Boolean(offline)} />
          <span className="brand-sub">
            {appId} · {activeTitle}
            {session ? ` · ${session.displayName}` : ""}
          </span>
        </div>
        <div className="topbar-actions">
          <OperatorFederationPeerSelector
            selectedPeerId={federationPeerId}
            onSelectPeer={onFederationPeerChange}
          />
          {!hideTasksAndEvents && (
            <OperatorSidebarToggle
              open={sidebarOpen}
              onClick={onToggleSidebar}
              appId={appId}
              ui={ui}
            />
          )}
          <OperatorPreferences />
          {onLogout && (
            <button type="button" className="btn" onClick={onLogout}>
              {t("common:action.logout")}
            </button>
          )}
          {onSwitchAdmin && (
            <button type="button" className="btn" onClick={onSwitchAdmin}>
              {t("operator:launcher.switchAdmin")}
            </button>
          )}
        </div>
      </header>
      {!hideDashboardNav && (
        <nav className="op-nav" data-testid="operator-nav">
          {ui.dashboards.map((dashboard) => (
            <button
              key={dashboard.path}
              type="button"
              className={`btn small ${viewKind === "dashboard" && dashboard.path === activePath ? "primary" : ""}`}
              onClick={() => onSelectDashboard(dashboard.path)}
            >
              {dashboard.title}
            </button>
          ))}
          {(ui.reports ?? []).map((report) => (
            <button
              key={report.path}
              type="button"
              className={`btn small ${viewKind === "report" && report.path === activePath ? "primary" : ""}`}
              onClick={() => onSelectReport(report.path)}
            >
              {report.title}
            </button>
          ))}
        </nav>
      )}
    </>
  );
}
