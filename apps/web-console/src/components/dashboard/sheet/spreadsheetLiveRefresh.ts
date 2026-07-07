import type { SpreadsheetWidget } from "../../../types/dashboard";

const MIN_LIVE_POLL_MS = 500;

/** Binding / variable poll interval for spreadsheet widgets (BL-150). */
export function resolveSpreadsheetRefreshInterval(
  widget: SpreadsheetWidget,
  dashboardRefreshMs: number
): number | false {
  if (widget.live === false) {
    return false;
  }
  const base =
    widget.live === true
      ? widget.liveRefreshIntervalMs ?? dashboardRefreshMs
      : dashboardRefreshMs;
  return Math.max(MIN_LIVE_POLL_MS, base);
}
