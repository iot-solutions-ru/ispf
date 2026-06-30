/** When WebSocket is connected, live invalidations replace polling. */
export function variablesRefetchIntervalMs(
  refreshIntervalMs: number,
  webSocketConnected: boolean,
): number | false {
  return webSocketConnected ? false : refreshIntervalMs;
}
