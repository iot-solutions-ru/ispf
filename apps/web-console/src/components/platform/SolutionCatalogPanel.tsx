import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import {
  fetchSolutionCatalog,
  installReferenceSolution,
  type SolutionCatalogInstalled,
  type SolutionReferenceExample,
} from "../../api/solutions";

function InstalledAppCard({ app }: { app: SolutionCatalogInstalled }) {
  const { t } = useTranslation("system");
  const versionCount = app.versions?.length ?? 0;
  return (
    <article className="solution-catalog-card">
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
    </article>
  );
}

function ReferenceExampleCard({
  example,
  onInstall,
  installing,
}: {
  example: SolutionReferenceExample;
  onInstall: (id: string) => void;
  installing: boolean;
}) {
  const { t } = useTranslation("system");
  return (
    <article className="solution-catalog-card solution-catalog-card--reference">
      <header className="solution-catalog-card-head">
        <div>
          <strong>{example.title}</strong>
          <p className="op-muted solution-catalog-app-id">
            <code>{example.exampleId}</code> → <code>{example.appId}</code>
          </p>
        </div>
        {example.installed && example.activeVersion && (
          <span className="solution-catalog-version-pill muted">v{example.activeVersion}</span>
        )}
      </header>
      <p>{example.description}</p>
      <footer className="solution-catalog-card-actions">
        <button
          type="button"
          className="btn primary small"
          disabled={installing}
          onClick={() => onInstall(example.exampleId)}
        >
          {example.installed ? t("solutions.reinstall") : t("solutions.installDemo")}
        </button>
        {example.installed && (
          <a
            className="btn small"
            href={`/?mode=operator&app=${encodeURIComponent(example.appId)}`}
          >
            {t("solutions.openOperator")}
          </a>
        )}
      </footer>
    </article>
  );
}

export default function SolutionCatalogPanel() {
  const { t } = useTranslation("system");
  const queryClient = useQueryClient();
  const catalogQuery = useQuery({
    queryKey: ["solution-catalog"],
    queryFn: fetchSolutionCatalog,
  });

  const installMutation = useMutation({
    mutationFn: installReferenceSolution,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["solution-catalog"] });
    },
  });

  const installed = catalogQuery.data?.installed ?? [];
  const references = catalogQuery.data?.referenceExamples ?? [];

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
          onClick={() => catalogQuery.refetch()}
        >
          {t("metrics.refresh")}
        </button>
      </header>

      {catalogQuery.error && (
        <div className="op-alert op-alert-error">{String(catalogQuery.error)}</div>
      )}
      {installMutation.error && (
        <div className="op-alert op-alert-error">{String(installMutation.error)}</div>
      )}
      {installMutation.isSuccess && (
        <div className="op-alert op-alert-success">{t("solutions.installOk")}</div>
      )}

      {catalogQuery.isLoading && <p className="hint">{t("solutions.loading")}</p>}

      {references.length > 0 && (
        <section className="solution-catalog-section">
          <h4>{t("solutions.referenceTitle")}</h4>
          <div className="solution-catalog-grid">
            {references.map((example) => (
              <ReferenceExampleCard
                key={example.exampleId}
                example={example}
                installing={installMutation.isPending}
                onInstall={(id) => installMutation.mutate(id)}
              />
            ))}
          </div>
        </section>
      )}

      <section className="solution-catalog-section">
        <h4>{t("solutions.installedTitle")}</h4>
        {installed.length === 0 ? (
          <p className="op-muted">{t("solutions.installedEmpty")}</p>
        ) : (
          <div className="solution-catalog-grid">
            {installed.map((app) => (
              <InstalledAppCard key={app.appId} app={app} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
