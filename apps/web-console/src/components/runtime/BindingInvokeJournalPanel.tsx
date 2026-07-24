import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { Button, Input, Select } from "antd";
import { fetchBindingAuditStatus, fetchBindingInvocations } from "../../api";
import type { BindingInvokeAuditEntry } from "../../types/runtime";
import { mapBindingInvokeExportRow } from "../../utils/journal/journalExport";
import { parseAuditBeforeAfter } from "../../utils/journal/journalDetails";
import JournalViewShell, { JOURNAL_VIEW_MODES, type JournalViewMode } from "../journal/JournalViewShell";
import JournalVirtualList from "../journal/JournalVirtualList";
import JournalExpandableItem from "../journal/JournalExpandableItem";
import { usePersistentTab } from "../../hooks/usePersistentTab";
import { useUserTimeZone } from "../../context/UserTimeZoneContext";
import { useSystemTabFocus } from "../../hooks/useSystemTabFocus";

const LIVE_LIMIT = 25;
const HISTORY_PAGE = 50;
const HISTORY_MAX = 200;
const ITEM_ESTIMATE_PX = 108;

interface BindingInvokeJournalPanelProps {
  objectPath?: string;
  ruleId?: string;
  limit?: number;
  showFilters?: boolean;
  compact?: boolean;
  scrollMaxHeight?: number | string;
  defaultMode?: JournalViewMode;
  /** When true (System console), publish Admin Copilot focus. */
  publishAdminFocus?: boolean;
}

