import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { fetchEvents, invokeFunction } from "../api";
import { OBJECT_WS_EVENT, type ObjectWsMessage } from "./useObjectWebSocket";
import type { OperatorAlarmBarConfig } from "../types/operatorAlarmBar";
import type { OperatorUi } from "../types/operatorUi";
import type { OpenDashboardOptions } from "../components/dashboard/DashboardContext";
import {
  ALARM_MUTE_STORAGE_KEY,
  buildActiveAlarm,
  compareAlarmPriority,
  isAlarmBarEnabled,
  matchAlarmRule,
  resolveAlarmBarConfig,
} from "../utils/operatorAlarmBar";
import { playAlarmSound, playBeepFallback } from "../utils/alarmSound";

interface UseOperatorAlarmBarOptions {
  ui?: OperatorUi;
  navigateDashboard: (path: string, options?: OpenDashboardOptions) => void;
  navigateReport: (path: string) => void;
}

export function useOperatorAlarmBar(
  config: OperatorAlarmBarConfig | undefined,
  options: UseOperatorAlarmBarOptions
) {
  const resolved = useMemo(() => resolveAlarmBarConfig(config), [config]);
  const enabled = isAlarmBarEnabled(config);
  const [alarms, setAlarms] = useState<ReturnType<typeof buildActiveAlarm>[]>([]);
  const [muted, setMuted] = useState(() => localStorage.getItem(ALARM_MUTE_STORAGE_KEY) === "1");
  const seenIds = useRef(new Set<string>());
  const soundTimer = useRef<number | undefined>(undefined);

  const sortedAlarms = useMemo(
    () => [...alarms].sort(compareAlarmPriority),
    [alarms]
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
      if (muted || !alarm.soundEnabled || !alarm.soundUrl) {
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
    [muted, resolved.soundRepeatMs, stopSound]
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
    if (!enabled) {
      return;
    }
    const handler = async (raw: Event) => {
      const message = (raw as CustomEvent<ObjectWsMessage>).detail;
      if (message.type !== "EVENT_FIRED") {
        return;
      }
      try {
        const events = await fetchEvents(message.path, 5);
        const event =
          events.find(
            (item) =>
              item.eventName === message.variableName &&
              Math.abs(new Date(item.timestamp).getTime() - new Date(message.timestamp).getTime()) < 5000
          ) ?? events[0];
        if (!event || seenIds.current.has(event.id)) {
          return;
        }
        const rule = matchAlarmRule(event, resolved.rules, resolved.minLevel);
        if (!rule) {
          return;
        }
        seenIds.current.add(event.id);
        const active = buildActiveAlarm(event, rule, resolved, options.ui);
        setAlarms((current) => {
          const without = current.filter((item) => item.id !== active.id);
          return [...without, active];
        });
      } catch {
        // ignore fetch errors
      }
    };
    window.addEventListener(OBJECT_WS_EVENT, handler);
    return () => window.removeEventListener(OBJECT_WS_EVENT, handler);
  }, [enabled, options.ui, resolved]);

  const dismissAlarm = useCallback(
    async (alarmId: string) => {
      const alarm = alarms.find((item) => item.id === alarmId);
      if (alarm?.acknowledgeFunction) {
        try {
          await invokeFunction(alarm.event.objectPath, alarm.acknowledgeFunction);
        } catch {
          // still dismiss UI
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
      options.navigateDashboard(alarm.dashboardPath, { selection });
    },
    [options]
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
    muted,
    toggleMute,
    onDismiss: dismissAlarm,
    onOpenDashboard: openDashboardFor,
    onOpenReport: openReportFor,
    onOpenObject: openObjectFor,
    hasActiveAlarm: sortedAlarms.length > 0,
  };
}
