import { useCallback, useSyncExternalStore } from "react";
import {
  OPERATOR_LIVE_EVENTS_CLEARED_EVENT,
  clearVisibleLiveEvents,
  getLiveEventsClearedAtMs,
  operatorLiveEventsClearedKey,
} from "../utils/operatorLiveEventsCleared";

export function useOperatorLiveEventsCleared(appId?: string) {
  const subscribe = useCallback(
    (onStoreChange: () => void) => {
      if (!appId) {
        return () => {};
      }
      const onCustom = (event: Event) => {
        const detail = (event as CustomEvent<{ appId?: string }>).detail;
        if (detail?.appId === appId) {
          onStoreChange();
        }
      };
      const onStorage = (event: StorageEvent) => {
        if (event.key === operatorLiveEventsClearedKey(appId)) {
          onStoreChange();
        }
      };
      window.addEventListener(OPERATOR_LIVE_EVENTS_CLEARED_EVENT, onCustom);
      window.addEventListener("storage", onStorage);
      return () => {
        window.removeEventListener(OPERATOR_LIVE_EVENTS_CLEARED_EVENT, onCustom);
        window.removeEventListener("storage", onStorage);
      };
    },
    [appId],
  );

  const getSnapshot = useCallback(
    () => (appId ? getLiveEventsClearedAtMs(appId) : null),
    [appId],
  );

  const clearedAtMs = useSyncExternalStore(subscribe, getSnapshot, () => null);

  const clearVisible = useCallback(() => {
    if (!appId) {
      return;
    }
    clearVisibleLiveEvents(appId);
  }, [appId]);

  return { clearedAtMs, clearVisible };
}
