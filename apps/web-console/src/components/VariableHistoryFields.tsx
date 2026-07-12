import { useTranslation } from "react-i18next";
import i18n from "../i18n";

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

export function historyStateEqual(a: VariableHistoryState, b: VariableHistoryState): boolean {
  return (
    a.historyEnabled === b.historyEnabled
    && a.historyRetentionDays === b.historyRetentionDays
    && a.telemetryPublishMode === b.telemetryPublishMode
    && a.historySampleMode === b.historySampleMode
    && a.includePreviousValueInEvent === b.includePreviousValueInEvent
    && a.storageMode === b.storageMode
  );
}

interface VariableHistoryFieldsProps {
  value: VariableHistoryState;
  onChange: (next: VariableHistoryState) => void;
  disabled?: boolean;
  idPrefix?: string;
}

export function formatHistoryRetention(days: number | null | undefined): string {
  if (days == null || days <= 0) {
    return i18n.t("inspector:variables.historyRetentionPlatform");
  }
  return i18n.t("inspector:variables.historyRetentionDaysShort", { count: days });
}

export default function VariableHistoryFields({
  value,
  onChange,
  disabled = false,
  idPrefix = "var-history",
}: VariableHistoryFieldsProps) {
  const { t } = useTranslation("inspector");
  const retentionId = `${idPrefix}-retention`;
  const telemetryModeId = `${idPrefix}-telemetry-mode`;
  const sampleModeId = `${idPrefix}-sample-mode`;
  const storageModeId = `${idPrefix}-storage-mode`;
  const previousValueId = `${idPrefix}-previous-value`;

  return (
    <div className="variable-history-fields">
      <label className="checkbox-label">
        <input
          type="checkbox"
          checked={value.historyEnabled}
          disabled={disabled}
          onChange={(e) =>
            onChange({
              ...value,
              historyEnabled: e.target.checked,
            })
          }
        />
        {t("variables.storeHistory")}
      </label>
      <label htmlFor={retentionId}>
        {t("variables.retentionDays")}
        <input
          id={retentionId}
          type="number"
          min={1}
          max={3650}
          placeholder={t("variables.retentionPlaceholder")}
          disabled={disabled || !value.historyEnabled}
          value={value.historyRetentionDays ?? ""}
          onChange={(e) => {
            const raw = e.target.value.trim();
            onChange({
              ...value,
              historyRetentionDays: raw === "" ? null : Number.parseInt(raw, 10),
            });
          }}
        />
      </label>
      <p className="hint">
        {t("variables.retentionHint")}
      </p>
      <label htmlFor={sampleModeId}>
        {t("variables.historySampleMode")}
        <select
          id={sampleModeId}
          disabled={disabled || !value.historyEnabled}
          value={value.historySampleMode}
          onChange={(e) =>
            onChange({
              ...value,
              historySampleMode: e.target.value as HistorySampleModeValue,
            })
          }
        >
          <option value="CHANGES_ONLY">{t("variables.historySampleModeChangesOnly")}</option>
          <option value="ALL_VALUES">{t("variables.historySampleModeAllValues")}</option>
        </select>
      </label>
      <p className="hint">{t("variables.historySampleModeHint")}</p>
      <label htmlFor={telemetryModeId}>
        {t("variables.telemetryPublishMode")}
        <select
          id={telemetryModeId}
          disabled={disabled}
          value={value.telemetryPublishMode}
          onChange={(e) =>
            onChange({
              ...value,
              telemetryPublishMode: e.target.value as TelemetryPublishModeValue,
            })
          }
        >
          <option value="INHERIT">{t("variables.telemetryPublishModeInherit")}</option>
          <option value="FULL">{t("variables.telemetryPublishModeFull")}</option>
          <option value="TELEMETRY_ONLY">{t("variables.telemetryPublishModeTelemetryOnly")}</option>
          <option value="EVENT_JOURNAL_ONLY">{t("variables.telemetryPublishModeEventJournalOnly")}</option>
        </select>
      </label>
      <p className="hint">
        {t("variables.telemetryPublishModeHint")}
      </p>
      <label htmlFor={storageModeId}>
        {t("variables.storageMode")}
        <select
          id={storageModeId}
          disabled={disabled}
          value={value.storageMode}
          onChange={(e) =>
            onChange({
              ...value,
              storageMode: e.target.value as VariableStorageModeValue,
            })
          }
        >
          <option value="PERSISTENT">{t("variables.storageModePersistent")}</option>
          <option value="TRANSIENT">{t("variables.storageModeTransient")}</option>
        </select>
      </label>
      <p className="hint">{t("variables.storageModeHint")}</p>
      <label className="checkbox-label" htmlFor={previousValueId}>
        <input
          id={previousValueId}
          type="checkbox"
          checked={value.includePreviousValueInEvent}
          disabled={disabled}
          onChange={(e) =>
            onChange({
              ...value,
              includePreviousValueInEvent: e.target.checked,
            })
          }
        />
        {t("variables.includePreviousValueInEvent")}
      </label>
      <p className="hint">{t("variables.includePreviousValueInEventHint")}</p>
    </div>
  );
}
