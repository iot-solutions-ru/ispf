export const TIMEZONE_STORAGE_KEY = "ispf.ui.timeZone";

/** Common IANA zones for operator pickers (not exhaustive). */
export const COMMON_TIME_ZONES: string[] = [
  "UTC",
  "Europe/Moscow",
  "Europe/Kaliningrad",
  "Europe/Samara",
  "Asia/Yekaterinburg",
  "Asia/Omsk",
  "Asia/Krasnoyarsk",
  "Asia/Irkutsk",
  "Asia/Yakutsk",
  "Asia/Vladivostok",
  "Asia/Magadan",
  "Asia/Kamchatka",
  "Europe/Berlin",
  "Europe/London",
  "Asia/Shanghai",
  "America/New_York",
  "America/Los_Angeles",
];

export function detectBrowserTimeZone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";
  } catch {
    return "UTC";
  }
}

export function readStoredTimeZone(): string | null {
  try {
    const value = localStorage.getItem(TIMEZONE_STORAGE_KEY);
    return value?.trim() ? value.trim() : null;
  } catch {
    return null;
  }
}

export function persistTimeZone(timeZone: string): void {
  try {
    localStorage.setItem(TIMEZONE_STORAGE_KEY, timeZone);
  } catch {
    // ignore quota errors
  }
}

export function timeZoneLabel(timeZone: string): string {
  return timeZone;
}

export function normalizeTimeZoneList(current: string | undefined): string[] {
  const zones = [...COMMON_TIME_ZONES];
  if (current && !zones.includes(current)) {
    zones.unshift(current);
  }
  return zones;
}
