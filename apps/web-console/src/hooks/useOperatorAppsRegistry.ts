import { useQueries, useQuery } from "@tanstack/react-query";
import { useMemo } from "react";
import { fetchOperatorApps, fetchOperatorAppUi, type OperatorAppEntry } from "../api/operatorApps";
import { getAuthHeaders } from "../auth/session";
import { fetchWithIngressFallback } from "../utils/ingressFetch";
import { operatorAppIdCandidates } from "../utils/operatorAppsPath";
import type { OperatorUi } from "../types/operatorUi";

const BUNDLE_API_PATHS = ["operator-ui", "hmi-ui"] as const;

async function loadUiFromBundleApi(appId: string): Promise<OperatorUi | null> {
  for (const path of BUNDLE_API_PATHS) {
    const response = await fetchWithIngressFallback(`/api/v1/applications/${encodeURIComponent(appId)}/${path}`, {
      headers: getAuthHeaders(),
    });
    if (response.status === 404 || response.status === 403) {
      continue;
    }
    if (!response.ok) {
      throw new Error(`Operator UI API failed: ${response.status}`);
    }
    const contentType = response.headers.get("content-type") ?? "";
    if (!contentType.includes("json")) {
      continue;
    }
    try {
      return (await response.json()) as OperatorUi;
    } catch {
      continue;
    }
  }
  return null;
}

export async function loadOperatorAppUi(
  appId: string,
  options?: { source?: OperatorAppEntry["source"] }
): Promise<OperatorUi | null> {
  // Prefer canonical id (without bundle-) first so visual-group leaves resolve to operator-apps.
  const candidates = [...operatorAppIdCandidates(appId)].reverse();
  const skipPlatformApi = options?.source === "bundle";
  for (const candidate of candidates) {
    if (!skipPlatformApi) {
      const fromPlatformApi = await fetchOperatorAppUi(candidate);
      if (fromPlatformApi) {
        return fromPlatformApi;
      }
    }
    const fromBundle = await loadUiFromBundleApi(candidate);
    if (fromBundle) {
      return fromBundle;
    }
  }
  return null;
}

export function useOperatorAppsRegistry(currentUi?: OperatorUi) {
  const appsQuery = useQuery({
    queryKey: ["operator-apps"],
    queryFn: fetchOperatorApps,
    staleTime: 60_000,
  });

  const appIds = useMemo(() => appsQuery.data?.map((app) => app.appId) ?? [], [appsQuery.data]);

  const uiQueries = useQueries({
    queries: appIds.map((appId) => ({
      queryKey: ["operator-ui", appId],
      queryFn: () => {
        const source = appsQuery.data?.find((app) => app.appId === appId)?.source;
        return loadOperatorAppUi(appId, { source });
      },
      enabled: Boolean(appId),
      staleTime: 60_000,
    })),
  });

  const uiSignature = uiQueries
    .map((query, index) => `${appIds[index] ?? ""}:${query.dataUpdatedAt}:${query.data?.appId ?? ""}`)
    .join("|");

  const operatorApps = useMemo(() => {
    const loaded = uiQueries
      .map((query) => query.data)
      .filter((ui): ui is OperatorUi => Boolean(ui));
    if (!currentUi) {
      return loaded;
    }
    const merged = new Map(loaded.map((ui) => [ui.appId, ui]));
    merged.set(currentUi.appId, currentUi);
    return [...merged.values()];
  }, [currentUi, uiSignature, uiQueries]);

  const isLoading =
    appsQuery.isLoading || uiQueries.some((query) => query.isLoading && query.fetchStatus !== "idle");

  return { operatorApps, isLoading, appsQuery };
}
