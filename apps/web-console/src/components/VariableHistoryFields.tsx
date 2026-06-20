export interface VariableHistoryState {
  historyEnabled: boolean;
  historyRetentionDays: number | null;
}

interface VariableHistoryFieldsProps {
  value: VariableHistoryState;
  onChange: (next: VariableHistoryState) => void;
  disabled?: boolean;
  idPrefix?: string;
}

export function formatHistoryRetention(days: number | null | undefined): string {
  if (days == null || days <= 0) {
    return "платформа (90 дн)";
  }
  return `${days} дн`;
}

export default function VariableHistoryFields({
  value,
  onChange,
  disabled = false,
  idPrefix = "var-history",
}: VariableHistoryFieldsProps) {
  const retentionId = `${idPrefix}-retention`;

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
        Хранить историю значений
      </label>
      <label htmlFor={retentionId}>
        Срок хранения (дней)
        <input
          id={retentionId}
          type="number"
          min={1}
          max={3650}
          placeholder="платформа (90)"
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
        Пустой срок — используется платформенный default. История пишется только для числовых полей.
      </p>
    </div>
  );
}
