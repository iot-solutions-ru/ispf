import type { EventLevel } from "../../types/event";
import type { OperatorAlarmBarConfig, OperatorAlarmRule } from "../../types/operatorAlarmBar";

const EVENT_LEVELS: EventLevel[] = ["WARNING", "ERROR", "CRITICAL"];

function newRule(index: number): OperatorAlarmRule {
  return {
    id: `rule-${index}`,
    minLevel: "ERROR",
    fields: [
      { label: "Событие", source: "eventName" },
      { label: "Объект", source: "objectPath" },
      { label: "Время", source: "timestamp" },
    ],
    persistUntilDismiss: true,
  };
}

interface OperatorAlarmBarEditorProps {
  value: OperatorAlarmBarConfig | undefined;
  onChange: (value: OperatorAlarmBarConfig) => void;
  disabled?: boolean;
  dashboardPaths: string[];
}

export default function OperatorAlarmBarEditor({
  value,
  onChange,
  disabled,
  dashboardPaths,
}: OperatorAlarmBarEditorProps) {
  const config: OperatorAlarmBarConfig = value ?? { enabled: true, rules: [] };
  const rules = config.rules ?? [];

  const patch = (partial: Partial<OperatorAlarmBarConfig>) => {
    onChange({ ...config, ...partial });
  };

  const patchRule = (index: number, partial: Partial<OperatorAlarmRule>) => {
    const next = rules.map((rule, idx) => (idx === index ? { ...rule, ...partial } : rule));
    patch({ rules: next });
  };

  const addRule = () => {
    patch({ rules: [...rules, newRule(rules.length + 1)] });
  };

  const removeRule = (index: number) => {
    patch({ rules: rules.filter((_, idx) => idx !== index) });
  };

  return (
    <div className="operator-alarm-bar-editor full">
      <strong>Тревожная планка</strong>
      <p className="hint">
        Всплывающая полоса со звуком в operator mode при срабатывании событий.
      </p>

      <label className="operator-alarm-bar-toggle">
        <input
          type="checkbox"
          checked={config.enabled !== false}
          disabled={disabled}
          onChange={(e) => patch({ enabled: e.target.checked })}
        />
        Включить тревожную планку
      </label>

      <div className="form-grid compact">
        <label>
          Мин. уровень (глобально)
          <select
            value={config.minLevel ?? "ERROR"}
            disabled={disabled}
            onChange={(e) => patch({ minLevel: e.target.value as EventLevel })}
          >
            {EVENT_LEVELS.map((level) => (
              <option key={level} value={level}>
                {level}
              </option>
            ))}
          </select>
        </label>
        <label>
          Позиция
          <select
            value={config.position ?? "top"}
            disabled={disabled}
            onChange={(e) => patch({ position: e.target.value as "top" | "bottom" })}
          >
            <option value="top">Сверху</option>
            <option value="bottom">Снизу</option>
          </select>
        </label>
        <label>
          URL звука
          <input
            value={config.soundUrl ?? "/sounds/alarm.wav"}
            disabled={disabled}
            onChange={(e) => patch({ soundUrl: e.target.value })}
          />
        </label>
        <label>
          Повтор звука (мс)
          <input
            type="number"
            min={1000}
            step={500}
            value={config.soundRepeatMs ?? 3000}
            disabled={disabled}
            onChange={(e) => patch({ soundRepeatMs: Number(e.target.value) })}
          />
        </label>
        <label className="operator-alarm-bar-toggle">
          <input
            type="checkbox"
            checked={config.soundEnabled !== false}
            disabled={disabled}
            onChange={(e) => patch({ soundEnabled: e.target.checked })}
          />
          Звук включён
        </label>
      </div>

      <div className="operator-alarm-bar-rules">
        <div className="operator-alarm-bar-rules-head">
          <strong>Правила отображения</strong>
          {!disabled && (
            <button type="button" className="btn small" onClick={addRule}>
              + Правило
            </button>
          )}
        </div>
        {rules.length === 0 && (
          <p className="hint">Правила не заданы — используется правило по умолчанию (ERROR+).</p>
        )}
        {rules.map((rule, index) => (
          <div key={rule.id} className="operator-alarm-bar-rule-card">
            <div className="operator-alarm-bar-rule-head">
              <strong>{rule.id}</strong>
              {!disabled && (
                <button type="button" className="btn small" onClick={() => removeRule(index)}>
                  Удалить
                </button>
              )}
            </div>
            <div className="form-grid compact">
              <label>
                ID правила
                <input
                  value={rule.id}
                  disabled={disabled}
                  onChange={(e) => patchRule(index, { id: e.target.value })}
                />
              </label>
              <label>
                Имена событий (через запятую)
                <input
                  value={rule.eventNames?.join(", ") ?? ""}
                  disabled={disabled}
                  placeholder="fireAlarm, gasLeak"
                  onChange={(e) =>
                    patchRule(index, {
                      eventNames: e.target.value
                        .split(",")
                        .map((item) => item.trim())
                        .filter(Boolean),
                    })
                  }
                />
              </label>
              <label>
                Префикс objectPath
                <input
                  value={rule.objectPathPrefix ?? ""}
                  disabled={disabled}
                  onChange={(e) => patchRule(index, { objectPathPrefix: e.target.value || undefined })}
                />
              </label>
              <label>
                Мин. уровень
                <select
                  value={rule.minLevel ?? ""}
                  disabled={disabled}
                  onChange={(e) =>
                    patchRule(index, {
                      minLevel: e.target.value ? (e.target.value as EventLevel) : undefined,
                    })
                  }
                >
                  <option value="">(глобальный)</option>
                  {EVENT_LEVELS.map((level) => (
                    <option key={level} value={level}>
                      {level}
                    </option>
                  ))}
                </select>
              </label>
              <label className="full">
                Заголовок (шаблон)
                <input
                  value={rule.title ?? ""}
                  disabled={disabled}
                  placeholder="{{eventName}}"
                  onChange={(e) => patchRule(index, { title: e.target.value || undefined })}
                />
              </label>
              <label>
                Дашборд
                <select
                  value={rule.actions?.dashboardPath ?? ""}
                  disabled={disabled}
                  onChange={(e) =>
                    patchRule(index, {
                      actions: { ...rule.actions, dashboardPath: e.target.value || undefined },
                    })
                  }
                >
                  <option value="">(авто)</option>
                  {dashboardPaths.map((path) => (
                    <option key={path} value={path}>
                      {path}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                ctx-ключ объекта
                <input
                  value={rule.actions?.selectionKey ?? "objectPath"}
                  disabled={disabled}
                  onChange={(e) =>
                    patchRule(index, {
                      actions: { ...rule.actions, selectionKey: e.target.value || undefined },
                    })
                  }
                />
              </label>
              <label>
                Функция подтверждения
                <input
                  value={rule.actions?.acknowledgeFunction ?? ""}
                  disabled={disabled}
                  placeholder="acknowledgeAlarm"
                  onChange={(e) =>
                    patchRule(index, {
                      actions: { ...rule.actions, acknowledgeFunction: e.target.value || undefined },
                    })
                  }
                />
              </label>
              <label>
                Цвет фона
                <input
                  type="color"
                  value={rule.colors?.background ?? "#450a0a"}
                  disabled={disabled}
                  onChange={(e) =>
                    patchRule(index, { colors: { ...rule.colors, background: e.target.value } })
                  }
                />
              </label>
              <label>
                Цвет текста
                <input
                  type="color"
                  value={rule.colors?.text ?? "#fee2e2"}
                  disabled={disabled}
                  onChange={(e) =>
                    patchRule(index, { colors: { ...rule.colors, text: e.target.value } })
                  }
                />
              </label>
              <label>
                Цвет рамки
                <input
                  type="color"
                  value={rule.colors?.border ?? "#ef4444"}
                  disabled={disabled}
                  onChange={(e) =>
                    patchRule(index, { colors: { ...rule.colors, border: e.target.value } })
                  }
                />
              </label>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}
