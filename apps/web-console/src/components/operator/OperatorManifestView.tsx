import { useCallback, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQueryClient } from "@tanstack/react-query";
import { useOperatorManifest } from "../../hooks/useOperatorManifest";
import { useOperatorConnectivity } from "../../hooks/useOperatorConnectivity";
import { ISPF_OPERATOR_WIRE_PROFILE } from "../../types/bff";
import { resolveOperatorScreen } from "../../types/operatorManifest";
import OperatorPreferences from "./OperatorPreferences";
import ManifestScreen from "./ManifestScreen";
import OperatorShellFrame from "./OperatorShellFrame";
import OperatorSidebar from "./OperatorSidebar";
import OperatorSidebarToggle from "./OperatorSidebarToggle";
import OperatorAgentFab from "./OperatorAgentFab";
import OperatorOfflineBanner from "./OperatorOfflineBanner";
import OperatorOfflineBadge from "./OperatorOfflineBadge";
import { useOperatorSidebarDrawer } from "../../hooks/useOperatorSidebarDrawer";
import { cachedAtForManifest } from "../../utils/operatorOfflineCache";
import { syncOperatorCachesOnReconnect } from "../../utils/operatorOfflineSync";

import type { AuthSession } from "../../auth/session";

interface OperatorManifestViewProps {
  appId: string;
  operatorId?: string;
  onSwitchAdmin?: () => void;
  session?: AuthSession;
  onLogout?: () => void;
}

function resolveScreenFromUrl(): string | null {
  return new URLSearchParams(window.location.search).get("screen");
}

export default function OperatorManifestView({
  appId,
  operatorId = "operator",
  onSwitchAdmin,
  session,
  onLogout,
}: OperatorManifestViewProps) {
  const { t } = useTranslation(["operator", "common"]);
  const queryClient = useQueryClient();
  const manifestQuery = useOperatorManifest(appId);
  const { showStaleBanner, reconnecting, offline } = useOperatorConnectivity(() =>
    syncOperatorCachesOnReconnect(queryClient, appId)
  );
  const cachedAt = cachedAtForManifest(appId);
  const [screenId, setScreenId] = useState<string | null>(resolveScreenFromUrl);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  const manifest = manifestQuery.data;
  const wireProfile = manifest?.wireProfile ?? ISPF_OPERATOR_WIRE_PROFILE;

  const activeScreen = useMemo(
    () => (manifest ? resolveOperatorScreen(manifest, screenId) : null),
    [manifest, screenId]
  );
  const sidebarDrawer = useOperatorSidebarDrawer([activeScreen?.id ?? null]);

  const navigateScreen = useCallback(
    (nextId: string) => {
      setScreenId(nextId);
      const url = new URL(window.location.href);
      url.searchParams.set("mode", "operator");
      url.searchParams.set("app", appId);
      url.searchParams.set("screen", nextId);
      window.history.replaceState({}, "", url.toString());
    },
    [appId]
  );

  if (manifestQuery.isLoading) {
    return <div className="operator-shell op-loading">{t("operator:loadingManifest")}</div>;
  }

  if (manifestQuery.error || !manifest || !activeScreen) {
    return (
      <div className="operator-shell op-loading">
        {manifestQuery.error
          ? String(manifestQuery.error)
          : t("operator:manifestNotFound", { appId })}
      </div>
    );
  }

  return (
    <div
      className={`operator-shell${sidebarDrawer.open ? " operator-shell--sidebar-open" : ""}`}
      data-testid="operator-shell"
    >
      <header className="operator-topbar">
        <div>
          <strong>{manifest.title}</strong>
          <OperatorOfflineBadge visible={offline} />
          <span className="brand-sub">
            {appId} · {wireProfile}
            {session ? ` · ${session.displayName}` : ""}
          </span>
        </div>
        <div className="topbar-actions">
          <OperatorSidebarToggle open={sidebarDrawer.open} onClick={sidebarDrawer.toggle} />
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
      <OperatorOfflineBanner
        visible={showStaleBanner}
        cachedAt={cachedAt}
        reconnecting={reconnecting}
      />
      <nav className="op-nav" data-testid="operator-nav">
        {manifest.screens.map((screen) => (
          <button
            key={screen.id}
            type="button"
            className={`btn small ${activeScreen.id === screen.id ? "primary" : ""}`}
            onClick={() => navigateScreen(screen.id)}
          >
            {screen.title}
          </button>
        ))}
      </nav>
      {statusMessage && <div className="op-alert op-alert-info">{statusMessage}</div>}
      <OperatorShellFrame
        mainClassName="operator-dashboard op-manifest-main"
        sidebarOpen={sidebarDrawer.open}
        onSidebarClose={sidebarDrawer.close}
        main={
          <ManifestScreen
            screen={activeScreen}
            wireProfile={wireProfile}
            appId={appId}
            onStatus={setStatusMessage}
          />
        }
        sidebar={<OperatorSidebar operatorId={operatorId} />}
      />
      <OperatorAgentFab appId={appId} />
    </div>
  );
}
