import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import type { SpreadsheetWidget } from "../../../types/dashboard";
import { setVariable } from "../../../api";
import { useBoundVariable } from "../../../hooks/useBoundVariable";
import { useDashboardContext } from "../DashboardContext";
import { defaultSessionKey, resolveSheetConfig, resolveSheetMode } from "./sheetConfig";
import type { SheetValues } from "./sheetFormulaEngine";
import {
  loadCellContents,
  loadValuesFromSession,
  saveValuesToSessionPatch,
  saveValuesToVariableRecord,
} from "./sheetPersist";

const PERSIST_DEBOUNCE_MS = 400;

export function useSpreadsheetPersist(
  widget: SpreadsheetWidget,
  objectPath: string,
  refreshIntervalMs: number
) {
  const queryClient = useQueryClient();
  const { params, setParams } = useDashboardContext();
  const persistTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const sheetConfig = useMemo(() => resolveSheetConfig(widget), [widget]);
  const sheetMode = resolveSheetMode(widget);
  const persistMode = widget.persistMode ?? "session";
  const sessionKey = defaultSessionKey(widget);
  const valuesVarName = widget.valuesVariable?.trim() ?? "";

  const { variable: valuesVariable, isLoading: valuesLoading } = useBoundVariable(
    persistMode === "variable" && valuesVarName ? objectPath : "",
    persistMode === "variable" ? valuesVarName : "",
    widget.valueField,
    refreshIntervalMs
  );

  const loadedContents = useMemo((): SheetValues => {
    if (persistMode === "variable" && valuesVarName) {
      return loadCellContents(valuesVariable?.value ?? undefined, sheetConfig, sheetMode);
    }
    return {
      ...loadCellContents(undefined, sheetConfig, sheetMode),
      ...loadValuesFromSession(params, sessionKey),
    };
  }, [
    persistMode,
    valuesVarName,
    valuesVariable?.value,
    sheetConfig,
    sheetMode,
    params,
    sessionKey,
  ]);

  const [localContents, setLocalContents] = useState<SheetValues>(loadedContents);

  useEffect(() => {
    setLocalContents(loadedContents);
  }, [loadedContents]);

  const persistContents = useCallback(
    (nextContents: SheetValues) => {
      if (persistMode === "session") {
        setParams({ [sessionKey]: saveValuesToSessionPatch(nextContents) });
        return;
      }
      if (!valuesVarName || !objectPath) {
        return;
      }
      const record = saveValuesToVariableRecord(
        nextContents,
        valuesVariable?.value ?? undefined
      );
      setVariable(objectPath, valuesVarName, record).then(() => {
        queryClient.invalidateQueries({ queryKey: ["variables", objectPath] });
      });
    },
    [
      persistMode,
      sessionKey,
      setParams,
      valuesVarName,
      objectPath,
      valuesVariable?.value,
      queryClient,
    ]
  );

  const schedulePersist = useCallback(
    (nextContents: SheetValues) => {
      if (persistTimerRef.current) {
        clearTimeout(persistTimerRef.current);
      }
      persistTimerRef.current = setTimeout(() => {
        persistContents(nextContents);
      }, PERSIST_DEBOUNCE_MS);
    },
    [persistContents]
  );

  useEffect(
    () => () => {
      if (persistTimerRef.current) {
        clearTimeout(persistTimerRef.current);
      }
    },
    []
  );

  const isLoading = persistMode === "variable" && valuesLoading && Boolean(valuesVarName);
  const canEdit = widget.editable !== false;

  return {
    sheetConfig,
    sheetMode,
    localContents,
    setLocalContents,
    schedulePersist,
    isLoading,
    canEdit,
  };
}
