import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  fetchAlertRules,
  fetchAlarmShelves,
  fetchEvents,
  invokeFunction,
  isAlarmShelfPendingRequest,
  shelveAlarm,
  unshelveAlarm,
} from "../api";
import { OBJECT_WS_EVENT, useObjectPathsSubscription, type ObjectWsMessage } from "./useObjectWebSocket";
import type { EventLevel, ObjectEvent } from "../types/event";
import type { OperatorAlarmBarConfig, OperatorAlarmRule } from "../types/operatorAlarmBar";
import type { OperatorUi } from "../types/operatorUi";
import type { OpenDashboardOptions } from "../components/dashboard/DashboardContext";
import {
  ALARM_MUTE_STORAGE_KEY,
  buildActiveAlarm,
  compareAlarmPriority,
  isAlarmShelved,
  isAlarmBarEnabled,
  matchAlarmRule,
  resolveAlarmBarConfig,
  resolveAlarmNavigateParams,
  shouldResurfaceAlarmFromFeed,
} from "../utils/operatorAlarmBar";
import { playAlarmSound, playBeepFallback } from "../utils/alarmSound";
import {
  isOperatorAlarmSoundEnabled,
  OPERATOR_PREFERENCES_CHANGED_EVENT,
  showOperatorAlarmNotification,
} from "../utils/operatorPreferences";
import { collectOperatorAppWatchPaths } from "../utils/operatorSidebarScope";

interface UseOperatorAlarmBarOptions {
  ui?: OperatorUi;
  navigateDashboard: (path: string, options?: OpenDashboardOptions) => void;
  navigateReport: (path: string) => void;
}

async function enrichEventFromFunction(
  event: ObjectEvent,
  rule: OperatorAlarmRule
): Promise<ObjectEvent> {
  const cfg = rule.actions?.enrichFromFunction;
  if (!cfg) {
    return event;
  }
  try {
    const result = await invokeFunction(cfg.objectPath, cfg.functionName);
    const row = result.rows?.[0];
    if (!row) {
      return event;
    }
    const mergedRow: Record<string, unknown> = { ...(event.payload?.rows?.[0] ?? {}) };
    for (const [paramKey, column] of Object.entries(cfg.rowFields)) {
      const value = row[column];
      if (value !== undefined && value !== null) {
        mergedRow[paramKey] = value;
      }
    }
    return {
      ...event,
      payload: {
        schema: event.payload?.schema ?? {},
        rows: [mergedRow],
      },
    };
  } catch {
    return event;
  }
}

function pickEventFromMessage(
  events: ObjectEvent[],
  message: ObjectWsMessage
): ObjectEvent | undefined {
  return (
    events.find(
      (item) =>
        item.eventName === message.variableName &&
        Math.abs(new Date(item.timestamp).getTime() - new Date(message.timestamp).getTime()) < 5000
    ) ?? events[0]
  );
}

const ALARM_FEED_POLL_MS = 5000;
const ALARM_FEED_LIMIT = 80;

function pickLatestMatchingEvents(
  events: ObjectEvent[],
  rules: OperatorAlarmRule[],
  minLevel: EventLevel
): ObjectEvent[] {
  const latestByKey = new Map<string, ObjectEvent>();
  for (const event of events) {
    if (!matchAlarmRule(event, rules, minLevel)) {
      continue;
    }
    const key = `${event.eventName}\0${event.objectPath}`;
    const existing = latestByKey.get(key);
    if (
      !existing
      || new Date(event.timestamp).getTime() > new Date(existing.timestamp).getTime()
    ) {
      latestByKey.set(key, event);
    }
  }
  return [...latestByKey.values()];
}

