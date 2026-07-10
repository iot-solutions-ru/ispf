import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { filterPlatformBindings, type PlatformBindingEntry } from "../../utils/platformBindings";
import { HISTORIAN_EXPRESSION_FALLBACK_ENTRIES } from "../../utils/historianExpressionBindings";
import { useAnalyticsCatalog, useAnalyticsCatalogFunction } from "../../hooks/useAnalyticsCatalog";
import type { AnalyticsCatalogEntryDto, AnalyticsCatalogParameterDto } from "../../api/analyticsCatalog";
import ApplyAnalyticsFormulaModal, { type FormulaApplyResult } from "./ApplyAnalyticsFormulaModal";
import type { BindingFormulaLink } from "../../types";
import { matchesAnalyticsCatalogKindFilter, type AnalyticsFormulaKindFilter } from "../../utils/analyticsCatalogKindFilter";

export type { AnalyticsFormulaKindFilter };

interface AnalyticsFormulaBrowserProps {
  disabled?: boolean;
  defaultKind?: AnalyticsFormulaKindFilter;
  fallbackEntries?: PlatformBindingEntry[];
  onInsert: (snippet: string, formulaLink?: BindingFormulaLink | null) => void;
  initialFormulaLink?: BindingFormulaLink | null;
}

function matchesQuery(entry: AnalyticsCatalogEntryDto, query: string): boolean {
  const normalized = query.trim().toLowerCase();
  if (!normalized) {
    return true;
  }
  return (
    entry.id.toLowerCase().includes(normalized) ||
    entry.displayName.toLowerCase().includes(normalized) ||
    entry.syntax.toLowerCase().includes(normalized) ||
    entry.description.toLowerCase().includes(normalized) ||
    entry.tags.some((tag) => tag.toLowerCase().includes(normalized))
  );
}

function placeholderForParam(parameter: AnalyticsCatalogParameterDto): string {
  const defaultValue = parameter.defaultValue?.trim();
  if (defaultValue) {
    return defaultValue;
  }
  return `<${parameter.name}>`;
}

function buildInsertSnippet(entry: AnalyticsCatalogEntryDto): string {
  if (!entry.parameters.length) {
    return `${entry.id}()`;
  }
  const args = entry.parameters.map((parameter) => placeholderForParam(parameter));
  return `${entry.id}(${args.join(", ")})`;
}

