import { Alert, Button, Form, Input, Select, Space } from "antd";
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

function fieldClassName(className?: string) {
  return ["scada-form-field", className].filter(Boolean).join(" ");
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
        <Button type="text" size="small" className="scada-btn-ghost scada-btn-compact" onClick={addBehavior}>
          {t("props.behaviorsAdd")}
        </Button>
      </div>

      <Alert
        className="scada-props-hint scada-props-hint-compact"
        type="info"
        showIcon={false}
        message={t("props.behaviorsHint")}
      />

      {behaviors.length === 0 ? (
        <Alert
          className="scada-props-hint scada-props-hint-compact"
          type="info"
          showIcon={false}
          message={t("props.behaviorsEmpty")}
        />
      ) : (
        behaviors.map((behavior, index) => (
          <div key={index} className="scada-binding-slot scada-behavior-card">
            <Space className="scada-behavior-card-head">
              <strong className="scada-binding-slot-title">
                {t(`behaviorTypes.${behavior.type}`)} · {behavior.bind || "—"}
              </strong>
              <Button
                type="text"
                size="small"
                className="scada-btn-ghost scada-btn-compact scada-btn-danger-text"
                danger
                onClick={() => removeAt(index)}
              >
                {t("props.behaviorsRemove")}
              </Button>
            </Space>

            <div className="scada-form-row">
              <Form.Item className={fieldClassName("scada-form-field-half")} label={t("props.behaviorType")}>
                <Select
                  className="scada-form-input"
                  value={behavior.type}
                  onChange={(value) => changeType(index, value)}
                  options={BEHAVIOR_TYPES.map((type) => ({
                    value: type,
                    label: t(`behaviorTypes.${type}`),
                  }))}
                />
              </Form.Item>
              <Form.Item className={fieldClassName("scada-form-field-half")} label={t("props.behaviorBind")}>
                <Input
                  className="scada-form-input mono"
                  spellCheck={false}
                  value={behavior.bind}
                  onChange={(e) => updateAt(index, { bind: e.target.value.trim() })}
                />
              </Form.Item>
            </div>

            <Form.Item className={fieldClassName()} label={t("props.behaviorTarget")}>
              <Input
                className="scada-form-input mono"
                list={listId}
                spellCheck={false}
                placeholder="#ispf-label"
                value={behavior.target}
                onChange={(e) => updateAt(index, { target: e.target.value })}
              />
            </Form.Item>

            {(behavior.type === "visibility" ||
              behavior.type === "hidden" ||
              behavior.type === "blink") && (
              <Form.Item className={fieldClassName()} label={t("props.behaviorWhen")}>
                <Select
                  className="scada-form-input"
                  value={behavior.when ?? "truthy"}
                  onChange={(value) => updateAt(index, { when: value as "truthy" | "falsy" })}
                  options={[
                    { value: "truthy", label: t("props.behaviorWhenTruthy") },
                    { value: "falsy", label: t("props.behaviorWhenFalsy") },
                  ]}
                />
              </Form.Item>
            )}

            {(behavior.type === "fill" || behavior.type === "stroke") && (
              <div className="scada-form-row">
                <Form.Item className={fieldClassName("scada-form-field-half")} label={t("props.behaviorTrueColor")}>
                  <Input
                    className="scada-form-input mono"
                    value={behavior.trueColor ?? ""}
                    onChange={(e) => updateAt(index, { trueColor: e.target.value })}
                  />
                </Form.Item>
                <Form.Item className={fieldClassName("scada-form-field-half")} label={t("props.behaviorFalseColor")}>
                  <Input
                    className="scada-form-input mono"
                    value={behavior.falseColor ?? ""}
                    onChange={(e) => updateAt(index, { falseColor: e.target.value })}
                  />
                </Form.Item>
              </div>
            )}

            {behavior.type === "text" && (
              <>
                <div className="scada-form-row">
                  <Form.Item className={fieldClassName("scada-form-field-half")} label={t("props.behaviorFormat")}>
                    <Select
                      className="scada-form-input"
                      value={behavior.format ?? "string"}
                      onChange={(value) => updateAt(index, { format: value as "string" | "number" })}
                      options={[
                        { value: "string", label: "string" },
                        { value: "number", label: "number" },
                      ]}
                    />
                  </Form.Item>
                  <Form.Item className={fieldClassName("scada-form-field-half")} label={t("props.behaviorSuffix")}>
                    <Input
                      className="scada-form-input mono"
                      value={behavior.suffix ?? ""}
                      onChange={(e) => updateAt(index, { suffix: e.target.value })}
                    />
                  </Form.Item>
                </div>
                <div className="scada-form-row">
                  <Form.Item className={fieldClassName("scada-form-field-half")} label={t("props.behaviorDecimals")}>
                    <Input
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
                  </Form.Item>
                  <Form.Item className={fieldClassName("scada-form-field-half")} label={t("props.behaviorQualityBind")}>
                    <Input
                      className="scada-form-input mono"
                      spellCheck={false}
                      placeholder="valueQuality"
                      value={behavior.qualityBind ?? ""}
                      onChange={(e) => updateAt(index, { qualityBind: e.target.value || undefined })}
                    />
                  </Form.Item>
                </div>
              </>
            )}

            {behavior.type === "fillLevel" && (
              <div className="scada-form-row">
                <Form.Item className={fieldClassName("scada-form-field-half")} label={t("props.behaviorMaxBind")}>
                  <Input
                    className="scada-form-input mono"
                    spellCheck={false}
                    value={behavior.maxBind ?? ""}
                    onChange={(e) => updateAt(index, { maxBind: e.target.value || undefined })}
                  />
                </Form.Item>
                <Form.Item className={fieldClassName("scada-form-field-half")} label={t("props.behaviorInset")}>
                  <Input
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
                </Form.Item>
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
