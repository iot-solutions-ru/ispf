import type { DataRecord, VariableDto } from "../../types";
import { ensureRecord, recordsEqual } from "../ui/record";

/** Subset of VariableHistoryState needed when adopting remote variables. */
export interface RemoteVariableHistoryState {
  historyEnabled: boolean;
  historyRetentionDays: number | null;
  telemetryPublishMode: "INHERIT" | "FULL" | "TELEMETRY_ONLY" | "EVENT_JOURNAL_ONLY";
  historySampleMode: "CHANGES_ONLY" | "ALL_VALUES";
  includePreviousValueInEvent: boolean;
  storageMode: "PERSISTENT" | "TRANSIENT";
}

export interface RemoteVariableEditorState {
  variables: Record<string, DataRecord>;
  variableHistory?: Record<string, RemoteVariableHistoryState>;
}

function historyFromVariable(variable: VariableDto): RemoteVariableHistoryState {
  return {
    historyEnabled: variable.historyEnabled ?? false,
    historyRetentionDays: variable.historyRetentionDays ?? null,
    telemetryPublishMode: "INHERIT",
    historySampleMode: "CHANGES_ONLY",
    includePreviousValueInEvent: false,
    storageMode: "PERSISTENT",
  };
}

/** Merge server-side variable telemetry into inspector state; skip in-flight user edits. */
export function applyRemoteVariables<T extends RemoteVariableEditorState>(
  remote: VariableDto[],
  state: T,
  baseline: T,
): { state: T; baseline: T } | null {
  let stateChanged = false;
  let baselineChanged = false;
  const nextStateVars = { ...state.variables };
  const nextBaselineVars = { ...baseline.variables };
  const nextStateHistory = { ...(state.variableHistory ?? {}) };
  const nextBaselineHistory = { ...(baseline.variableHistory ?? {}) };
  let historyTouched = false;

  for (const variable of remote) {
    if (variable.name === "uiIcon") {
      continue;
    }
    const name = variable.name;
    const current = state.variables[name];
    const baseRec = baseline.variables[name];
    // Newly created variables appear in the live list before local state rebuilds — adopt them.
    if (!(name in baseline.variables)) {
      const next = ensureRecord(variable);
      const hist = historyFromVariable(variable);
      nextStateVars[name] = next;
      nextBaselineVars[name] = next;
      nextStateHistory[name] = hist;
      nextBaselineHistory[name] = hist;
      stateChanged = true;
      baselineChanged = true;
      historyTouched = true;
      continue;
    }
    if (current && baseRec && !recordsEqual(current, baseRec)) {
      continue;
    }
    const next = ensureRecord(variable);
    if (!recordsEqual(next, nextStateVars[name])) {
      nextStateVars[name] = next;
      stateChanged = true;
    }
    if (!recordsEqual(next, nextBaselineVars[name])) {
      nextBaselineVars[name] = next;
      baselineChanged = true;
    }
  }

  if (!stateChanged && !baselineChanged) {
    return null;
  }
  return {
    state: stateChanged
      ? {
          ...state,
          variables: nextStateVars,
          ...(historyTouched || state.variableHistory
            ? { variableHistory: nextStateHistory }
            : {}),
        }
      : state,
    baseline: baselineChanged
      ? {
          ...baseline,
          variables: nextBaselineVars,
          ...(historyTouched || baseline.variableHistory
            ? { variableHistory: nextBaselineHistory }
            : {}),
        }
      : baseline,
  };
}
