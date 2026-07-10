import { useMemo } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  fetchAnalyticsCatalog,
  fetchAnalyticsCatalogById,
  validateAnalyticsCatalogExpression,
  type AnalyticsCatalogEntryDto,
  type AnalyticsCatalogValidateRequest,
} from "../api/analyticsCatalog";
import { PLATFORM_BINDING_ENTRIES, type PlatformBindingEntry } from "../utils/platformBindings";
import {
  HISTORIAN_EXPRESSION_FALLBACK_ENTRIES,
  mapAnalyticsCatalogToBindingEntries,
} from "../utils/historianExpressionBindings";

export type AnalyticsBrowserKind = "historian" | "reactive";

export function useAnalyticsCatalog(kind?: AnalyticsBrowserKind) {
  const query = useQuery<AnalyticsCatalogEntryDto[]>({
    queryKey: ["analytics-catalog"],
    queryFn: fetchAnalyticsCatalog,
    staleTime: 5 * 60_000,
  });

  const remoteEntries = useMemo(() => {
    if (!kind || !query.data?.length) {
      return [] as PlatformBindingEntry[];
    }
    return mapAnalyticsCatalogToBindingEntries(query.data, kind);
  }, [query.data, kind]);

  const fallbackEntries = useMemo(() => {
    if (kind === "historian") {
      return HISTORIAN_EXPRESSION_FALLBACK_ENTRIES;
    }
    if (kind === "reactive") {
      return PLATFORM_BINDING_ENTRIES;
    }
    return [] as PlatformBindingEntry[];
  }, [kind]);

  return {
    ...query,
    entries: remoteEntries.length > 0 ? remoteEntries : fallbackEntries,
    hasRemoteEntries: kind ? remoteEntries.length > 0 : (query.data?.length ?? 0) > 0,
  };
}

export function useAnalyticsCatalogFunction(functionId: string | null, enabled = true) {
  return useQuery({
    queryKey: ["analytics-catalog", "function", functionId],
    queryFn: () => fetchAnalyticsCatalogById(functionId ?? ""),
    enabled: enabled && Boolean(functionId),
    staleTime: 5 * 60_000,
  });
}

export function useAnalyticsCatalogValidate() {
  return useMutation({
    mutationFn: (payload: AnalyticsCatalogValidateRequest) => validateAnalyticsCatalogExpression(payload),
  });
}
