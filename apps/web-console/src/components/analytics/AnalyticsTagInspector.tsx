import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchAnalyticsTagByPath, type AnalyticsTagCatalogEntryDto } from "../../api";

interface AnalyticsTagInspectorProps {
  path: string;
}

function formatInstant(value: string | null | undefined): string {
  if (!value) {
    return "—";
  }
  try {
    return new Date(value).toLocaleString();
  } catch {
    return value;
  }
}

function SourceList({ tag }: { tag: AnalyticsTagCatalogEntryDto }) {
  if (!tag.sources.length) {
    return <p className="hint">—</p>;
  }
  return (
    <ul className="analytics-tag-source-list">
      {tag.sources.map((source) => (
        <li key={`${source.path}.${source.variable}.${source.field}`}>
          <code>{source.path}</code>
          <span className="hint">.{source.variable}</span>
          {source.field !== "value" && <span className="hint"> [{source.field}]</span>}
        </li>
      ))}
    </ul>
  );
}

function LineageGraph({ tag }: { tag: AnalyticsTagCatalogEntryDto }) {
  if (!tag.lineage.nodes.length) {
    return <p className="hint">—</p>;
  }
  return (
    <div className="analytics-tag-lineage">
      <ul className="analytics-tag-lineage-nodes">
        {tag.lineage.nodes.map((node) => (
          <li key={node.id} data-kind={node.kind}>
            <span className="analytics-tag-lineage-kind">{node.kind}</span>
            <code>{node.label}</code>
          </li>
        ))}
      </ul>
      {tag.lineage.edges.length > 0 && (
        <ul className="analytics-tag-lineage-edges">
          {tag.lineage.edges.map((edge) => (
            <li key={`${edge.from}->${edge.to}`}>
              <code>{edge.from}</code>
              <span className="hint"> → </span>
              <code>{edge.to}</code>
              <span className="hint"> ({edge.relation})</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export default function AnalyticsTagInspector({ path }: AnalyticsTagInspectorProps) {
  const { t } = useTranslation(["automation", "common"]);

  const tagQuery = useQuery({
    queryKey: ["analytics-tag", path],
    queryFn: () => fetchAnalyticsTagByPath(path),
  });

  if (tagQuery.isLoading) {
    return <p className="hint">{t("automation:analyticsTag.loading")}</p>;
  }

  if (tagQuery.error || !tagQuery.data) {
    return <p className="hint error">{String(tagQuery.error ?? t("automation:analyticsTag.missing"))}</p>;
  }

  const tag = tagQuery.data;

  return (
    <section className="automation-inspector analytics-tag-inspector">
      <header className="automation-panel-head">
        <div>
          <h3>{t("automation:analyticsTag.title")}</h3>
          <p className="hint">{t("automation:analyticsTag.subtitle")}</p>
          <p className="hint">
            <code>{path}</code>
          </p>
        </div>
      </header>

      <dl className="analytics-tag-summary">
        <div>
          <dt>{t("automation:analyticsTag.helper")}</dt>
          <dd><code>{tag.helper}</code></dd>
        </div>
        <div>
          <dt>{t("automation:analyticsTag.outputVariable")}</dt>
          <dd><code>{tag.outputVariable}</code></dd>
        </div>
        <div>
          <dt>{t("automation:analyticsTag.expression")}</dt>
          <dd><code>{tag.expression}</code></dd>
        </div>
        <div>
          <dt>{t("automation:analyticsTag.windowBucket")}</dt>
          <dd><code>{tag.windowBucket}</code></dd>
        </div>
        <div>
          <dt>{t("automation:analyticsTag.enabled")}</dt>
          <dd>{tag.enabled ? t("common:action.yes") : t("common:action.no")}</dd>
        </div>
        <div>
          <dt>{t("automation:analyticsTag.qualityStatus")}</dt>
          <dd><span className={`status-badge quality-${tag.qualityStatus}`}>{tag.qualityStatus}</span></dd>
        </div>
        <div>
          <dt>{t("automation:analyticsTag.lastEvalAt")}</dt>
          <dd>{formatInstant(tag.lastEvalAt)}</dd>
        </div>
        <div>
          <dt>{t("automation:analyticsTag.lastTickAt")}</dt>
          <dd>{formatInstant(tag.lastTickAt)}</dd>
        </div>
      </dl>

      <section>
        <h4>{t("automation:analyticsTag.sourcesTitle")}</h4>
        <SourceList tag={tag} />
      </section>

      <section>
        <h4>{t("automation:analyticsTag.impactTitle")}</h4>
        {tag.downstreamTagPaths.length === 0 ? (
          <p className="hint">{t("automation:analyticsTag.noDownstream")}</p>
        ) : (
          <ul className="analytics-tag-impact-list">
            {tag.downstreamTagPaths.map((downstream) => (
              <li key={downstream}><code>{downstream}</code></li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <h4>{t("automation:analyticsTag.lineageTitle")}</h4>
        <LineageGraph tag={tag} />
      </section>
    </section>
  );
}
