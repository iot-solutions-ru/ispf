/** When WebSocket is connected, live invalidations replace polling. */
export function variablesRefetchIntervalMs(
  refreshIntervalMs: number | false,
  webSocketConnected: boolean,
): number | false {
  if (refreshIntervalMs === false || refreshIntervalMs <= 0) {
    return false;
  }
  return webSocketConnected ? false : refreshIntervalMs;
}
