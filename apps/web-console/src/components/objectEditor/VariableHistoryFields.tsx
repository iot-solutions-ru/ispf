import { useTranslation } from "react-i18next";
import type {
  TelemetryPublishModeValue,
  HistorySampleModeValue,
  VariableStorageModeValue,
  VariableHistoryState,
} from "./variableHistoryModel";

interface VariableHistoryFieldsProps {
  value: VariableHistoryState;
  onChange: (next: VariableHistoryState) => void;
  disabled?: boolean;
  idPrefix?: string;
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
