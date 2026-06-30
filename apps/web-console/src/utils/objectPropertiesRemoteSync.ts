import type { DataRecord, VariableDto } from "../types";
import { ensureRecord, recordsEqual } from "./record";

export interface RemoteVariableEditorState {
  variables: Record<string, DataRecord>;
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

  for (const variable of remote) {
    if (variable.name === "uiIcon") {
      continue;
    }
    const name = variable.name;
    if (!(name in baseline.variables)) {
      continue;
    }
    const current = state.variables[name];
    const baseRec = baseline.variables[name];
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
    state: stateChanged ? { ...state, variables: nextStateVars } : state,
    baseline: baselineChanged ? { ...baseline, variables: nextBaselineVars } : baseline,
  };
}
