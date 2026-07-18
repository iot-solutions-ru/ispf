import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchEvents, fetchWorkQueue } from "../api";
import { useOperatorAppsRegistry } from "./useOperatorAppsRegistry";
import {
  OPERATOR_SIDEBAR_EVENTS_QUERY_KEY,
  useOperatorSidebarRefresh,
} from "./useOperatorSidebarRefresh";
import { useOperatorLiveEventsCleared } from "./useOperatorLiveEventsCleared";
import { workQueueQueryKey } from "./workQueueCache";
import type { OperatorUi } from "../types/operatorUi";
import {
  filterOperatorSidebarEvents,
  filterOperatorSidebarTasks,
} from "../utils/operator/operatorSidebarScope";
import { filterEventsAfterLiveClear } from "../utils/operator/operatorLiveEventsCleared";

const LIVE_EVENT_LIMIT = 80;

/** Live task/event counts for the collapsed sidebar toggle badges. */
export function useOperatorSidebarCounts(appId?: string, ui?: OperatorUi) {
  const resolvedAppId = appId ?? ui?.appId;
  const { operatorApps } = useOperatorAppsRegistry(ui);
  useOperatorSidebarRefresh(resolvedAppId, ui);
  const { clearedAtMs } = useOperatorLiveEventsCleared(resolvedAppId);

  const operatorJournalPath = ui?.eventJournalObjectPath?.trim() || undefined;

  const queue = useQuery({
    queryKey: workQueueQueryKey(resolvedAppId),
    queryFn: () => fetchWorkQueue(50, resolvedAppId),
    refetchInterval: 60_000,
    refetchOnWindowFocus: true,
    staleTime: 15_000,
  });

  const events = useQuery({
    queryKey: [
      "events",
      OPERATOR_SIDEBAR_EVENTS_QUERY_KEY,
      operatorJournalPath ?? "scoped",
      LIVE_EVENT_LIMIT,
      "live",
    ],
    queryFn: () => fetchEvents(operatorJournalPath, LIVE_EVENT_LIMIT),
    // Match EventJournalPanel: always poll RecentEventCache even when durable journal is off.
    enabled: Boolean(resolvedAppId || ui),
    refetchInterval: 5000,
    staleTime: 0,
  });

  const taskCount = useMemo(() => {
    const open = (queue.data ?? []).filter((task) => task.status !== "COMPLETED");
    if (!resolvedAppId || !ui) {
      return open.length;
    }
    return filterOperatorSidebarTasks(open, {
      appId: resolvedAppId,
      ui,
      operatorApps,
    }).length;
  }, [operatorApps, queue.data, resolvedAppId, ui]);

  const eventCount = useMemo(() => {
    let rows = events.data ?? [];
    if (resolvedAppId && ui) {
      rows = filterOperatorSidebarEvents(rows, {
        appId: resolvedAppId,
        ui,
        operatorApps,
      });
    }
    // Badge matches live feed after operator clear (history is separate).
    return filterEventsAfterLiveClear(rows, clearedAtMs).length;
  }, [clearedAtMs, events.data, operatorApps, resolvedAppId, ui]);

  return { taskCount, eventCount };
}
