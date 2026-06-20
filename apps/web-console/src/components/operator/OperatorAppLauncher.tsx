import { useQuery } from "@tanstack/react-query";
import type { OperatorManifest } from "../../types/operatorManifest";

interface OperatorAppEntry {
  appId: string;
  title: string;
  description?: string;
}

interface OperatorAppsIndex {
  apps: OperatorAppEntry[];
}

async function loadAppsIndex(): Promise<OperatorAppEntry[]> {
  const response = await fetch("/operator-apps/index.json");
  if (!response.ok) {
    return [{ appId: "demo", title: "Demo Application", description: "SQL-отчёты" }];
  }
  const index = (await response.json()) as OperatorAppsIndex;
  return index.apps ?? [];
}

async function loadManifestSummary(appId: string): Promise<OperatorManifest | null> {
  const response = await fetch(`/operator-apps/${appId}.manifest.json`);
  if (!response.ok) {
    return null;
  }
  return response.json();
}

interface OperatorAppLauncherProps {
  onOpenApp: (appId: string) => void;
  onSwitchAdmin?: () => void;
}

export default function OperatorAppLauncher({ onOpenApp, onSwitchAdmin }: OperatorAppLauncherProps) {
  const appsQuery = useQuery({
    queryKey: ["operator-apps-index"],
    queryFn: loadAppsIndex,
  });

  return (
    <div className="operator-shell">
      <header className="operator-topbar">
        <div>
          <strong>Оператор · выбор приложения</strong>
          <span className="brand-sub">manifest из deploy bundle или public/operator-apps/</span>
        </div>
        {onSwitchAdmin && (
          <button type="button" className="btn" onClick={onSwitchAdmin}>
            Админ-консоль
          </button>
        )}
      </header>
      <main className="op-launcher">
        {appsQuery.isLoading && <p className="op-muted">Загрузка…</p>}
        {appsQuery.error && <p className="op-alert op-alert-error">{String(appsQuery.error)}</p>}
        <div className="op-launcher-grid">
          {(appsQuery.data ?? []).map((app) => (
            <OperatorAppCard key={app.appId} app={app} onOpen={() => onOpenApp(app.appId)} />
          ))}
        </div>
        <p className="op-muted op-launcher-hint">
          Для отчётов demo: сначала deploy{" "}
          <code>examples/demo-app/bundle.json</code>, затем откройте приложение выше.
        </p>
      </main>
    </div>
  );
}

function OperatorAppCard({ app, onOpen }: { app: OperatorAppEntry; onOpen: () => void }) {
  const manifestQuery = useQuery({
    queryKey: ["operator-app-card", app.appId],
    queryFn: () => loadManifestSummary(app.appId),
  });
  const screenCount = manifestQuery.data?.screens.length ?? 0;
  const reportScreens =
    manifestQuery.data?.screens.filter((screen) => Boolean(screen.report)).length ?? 0;

  return (
    <button type="button" className="op-launcher-card" onClick={onOpen}>
      <strong>{app.title}</strong>
      <span className="op-muted">{app.appId}</span>
      {app.description && <p>{app.description}</p>}
      {manifestQuery.data && (
        <span className="op-launcher-meta">
          {screenCount} экран(ов)
          {reportScreens > 0 ? ` · ${reportScreens} отчёт(ов)` : ""}
        </span>
      )}
    </button>
  );
}
