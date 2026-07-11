import { useQuery } from "@tanstack/react-query";
import { getAuthHeaders } from "../auth/session";
import { fetchWithIngressFallback } from "../utils/ingressFetch";
import type { OperatorManifest } from "../types/operatorManifest";
import {
  cacheOperatorManifest,
  readCachedOperatorManifest,
} from "../utils/operatorOfflineCache";

async function loadManifestFromApi(appId: string): Promise<OperatorManifest | null> {
  const response = await fetchWithIngressFallback(
    `/api/v1/applications/${encodeURIComponent(appId)}/operator-manifest`,
    { headers: getAuthHeaders() },
  );
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`Operator manifest API failed: ${response.status}`);
  }
  return response.json();
}

async function loadManifestFromPublic(appId: string): Promise<OperatorManifest> {
  const response = await fetch(`/operator-apps/${appId}.manifest.json`);
  if (!response.ok) {
    throw new Error(`Operator manifest not found for app: ${appId}`);
  }
  return response.json();
}

async function loadManifest(appId: string): Promise<OperatorManifest> {
  try {
    console.warn(
      "[ISPF] Static operator manifest (operator-apps/*.manifest.json) is deprecated. "
      + "Use Application bundle operatorUi / wire profile instead."
    );
    const fromApi = await loadManifestFromApi(appId);
    if (fromApi) {
      cacheOperatorManifest(appId, fromApi);
      return fromApi;
    }
    const fromPublic = await loadManifestFromPublic(appId);
    cacheOperatorManifest(appId, fromPublic);
    return fromPublic;
  } catch (error) {
    const cached = readCachedOperatorManifest(appId);
    if (cached) {
      return cached;
    }
    throw error;
  }
}

export function useOperatorManifest(appId: string | null) {
  return useQuery({
    queryKey: ["operator-manifest", appId],
    queryFn: () => loadManifest(appId!),
    enabled: Boolean(appId),
    refetchOnReconnect: true,
    placeholderData: () =>
      appId ? readCachedOperatorManifest(appId) ?? undefined : undefined,
  });
}
