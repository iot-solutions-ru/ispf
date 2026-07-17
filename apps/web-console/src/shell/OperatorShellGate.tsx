import { lazy, Suspense } from "react";
import type { AuthSession } from "../auth/session";
import type { OperatorAppEntry } from "../api/operatorApps";
import { resolveOperatorAppId } from "../auth/routing";
import { resolveOperatorAppId as resolveRegistryOperatorAppId } from "../utils/operatorAppsPath";

const OperatorView = lazy(() => import("../components/operator/OperatorView"));

function LazyFallback() {
  return <div className="loading" />;
}

type Props = {
  session: AuthSession;
  searchParams: URLSearchParams;
  operatorApps: OperatorAppEntry[] | undefined;
  canConfigure: boolean;
  onSelectApp: (appId: string) => void;
  onSwitchAdmin: () => void;
  onLogout: () => void;
};

function isKnownOperatorApp(appId: string, apps: OperatorAppEntry[]): boolean {
  const resolved = resolveRegistryOperatorAppId(appId, apps);
  return apps.some((app) => app.appId === resolved);
}

export default function OperatorShellGate({
  session,
  searchParams,
  operatorApps,
  canConfigure,
  onSelectApp,
  onSwitchAdmin,
  onLogout,
}: Props) {
  const rawOperatorAppId = resolveOperatorAppId(session, searchParams);
  // Wait for registry so a stale autoStartApp (e.g. "platform") does not hang on manifest load.
  if (rawOperatorAppId && operatorApps === undefined) {
    return <LazyFallback />;
  }

  let operatorAppId: string | null = null;
  if (rawOperatorAppId && operatorApps) {
    const resolved = resolveRegistryOperatorAppId(rawOperatorAppId, operatorApps);
    if (isKnownOperatorApp(rawOperatorAppId, operatorApps)) {
      operatorAppId = resolved;
    } else if (typeof window !== "undefined") {
      // Drop unknown ?app=; ignore invalid autoStartApp so we open the launcher once.
      const url = new URL(window.location.href);
      if (url.searchParams.has("app")) {
        url.searchParams.delete("app");
        window.history.replaceState({}, "", url.toString());
      }
    }
  }

  return (
    <Suspense fallback={<LazyFallback />}>
      <OperatorView
        appId={operatorAppId}
        operatorId={session.username}
        onSelectApp={onSelectApp}
        onSwitchAdmin={canConfigure ? onSwitchAdmin : undefined}
        session={session}
        onLogout={onLogout}
      />
    </Suspense>
  );
}