export default function AnalyticsFormulaBrowser({
  disabled = false,
  defaultKind = "historian",
  fallbackEntries = [],
  onInsert,
  initialFormulaLink = null,
}: AnalyticsFormulaBrowserProps) {
  const { t } = useTranslation("inspector");
  const [search, setSearch] = useState("");
  const [kindFilter, setKindFilter] = useState<AnalyticsFormulaKindFilter>(defaultKind);
  const catalogQuery = useAnalyticsCatalog(kindFilter);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [applyEntry, setApplyEntry] = useState<AnalyticsCatalogEntryDto | null>(null);

  const effectiveFallbackEntries = useMemo(() => {
    if (fallbackEntries.length > 0) {
      return fallbackEntries;
    }
    if (kindFilter === "reactive") {
      return [];
    }
    return HISTORIAN_EXPRESSION_FALLBACK_ENTRIES;
  }, [fallbackEntries, kindFilter]);

  const remoteFiltered = useMemo(() => {
    const list = catalogQuery.data ?? [];
    return list.filter(
      (entry) => matchesAnalyticsCatalogKindFilter(entry, kindFilter) && matchesQuery(entry, search)
    );
  }, [catalogQuery.data, kindFilter, search]);

  const fallbackFiltered = useMemo(
    () => filterPlatformBindings(search, effectiveFallbackEntries),
    [search, effectiveFallbackEntries]
  );

  const showRemoteCatalog = remoteFiltered.length > 0;
  const selectedQuery = useAnalyticsCatalogFunction(selectedId, showRemoteCatalog);

  return (
    <div className="analytics-formula-browser">
      <div className="analytics-formula-browser-toolbar">
        <input
          type="search"
          value={search}
          placeholder={t("catalog.search")}
          onChange={(event) => setSearch(event.target.value)}
        />
        <select
          value={kindFilter}
          disabled={disabled}
          onChange={(event) => setKindFilter(event.target.value as AnalyticsFormulaKindFilter)}
        >
          <option value="historian">{t("catalog.kind.historian")}</option>
          <option value="reactive">{t("catalog.kind.reactive")}</option>
        </select>
      </div>

      {!showRemoteCatalog && (
        <p className="hint">{t("catalog.fallbackHint")}</p>
      )}

      {showRemoteCatalog ? (
        <ul className="platform-binding-catalog-list analytics-formula-browser-list">
          {remoteFiltered.map((entry) => (
            <li key={entry.id}>
              <div className="platform-binding-catalog-item-wrap">
                <button
                  type="button"
                  className="platform-binding-catalog-item"
                  disabled={disabled}
                  onClick={() => setSelectedId((current) => (current === entry.id ? null : entry.id))}
                >
                  <div className="platform-binding-catalog-head">
                    <code>{entry.id}</code>
                    <span className="platform-binding-catalog-category">{entry.displayName}</span>
                    <span className="inline-badge">{entry.tier}</span>
                  </div>
                  <code className="platform-binding-catalog-snippet">
                    {entry.tier === "B" ? entry.syntax : buildInsertSnippet(entry)}
                  </code>
                  <p className="hint">{entry.description}</p>
                </button>
                <div className="platform-binding-catalog-actions">
                  {entry.tier === "B" ? (
                    <button
                      type="button"
                      className="btn btn-sm"
                      disabled={disabled}
                      onClick={() => setApplyEntry(entry)}
                    >
                      {t("formula.use")}
                    </button>
                  ) : (
                    <button
                      type="button"
                      className="btn btn-sm"
                      disabled={disabled}
                      onClick={() => onInsert(buildInsertSnippet(entry))}
                    >
                      {t("catalog.insert")}
                    </button>
                  )}
                </div>
              </div>
              {selectedId === entry.id && selectedQuery.data && (
                <div className="analytics-formula-browser-detail">
                  <code>{selectedQuery.data.syntax}</code>
                  {selectedQuery.data.examples.length > 0 && (
                    <p className="hint">
                      {t("catalog.examples")} {selectedQuery.data.examples[0]}
                    </p>
                  )}
                </div>
              )}
            </li>
          ))}
        </ul>
      ) : (
        <ul className="platform-binding-catalog-list">
          {fallbackFiltered.map((entry) => (
            <li key={entry.id}>
              <div className="platform-binding-catalog-item-wrap">
                <button
                  type="button"
                  className="platform-binding-catalog-item"
                  disabled={disabled}
                  onClick={() => onInsert(entry.snippet)}
                >
                  <div className="platform-binding-catalog-head">
                    <code>{entry.name}</code>
                    <span className="platform-binding-catalog-category">
                      {t(`platformBindings.category.${entry.category}`)}
                    </span>
                    {entry.stateful ? (
                      <span className="inline-badge">{t("platformBindings.stateful")}</span>
                    ) : null}
                  </div>
                  <code className="platform-binding-catalog-snippet">{entry.snippet}</code>
                  <p className="hint">{t(`platformBindings.${entry.id}.desc`, { defaultValue: "" })}</p>
                </button>
                <div className="platform-binding-catalog-actions">
                  <button
                    type="button"
                    className="btn btn-sm"
                    disabled={disabled}
                    onClick={() => onInsert(entry.snippet)}
                  >
                    {t("catalog.insert")}
                  </button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
      <ApplyAnalyticsFormulaModal
        open={applyEntry != null}
        entry={applyEntry}
        initialParams={initialFormulaLink && initialFormulaLink.formulaRef === applyEntry?.id ? initialFormulaLink.formulaParams : null}
        onClose={() => setApplyEntry(null)}
        onApply={(result: FormulaApplyResult) => onInsert(result.expression, result.formulaLink)}
      />
    </div>
  );
}
