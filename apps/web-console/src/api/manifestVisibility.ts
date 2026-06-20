import type { OperatorManifestAction, OperatorManifestShowWhen } from "../types/operatorManifest";

export function isFieldEmpty(value: unknown): boolean {
  return value === null || value === undefined || value === "";
}

export function matchesShowWhen(row: Record<string, unknown>, condition: OperatorManifestShowWhen): boolean {
  const actual = row[condition.field];
  if (condition.empty === true) {
    return isFieldEmpty(actual);
  }
  if (condition.present === true) {
    return !isFieldEmpty(actual);
  }
  if (condition.equals !== undefined) {
    return String(actual) === String(condition.equals);
  }
  return true;
}

/** Whether an action should be shown/enabled for the selected table row. */
export function isActionVisible(action: OperatorManifestAction, selectedRow: Record<string, unknown> | null): boolean {
  if (action.showWhenAll?.length) {
    if (!selectedRow) {
      return false;
    }
    return action.showWhenAll.every((condition) => matchesShowWhen(selectedRow, condition));
  }
  if (action.showWhen) {
    if (!selectedRow) {
      return false;
    }
    return matchesShowWhen(selectedRow, action.showWhen);
  }
  return true;
}
