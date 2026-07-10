import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createAnalyticsFormula,
  deleteAnalyticsFormula,
  expandAnalyticsFormula,
  fetchAnalyticsFormulas,
  updateAnalyticsFormula,
  type AnalyticsFormulaDto,
  type AnalyticsFormulaExpandRequest,
} from "../api/analyticsFormulas";

export function useAnalyticsFormulas(scope = "site", appId?: string) {
  return useQuery({
    queryKey: ["analytics-formulas", scope, appId ?? ""],
    queryFn: () => fetchAnalyticsFormulas(scope, appId),
    staleTime: 30_000,
  });
}

export function useCreateAnalyticsFormula() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (formula: AnalyticsFormulaDto) => createAnalyticsFormula(formula),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["analytics-formulas"] });
      queryClient.invalidateQueries({ queryKey: ["analytics-catalog"] });
    },
  });
}

export function useUpdateAnalyticsFormula() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      formulaId,
      formula,
      scope = "site",
      appId,
    }: {
      formulaId: string;
      formula: AnalyticsFormulaDto;
      scope?: string;
      appId?: string;
    }) => updateAnalyticsFormula(formulaId, formula, scope, appId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["analytics-formulas"] });
      queryClient.invalidateQueries({ queryKey: ["analytics-catalog"] });
    },
  });
}

export function useDeleteAnalyticsFormula() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({
      formulaId,
      scope = "site",
      appId,
    }: {
      formulaId: string;
      scope?: string;
      appId?: string;
    }) => deleteAnalyticsFormula(formulaId, scope, appId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["analytics-formulas"] });
      queryClient.invalidateQueries({ queryKey: ["analytics-catalog"] });
    },
  });
}

export function useExpandAnalyticsFormula() {
  return useMutation({
    mutationFn: ({
      formulaId,
      payload,
    }: {
      formulaId: string;
      payload: AnalyticsFormulaExpandRequest;
    }) => expandAnalyticsFormula(formulaId, payload),
  });
}
