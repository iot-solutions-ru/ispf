import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  evaluateAnalyticsExpression,
  fetchAnalyticsTagByPath,
  refreshAnalyticsDerivedTag,
  setVariable,
  validateAnalyticsExpression,
  type AnalyticsTagCatalogEntryDto,
} from "../../api";
import type { DataRecord } from "../../types";
import BindingExpressionField from "../BindingExpressionField";
import { ANALYTICS_CEL_BINDING_ENTRIES } from "../../utils/analyticsCelBindings";

interface AnalyticsTagInspectorProps {
  path: string;
  canManage?: boolean;
  /** Read-only catalog view (binding-rule historian tags). Hides legacy editor. */
  readOnly?: boolean;
}

const CEL_HELPERS = new Set(["cel", "expression"]);

function stringRecord(value: string): DataRecord {
  return {
    schema: {
      name: "stringValue",
      fields: [{ name: "value", type: "STRING" }],
    },
    rows: [{ value }],
  };
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

export default function AnalyticsTagInspector({
  path,
  canManage = false,
  readOnly = false,
}: AnalyticsTagInspectorProps) {
  const { t } = useTranslation(["automation", "common", "inspector"]);
  const queryClient = useQueryClient();
  const [helperMode, setHelperMode] = useState<"builtin" | "cel">("builtin");
  const [expressionDraft, setExpressionDraft] = useState("");
  const [saveMessage, setSaveMessage] = useState<string | null>(null);
  const [evalResult, setEvalResult] = useState<string | null>(null);

  const tagQuery = useQuery({
    queryKey: ["analytics-tag", path],
    queryFn: () => fetchAnalyticsTagByPath(path),
  });

  useEffect(() => {
    if (!tagQuery.data) {
      return;
    }
    const isCel = CEL_HELPERS.has(tagQuery.data.helper.toLowerCase());
    setHelperMode(isCel ? "cel" : "builtin");
    setExpressionDraft(tagQuery.data.expression ?? "");
  }, [tagQuery.data]);

  const saveMutation = useMutation({
    mutationFn: async () => {
      const helper = helperMode === "cel" ? "cel" : tagQuery.data?.helper ?? "rollingAvg";
      await setVariable(path, "analyticsHelper", stringRecord(helper));
      if (helperMode === "cel") {
        await setVariable(path, "analyticsExpression", stringRecord(expressionDraft.trim()));
      }
      await refreshAnalyticsDerivedTag(path);
    },
    onSuccess: async () => {
      setSaveMessage(t("automation:analyticsTag.saved"));
      setEvalResult(null);
      await queryClient.invalidateQueries({ queryKey: ["analytics-tag", path] });
    },
    onError: (error: Error) => {
      setSaveMessage(error.message);
    },
  });

  const evaluateMutation = useMutation({
    mutationFn: () => {
      const expression = readOnly ? (tagQuery.data?.expression ?? "") : expressionDraft.trim();
      return evaluateAnalyticsExpression(expression, path);
    },
    onSuccess: (result) => {
      setEvalResult(`${result.value} (${result.latencyMs} ms)`);
    },
    onError: (error: Error) => {
      setEvalResult(error.message);
    },
  });

  if (tagQuery.isLoading) {
    return <p className="hint">{t("automation:analyticsTag.loading")}</p>;
  }

  if (tagQuery.error || !tagQuery.data) {
    return <p className="hint error">{String(tagQuery.error ?? t("automation:analyticsTag.missing"))}</p>;
  }

  const tag = tagQuery.data;
  const isCelMode = helperMode === "cel";
  const showLegacyEditor = canManage && !readOnly;

  return (
    <section className={`automation-inspector analytics-tag-inspector${readOnly ? " analytics-tag-inspector-readonly" : ""}`}>
      <header className="automation-panel-head">
        <div>
          <h3>{readOnly ? t("inspector:computations.inspectHeading") : t("automation:analyticsTag.title")}</h3>
          <p className="hint">
            {readOnly ? t("inspector:computations.inspectSubtitle") : t("automation:analyticsTag.subtitle")}
          </p>
          <p className="hint">
            <code>{path}</code>
          </p>
        </div>
      </header>

      {readOnly && (
        <div className="analytics-tag-readonly-actions">
          <button
            type="button"
            className="btn primary small"
            disabled={!tag.expression?.trim() || evaluateMutation.isPending}
            onClick={() => evaluateMutation.mutate()}
          >
            {t("automation:analyticsTag.evaluate")}
          </button>
          {evalResult && <p className="hint">{evalResult}</p>}
        </div>
      )}

      {showLegacyEditor && (
        <section className="analytics-tag-editor">
          <label className="analytics-tag-editor-mode">
            <span>{t("automation:analyticsTag.helperMode")}</span>
            <select
              value={helperMode}
              onChange={(event) => setHelperMode(event.target.value as "builtin" | "cel")}
            >
              <option value="builtin">{t("automation:analyticsTag.helperBuiltin")}</option>
              <option value="cel">{t("automation:analyticsTag.helperCel")}</option>
            </select>
          </label>

          {isCelMode && (
            <>
              <label>
                <span>{t("automation:analyticsTag.expression")}</span>
                <BindingExpressionField
                  id="analytics-tag-expression"
                  value={expressionDraft}
                  onChange={setExpressionDraft}
                  objectPath={path}
                  entries={ANALYTICS_CEL_BINDING_ENTRIES}
                  placeholder={t("automation:analyticsTag.expressionPlaceholder")}
                  editorTitle={t("automation:analyticsTag.expression")}
                  onValidate={async (expression) => {
                    const result = await validateAnalyticsExpression(expression, path);
                    return {
                      valid: result.valid,
                      error: result.errors[0] ?? null,
                    };
                  }}
                />
              </label>
              <p className="hint">{t("automation:analyticsTag.expressionHint")}</p>
              <div className="analytics-tag-editor-actions">
                <button
                  type="button"
                  className="btn primary"
                  disabled={!expressionDraft.trim() || saveMutation.isPending}
                  onClick={() => saveMutation.mutate()}
                >
                  {t("automation:analyticsTag.save")}
                </button>
                <button
                  type="button"
                  className="btn"
                  disabled={!expressionDraft.trim() || evaluateMutation.isPending}
                  onClick={() => evaluateMutation.mutate()}
                >
                  {t("automation:analyticsTag.evaluate")}
                </button>
              </div>
              {evalResult && <p className="hint">{evalResult}</p>}
              {saveMessage && <p className="hint">{saveMessage}</p>}
            </>
          )}

          {!isCelMode && (
            <p className="hint">{t("automation:analyticsTag.builtinHint")}</p>
          )}
        </section>
      )}

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
