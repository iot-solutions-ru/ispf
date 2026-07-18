import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { fetchEventJournalStatus, fetchEvents } from "../../api";
import {
  OPERATOR_SIDEBAR_EVENTS_QUERY_KEY,
  useOperatorSidebarRefresh,
} from "../../hooks/useOperatorSidebarRefresh";
import { useOperatorLiveEventsCleared } from "../../hooks/useOperatorLiveEventsCleared";
import type { ObjectEvent } from "../../types/event";
import type { OperatorUi } from "../../types/operatorUi";
import { filterOperatorSidebarEvents } from "../../utils/operator/operatorSidebarScope";
import { filterEventsAfterLiveClear } from "../../utils/operator/operatorLiveEventsCleared";
import { mapEventJournalExportRow } from "../../utils/journal/journalExport";
import JournalViewShell, { JOURNAL_VIEW_MODES, type JournalViewMode } from "../journal/JournalViewShell";
import JournalVirtualList from "../journal/JournalVirtualList";
import JournalExpandableItem from "../journal/JournalExpandableItem";
import { useUserTimeZone } from "../../context/UserTimeZoneContext";
import { usePersistentTab } from "../../hooks/usePersistentTab";
import { useSystemTabFocus } from "../../hooks/useSystemTabFocus";

const LIVE_LIMIT = 25;
const HISTORY_PAGE = 50;
const HISTORY_MAX = 200;
const EVENT_ITEM_ESTIMATE_PX = 120;

interface EventJournalPanelProps {
  appId?: string;
  ui?: OperatorUi;
  operatorApps?: OperatorUi[];
  objectPath?: string;
  limit?: number;
  showFilters?: boolean;
  objectPathFilter?: string;
  knownEventNames?: string[];
  compact?: boolean;
  scrollMaxHeight?: number | string;
  defaultMode?: JournalViewMode;
  showModeToggle?: boolean;
  /** When true (System console), publish Admin Copilot focus for this journal. */
  publishAdminFocus?: boolean;
}

