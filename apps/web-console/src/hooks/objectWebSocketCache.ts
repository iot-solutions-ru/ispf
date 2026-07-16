import type { QueryClient } from "@tanstack/react-query";
import type { DataRecord, VariableDto } from "../types";
import { fetchVariables } from "../api";
import { refreshWorkQueue } from "./workQueueCache";
import type { ObjectWsMessage } from "./objectWebSocketTypes";

/** Debounce HTTP fallback when VARIABLE_UPDATED arrives without `value`. */
const pendingPathRefresh = new Map<string, ReturnType<typeof setTimeout>>();
const PATH_REFRESH_DEBOUNCE_MS = 400;

function schedulePathRefresh(queryClient: QueryClient, path: string): void {
  const existing = pendingPathRefresh.get(path);
  if (existing !== undefined) {
    clearTimeout(existing);
  }
  pendingPathRefresh.set(
    path,
    setTimeout(() => {
      pendingPathRefresh.delete(path);
      void queryClient
        .fetchQuery({
          queryKey: ["variables", path],
          queryFn: () => fetchVariables(path),
        })
        .then((variables) => {
          mergePathIntoBatchCaches(queryClient, path, variables);
        })
        .catch(() => {
          // Leave existing cache; next reconnect / subscription refresh will recover.
        });
    }, PATH_REFRESH_DEBOUNCE_MS),
  );
}

function patchVariableList(
  list: VariableDto[],
  variableName: string,
  value: DataRecord,
  updatedAt: string,
): VariableDto[] | null {
  const idx = list.findIndex((variable) => variable.name === variableName);
  if (idx < 0) {
    return null;
  }
  const current = list[idx];
  if (current.value === value && current.updatedAt === updatedAt) {
    return list;
  }
  const next = list.slice();
  next[idx] = { ...current, value, updatedAt };
  return next;
}

function mergePathIntoBatchCaches(
  queryClient: QueryClient,
  path: string,
  variables: VariableDto[],
): void {
  queryClient.setQueryData<VariableDto[]>(["variables", path], variables);
  queryClient.setQueriesData<Record<string, VariableDto[]>>(
    { queryKey: ["variables-batch"] },
    (current) => {
      if (!current || !(path in current)) {
        return current;
      }
      return { ...current, [path]: variables };
    },
  );
}

/**
 * Apply one VARIABLE_UPDATED into React Query caches without invalidating
 * the whole variables-batch (avoids full HTTP refetch storms).
 */
export function patchVariableCachesFromWs(
  queryClient: QueryClient,
  path: string,
  variableName: string,
  value: DataRecord | undefined,
  updatedAt: string,
): void {
  if (value != null) {
    let missedCachedVariable = false;

    queryClient.setQueryData<VariableDto[]>(["variables", path], (current) => {
      if (!current) {
        return current;
      }
      const patched = patchVariableList(current, variableName, value, updatedAt);
      if (patched == null) {
        missedCachedVariable = true;
        return current;
      }
      return patched;
    });

    queryClient.setQueriesData<Record<string, VariableDto[]>>(
      { queryKey: ["variables-batch"] },
      (current) => {
        if (!current || !(path in current)) {
          return current;
        }
        const patched = patchVariableList(current[path], variableName, value, updatedAt);
        if (patched == null) {
          missedCachedVariable = true;
          return current;
        }
        if (patched === current[path]) {
          return current;
        }
        return { ...current, [path]: patched };
      },
    );

    if (!missedCachedVariable) {
      return;
    }
  }

  // Payload had no value, or variable missing from cached lists — refresh that path (debounced).
  schedulePathRefresh(queryClient, path);
}

/** React Query updates for a single `/ws/objects` payload. */
export function applyObjectWebSocketMessage(queryClient: QueryClient, message: ObjectWsMessage): void {
  switch (message.type) {
    case "CREATED":
    case "UPDATED":
    case "DELETED":
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["object", message.path] });
      queryClient.invalidateQueries({ queryKey: ["object-editor", message.path] });
      break;
    case "VARIABLE_UPDATED":
      patchVariableCachesFromWs(
        queryClient,
        message.path,
        message.variableName,
        message.value,
        message.timestamp,
      );
      if (message.variableName === "driverStatus") {
        queryClient.invalidateQueries({ queryKey: ["objects"] });
        queryClient.invalidateQueries({ queryKey: ["driver-status", message.path] });
      }
      break;
    case "EVENT_FIRED":
      queryClient.invalidateQueries({ queryKey: ["events", message.path] });
      queryClient.invalidateQueries({ queryKey: ["events", "all"] });
      queryClient.invalidateQueries({ queryKey: ["events", "operator-sidebar"] });
      break;
    case "presence":
      break;
  }

  if (message.type === "UPDATED" || message.type === "DELETED") {
    queryClient.invalidateQueries({ queryKey: ["dashboard", message.path] });
    queryClient.invalidateQueries({ queryKey: ["workflow", message.path] });
  }
  if (message.type === "EVENT_FIRED") {
    void refreshWorkQueue(queryClient);
  }
}
