import { useMemo } from "react";
import type { SheetConfig } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import { regionContentsFromRows } from "./sheetFormulaEngine";

export function useSheetDataRegion(
  config: SheetConfig,
  objectPath: string,
  refreshIntervalMs: number | false
): { regionContents: Record<string, string>; revision: number } {
  const region = config.dataRegion;
  const variableName = region?.variableName?.trim() ?? "";

  const { variable } = useBoundVariable(
    region && variableName ? objectPath : "",
    variableName,
    undefined,
    refreshIntervalMs
  );

  const regionContents = useMemo(() => {
    if (!region || !variable?.value?.rows?.length) {
      return {};
    }
    const rows = variable.value.rows.map((row) => {
      const mapped: Record<string, unknown> = {};
      for (const field of region.columnFields) {
        mapped[field] = readFieldValue(row, field);
      }
      return mapped;
    });
    return regionContentsFromRows(config, rows);
  }, [config, region, variable?.value?.rows]);

  return {
    regionContents,
    revision: variable?.value?.rows?.length ?? 0,
  };
}
