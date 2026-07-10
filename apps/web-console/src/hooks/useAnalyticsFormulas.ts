import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import {
  createAnalyticsFormula,
  expandAnalyticsFormula,
  fetchAnalyticsFormulas,
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