export default function BindingInvokeJournalPanel({
  objectPath: fixedObjectPath,
  ruleId: fixedRuleId,
  limit,
  showFilters = false,
  compact = false,
  scrollMaxHeight,
  defaultMode = "live",
  publishAdminFocus = false,
}: BindingInvokeJournalPanelProps) {
  const { t } = useTranslation(["runtime", "journal", "common"]);
  const [mode, setMode] = usePersistentTab<JournalViewMode>(
    `binding-journal:${fixedObjectPath ?? "all"}:${fixedRuleId ?? "all"}`,
    defaultMode,
    JOURNAL_VIEW_MODES
  );
  const [historyLimit, setHistoryLimit] = useState(HISTORY_PAGE);
  const [objectPath, setObjectPath] = useState(fixedObjectPath ?? "");
  const [bindingKind, setBindingKind] = useState("");
  const [ruleFilter, setRuleFilter] = useState(fixedRuleId ?? "");
  const [successFilter, setSuccessFilter] = useState<"" | "true" | "false">("");
  const [changedFilter, setChangedFilter] = useState<"" | "true" | "false">("");
  const [searchFilter, setSearchFilter] = useState("");

  const liveLimit = limit ?? LIVE_LIMIT;
  const fetchLimit = mode === "live" ? liveLimit : historyLimit;

  const statusQuery = useQuery({
    queryKey: ["binding-audit-status", fixedObjectPath ?? objectPath],
    queryFn: () => fetchBindingAuditStatus(fixedObjectPath ?? (objectPath.trim() || undefined)),
    staleTime: 30_000,
  });

  const query = useQuery({
    queryKey: [
      "binding-invocations",
      fixedObjectPath ?? objectPath,
      fixedRuleId ?? ruleFilter,
      bindingKind,
      successFilter,
      changedFilter,
      fetchLimit,
      mode,
    ],
    queryFn: () =>
      fetchBindingInvocations({
        objectPath: fixedObjectPath ?? (objectPath.trim() || undefined),
        bindingKind: bindingKind || undefined,
        ruleId: fixedRuleId ?? (ruleFilter.trim() || undefined),
        success: successFilter === "" ? undefined : successFilter === "true",
        changed: changedFilter === "" ? undefined : changedFilter === "true",
        limit: fetchLimit,
      }),
    refetchInterval: mode === "live" ? 10_000 : false,
    staleTime: mode === "live" ? 0 : 30_000,
  });

  const filtered = useMemo(() => {
    let rows = query.data ?? [];
    if (mode === "history") {
      const queryText = searchFilter.trim().toLowerCase();
      if (queryText) {
        rows = rows.filter(
          (entry) =>
            entry.objectPath.toLowerCase().includes(queryText)
            || (entry.ruleName?.toLowerCase().includes(queryText) ?? false)
            || (entry.ruleId?.toLowerCase().includes(queryText) ?? false)
            || (entry.targetVariable?.toLowerCase().includes(queryText) ?? false)
            || (entry.errorMessage?.toLowerCase().includes(queryText) ?? false),
        );
      }
    }
    return rows;
  }, [mode, query.data, searchFilter]);

  const exportRows = useMemo(
    () => filtered.map(mapBindingInvokeExportRow),
    [filtered],
  );

  const exportFilenameBase = fixedObjectPath
    ? `binding-journal-${fixedObjectPath.replace(/\./g, "-")}`
    : "binding-journal";

  const focusDetail = useMemo(
    () => ({
      journalMode: mode,
      filters: {
        objectPath: fixedObjectPath || objectPath,
        bindingKind,
        ruleId: fixedRuleId || ruleFilter,
        success: successFilter,
        changed: changedFilter,
        search: searchFilter,
      },
      resultCount: filtered.length,
      sampleEntries: filtered.slice(0, 8).map((entry) => ({
        ruleId: entry.ruleId,
        ruleName: entry.ruleName,
        targetVariable: entry.targetVariable,
        objectPath: entry.objectPath,
        changed: entry.changed,
        success: entry.success,
      })),
      screenHint:
        "Binding invoke journal — help debug CEL rules, propose expression fixes from failed/changed samples",
      helpIntents: ["explainBindingFailures", "draftCelFix", "suggestActivators"],
    }),
    [
      mode,
      fixedObjectPath,
      objectPath,
      bindingKind,
      fixedRuleId,
      ruleFilter,
      successFilter,
      changedFilter,
      searchFilter,
      filtered,
    ]
  );
  useSystemTabFocus("system-binding-journal", "bindings", focusDetail, {
    active: publishAdminFocus,
    screenTitle: "System › Binding journal",
  });

  const canLoadMore =
    mode === "history"
    && (query.data?.length ?? 0) >= historyLimit
    && historyLimit < HISTORY_MAX;

  const auditDisabled = statusQuery.data?.enabled === false;
  const auditHint = !statusQuery.data?.masterEnabled
    ? t("bindingJournal.masterDisabledHint")
    : fixedObjectPath && !statusQuery.data?.objectEnabled
      ? t("bindingJournal.objectDisabledHint")
      : auditDisabled
        ? t("bindingJournal.disabledHint")
        : t("bindingJournal.subtitle");

  return (
    <JournalViewShell
      title={t("bindingJournal.title")}
      subtitle={auditHint}
      mode={mode}
      onModeChange={setMode}
      count={filtered.length}
      isLoading={query.isLoading}
      error={query.error}
      empty={filtered.length === 0}
      emptyMessage={
        auditDisabled
          ? (fixedObjectPath ? t("bindingJournal.objectDisabledEmpty") : t("bindingJournal.disabledEmpty"))
          : t("bindingJournal.empty")
      }
      compact={compact}
      scrollMaxHeight={scrollMaxHeight ?? (compact ? 280 : 400)}
      className="event-journal-panel binding-invoke-journal"
      exportFilenameBase={exportFilenameBase}
      exportRows={exportRows}
      filters={
        (mode === "history" || showFilters) ? (
          <div className="journal-filters form-grid">
            {showFilters && !fixedObjectPath && (
              <label>
                objectPath
                <Input
                  value={objectPath}
                  onChange={(e) => setObjectPath(e.target.value)}
                  placeholder="root.platform.devices…"
                />
              </label>
            )}
            {(showFilters || mode === "history") && (
              <>
                <label>
                  {t("bindingJournal.filter.kind")}
                  <Select
                    value={bindingKind}
                    onChange={setBindingKind}
                    options={[
                      { value: "", label: t("journal:filter.all") },
                      { value: "cel", label: "CEL" },
                      { value: "sql", label: "SQL" },
                    ]}
                  />
                </label>
                <label>
                  success
                  <Select
                    value={successFilter}
                    onChange={(value) => setSuccessFilter(value as "" | "true" | "false")}
                    options={[
                      { value: "", label: t("bindingJournal.filter.all") },
                      { value: "true", label: t("bindingJournal.filter.success") },
                      { value: "false", label: t("bindingJournal.filter.error") },
                    ]}
                  />
                </label>
                <label>
                  {t("bindingJournal.filter.changed")}
                  <Select
                    value={changedFilter}
                    onChange={(value) => setChangedFilter(value as "" | "true" | "false")}
                    options={[
                      { value: "", label: t("journal:filter.all") },
                      { value: "true", label: t("bindingJournal.filter.changedYes") },
                      { value: "false", label: t("bindingJournal.filter.changedNo") },
                    ]}
                  />
                </label>
              </>
            )}
            {showFilters && !fixedRuleId && (
              <label>
                ruleId
                <Input
                  value={ruleFilter}
                  onChange={(e) => setRuleFilter(e.target.value)}
                  placeholder="rule-id"
                />
              </label>
            )}
            {mode === "history" && (
              <label>
                {t("common:action.search")}
                <Input
                  value={searchFilter}
                  onChange={(e) => setSearchFilter(e.target.value)}
                  placeholder={t("journal:filter.searchPlaceholder")}
                />
              </label>
            )}
          </div>
        ) : undefined
      }
      footer={
        canLoadMore ? (
          <div className="journal-footer">
            <Button
              size="small"
              loading={query.isFetching}
              onClick={() => setHistoryLimit((n) => Math.min(n + HISTORY_PAGE, HISTORY_MAX))}
            >
              {t("journal:loadMore")}
            </Button>
          </div>
        ) : undefined
      }
    >
      <JournalVirtualList
        items={filtered}
        estimateSizePx={ITEM_ESTIMATE_PX}
        getTime={(entry) => entry.invokedAt}
        getKey={(entry) => entry.id}
        renderItem={(entry) => <BindingRow entry={entry} />}
      />
    </JournalViewShell>
  );
}

