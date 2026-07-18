/** Per-operator-app watermark: hide live feed events at/before this time. History is untouched. */

const STORAGE_PREFIX = "ispf-operator-live-events-cleared:";
export const OPERATOR_LIVE_EVENTS_CLEARED_EVENT = "ispf-operator-live-events-cleared";

export function operatorLiveEventsClearedKey(appId: string): string {
  return `${STORAGE_PREFIX}${appId}`;
}

export function getLiveEventsClearedAtMs(appId: string): number | null {
  try {
    const raw = localStorage.getItem(operatorLiveEventsClearedKey(appId));
    if (!raw) {
      return null;
    }
    const value = Number(raw);
    return Number.isFinite(value) ? value : null;
  } catch {
    return null;
  }
}

/** Hide currently visible live events; newer fires still appear. Does not affect history. */
export function clearVisibleLiveEvents(appId: string, atMs: number = Date.now()): void {
  try {
    localStorage.setItem(operatorLiveEventsClearedKey(appId), String(atMs));
  } catch {
    // ignore quota / private mode
  }
  window.dispatchEvent(
    new CustomEvent(OPERATOR_LIVE_EVENTS_CLEARED_EVENT, {
      detail: { appId, atMs },
    }),
  );
}

export function filterEventsAfterLiveClear<T extends { timestamp: string }>(
  events: T[],
  clearedAtMs: number | null,
): T[] {
  if (clearedAtMs == null) {
    return events;
  }
  return events.filter((event) => {
    const ts = Date.parse(event.timestamp);
    return !Number.isFinite(ts) || ts > clearedAtMs;
  });
}
