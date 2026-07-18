import { useMemo } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import {
  fetchAnalyticsCatalog,
  fetchAnalyticsCatalogById,
  validateAnalyticsCatalogExpression,
  type AnalyticsCatalogEntryDto,
  type AnalyticsCatalogValidateRequest,
} from "../api/analyticsCatalog";
import type { PlatformBindingEntry } from "../utils/platform/platformBindings";
import { mapAnalyticsCatalogToBindingEntries } from "../utils/analytics/historianExpressionBindings";
import { mapMergedExpressionCatalog } from "../utils/functionScript/functionExpressionCatalog";

export type AnalyticsBrowserKind = "historian" | "reactive" | "all";

export function useAnalyticsCatalog(kind?: AnalyticsBrowserKind) {
  const query = useQuery<AnalyticsCatalogEntryDto[]>({
    queryKey: ["analytics-catalog"],
    queryFn: fetchAnalyticsCatalog,
    staleTime: 5 * 60_000,
  });

  const remoteEntries = useMemo(() => {
    if (!kind || kind === "all" || !query.data?.length) {
      return [] as PlatformBindingEntry[];
    }
    return mapAnalyticsCatalogToBindingEntries(query.data, kind);
  }, [query.data, kind]);

  return {
    ...query,
    entries: remoteEntries,
    hasRemoteEntries: (query.data?.length ?? 0) > 0,
  };
}

export function useFunctionExpressionCatalog() {
  const query = useQuery<AnalyticsCatalogEntryDto[]>({
    queryKey: ["analytics-catalog"],
    queryFn: fetchAnalyticsCatalog,
    staleTime: 5 * 60_000,
  });

  const entries = useMemo(() => {
    if (!query.data?.length) {
      return [] as PlatformBindingEntry[];
    }
    return mapMergedExpressionCatalog(query.data);
  }, [query.data]);

  return {
    ...query,
    entries,
    hasRemoteEntries: (query.data?.length ?? 0) > 0,
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