function BindingRow({
  entry,
}: {
  entry: BindingInvokeAuditEntry;
}) {
  const { t } = useTranslation(["runtime", "journal"]);
  const { formatDate } = useUserTimeZone();
  const label = entry.ruleName || entry.ruleId || entry.targetVariable || "binding";
  const status = !entry.success ? "FAIL" : entry.changed ? "CHANGED" : "OK";
  const diff = useMemo(() => parseAuditBeforeAfter(entry.detailJson), [entry.detailJson]);
  const sections = useMemo(
    () => [
      {
        id: "before",
        label: t("journal:details.before"),
        value: diff.before,
      },
      {
        id: "after",
        label: t("journal:details.after"),
        value: diff.after,
      },
    ],
    [diff.after, diff.before, t],
  );

  return (
    <JournalExpandableItem
      className={`event-journal-item ${entry.success ? (entry.changed ? "level-info" : "level-info") : "level-error"}`}
      sections={sections}
    >
      <div className="event-journal-row-top">
        <strong>{label}</strong>
        <span className="event-level-pill">{status}</span>
      </div>
      <p className="hint">
        {entry.bindingKind.toUpperCase()} · {entry.triggerKind}
        {entry.targetVariable ? ` → ${entry.targetVariable}` : ""}
      </p>
      <p className="hint">{entry.objectPath}</p>
      {entry.durationMs != null && (
        <p className="hint">{entry.durationMs} ms</p>
      )}
      {entry.errorMessage && <p className="event-journal-detail">{entry.errorMessage}</p>}
      <time className="hint event-journal-time">
        {formatDate(entry.invokedAt)}
      </time>
    </JournalExpandableItem>
  );
}