export default function EventJournalPanel({
  appId,
  ui,
  operatorApps = [],
  objectPath: fixedObjectPath,
  limit,
  showFilters = false,
  objectPathFilter: initialFilter = "",
  knownEventNames,
  compact = false,
  scrollMaxHeight,
  defaultMode = "live",
  showModeToggle = true,
  publishAdminFocus = false,
}: EventJournalPanelProps) {
  const { t } = useTranslation(["operator", "runtime", "journal", "common"]);
  const operatorScoped = Boolean(appId && ui);
  const operatorJournalPath = ui?.eventJournalObjectPath?.trim() || undefined;
  const [mode, setMode] = usePersistentTab<JournalViewMode>(
    `event-journal:${appId ?? fixedObjectPath ?? "all"}`,
    defaultMode,
    JOURNAL_VIEW_MODES
  );
  const [historyLimit, setHistoryLimit] = useState(HISTORY_PAGE);
  const [filterPath, setFilterPath] = useState(initialFilter);
  const [eventNameFilter, setEventNameFilter] = useState("");
  const [levelFilter, setLevelFilter] = useState("");
  const [searchFilter, setSearchFilter] = useState("");

  const objectPath = fixedObjectPath ?? (filterPath.trim() || undefined);
  const statusObjectPath = operatorScoped
    ? operatorJournalPath
    : fixedObjectPath ?? (filterPath.trim() || undefined);
  const liveLimit = limit ?? LIVE_LIMIT;
  const fetchLimit = mode === "live" ? liveLimit : historyLimit;

  useOperatorSidebarRefresh(appId, operatorScoped ? ui : undefined);
  const { clearedAtMs, clearVisible } = useOperatorLiveEventsCleared(appId);

  const statusQuery = useQuery({
    queryKey: ["event-journal-status", statusObjectPath ?? "all"],
    queryFn: () => fetchEventJournalStatus(statusObjectPath),
    staleTime: 30_000,
  });

  const events = useQuery({
    queryKey: [
      "events",
      operatorScoped ? OPERATOR_SIDEBAR_EVENTS_QUERY_KEY : objectPath ?? "all",
      operatorScoped ? operatorJournalPath ?? "scoped" : null,
      fetchLimit,
      mode,
    ],
    queryFn: () => {
      if (operatorScoped) {
        return fetchEvents(
          operatorJournalPath,
          Math.max(fetchLimit, mode === "live" ? 80 : fetchLimit),
        );
      }
      return fetchEvents(objectPath, fetchLimit);
    },
    // Always poll/fetch: RecentEventCache still serves live events when durable journal is off.
    // Gating on journal-status.enabled froze the list after the first paint.
    refetchInterval: mode === "live" ? (operatorScoped ? 5000 : 8000) : false,
    staleTime: mode === "live" ? 0 : 30_000,
  });

  const rawItems = useMemo(() => {
    let rows: ObjectEvent[];
    if (!operatorScoped || !ui || !appId) {
      rows = events.data ?? [];
    } else {
      rows = filterOperatorSidebarEvents(events.data ?? [], {
        appId,
        ui,
        operatorApps,
      });
    }
    // Clear only affects the live feed; history keeps the full record.
    if (mode === "live" && appId) {
      return filterEventsAfterLiveClear(rows, clearedAtMs);
    }
    return rows;
  }, [appId, clearedAtMs, events.data, mode, operatorApps, operatorScoped, ui]);

  const eventNames = useMemo(() => {
    const set = new Set<string>(knownEventNames ?? []);
    for (const event of rawItems) {
      set.add(event.eventName);
    }
    return Array.from(set).sort();
  }, [knownEventNames, rawItems]);

  const levels = useMemo(() => {
    const set = new Set<string>();
    for (const event of rawItems) {
      set.add(event.level);
    }
    return Array.from(set).sort();
  }, [rawItems]);

  const filtered = useMemo(() => {
    let rows = rawItems;
    if (mode === "history") {
      if (eventNameFilter) {
        rows = rows.filter((event) => event.eventName === eventNameFilter);
      }
      if (levelFilter) {
        rows = rows.filter((event) => event.level === levelFilter);
      }
      const query = searchFilter.trim().toLowerCase();
      if (query) {
        rows = rows.filter(
          (event) =>
            event.objectPath.toLowerCase().includes(query)
            || event.eventName.toLowerCase().includes(query),
        );
      }
    }
    return rows;
  }, [eventNameFilter, levelFilter, mode, rawItems, searchFilter]);

  const exportRows = useMemo(
    () => filtered.map(mapEventJournalExportRow),
    [filtered],
  );

  const exportFilenameBase = operatorScoped && appId
    ? `event-journal-${appId}`
    : objectPath
      ? `event-journal-${objectPath.replace(/\./g, "-")}`
      : "event-journal";

  const focusDetail = useMemo(
    () => ({
      journalMode: mode,
      filters: {
        objectPath: filterPath || objectPath || "",
        eventName: eventNameFilter,
        level: levelFilter,
        search: searchFilter,
      },
      resultCount: filtered.length,
      sampleEventNames: eventNames.slice(0, 20),
      sampleLevels: levels.slice(0, 10),
      sampleEvents: filtered.slice(0, 6).map((event) => ({
        eventName: event.eventName,
        level: event.level,
        objectPath: event.objectPath,
      })),
      auditEnabled: statusQuery.data?.enabled !== false,
      screenHint:
        "Event journal — help diagnose fires, filter by path/event, suggest bindings or alerts from samples",
      helpIntents: ["explainEvents", "suggestFilter", "draftAlertCondition", "draftBindingExpression"],
    }),
    [
      mode,
      filterPath,
      objectPath,
      eventNameFilter,
      levelFilter,
      searchFilter,
      filtered,
      eventNames,
      levels,
      statusQuery.data?.enabled,
    ]
  );
  useSystemTabFocus("system-event-journal", "events", focusDetail, {
    active: publishAdminFocus,
    screenTitle: "System › Event journal",
  });

  const canLoadMore =
    mode === "history"
    && !operatorScoped
    && (events.data?.length ?? 0) >= historyLimit
    && historyLimit < HISTORY_MAX;

  const journalDisabled = statusQuery.data?.enabled === false;
  const journalHint = !statusQuery.data?.masterEnabled
    ? mode === "live"
      ? t("runtime:eventJournal.masterDisabledLiveHint")
      : t("runtime:eventJournal.masterDisabledHint")
    : fixedObjectPath && !statusQuery.data?.objectEnabled
      ? t("runtime:eventJournal.objectDisabledHint")
      : mode === "history"
        && !objectPath
        && !operatorScoped
        && statusQuery.data?.masterEnabled
        && !statusQuery.data?.globalTableEnabled
        ? t("runtime:eventJournal.globalHistoryDisabledHint")
        : journalDisabled
          ? t("runtime:eventJournal.disabledHint")
          : operatorScoped && appId
            ? t("eventJournal.operatorApp", { appId })
            : !operatorScoped && objectPath
              ? t("eventJournal.filter", { path: objectPath })
              : undefined;

  const emptyMessage = operatorScoped
    ? t("eventJournal.emptyScoped")
    : t("eventJournal.empty");

  const canClearLive =
    Boolean(appId)
    && mode === "live"
    && filtered.length > 0
    && !events.isLoading
    && !events.error;

  return (
    <JournalViewShell
      title={t("eventJournal.title")}
      subtitle={journalHint}
      mode={mode}
      onModeChange={setMode}
      showModeToggle={showModeToggle}
      count={filtered.length}
      isLoading={events.isLoading || statusQuery.isLoading}
      error={events.error ?? statusQuery.error}
      empty={filtered.length === 0}
      emptyMessage={emptyMessage}
      emptyHint={
        filtered.length === 0 && mode === "live" && showModeToggle
          ? t("eventJournal.emptyLiveHint")
          : undefined
      }
      emptyAction={
        filtered.length === 0 && mode === "live" && showModeToggle ? (
          <button type="button" className="btn" onClick={() => setMode("history")}>
            {t("eventJournal.viewHistory")}
          </button>
        ) : undefined
      }
      compact={compact}
      scrollMaxHeight={scrollMaxHeight ?? (compact ? 280 : 400)}
      className="event-journal-panel"
      exportFilenameBase={exportFilenameBase}
      exportRows={exportRows}
      actions={
        canClearLive ? (
          <button
            type="button"
            className="btn small"
            title={t("eventJournal.clearHint")}
            onClick={clearVisible}
          >
            {t("eventJournal.clear")}
          </button>
        ) : undefined
      }
      filters={
        (mode === "history" || (showFilters && !fixedObjectPath)) ? (
          <div className="journal-filters form-grid">
            {showFilters && !fixedObjectPath && (
              <label>
                objectPath
                <input
                  value={filterPath}
                  onChange={(e) => setFilterPath(e.target.value)}
                  placeholder={t("eventJournal.filterPlaceholder")}
                />
              </label>
            )}
            {mode === "history" && (
              <>
                <label>
                  {t("journal:filter.eventName")}
                  <select value={eventNameFilter} onChange={(e) => setEventNameFilter(e.target.value)}>
                    <option value="">{t("journal:filter.all")}</option>
                    {eventNames.map((name) => (
                      <option key={name} value={name}>
                        {name}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  {t("journal:filter.level")}
                  <select value={levelFilter} onChange={(e) => setLevelFilter(e.target.value)}>
                    <option value="">{t("journal:filter.all")}</option>
                    {levels.map((level) => (
                      <option key={level} value={level}>
                        {level}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  {t("common:action.search")}
                  <input
                    value={searchFilter}
                    onChange={(e) => setSearchFilter(e.target.value)}
                    placeholder={t("journal:filter.searchPlaceholder")}
                  />
                </label>
              </>
            )}
          </div>
        ) : undefined
      }
      footer={
        canLoadMore ? (
          <div className="journal-footer">
            <button
              type="button"
              className="btn small"
              disabled={events.isFetching}
              onClick={() => setHistoryLimit((n) => Math.min(n + HISTORY_PAGE, HISTORY_MAX))}
            >
              {t("journal:loadMore")}
            </button>
          </div>
        ) : undefined
      }
    >
      <JournalVirtualList
        items={filtered}
        estimateSizePx={EVENT_ITEM_ESTIMATE_PX}
        getTime={(event) => event.timestamp}
        getKey={(event) => event.id}
        renderItem={(event) => <EventRow event={event} />}
      />
    </JournalViewShell>
  );
}

function EventRow({
  event,
}: {
  event: ObjectEvent;
}) {
  const { t } = useTranslation(["operator", "journal"]);
  const { formatDate } = useUserTimeZone();
  const payload = event.payload?.rows?.[0];
  const detail = (() => {
    if (!payload) {
      return null;
    }
    const message = typeof payload.message === "string" ? payload.message.trim() : "";
    if (message) {
      return message;
    }
    if (payload.value == null) {
      return null;
    }
    return `${payload.value}${payload.unit ? ` ${payload.unit}` : ""}`;
  })();
  const sections = useMemo(
    () => [
      {
        id: "payload",
        label: t("journal:details.payload"),
        value: event.payload,
      },
    ],
    [event.payload, t],
  );

  return (
    <JournalExpandableItem
      className={`event-journal-item level-${event.level.toLowerCase()}`}
      sections={sections}
    >
      <div className="event-journal-row-top">
        <strong>{event.eventName}</strong>
        <span className="event-level-pill">{event.level}</span>
      </div>
      <p className="hint">{event.objectPath}</p>
      {detail && <p className="event-journal-detail">{detail}</p>}
      <time className="hint event-journal-time" dateTime={event.timestamp}>
        {formatDate(event.timestamp)}
      </time>
    </JournalExpandableItem>
  );
}
