import { Alert, Button, Form, Input, Select } from "antd";
import { useTranslation } from "react-i18next";
import type {
  MimicAction,
  MimicConnection,
  MimicCustomSymbol,
  MimicElement,
  MimicFormatRule,
  MimicFormatOperator,
  ScadaMimicDocument,
} from "../../types/scadaMimic";
import { createMimicId } from "../../scada/document";
import { resolveElementSymbol, symbolSize } from "../../scada/symbols/registry";
import CustomSvgEditor, { supportsSvgMarkupEditor } from "./CustomSvgEditor";
import MimicBindingSlotEditor from "./MimicBindingSlotEditor";

interface MimicPropertiesPanelProps {
  document: ScadaMimicDocument;
  selectedElement: MimicElement | null;
  selectedConnection: MimicConnection | null;
  onUpdateElement: (element: MimicElement) => void;
  onUpdateConnection: (connection: MimicConnection) => void;
  onUpdateCanvasSize: (width: number, height: number) => void;
  onDeleteSelected: () => void;
  onAddCustomSymbol: (def: MimicCustomSymbol) => void;
  onUpdateCustomSymbol?: (id: string, patch: Partial<MimicCustomSymbol>) => void;
  onUpdateCustomSymbols?: (symbols: MimicCustomSymbol[]) => void;
}

const ACTION_TYPES: MimicAction["type"][] = [
  "invokeFunction",
  "setVariable",
  "toggleVariable",
  "navigate",
  "toggleLayer",
  "cycleUnit",
  "toggleExpand",
];

const FORMAT_OPS: MimicFormatOperator[] = [">", ">=", "<", "<=", "==", "!="];

function fieldClassName(className?: string) {
  return ["scada-form-field", className].filter(Boolean).join(" ");
}

