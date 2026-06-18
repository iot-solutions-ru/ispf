import { useQuery } from "@tanstack/react-query";
import { fetchVariables } from "../api";
import { readFieldValue } from "../types/dashboard";

export function useBoundVariable(
  objectPath: string,
  variableName: string,
  valueField?: string,
  refreshIntervalMs = 5000
) {
  const query = useQuery({
    queryKey: ["variables", objectPath],
    queryFn: () => fetchVariables(objectPath),
    enabled: Boolean(objectPath && variableName),
    refetchInterval: refreshIntervalMs,
  });

  const variable = query.data?.find((item) => item.name === variableName);
  const row = variable?.value?.rows[0];
  const rawValue = readFieldValue(row, valueField);

  return {
    ...query,
    variable,
    rawValue,
    writable: variable?.writable ?? false,
  };
}
