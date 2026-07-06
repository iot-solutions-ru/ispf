import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import {
  BEHAVIOR_TYPES,
  defaultBehavior,
  extractSvgTargetIds,
  syncBindingSchemaFromBehaviors,
  type BehaviorType,
} from "../../scada/symbolBehaviors";
import type { MimicBindingSlot, MimicSymbolBehavior } from "../../types/scadaMimic";

interface SymbolBehaviorsEditorProps {
  svg: string;
  behaviors?: MimicSymbolBehavior[];
  bindingSchema?: MimicBindingSlot[];
  onChange: (patch: { behaviors: MimicSymbolBehavior[]; bindingSchema: MimicBindingSlot[] }) => void;
}

export default function SymbolBehaviorsEditor({
  svg,
  behaviors = [],
  bindingSchema = [],
  onChange,
}: SymbolBehaviorsEditorProps) {
  const { t } = useTranslation("scada");
  const targetIds = useMemo(() => extractSvgTargetIds(svg), [svg]);
  const listId = useMemo(() => `scada-behavior-targets-${Math.random().toString(36).slice(2, 9)}`, []);

  const commit = (next: MimicSymbolBehavior[]) => {
    onChange({
      behaviors: next,
      bindingSchema: syncBindingSchemaFromBehaviors(next, bindingSchema),
    });
  };

  const updateAt = (index: number, patch: Partial<MimicSymbolBehavior>) => {
    const next = behaviors.map((b, i) => (i === index ? ({ ...b, ...patch } as MimicSymbolBehavior) : b));
    commit(next);
  };

  const changeType = (index: number, type: BehaviorType) => {
    const prev = behaviors[index];
    const fresh = defaultBehavior(type);
    const next = behaviors.map((b, i) =>
      i === index
        ? {
            ...fresh,
            bind: prev?.bind || fresh.bind,
            target: prev?.target || fresh.target,
          }
        : b
    );
    commit(next);
  };

  const removeAt = (index: number) => {
    commit(behaviors.filter((_, i) => i !== index));
  };

  const addBehavior = () => {
    commit([...behaviors, defaultBehavior("fill")]);
  };

  return (
    <div className="scada-props-section scada-behaviors-editor">
      <div className="scada-props-section-head">
        <h3 className="scada-props-section-title">{t("props.behaviors")}</h3>
        <button type="button" className="scada-btn-ghost scada-btn-compact" onClick={addBehavior}>
          {t("props.behaviorsAdd")}
        </button>
      </div>

      <p className="scada-props-hint scada-props-hint-compact">{t("props.behaviorsHint")}</p>

      {behaviors.length === 0 ? (
        <p className="scada-props-hint scada-props-hint-compact">{t("props.behaviorsEmpty")}</p>
      ) : (
        behaviors.map((behavior, index) => (
          <div key={index} className="scada-binding-slot scada-behavior-card">
            <div className="scada-behavior-card-head">
              <strong className="scada-binding-slot-title">
                {t(`behaviorTypes.${behavior.type}`)} · {behavior.bind || "—"}
              </strong>
              <button
                type="button"
                className="scada-btn-ghost scada-btn-compact scada-btn-danger-text"
                onClick={() => removeAt(index)}
              >
                {t("props.behaviorsRemove")}
              </button>
            </div>

            <div className="scada-form-row">
              <label className="scada-form-field scada-form-field-half">
                <span className="scada-form-label">{t("props.behaviorType")}</span>
                <select
                  className="scada-form-input"
                  value={behavior.type}
                  onChange={(e) => changeType(index, e.target.value as BehaviorType)}
                >
                  {BEHAVIOR_TYPES.map((type) => (
                    <option key={type} value={type}>
                      {t(`behaviorTypes.${type}`)}
                    </option>
                  ))}
                </select>
              </label>
              <label className="scada-form-field scada-form-field-half">
                <span className="scada-form-label">{t("props.behaviorBind")}</span>
                <input
                  type="text"
                  className="scada-form-input mono"
                  spellCheck={false}
                  value={behavior.bind}
                  onChange={(e) => updateAt(index, { bind: e.target.value.trim() })}
                />
              </label>
            </div>

            <label className="scada-form-field">
              <span className="scada-form-label">{t("props.behaviorTarget")}</span>
              <input
                type="text"
                className="scada-form-input mono"
                list={listId}
                spellCheck={false}
                placeholder="#ispf-label"
                value={behavior.target}
                onChange={(e) => updateAt(index, { target: e.target.value })}
              />
            </label>

            {(behavior.type === "visibility" ||
              behavior.type === "hidden" ||
              behavior.type === "blink") && (
              <label className="scada-form-field">
                <span className="scada-form-label">{t("props.behaviorWhen")}</span>
                <select
                  className="scada-form-input"
                  value={behavior.when ?? "truthy"}
                  onChange={(e) =>
                    updateAt(index, { when: e.target.value as "truthy" | "falsy" })
                  }
                >
                  <option value="truthy">{t("props.behaviorWhenTruthy")}</option>
                  <option value="falsy">{t("props.behaviorWhenFalsy")}</option>
                </select>
              </label>
            )}

            {(behavior.type === "fill" || behavior.type === "stroke") && (
              <div className="scada-form-row">
                <label className="scada-form-field scada-form-field-half">
                  <span className="scada-form-label">{t("props.behaviorTrueColor")}</span>
                  <input
                    type="text"
                    className="scada-form-input mono"
                    value={behavior.trueColor ?? ""}
                    onChange={(e) => updateAt(index, { trueColor: e.target.value })}
                  />
                </label>
                <label className="scada-form-field scada-form-field-half">
                  <span className="scada-form-label">{t("props.behaviorFalseColor")}</span>
                  <input
                    type="text"
                    className="scada-form-input mono"
                    value={behavior.falseColor ?? ""}
                    onChange={(e) => updateAt(index, { falseColor: e.target.value })}
                  />
                </label>
              </div>
            )}

            {behavior.type === "text" && (
              <>
                <div className="scada-form-row">
                  <label className="scada-form-field scada-form-field-half">
                    <span className="scada-form-label">{t("props.behaviorFormat")}</span>
                    <select
                      className="scada-form-input"
                      value={behavior.format ?? "string"}
                      onChange={(e) =>
                        updateAt(index, { format: e.target.value as "string" | "number" })
                      }
                    >
                      <option value="string">string</option>
                      <option value="number">number</option>
                    </select>
                  </label>
                  <label className="scada-form-field scada-form-field-half">
                    <span className="scada-form-label">{t("props.behaviorSuffix")}</span>
                    <input
                      type="text"
                      className="scada-form-input mono"
                      value={behavior.suffix ?? ""}
                      onChange={(e) => updateAt(index, { suffix: e.target.value })}
                    />
                  </label>
                </div>
                <div className="scada-form-row">
                  <label className="scada-form-field scada-form-field-half">
                    <span className="scada-form-label">{t("props.behaviorDecimals")}</span>
                    <input
                      type="number"
                      className="scada-form-input"
                      min={0}
                      max={6}
                      value={behavior.decimals ?? ""}
                      onChange={(e) =>
                        updateAt(index, {
                          decimals: e.target.value === "" ? undefined : Number(e.target.value),
                        })
                      }
                    />
                  </label>
                  <label className="scada-form-field scada-form-field-half">
                    <span className="scada-form-label">{t("props.behaviorQualityBind")}</span>
                    <input
                      type="text"
                      className="scada-form-input mono"
                      spellCheck={false}
                      placeholder="valueQuality"
                      value={behavior.qualityBind ?? ""}
                      onChange={(e) => updateAt(index, { qualityBind: e.target.value || undefined })}
                    />
                  </label>
                </div>
              </>
            )}

            {behavior.type === "fillLevel" && (
              <div className="scada-form-row">
                <label className="scada-form-field scada-form-field-half">
                  <span className="scada-form-label">{t("props.behaviorMaxBind")}</span>
                  <input
                    type="text"
                    className="scada-form-input mono"
                    spellCheck={false}
                    value={behavior.maxBind ?? ""}
                    onChange={(e) => updateAt(index, { maxBind: e.target.value || undefined })}
                  />
                </label>
                <label className="scada-form-field scada-form-field-half">
                  <span className="scada-form-label">{t("props.behaviorInset")}</span>
                  <input
                    type="number"
                    className="scada-form-input"
                    min={0}
                    value={behavior.inset ?? ""}
                    onChange={(e) =>
                      updateAt(index, {
                        inset: e.target.value === "" ? undefined : Number(e.target.value),
                      })
                    }
                  />
                </label>
              </div>
            )}
          </div>
        ))
      )}

      {targetIds.length > 0 && (
        <datalist id={listId}>
          {targetIds.map((id) => (
            <option key={id} value={id} />
          ))}
        </datalist>
      )}
    </div>
  );
}