function MimicActionFields({
  action,
  document,
  onChange,
}: {
  action: MimicAction;
  document: ScadaMimicDocument;
  onChange: (patch: Partial<MimicAction>) => void;
}) {
  const { t } = useTranslation("scada");

  return (
    <>
      <Form.Item className={fieldClassName()} label={t("props.actionType")}>
        <Select
          className="scada-form-input"
          value={action.type}
          onChange={(value) => onChange({ type: value })}
          options={ACTION_TYPES.map((type) => ({ value: type, label: type }))}
        />
      </Form.Item>
      <Form.Item className={fieldClassName()} label={t("props.actionTrigger")}>
        <Select
          className="scada-form-input"
          value={action.trigger ?? "primary"}
          onChange={(value) => onChange({ trigger: value as MimicAction["trigger"] })}
          options={[
            { value: "primary", label: t("props.triggerPrimary") },
            { value: "context", label: t("props.triggerContext") },
          ]}
        />
      </Form.Item>
      {action.trigger === "context" && (
        <>
          <Form.Item className={fieldClassName()} label={t("props.actionLabel")}>
            <Input
              className="scada-form-input"
              value={action.label ?? ""}
              onChange={(e) => onChange({ label: e.target.value })}
            />
          </Form.Item>
          <Form.Item className={fieldClassName()} label={t("props.actionOrder")}>
            <Input
              type="number"
              className="scada-form-input"
              value={action.order ?? 0}
              onChange={(e) => onChange({ order: Number(e.target.value) })}
            />
          </Form.Item>
        </>
      )}
      {(action.type === "setVariable" ||
        action.type === "toggleVariable" ||
        action.type === "invokeFunction") && (
        <>
          <Form.Item className={fieldClassName()} label="objectPath">
            <Input
              className="scada-form-input mono"
              spellCheck={false}
              value={action.objectPath ?? ""}
              onChange={(e) => onChange({ objectPath: e.target.value })}
            />
          </Form.Item>
          {action.type === "invokeFunction" ? (
            <Form.Item className={fieldClassName()} label="functionName">
              <Input
                className="scada-form-input mono"
                spellCheck={false}
                value={action.functionName ?? ""}
                onChange={(e) => onChange({ functionName: e.target.value })}
              />
            </Form.Item>
          ) : (
            <>
              <Form.Item className={fieldClassName()} label="variableName">
                <Input
                  className="scada-form-input mono"
                  spellCheck={false}
                  value={action.variableName ?? ""}
                  onChange={(e) => onChange({ variableName: e.target.value })}
                />
              </Form.Item>
              {action.type === "setVariable" && (
                <Form.Item className={fieldClassName()} label="value">
                  <Input
                    className="scada-form-input mono"
                    value={action.value == null ? "" : String(action.value)}
                    onChange={(e) => onChange({ value: e.target.value })}
                  />
                </Form.Item>
              )}
            </>
          )}
        </>
      )}
      {action.type === "navigate" && (
        <>
          <Form.Item className={fieldClassName()} label="dashboardPath">
            <Input
              className="scada-form-input mono"
              spellCheck={false}
              value={action.dashboardPath ?? ""}
              onChange={(e) => onChange({ dashboardPath: e.target.value })}
            />
          </Form.Item>
          <Form.Item className={fieldClassName()} label="mimicPath">
            <Input
              className="scada-form-input mono"
              spellCheck={false}
              value={action.mimicPath ?? ""}
              onChange={(e) => onChange({ mimicPath: e.target.value })}
            />
          </Form.Item>
          <Form.Item className={fieldClassName()} label="url">
            <Input
              className="scada-form-input mono"
              spellCheck={false}
              value={action.url ?? ""}
              onChange={(e) => onChange({ url: e.target.value })}
            />
          </Form.Item>
        </>
      )}
      {action.type === "toggleLayer" && (
        <Form.Item className={fieldClassName()} label={t("props.layer")}>
          <Select
            className="scada-form-input"
            value={action.layerId ?? ""}
            onChange={(value) => onChange({ layerId: value })}
            options={[
              { value: "", label: "—" },
              ...document.layers.map((layer) => ({ value: layer.id, label: layer.name })),
            ]}
          />
        </Form.Item>
      )}
      {action.type === "cycleUnit" && (
        <Form.Item className={fieldClassName()} label={t("props.unitModes")}>
          <Input
            className="scada-form-input mono"
            placeholder="mm, m3, t"
            value={(action.unitModes ?? []).join(", ")}
            onChange={(e) =>
              onChange({
                unitModes: e.target.value
                  .split(",")
                  .map((s) => s.trim())
                  .filter(Boolean),
              })
            }
          />
        </Form.Item>
      )}
      {action.type === "toggleExpand" && (
        <Form.Item className={fieldClassName()} label={t("props.expandProp")}>
          <Input
            className="scada-form-input mono"
            placeholder="tableExpand"
            value={action.expandProp ?? ""}
            onChange={(e) => onChange({ expandProp: e.target.value })}
          />
        </Form.Item>
      )}
    </>
  );
}

