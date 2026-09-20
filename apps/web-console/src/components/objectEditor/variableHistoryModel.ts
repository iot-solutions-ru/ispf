// Extracted from VariableHistoryFields.tsx so that component modules only export components
// (react-refresh/only-export-components keeps Fast Refresh state-preserving).
import i18n from "../../i18n/index";

export function telemetryModeFromVariable(mode: string | null | undefined): TelemetryPublishModeValue {
  if (mode == null || mode === "") {
    return "INHERIT";
  }
  if (
    mode === "FULL"
    || mode === "TELEMETRY_ONLY"
    || mode === "EVENT_JOURNAL_ONLY"
  ) {
    return mode;
  }
  return "INHERIT";
}

export function telemetryModeToApi(mode: TelemetryPublishModeValue): string | null {
  return mode === "INHERIT" ? null : mode;
}

export function historySampleModeFromVariable(mode: string | null | undefined): HistorySampleModeValue {
  return mode === "ALL_VALUES" ? "ALL_VALUES" : "CHANGES_ONLY";
}

export function storageModeFromVariable(mode: string | null | undefined): VariableStorageModeValue {
  return mode === "TRANSIENT" ? "TRANSIENT" : "PERSISTENT";
}

export function historyStateFromVariable(variable: {
  historyEnabled?: boolean;
  historyRetentionDays?: number | null;
  telemetryPublishMode?: string | null;
  historySampleMode?: string | null;
  includePreviousValueInEvent?: boolean;
  storageMode?: string | null;
}): VariableHistoryState {
  return {
    historyEnabled: variable.historyEnabled ?? false,
    historyRetentionDays: variable.historyRetentionDays ?? null,
    telemetryPublishMode: telemetryModeFromVariable(variable.telemetryPublishMode),
    historySampleMode: historySampleModeFromVariable(variable.historySampleMode),
    includePreviousValueInEvent: variable.includePreviousValueInEvent ?? false,
    storageMode: storageModeFromVariable(variable.storageMode),
  };
}

export function historyStateEqual(
  a: VariableHistoryState | null | undefined,
  b: VariableHistoryState | null | undefined,
): boolean {
  if (a == null && b == null) return true;
  if (a == null || b == null) return false;
  return (
    a.historyEnabled === b.historyEnabled
    && a.historyRetentionDays === b.historyRetentionDays
    && a.telemetryPublishMode === b.telemetryPublishMode
    && a.historySampleMode === b.historySampleMode
    && a.includePreviousValueInEvent === b.includePreviousValueInEvent
    && a.storageMode === b.storageMode
  );
}

export function formatHistoryRetention(days: number | null | undefined): string {
  if (days == null || days <= 0) {
    return i18n.t("inspector:variables.historyRetentionPlatform");
  }
  return i18n.t("inspector:variables.historyRetentionDaysShort", { count: days });
}

export type TelemetryPublishModeValue = "INHERIT" | "FULL" | "TELEMETRY_ONLY" | "EVENT_JOURNAL_ONLY";

export type HistorySampleModeValue = "CHANGES_ONLY" | "ALL_VALUES";

export type VariableStorageModeValue = "PERSISTENT" | "TRANSIENT";

export interface VariableHistoryState {
  historyEnabled: boolean;
  historyRetentionDays: number | null;
  telemetryPublishMode: TelemetryPublishModeValue;
  historySampleMode: HistorySampleModeValue;
  includePreviousValueInEvent: boolean;
  storageMode: VariableStorageModeValue;
}
