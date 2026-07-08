export const EVENT_FILTERS_ROOT = "root.platform.event-filters";

export function isEventFiltersRoot(path: string): boolean {
  return path === EVENT_FILTERS_ROOT;
}

export function isEventFilterPath(path: string): boolean {
  return path.startsWith(`${EVENT_FILTERS_ROOT}.`);
}
