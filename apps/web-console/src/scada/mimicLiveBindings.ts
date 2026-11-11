/**
 * HTTP batch interval for a mimic widget.
 * While the object socket is up, VARIABLE_UPDATED patches the same query.
 * The dashboard interval is the fallback while that socket is down.
 */
export function mimicBatchFallbackInterval(
  refreshIntervalMs: number | false,
  webSocketConnected: boolean,
): number | false {
  if (webSocketConnected) {
    return false;
  }
  if (refreshIntervalMs === false || refreshIntervalMs <= 0) {
    return false;
  }
  return refreshIntervalMs;
}
