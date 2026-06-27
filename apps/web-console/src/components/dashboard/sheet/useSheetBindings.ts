import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchVariableHistory, fetchVariables } from "../../../api";
import type { SheetConfig } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import { resolveWidgetPath } from "../dashboardUtils";
import { useDashboardContext } from "../DashboardContext";
import {
  bindingCacheKey,
  histCacheKey,
  type IspfFormulaContext,
} from "./sheetFormulaEngine";

interface BindingCellRef {
  address: string;
  objectPath: string;
  variableName: string;
  valueField?: string;
}

export function useSheetBindings(
  config: SheetConfig,
  objectPath: string,
  refreshIntervalMs: number
): {
  externalByAddr: Map<string, number | string | boolean>;
  ispfContext: IspfFormulaContext;
} {
  const { selection, params } = useDashboardContext();

  const bindingCells = useMemo((): BindingCellRef[] => {
    const refs: BindingCellRef[] = [];
    for (const [address, cell] of Object.entries(config.cells)) {
      if (cell.kind !== "binding" || !cell.variableName) {
        continue;
      }
      const path =
        cell.objectPath?.trim() ||
        resolveWidgetPath(objectPath, undefined, selection, undefined, params);
      refs.push({
        address,
        objectPath: path,
        variableName: cell.variableName,
        valueField: cell.valueField,
      });
    }
    return refs;
  }, [config.cells, objectPath, selection, params]);

  const pathsKey = bindingCells.map((b) => b.objectPath).join("|");
  const varsKey = bindingCells.map((b) => b.variableName).join("|");

  const queries = useQuery({
    queryKey: ["sheet-bindings", pathsKey, varsKey],
    queryFn: async () => {
      const byPath = new Map<string, Awaited<ReturnType<typeof fetchVariables>>>();
      const uniquePaths = [...new Set(bindingCells.map((b) => b.objectPath).filter(Boolean))];
      await Promise.all(
        uniquePaths.map(async (path) => {
          byPath.set(path, await fetchVariables(path));
        })
      );
      return byPath;
    },
    enabled: bindingCells.length > 0,
    refetchInterval: refreshIntervalMs,
  });

  const histQuery = useQuery({
    queryKey: ["sheet-bindings-hist", pathsKey, varsKey],
    queryFn: async () => {
      const histValues = new Map<string, number>();
      const to = new Date().toISOString();
      const from = new Date(Date.now() - 5 * 60 * 1000).toISOString();
      await Promise.all(
        bindingCells.map(async (ref) => {
          try {
            const resp = await fetchVariableHistory(ref.objectPath, ref.variableName, {
              from,
              to,
              limit: 1,
              field: ref.valueField ?? "value",
            });
            const sample = resp.samples?.[0];
            if (sample && typeof sample.value === "number") {
              histValues.set(histCacheKey(ref.objectPath, ref.variableName, 5), sample.value);
            }
          } catch {
            // historian optional
          }
        })
      );
      return histValues;
    },
    enabled: bindingCells.length > 0,
    refetchInterval: refreshIntervalMs,
  });

  return useMemo(() => {
    const externalByAddr = new Map<string, number | string | boolean>();
    const bindingValues = new Map<string, number | string | boolean>();
    const tableColumnSums = new Map<string, number>();
    const histValues = histQuery.data ?? new Map<string, number>();

    const byPath = queries.data;
    if (byPath) {
      for (const ref of bindingCells) {
        const vars = byPath.get(ref.objectPath) ?? [];
        const variable = vars.find((v) => v.name === ref.variableName);
        const raw = readFieldValue(variable?.value?.rows?.[0], ref.valueField);
        if (raw !== undefined && raw !== null) {
          const val =
            typeof raw === "number" || typeof raw === "boolean" ? raw : String(raw);
          externalByAddr.set(ref.address, val);
          bindingValues.set(
            bindingCacheKey(ref.objectPath, ref.variableName, ref.valueField ?? "value"),
            val
          );
        }

        if (variable?.value?.rows?.length) {
          for (const field of ["int", "value", "string"] as const) {
            let sum = 0;
            let count = 0;
            for (const row of variable.value.rows) {
              const v = readFieldValue(row, field);
              const num = typeof v === "number" ? v : Number.parseFloat(String(v ?? ""));
              if (Number.isFinite(num)) {
                sum += num;
                count++;
              }
            }
            if (count > 0) {
              tableColumnSums.set(`${ref.variableName}|${field}`, sum);
            }
          }
        }
      }
    }

    return {
      externalByAddr,
      ispfContext: { bindingValues, tableColumnSums, histValues },
    };
  }, [bindingCells, queries.data, histQuery.data]);
}