export default function MimicPropertiesPanel({
  document,
  selectedElement,
  selectedConnection,
  onUpdateElement,
  onUpdateConnection,
  onUpdateCanvasSize,
  onDeleteSelected,
  onAddCustomSymbol,
  onUpdateCustomSymbol,
  onUpdateCustomSymbols,
}: MimicPropertiesPanelProps) {
  const { t } = useTranslation("scada");

  const canvasSection = (
    <div className="scada-props-section scada-props-section-first">
      <h3 className="scada-props-section-title">{t("props.canvas")}</h3>
      <div className="scada-form-row">
        <Form.Item className={fieldClassName("scada-form-field-half")} label={t("props.canvasWidth")}>
          <Input
            type="number"
            className="scada-form-input"
            min={100}
            max={10000}
            step={1}
            value={document.width}
            onChange={(e) => onUpdateCanvasSize(Number(e.target.value), document.height)}
          />
        </Form.Item>
        <Form.Item className={fieldClassName("scada-form-field-half")} label={t("props.canvasHeight")}>
          <Input
            type="number"
            className="scada-form-input"
            min={100}
            max={10000}
            step={1}
            value={document.height}
            onChange={(e) => onUpdateCanvasSize(document.width, Number(e.target.value))}
          />
        </Form.Item>
      </div>
      <div className="scada-stat-grid scada-stat-grid-compact">
        <div className="scada-stat-card">
          <span className="scada-stat-label">{t("props.elements")}</span>
          <span className="scada-stat-value">{document.elements.length}</span>
        </div>
        <div className="scada-stat-card">
          <span className="scada-stat-label">{t("props.connections")}</span>
          <span className="scada-stat-value">{document.connections.length}</span>
        </div>
      </div>
    </div>
  );

  if (!selectedElement && !selectedConnection) {
    return (
      <div className="scada-props-panel">
        <div className="scada-panel-header">
          <h2 className="scada-panel-title">{t("props.title")}</h2>
        </div>
        {canvasSection}
        <Alert className="scada-props-hint" type="info" showIcon={false} message={t("props.selectHint")} />
      </div>
    );
  }

  if (selectedConnection) {
    return (
      <div className="scada-props-panel">
        <div className="scada-panel-header">
          <h2 className="scada-panel-title">{t("props.connection")}</h2>
        </div>
        {canvasSection}
        <div className="scada-props-section">
          <Form.Item className={fieldClassName()} label={t("props.strokeWidth")}>
            <Input
              type="number"
              className="scada-form-input"
              min={1}
              max={12}
              value={selectedConnection.style?.strokeWidth ?? 3}
              onChange={(e) =>
                onUpdateConnection({
                  ...selectedConnection,
                  style: { ...selectedConnection.style, strokeWidth: Number(e.target.value) },
                })
              }
            />
          </Form.Item>
        </div>
        <Button danger block className="scada-btn-danger scada-btn-block" onClick={onDeleteSelected}>
          {t("props.delete")}
        </Button>
      </div>
    );
  }

  if (!selectedElement) return null;
  const symbol = resolveElementSymbol(selectedElement, document.customSymbols);
  const actions = selectedElement.actions ?? [];
  const formatRules = selectedElement.formatRules ?? [];
  const bindingKeys = Object.keys(selectedElement.bindings);

  const updateAction = (index: number, patch: Partial<MimicAction>) => {
    const next = [...actions];
    next[index] = { ...next[index], ...patch };
    onUpdateElement({ ...selectedElement, actions: next });
  };

  const addAction = () => {
    onUpdateElement({
      ...selectedElement,
      actions: [
        ...actions,
        { id: createMimicId("act"), type: "invokeFunction", trigger: "primary", functionName: "" },
      ],
    });
  };

  const removeAction = (index: number) => {
    const next = actions.filter((_, i) => i !== index);
    onUpdateElement({ ...selectedElement, actions: next.length ? next : undefined });
  };

  const updateFormatRule = (index: number, patch: Partial<MimicFormatRule>) => {
    const next = [...formatRules];
    next[index] = { ...next[index], ...patch };
    onUpdateElement({ ...selectedElement, formatRules: next });
  };

  const addFormatRule = () => {
    const key = bindingKeys[0] ?? "value";
    onUpdateElement({
      ...selectedElement,
      formatRules: [
        ...formatRules,
        {
          id: createMimicId("fmt"),
          bindingKey: key,
          operator: ">",
          value: 0,
          style: { fill: "#ff0000" },
        },
      ],
    });
  };

  const removeFormatRule = (index: number) => {
    const next = formatRules.filter((_, i) => i !== index);
    onUpdateElement({ ...selectedElement, formatRules: next.length ? next : undefined });
  };

  return (
    <div className="scada-props-panel">
      <div className="scada-panel-header">
        <h2 className="scada-panel-title">
          {symbol?.displayName ?? (symbol ? t(symbol.nameKey) : selectedElement.symbolId)}
        </h2>
      </div>

      {canvasSection}

      {supportsSvgMarkupEditor(selectedElement) && (
        <CustomSvgEditor
          element={selectedElement}
          customSymbols={document.customSymbols}
          onUpdateElement={onUpdateElement}
          onAddCustomSymbol={onAddCustomSymbol}
          onUpdateCustomSymbol={onUpdateCustomSymbol}
          onUpdateCustomSymbols={onUpdateCustomSymbols}
        />
      )}

      <div className="scada-props-section">
        <h3 className="scada-props-section-title">{t("props.layout")}</h3>
        <div className="scada-form-row">
          <Form.Item className={fieldClassName("scada-form-field-half")} label="X">
            <Input
              type="number"
              className="scada-form-input"
              step={1}
              value={selectedElement.x}
              onChange={(e) => onUpdateElement({ ...selectedElement, x: Number(e.target.value) })}
            />
          </Form.Item>
          <Form.Item className={fieldClassName("scada-form-field-half")} label="Y">
            <Input
              type="number"
              className="scada-form-input"
              step={1}
              value={selectedElement.y}
              onChange={(e) => onUpdateElement({ ...selectedElement, y: Number(e.target.value) })}
            />
          </Form.Item>
        </div>
        <div className="scada-form-row">
          <Form.Item className={fieldClassName("scada-form-field-half")} label={t("props.sizeWidth")}>
            <Input
              type="number"
              className="scada-form-input"
              min={16}
              step={1}
              value={Math.round(symbolSize(selectedElement, document.customSymbols).width)}
              onChange={(e) => {
                const { height } = symbolSize(selectedElement, document.customSymbols);
                onUpdateElement({
                  ...selectedElement,
                  scale: 1,
                  props: { ...(selectedElement.props ?? {}), width: Number(e.target.value), height },
                });
              }}
            />
          </Form.Item>
          <Form.Item className={fieldClassName("scada-form-field-half")} label={t("props.sizeHeight")}>
            <Input
              type="number"
              className="scada-form-input"
              min={16}
              step={1}
              value={Math.round(symbolSize(selectedElement, document.customSymbols).height)}
              onChange={(e) => {
                const { width } = symbolSize(selectedElement, document.customSymbols);
                onUpdateElement({
                  ...selectedElement,
                  scale: 1,
                  props: { ...(selectedElement.props ?? {}), width, height: Number(e.target.value) },
                });
              }}
            />
          </Form.Item>
        </div>
        <Form.Item className={fieldClassName("scada-form-field-checkbox")}>
          <input
            type="checkbox"
            checked={Boolean(selectedElement.lockAspectRatio)}
            onChange={(e) =>
              onUpdateElement({ ...selectedElement, lockAspectRatio: e.target.checked || undefined })
            }
          />
          <span className="scada-form-label">{t("props.lockAspectRatio")}</span>
        </Form.Item>
        <Form.Item className={fieldClassName()} label={t("props.rotation")}>
          <Select
            className="scada-form-input"
            value={selectedElement.rotation ?? 0}
            onChange={(value) =>
              onUpdateElement({
                ...selectedElement,
                rotation: value as MimicElement["rotation"],
              })
            }
            options={[0, 90, 180, 270].map((r) => ({ value: r, label: `${r}°` }))}
          />
        </Form.Item>
        <Form.Item className={fieldClassName()} label={t("props.layer")}>
          <Select
            className="scada-form-input"
            value={selectedElement.layerId}
            onChange={(value) => onUpdateElement({ ...selectedElement, layerId: value })}
            options={document.layers.map((layer) => ({ value: layer.id, label: layer.name }))}
          />
        </Form.Item>
      </div>

      <div className="scada-props-section">
        <h3 className="scada-props-section-title">{t("props.tooltip")}</h3>
        <Form.Item className={fieldClassName()} label={t("props.tooltipTemplate")}>
          <Input
            className="scada-form-input mono"
            placeholder="{label}: {value}"
            value={selectedElement.tooltip?.template ?? ""}
            onChange={(e) =>
              onUpdateElement({
                ...selectedElement,
                tooltip: e.target.value.trim()
                  ? { ...selectedElement.tooltip, template: e.target.value }
                  : undefined,
              })
            }
          />
        </Form.Item>
      </div>

      {(symbol?.bindingSchema ?? []).length > 0 && (
        <div className="scada-props-section">
          <h3 className="scada-props-section-title">{t("props.bindings")}</h3>
          {(symbol?.bindingSchema ?? []).map((slot) => (
            <MimicBindingSlotEditor
              key={slot.key}
              title={t(slot.labelKey)}
              binding={selectedElement.bindings[slot.key]}
              onUpdate={(patch) => {
                const bindings = { ...selectedElement.bindings };
                const current = bindings[slot.key] ?? { variableName: "" };
                bindings[slot.key] = { ...current, ...patch };
                onUpdateElement({ ...selectedElement, bindings });
              }}
            />
          ))}
        </div>
      )}

      <div className="scada-props-section">
        <h3 className="scada-props-section-title">{t("props.formatRules")}</h3>
        {formatRules.length === 0 ? (
          <Alert
            className="scada-props-hint scada-props-hint-compact"
            type="info"
            showIcon={false}
            message={t("props.formatRulesEmpty")}
          />
        ) : (
          formatRules.map((rule, index) => (
            <div key={rule.id} className="scada-binding-slot">
              <Form.Item className={fieldClassName()} label={t("props.formatBindingKey")}>
                <Input
                  className="scada-form-input mono"
                  value={rule.bindingKey}
                  onChange={(e) => updateFormatRule(index, { bindingKey: e.target.value })}
                />
              </Form.Item>
              <div className="scada-form-row">
                <Form.Item className={fieldClassName("scada-form-field-half")} label={t("props.formatOperator")}>
                  <Select
                    className="scada-form-input"
                    value={rule.operator}
                    onChange={(value) => updateFormatRule(index, { operator: value })}
                    options={FORMAT_OPS.map((op) => ({ value: op, label: op }))}
                  />
                </Form.Item>
                <Form.Item className={fieldClassName("scada-form-field-half")} label={t("props.formatValue")}>
                  <Input
                    className="scada-form-input mono"
                    value={String(rule.value)}
                    onChange={(e) => updateFormatRule(index, { value: e.target.value })}
                  />
                </Form.Item>
              </div>
              <Form.Item className={fieldClassName()} label={t("props.formatFill")}>
                <Input
                  className="scada-form-input mono"
                  value={rule.style.fill ?? ""}
                  onChange={(e) =>
                    updateFormatRule(index, { style: { ...rule.style, fill: e.target.value } })
                  }
                />
              </Form.Item>
              <Button
                type="text"
                block
                className="scada-btn-ghost scada-btn-block"
                onClick={() => removeFormatRule(index)}
              >
                {t("props.removeFormatRule")}
              </Button>
            </div>
          ))
        )}
        <Button type="text" block className="scada-btn-ghost scada-btn-block" onClick={addFormatRule}>
          {t("props.addFormatRule")}
        </Button>
      </div>

      <div className="scada-props-section">
        <h3 className="scada-props-section-title">{t("props.actions")}</h3>
        {actions.length === 0 ? (
          <Button type="text" block className="scada-btn-ghost scada-btn-block" onClick={addAction}>
            {t("props.addAction")}
          </Button>
        ) : (
          actions.map((action, index) => (
            <div key={action.id} className="scada-binding-slot">
              <MimicActionFields
                action={action}
                document={document}
                onChange={(patch) => updateAction(index, patch)}
              />
              <Button
                type="text"
                block
                className="scada-btn-ghost scada-btn-block"
                onClick={() => removeAction(index)}
              >
                {t("props.removeAction")}
              </Button>
            </div>
          ))
        )}
        {actions.length > 0 && (
          <Button type="text" block className="scada-btn-ghost scada-btn-block" onClick={addAction}>
            {t("props.addAction")}
          </Button>
        )}
      </div>

      <Button danger block className="scada-btn-danger scada-btn-block" onClick={onDeleteSelected}>
        {t("props.delete")}
      </Button>
    </div>
  );
}
