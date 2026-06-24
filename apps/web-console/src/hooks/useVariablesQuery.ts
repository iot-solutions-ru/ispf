import { useSyncExternalStore } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchVariables, fetchVariablesBatch } from "../api";
import {
  isObjectWebSocketConnected,
  subscribeObjectWebSocketConnection,
} from "./useObjectWebSocket";

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

  return useQuery({
    queryKey: ["variables", objectPath],
    queryFn: () => fetchVariables(objectPath),
    enabled: enabled && Boolean(objectPath),
    refetchInterval: wsConnected ? false : refreshIntervalMs,
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

  return useQuery({
    queryKey: ["variables-batch", uniquePaths],
    queryFn: () => fetchVariablesBatch(uniquePaths),
    enabled: enabled && uniquePaths.length > 0,
    refetchInterval: wsConnected ? false : refreshIntervalMs,
  });
}
