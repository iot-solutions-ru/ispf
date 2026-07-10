import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import {
  fetchSolutionCatalog,
  uninstallAnalyticsPack,
  uninstallApplication,
  type SolutionCatalogAnalyticsPack,
  type SolutionCatalogInstalled,
} from "../../api/solutions";
import MarketplaceBrowser from "./MarketplaceBrowser";

function InstalledAppCard({
  app,
  onUninstall,
  uninstalling,
}: {
  app: SolutionCatalogInstalled;
  onUninstall: (appId: string) => void;
  uninstalling: boolean;
}) {
  const { t } = useTranslation("system");
  const versionCount = app.versions?.length ?? 0;
  return (
    <article className="solution-catalog-card marketplace-listing-card marketplace-listing-card--application">
      <header className="solution-catalog-card-head">
        <div>
          <strong>{app.bundleDisplayName ?? app.displayName ?? app.appId}</strong>
          <p className="op-muted solution-catalog-app-id">
            <code>{app.appId}</code>
          </p>
        </div>
        {app.activeVersion && (
          <span className="solution-catalog-version-pill">v{app.activeVersion}</span>
        )}
      </header>
      {app.changelog && <p className="solution-catalog-changelog">{String(app.changelog)}</p>}
      <dl className="solution-catalog-kv">
        {app.schemaName && (
          <div>
            <dt>{t("solutions.schema")}</dt>
            <dd><code>{app.schemaName}</code></dd>
          </div>
        )}
        <div>
          <dt>{t("solutions.versions")}</dt>
          <dd>{versionCount}</dd>
        </div>
        {app.screenCount != null && app.screenCount > 0 && (
          <div>
            <dt>{t("solutions.screens")}</dt>
            <dd>{app.screenCount}</dd>
          </div>
        )}
      </dl>
      <footer className="solution-catalog-card-actions">
        {app.activeVersion && (
          <a
            className="btn small"
            href={`/?mode=operator&app=${encodeURIComponent(app.appId)}`}
          >
            {t("solutions.openOperator")}
          </a>
        )}
        <button
          type="button"
          className="btn small danger"
          disabled={uninstalling}
          onClick={() => onUninstall(app.appId)}
        >
          {t("solutions.uninstall")}
        </button>
      </footer>
    </article>
  );
}

function InstalledAnalyticsPackCard({
  pack,
  onUninstall,
  uninstalling,
}: {
  pack: SolutionCatalogAnalyticsPack;
  onUninstall: (packId: string) => void;
  uninstalling: boolean;
}) {
  const { t } = useTranslation("system");
  const helpers = pack.helpers?.length ? pack.helpers : pack.functions ?? [];
  return (
    <article className="solution-catalog-card marketplace-listing-card marketplace-listing-card--analytics-pack">
      <header className="solution-catalog-card-head">
        <div>
          <strong>{pack.packId}</strong>
          <p className="op-muted solution-catalog-app-id">
            <span className="marketplace-kind-badge marketplace-kind-badge--analytics-pack">
              {t("solutions.marketplace.kind.analytics-pack")}
            </span>
          </p>
        </div>
        {pack.version && (
          <span className="solution-catalog-version-pill">v{pack.version}</span>
        )}
      </header>
      {helpers.length > 0 && (
        <p className="op-muted solution-catalog-meta">
          {t("solutions.installedAnalyticsHelpers", { helpers: helpers.join(", ") })}
        </p>
      )}
      <footer className="solution-catalog-card-actions">
        <button
          type="button"
          className="btn small danger"
          disabled={uninstalling}
          onClick={() => onUninstall(pack.packId)}
        >
          {t("solutions.uninstall")}
        </button>
      </footer>
    </article>
  );
}

export default function SolutionCatalogPanel() {
  const { t } = useTranslation("system");
  const queryClient = useQueryClient();
  const [marketplaceMessage, setMarketplaceMessage] = useState<string | null>(null);

  const catalogQuery = useQuery({
    queryKey: ["solution-catalog"],
    queryFn: fetchSolutionCatalog,
  });

  const uninstallAppMutation = useMutation({
    mutationFn: uninstallApplication,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["solution-catalog"] });
      void queryClient.invalidateQueries({ queryKey: ["marketplace-catalog"] });
    },
  });

  const uninstallPackMutation = useMutation({
    mutationFn: uninstallAnalyticsPack,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["solution-catalog"] });
      void queryClient.invalidateQueries({ queryKey: ["marketplace-catalog"] });
    },
  });

  const refreshAll = () => {
    void catalogQuery.refetch();
    void queryClient.invalidateQueries({ queryKey: ["marketplace-catalog"] });
  };

  const installed = catalogQuery.data?.installed ?? [];
  const analyticsPacks = catalogQuery.data?.installedAnalyticsPacks ?? [];
  const hasInstalled = installed.length > 0 || analyticsPacks.length > 0;
  const uninstalling = uninstallAppMutation.isPending || uninstallPackMutation.isPending;

  return (
    <div className="solution-catalog-panel">
      <header className="system-metrics-header">
        <div>
          <h3>{t("solutions.title")}</h3>
          <p className="op-muted">{t("solutions.subtitle")}</p>
        </div>
        <button
          type="button"
          className="btn"
          disabled={catalogQuery.isFetching}
          onClick={refreshAll}
        >
          {t("metrics.refresh")}
        </button>
      </header>

      {catalogQuery.error && (
        <div className="op-alert op-alert-error">{String(catalogQuery.error)}</div>
      )}
      {uninstallAppMutation.error && (
        <div className="op-alert op-alert-error">{String(uninstallAppMutation.error)}</div>
      )}
      {uninstallPackMutation.error && (
        <div className="op-alert op-alert-error">{String(uninstallPackMutation.error)}</div>
      )}
      {(uninstallAppMutation.isSuccess || uninstallPackMutation.isSuccess) && (
        <div className="op-alert op-alert-success">{t("solutions.uninstallOk")}</div>
      )}
      {marketplaceMessage && (
        <div className="op-alert op-alert-success">{marketplaceMessage}</div>
      )}

      <section className="solution-catalog-section">
        <h4>{t("solutions.marketplace.title")}</h4>
        <p className="op-muted">{t("solutions.marketplace.subtitle")}</p>
        <MarketplaceBrowser
          onInstalled={(message) => {
            setMarketplaceMessage(message ?? t("solutions.installOk"));
            void queryClient.invalidateQueries({ queryKey: ["solution-catalog"] });
            void queryClient.invalidateQueries({ queryKey: ["marketplace-catalog"] });
          }}
        />
      </section>

      {catalogQuery.isLoading && <p className="hint">{t("solutions.loading")}</p>}

      <section className="solution-catalog-section">
        <h4>{t("solutions.installedTitle")}</h4>
        {!hasInstalled ? (
          <p className="op-muted">{t("solutions.installedEmpty")}</p>
        ) : (
          <div className="solution-catalog-grid">
            {installed.map((app) => (
              <InstalledAppCard
                key={app.appId}
                app={app}
                uninstalling={uninstalling}
                onUninstall={(appId) => uninstallAppMutation.mutate(appId)}
              />
            ))}
            {analyticsPacks.map((pack) => (
              <InstalledAnalyticsPackCard
                key={pack.packId}
                pack={pack}
                uninstalling={uninstalling}
                onUninstall={(packId) => uninstallPackMutation.mutate(packId)}
              />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
