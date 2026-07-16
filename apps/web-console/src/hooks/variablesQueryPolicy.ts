/**
 * Keep HTTP polling even when WebSocket reports connected.
 * Half-open sockets and VARIABLE_UPDATED without `value` otherwise freeze widgets.
 */
export function variablesRefetchIntervalMs(
  refreshIntervalMs: number | false,
  _webSocketConnected: boolean,
): number | false {
  if (refreshIntervalMs === false || refreshIntervalMs <= 0) {
    return false;
  }
  return refreshIntervalMs;
}
