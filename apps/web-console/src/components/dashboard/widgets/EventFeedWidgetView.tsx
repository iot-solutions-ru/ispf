import { useMemo, useRef } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import { useVirtualizer } from "@tanstack/react-virtual";
import { fetchEvents } from "../../../api";
import type { EventFeedWidget } from "../../../types/dashboard";
import { matchesPayloadFilter } from "../../../utils/payloadFilter";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";
import { parseDemoPreview } from "../widgetDemoPreview";

const EVENT_ITEM_ESTIMATE_PX = 88;

interface DemoFeedEvent {
  id: string;
  eventName: string;
  level: string;
  objectPath: string;
  timestamp: string;
  payload?: { rows?: Array<Record<string, unknown>> };
}

interface EventFeedWidgetViewProps {
  widget: EventFeedWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function EventFeedWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: EventFeedWidgetViewProps) {
  const { t } = useTranslation(["widgets", "common"]);
  const styles = useWidgetStyles(widget.stylesJson);
  const listRef = useRef<HTMLUListElement>(null);
  const eventNames = useMemo(() => {
    try {
      return widget.eventNamesJson ? (JSON.parse(widget.eventNamesJson) as string[]) : [];
    } catch {
      return [] as string[];
    }
  }, [widget.eventNamesJson]);

  const events = useQuery({
    queryKey: ["events", "feed", widget.objectPathPrefix, widget.maxItems],
    queryFn: () => fetchEvents(undefined, widget.maxItems ?? 30),
    refetchInterval: refreshIntervalMs,
  });

  const filtered = (events.data ?? []).filter((event) => {
    if (widget.objectPathPrefix && !event.objectPath.startsWith(widget.objectPathPrefix)) {
      return false;
    }
    if (eventNames.length > 0 && !eventNames.includes(event.eventName)) {
      return false;
    }
    const payloadRow = event.payload?.rows?.[0];
    if (!matchesPayloadFilter(payloadRow, widget.payloadFilterExpr)) {
      return false;
    }
    return true;
  });

  const demoEvents =
    editable && filtered.length === 0 && !events.isLoading
      ? parseDemoPreview<DemoFeedEvent[]>(widget.demoPreviewJson) ?? []
      : [];
  const isDemo = demoEvents.length > 0;
  const displayEvents = isDemo ? demoEvents : filtered;

  const virtualizer = useVirtualizer({
    count: displayEvents.length,
    getScrollElement: () => listRef.current,
    estimateSize: () => EVENT_ITEM_ESTIMATE_PX,
    overscan: 5,
    enabled: displayEvents.length > 0,
  });

  const virtualItems = virtualizer.getVirtualItems();

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-event-feed"
      editable={editable}
      demo={isDemo}
    >
      {events.isLoading && !isDemo && <p className="hint">{t("common:action.loading")}</p>}
      {displayEvents.length === 0 && !events.isLoading && (
        <p className="hint">{t("view.noEvents")}</p>
      )}
      <ul
        ref={listRef}
        className="dash-event-feed-list dash-virtual-list"
        style={styles.body}
      >
        {displayEvents.length > 0 && (
          <li
            aria-hidden="true"
            className="dash-virtual-list-spacer"
            style={{ height: virtualizer.getTotalSize() }}
          />
        )}
        {virtualItems.map((virtualItem) => {
          const event = displayEvents[virtualItem.index];
          const payload = event.payload?.rows?.[0];
          const detail = payload
            ? Object.entries(payload)
                .map(([k, v]) => `${k}=${v}`)
                .join(", ")
            : "";
          return (
            <li
              key={event.id}
              className={`dash-event-item level-${event.level.toLowerCase()} dash-virtual-list-item`}
              style={{ transform: `translateY(${virtualItem.start}px)` }}
            >
              <div className="dash-event-row-top">
                <strong>{event.eventName}</strong>
                <time className="hint">
                  {new Date(event.timestamp).toLocaleTimeString()}
                </time>
              </div>
              <p className="hint">{event.objectPath}</p>
              {detail && <p className="dash-event-detail">{detail}</p>}
            </li>
          );
        })}
      </ul>
    </DashWidgetShell>
  );
}
