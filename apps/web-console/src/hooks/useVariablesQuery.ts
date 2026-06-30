import { useSyncExternalStore } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchVariables, fetchVariablesBatch } from "../api";
import {
  isObjectWebSocketConnected,
  subscribeObjectWebSocketConnection,
  useObjectPathsSubscription,
} from "./useObjectWebSocket";
import { variablesRefetchIntervalMs } from "./variablesQueryPolicy";

export function useVariablesQuery(
  objectPath: string,
  refreshIntervalMs = 5000,
  enabled = true,
) {
  const wsConnected = useSyncExternalStore(
    subscribeObjectWebSocketConnection,
    isObjectWebSocketConnected,
    () => false,
  );

  useObjectPathsSubscription(enabled && objectPath ? [objectPath] : []);

  return useQuery({
    queryKey: ["variables", objectPath],
    queryFn: () => fetchVariables(objectPath),
    enabled: enabled && Boolean(objectPath),
    refetchInterval: variablesRefetchIntervalMs(refreshIntervalMs, wsConnected),
    retry: 2,
  });
}

export function useVariablesBatchQuery(
  objectPaths: string[],
  refreshIntervalMs = 5000,
  enabled = true,
) {
  const wsConnected = useSyncExternalStore(
    subscribeObjectWebSocketConnection,
    isObjectWebSocketConnected,
    () => false,
  );
  const uniquePaths = [...new Set(objectPaths.filter(Boolean))];

  useObjectPathsSubscription(enabled && uniquePaths.length > 0 ? uniquePaths : []);

  return useQuery({
    queryKey: ["variables-batch", uniquePaths],
    queryFn: () => fetchVariablesBatch(uniquePaths),
    enabled: enabled && uniquePaths.length > 0,
    refetchInterval: variablesRefetchIntervalMs(refreshIntervalMs, wsConnected),
    retry: 2,
  });
}
