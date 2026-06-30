export interface FormatDateTimeOptions {
  timeZone?: string;
  locale?: string;
  dateStyle?: "short" | "medium" | "long" | "full";
  timeStyle?: "short" | "medium" | "long" | "full";
}

export function formatDateTime(
  value: string | number | Date | null | undefined,
  options?: FormatDateTimeOptions
): string {
  if (value == null || value === "") {
    return "";
  }
  const date = value instanceof Date ? value : new Date(value);
  if (!Number.isFinite(date.getTime())) {
    return "";
  }
  const timeZone = options?.timeZone ?? "UTC";
  const locale = options?.locale;
  try {
    return new Intl.DateTimeFormat(locale, {
      timeZone,
      dateStyle: options?.dateStyle ?? "short",
      timeStyle: options?.timeStyle ?? "medium",
    }).format(date);
  } catch {
    return date.toLocaleString(locale);
  }
}

export function formatDateTimeShort(
  value: string | number | Date | null | undefined,
  timeZone?: string,
  locale?: string
): string {
  return formatDateTime(value, { timeZone, locale, dateStyle: "short", timeStyle: "short" });
}
