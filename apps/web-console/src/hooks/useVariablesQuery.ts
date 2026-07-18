import { useSyncExternalStore } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchVariables, fetchVariablesBatch } from "../api";
import {
  isObjectWebSocketConnected,
  subscribeObjectWebSocketConnection,
  useObjectPathsSubscription,
  useObjectVariableSubscriptions,
  type ObjectVariableInterest,
} from "./useObjectWebSocket";
import { variablesRefetchIntervalMs } from "./variablesQueryPolicy";
import { isOperatorMode } from "../utils/operator/isOperatorMode";
import { cacheVariables, readCachedVariables } from "../utils/operator/operatorOfflineCache";

function mayUseOfflineVariablesCache(error: unknown): boolean {
  if (!(error instanceof Error)) {
    return true;
  }
  const message = error.message.toLowerCase();
  if (
    message.includes("401")
    || message.includes("403")
    || message.includes("unauthorized")
    || message.includes("forbidden")
  ) {
    return false;
  }
  return true;
}

async function loadVariablesWithOfflineCache(objectPath: string) {
  try {
    const data = await fetchVariables(objectPath);
    if (isOperatorMode()) {
      cacheVariables(objectPath, data);
    }
    return data;
  } catch (error) {
    if (isOperatorMode() && mayUseOfflineVariablesCache(error)) {
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
    if (isOperatorMode() && mayUseOfflineVariablesCache(error)) {
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
  /** When set, WS interest is limited to these variables; otherwise path-wide. */
  subscribeVariables?: string[] | null,
) {
  const wsConnected = useSyncExternalStore(
    subscribeObjectWebSocketConnection,
    isObjectWebSocketConnected,
    () => false,
  );

  const interestEnabled = enabled && Boolean(objectPath);
  const narrowed = Boolean(subscribeVariables && subscribeVariables.length > 0);
  useObjectVariableSubscriptions(
    interestEnabled && narrowed
      ? [{ path: objectPath, variables: subscribeVariables }]
      : [],
  );
  useObjectPathsSubscription(interestEnabled && !narrowed && objectPath ? [objectPath] : []);

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
  /** Optional per-path variable lists. Missing path = path-wide interest. */
  variablesByPath?: Record<string, string[] | undefined> | null,
) {
  const wsConnected = useSyncExternalStore(
    subscribeObjectWebSocketConnection,
    isObjectWebSocketConnected,
    () => false,
  );
  const uniquePaths = [...new Set(objectPaths.filter(Boolean))];

  const interests: ObjectVariableInterest[] = enabled && uniquePaths.length > 0
    ? uniquePaths.map((path) => {
        const variables = variablesByPath?.[path];
        if (variables && variables.length > 0) {
          return { path, variables };
        }
        return { path };
      })
    : [];
  useObjectVariableSubscriptions(interests);

  return useQuery({
    queryKey: ["variables-batch", uniquePaths],
    queryFn: () => loadVariablesBatchWithOfflineCache(uniquePaths),
    enabled: enabled && uniquePaths.length > 0,
    staleTime: isOperatorMode() ? 0 : undefined,
    refetchOnMount: isOperatorMode() ? "always" : true,
    refetchInterval: variablesRefetchIntervalMs(refreshIntervalMs, wsConnected),
    retry: 2,
  });
}
