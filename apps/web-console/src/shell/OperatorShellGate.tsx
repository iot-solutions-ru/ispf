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
  const operatorAppId = rawOperatorAppId
    ? resolveRegistryOperatorAppId(rawOperatorAppId, operatorApps ?? [])
    : rawOperatorAppId;
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
