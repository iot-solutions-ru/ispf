/**
 * Adds reportTimeZone when calendarRange is present (mirrors server-side enricher).
 */
export function enrichReportRunParameters(
  params: Record<string, unknown>,
  timeZone: string | undefined
): Record<string, unknown> {
  const calendarRange = params.calendarRange;
  if (calendarRange == null || String(calendarRange).trim() === "") {
    return params;
  }
  if (params.reportTimeZone != null && String(params.reportTimeZone).trim() !== "") {
    return params;
  }
  if (params.timeZone != null && String(params.timeZone).trim() !== "") {
    return params;
  }
  if (!timeZone?.trim()) {
    return params;
  }
  return { ...params, reportTimeZone: timeZone };
}