export function useOperatorAlarmBar(
  config: OperatorAlarmBarConfig | undefined,
  options: UseOperatorAlarmBarOptions
) {
  const resolved = useMemo(() => resolveAlarmBarConfig(config), [config]);
  const enabled = isAlarmBarEnabled(config);
  const watchPaths = useMemo(() => {
    if (!enabled || !options.ui) {
      return [];
    }
    return collectOperatorAppWatchPaths(options.ui, options.ui.appId);
  }, [enabled, options.ui]);
  useObjectPathsSubscription(watchPaths);
  const [alarms, setAlarms] = useState<ReturnType<typeof buildActiveAlarm>[]>([]);
  const [shelves, setShelves] = useState<import("../api").AlarmShelf[]>([]);
  const [alertRules, setAlertRules] = useState<import("../types/event").AlertRule[]>([]);
  const [muted, setMuted] = useState(() => localStorage.getItem(ALARM_MUTE_STORAGE_KEY) === "1");
  const [userSoundEnabled, setUserSoundEnabled] = useState(isOperatorAlarmSoundEnabled);
  const [actionError, setActionError] = useState<string | null>(null);
  const seenIds = useRef(new Set<string>());
  const shelvesRef = useRef(shelves);
  const soundTimer = useRef<number | undefined>(undefined);
  shelvesRef.current = shelves;

  const sortedAlarms = useMemo(
    () => [...alarms].sort((left, right) => compareAlarmPriority(left, right, alertRules)),
    [alarms, alertRules]
  );

  const activeAlarm = sortedAlarms[0] ?? null;

  const stopSound = useCallback(() => {
    if (soundTimer.current !== undefined) {
      window.clearInterval(soundTimer.current);
      soundTimer.current = undefined;
    }
  }, []);

  const playSoundForAlarm = useCallback(
    (alarm: NonNullable<typeof activeAlarm>) => {
      if (muted || !userSoundEnabled || !alarm.soundEnabled || !alarm.soundUrl) {
        return;
      }
      stopSound();
      const play = () => {
        if (alarm.soundUrl) {
          void playAlarmSound(alarm.soundUrl);
        } else {
          playBeepFallback();
        }
      };
      play();
      soundTimer.current = window.setInterval(play, resolved.soundRepeatMs);
    },
    [muted, resolved.soundRepeatMs, stopSound, userSoundEnabled]
  );

  useEffect(() => {
    if (!activeAlarm) {
      stopSound();
      return;
    }
    playSoundForAlarm(activeAlarm);
    return stopSound;
  }, [activeAlarm, playSoundForAlarm, stopSound]);

  useEffect(() => {
    const syncPreferences = () => setUserSoundEnabled(isOperatorAlarmSoundEnabled());
    syncPreferences();
    window.addEventListener(OPERATOR_PREFERENCES_CHANGED_EVENT, syncPreferences);
    return () => window.removeEventListener(OPERATOR_PREFERENCES_CHANGED_EVENT, syncPreferences);
  }, []);

  const refreshShelves = useCallback(() => {
    void fetchAlarmShelves().then(setShelves).catch(() => setShelves([]));
  }, []);

  useEffect(() => {
    if (!enabled) {
      return;
    }
    refreshShelves();
    void fetchAlertRules().then(setAlertRules).catch(() => setAlertRules([]));
    const timer = window.setInterval(refreshShelves, 60_000);
    return () => window.clearInterval(timer);
  }, [enabled, refreshShelves]);

  const activateEvent = useCallback(
    async (
      event: ObjectEvent,
      opts?: { fromFeed?: boolean; alertRulesOverride?: import("../types/event").AlertRule[] },
    ) => {
      if (seenIds.current.has(event.id) || isAlarmShelved(event, shelvesRef.current)) {
        return;
      }
      const rule = matchAlarmRule(event, resolved.rules, resolved.minLevel);
      if (!rule) {
        return;
      }
      const rulesForCheck = opts?.alertRulesOverride ?? alertRules;
      if (opts?.fromFeed && !shouldResurfaceAlarmFromFeed(event, rule, rulesForCheck)) {
        return;
      }
      seenIds.current.add(event.id);
      const enrichedEvent = await enrichEventFromFunction(event, rule);
      const active = buildActiveAlarm(enrichedEvent, rule, resolved, options.ui, rulesForCheck);
      if (rule.actions?.enrichFromFunction) {
        active.navigateParams = resolveAlarmNavigateParams(enrichedEvent, rule);
      }
      const summary = active.fieldRows.map((row) => `${row.label}: ${row.value}`).join(" · ");
      showOperatorAlarmNotification(active.title, summary, active.id);
      setAlarms((current) => {
        const without = current.filter((item) => item.id !== active.id);
        return [...without, active];
      });
    },
    [alertRules, options.ui, resolved]
  );

  const syncFromEventFeed = useCallback(async () => {
    try {
      const [rules, events] = await Promise.all([
        fetchAlertRules(),
        fetchEvents(undefined, ALARM_FEED_LIMIT),
      ]);
      setAlertRules(rules);
      const latest = pickLatestMatchingEvents(events, resolved.rules, resolved.minLevel);

      // Drop bar entries whose platform condition is no longer true (unless persistUntilDismiss).
      setAlarms((current) => {
        const kept = current.filter((alarm) => {
          if (alarm.rule.persistUntilDismiss) {
            return true;
          }
          return shouldResurfaceAlarmFromFeed(alarm.event, alarm.rule, rules);
        });
        for (const alarm of current) {
          if (!kept.some((item) => item.id === alarm.id)) {
            seenIds.current.delete(alarm.id);
          }
        }
        return kept;
      });

      for (const event of latest) {
        await activateEvent(event, { fromFeed: true, alertRulesOverride: rules });
      }
    } catch {
      // ignore feed errors
    }
  }, [activateEvent, resolved.minLevel, resolved.rules]);

  useEffect(() => {
    if (!enabled) {
      return;
    }
    void syncFromEventFeed();
    const timer = window.setInterval(() => void syncFromEventFeed(), ALARM_FEED_POLL_MS);
    return () => window.clearInterval(timer);
  }, [enabled, syncFromEventFeed]);

  useEffect(() => {
    if (!enabled) {
      return;
    }
    const handler = (raw: Event) => {
      const message = (raw as CustomEvent<ObjectWsMessage>).detail;
      if (message.type !== "EVENT_FIRED") {
        return;
      }
      void (async () => {
        try {
          const events = await fetchEvents(message.path, 5);
          const event = pickEventFromMessage(events, message);
          if (!event) {
            return;
          }
          await activateEvent(event);
        } catch {
          // ignore fetch errors
        }
      })();
    };
    window.addEventListener(OBJECT_WS_EVENT, handler);
    return () => window.removeEventListener(OBJECT_WS_EVENT, handler);
  }, [activateEvent, enabled]);

  const unshelveShelf = useCallback(
    async (shelfId: string) => {
      try {
        await unshelveAlarm(shelfId);
        setShelves((current) => current.filter((item) => item.id !== shelfId));
        setActionError(null);
      } catch (error) {
        setActionError(error instanceof Error ? error.message : "Failed to unshelve alarm");
        refreshShelves();
      }
    },
    [refreshShelves]
  );

  const shelveAlarmFor = useCallback(
    async (alarmId: string, durationMinutes?: number, comment?: string) => {
      const alarm = alarms.find((item) => item.id === alarmId);
      if (!alarm) {
        return;
      }
      try {
        const result = await shelveAlarm({
          objectPath: alarm.event.objectPath,
          eventName: alarm.event.eventName,
          durationMinutes,
          comment,
        });
        if (isAlarmShelfPendingRequest(result)) {
          return;
        }
        setShelves((current) => [...current.filter((item) => item.id !== result.id), result]);
        setAlarms((current) => current.filter((item) => item.id !== alarmId));
        setActionError(null);
      } catch (error) {
        setActionError(error instanceof Error ? error.message : "Failed to shelve alarm");
      }
    },
    [alarms]
  );

  const dismissAlarm = useCallback(
    async (alarmId: string) => {
      const alarm = alarms.find((item) => item.id === alarmId);
      if (!alarm) {
        return;
      }
      if (alarm.ackRequired) {
        if (!alarm.acknowledgeFunction) {
          return;
        }
        try {
          await invokeFunction(alarm.event.objectPath, alarm.acknowledgeFunction);
        } catch {
          return;
        }
      } else if (alarm.acknowledgeFunction) {
        try {
          await invokeFunction(alarm.event.objectPath, alarm.acknowledgeFunction);
        } catch {
          // optional ack when not required — still dismiss UI
        }
      }
      setAlarms((current) => current.filter((item) => item.id !== alarmId));
    },
    [alarms]
  );

  const toggleMute = useCallback(() => {
    setMuted((current) => {
      const next = !current;
      localStorage.setItem(ALARM_MUTE_STORAGE_KEY, next ? "1" : "0");
      return next;
    });
  }, []);

  const openDashboardFor = useCallback(
    (alarm: ReturnType<typeof buildActiveAlarm>) => {
      if (!alarm.dashboardPath) {
        return;
      }
      const selection: Record<string, string> = {};
      if (alarm.selectionKey) {
        selection[alarm.selectionKey] = alarm.event.objectPath;
      }
      options.navigateDashboard(alarm.dashboardPath, {
        selection,
        params: alarm.navigateParams,
      });
    },
    [options]
  );

  const primaryActionFor = useCallback(
    (alarm: ReturnType<typeof buildActiveAlarm>) => {
      if (!alarm.dashboardPath) {
        return;
      }
      openDashboardFor(alarm);
      setAlarms((current) => current.filter((item) => item.id !== alarm.id));
    },
    [openDashboardFor]
  );

  const openReportFor = useCallback(
    (alarm: ReturnType<typeof buildActiveAlarm>) => {
      if (!alarm.reportPath) {
        return;
      }
      options.navigateReport(alarm.reportPath);
    },
    [options]
  );

  const openObjectFor = useCallback(
    (alarm: ReturnType<typeof buildActiveAlarm>) => {
      if (alarm.dashboardPath) {
        openDashboardFor(alarm);
        return;
      }
      const fallback = options.ui?.dashboards[0]?.path;
      if (fallback) {
        options.navigateDashboard(fallback, {
          selection: { [alarm.selectionKey ?? "objectPath"]: alarm.event.objectPath },
        });
      }
    },
    [openDashboardFor, options]
  );

  return {
    enabled,
    position: resolved.position,
    alarms: sortedAlarms,
    shelves,
    muted,
    toggleMute,
    onDismiss: dismissAlarm,
    onShelve: shelveAlarmFor,
    onUnshelveShelf: unshelveShelf,
    onOpenDashboard: openDashboardFor,
    onOpenReport: openReportFor,
    onOpenObject: openObjectFor,
    onPrimaryAction: primaryActionFor,
    hasActiveAlarm: sortedAlarms.length > 0,
    actionError,
    clearActionError: () => setActionError(null),
  };
}
