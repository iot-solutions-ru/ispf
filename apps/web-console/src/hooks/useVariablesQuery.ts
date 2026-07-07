import { useSyncExternalStore } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchVariables, fetchVariablesBatch } from "../api";
import {
  isObjectWebSocketConnected,
  subscribeObjectWebSocketConnection,
  useObjectPathsSubscription,
} from "./useObjectWebSocket";
import { variablesRefetchIntervalMs } from "./variablesQueryPolicy";
import { isOperatorMode } from "../utils/isOperatorMode";
import { cacheVariables, readCachedVariables } from "../utils/operatorOfflineCache";

async function loadVariablesWithOfflineCache(objectPath: string) {
  try {
    const data = await fetchVariables(objectPath);
    if (isOperatorMode()) {
      cacheVariables(objectPath, data);
    }
    return data;
  } catch (error) {
    if (isOperatorMode()) {
      const cached = readCachedVariables(objectPath);
      if (cached) {
        return cached;
      }
    }
    throw error;
  }
}

async function loadVariablesBatchWithOfflineCache(objectPaths: string[]) {
  try {
    const data = await fetchVariablesBatch(objectPaths);
    if (isOperatorMode()) {
      for (const [path, variables] of Object.entries(data)) {
        cacheVariables(path, variables);
      }
    }
    return data;
  } catch (error) {
    if (isOperatorMode()) {
      const cached: Record<string, Awaited<ReturnType<typeof fetchVariablesBatch>>[string]> = {};
      let hasAny = false;
      for (const path of objectPaths) {
        const variables = readCachedVariables(path);
        if (variables) {
          cached[path] = variables;
          hasAny = true;
        }
      }
      if (hasAny) {
        return cached;
      }
    }
    throw error;
  }
}

export function useVariablesQuery(
  objectPath: string,
  refreshIntervalMs: number | false = 5000,
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
    queryFn: () => loadVariablesWithOfflineCache(objectPath),
    enabled: enabled && Boolean(objectPath),
    refetchInterval: variablesRefetchIntervalMs(refreshIntervalMs, wsConnected),
    retry: 2,
  });
}

export function useVariablesBatchQuery(
  objectPaths: string[],
  refreshIntervalMs: number | false = 5000,
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
    queryFn: () => loadVariablesBatchWithOfflineCache(uniquePaths),
    enabled: enabled && uniquePaths.length > 0,
    refetchInterval: variablesRefetchIntervalMs(refreshIntervalMs, wsConnected),
    retry: 2,
  });
}
