import { readFieldValue } from "../types/dashboard";
import { useVariablesQuery } from "./useVariablesQuery";

export function useBoundVariable(
  objectPath: string,
  variableName: string,
  valueField?: string,
  refreshIntervalMs: number | false = 5000
) {
  const query = useVariablesQuery(
    objectPath,
    refreshIntervalMs,
    Boolean(objectPath && variableName),
  );

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
