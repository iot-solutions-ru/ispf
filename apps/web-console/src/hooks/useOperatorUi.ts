import { useQuery } from "@tanstack/react-query";
import { loadOperatorAppUi } from "./useOperatorAppsRegistry";
import type { OperatorUi } from "../types/operatorUi";
import { operatorAppIdCandidates } from "../utils/operatorAppsPath";
import { cacheOperatorUi, readCachedOperatorUi } from "../utils/operatorOfflineCache";

async function loadUiFromPublic(appId: string): Promise<OperatorUi | null> {
  const response = await fetch(`/operator-apps/${appId}.ui.json`);
  if (!response.ok) {
    return null;
  }
  const contentType = response.headers.get("content-type") ?? "";
  if (!contentType.includes("json")) {
    return null;
  }
  try {
    return (await response.json()) as OperatorUi;
  } catch {
    return null;
  }
}

async function loadOperatorUi(appId: string): Promise<OperatorUi | null> {
  const candidates = [...operatorAppIdCandidates(appId)].reverse();
  let lastError: unknown;
  for (const candidate of candidates) {
    try {
      const fromRegistry = await loadOperatorAppUi(candidate);
      if (fromRegistry) {
        cacheOperatorUi(appId, fromRegistry);
        cacheOperatorUi(candidate, fromRegistry);
        return fromRegistry;
      }
      const fromPublic = await loadUiFromPublic(candidate);
      if (fromPublic) {
        cacheOperatorUi(appId, fromPublic);
        cacheOperatorUi(candidate, fromPublic);
        return fromPublic;
      }
    } catch (error) {
      lastError = error;
      const cached = readCachedOperatorUi(candidate) ?? readCachedOperatorUi(appId);
      if (cached) {
        return cached;
      }
    }
  }
  if (lastError) {
    throw lastError;
  }
  return null;
}

export function useOperatorUi(appId: string | null) {
  return useQuery({
    queryKey: ["operator-ui", appId],
    queryFn: () => loadOperatorUi(appId!),
    enabled: Boolean(appId),
    refetchOnReconnect: true,
    placeholderData: () => (appId ? readCachedOperatorUi(appId) ?? undefined : undefined),
  });
}
