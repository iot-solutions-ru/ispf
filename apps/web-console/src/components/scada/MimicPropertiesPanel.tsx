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
import CustomSvgEditor, { isCustomSvgElement } from "./CustomSvgEditor";

interface MimicPropertiesPanelProps {
  document: ScadaMimicDocument;
  selectedElement: MimicElement | null;
  selectedConnection: MimicConnection | null;
  onUpdateElement: (element: MimicElement) => void;
  onUpdateConnection: (connection: MimicConnection) => void;
  onUpdateCanvasSize: (width: number, height: number) => void;
  onDeleteSelected: () => void;
  onAddCustomSymbol: (def: MimicCustomSymbol) => void;
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
      <label className="scada-form-field">
        <span className="scada-form-label">{t("props.actionType")}</span>
        <select
          className="scada-form-input"
          value={action.type}
          onChange={(e) => onChange({ type: e.target.value as MimicAction["type"] })}
        >
          {ACTION_TYPES.map((type) => (
            <option key={type} value={type}>
              {type}
            </option>
          ))}
        </select>
      </label>
      <label className="scada-form-field">
        <span className="scada-form-label">{t("props.actionTrigger")}</span>
        <select
          className="scada-form-input"
          value={action.trigger ?? "primary"}
          onChange={(e) => onChange({ trigger: e.target.value as MimicAction["trigger"] })}
        >
          <option value="primary">{t("props.triggerPrimary")}</option>
          <option value="context">{t("props.triggerContext")}</option>
        </select>
      </label>
      {action.trigger === "context" && (
        <>
          <label className="scada-form-field">
            <span className="scada-form-label">{t("props.actionLabel")}</span>
            <input
              type="text"
              className="scada-form-input"
              value={action.label ?? ""}
              onChange={(e) => onChange({ label: e.target.value })}
            />
          </label>
          <label className="scada-form-field">
            <span className="scada-form-label">{t("props.actionOrder")}</span>
            <input
              type="number"
              className="scada-form-input"
              value={action.order ?? 0}
              onChange={(e) => onChange({ order: Number(e.target.value) })}
            />
          </label>
        </>
      )}
      {(action.type === "setVariable" ||
        action.type === "toggleVariable" ||
        action.type === "invokeFunction") && (
        <>
          <label className="scada-form-field">
            <span className="scada-form-label">objectPath</span>
            <input
              type="text"
              className="scada-form-input mono"
              spellCheck={false}
              value={action.objectPath ?? ""}
              onChange={(e) => onChange({ objectPath: e.target.value })}
            />
          </label>
          {action.type === "invokeFunction" ? (
            <label className="scada-form-field">
              <span className="scada-form-label">functionName</span>
              <input
                type="text"
                className="scada-form-input mono"
                spellCheck={false}
                value={action.functionName ?? ""}
                onChange={(e) => onChange({ functionName: e.target.value })}
              />
            </label>
          ) : (
            <>
              <label className="scada-form-field">
                <span className="scada-form-label">variableName</span>
                <input
                  type="text"
                  className="scada-form-input mono"
                  spellCheck={false}
                  value={action.variableName ?? ""}
                  onChange={(e) => onChange({ variableName: e.target.value })}
                />
              </label>
              {action.type === "setVariable" && (
                <label className="scada-form-field">
                  <span className="scada-form-label">value</span>
                  <input
                    type="text"
                    className="scada-form-input mono"
                    value={action.value == null ? "" : String(action.value)}
                    onChange={(e) => onChange({ value: e.target.value })}
                  />
                </label>
              )}
            </>
          )}
        </>
      )}
      {action.type === "navigate" && (
        <>
          <label className="scada-form-field">
            <span className="scada-form-label">dashboardPath</span>
            <input
              type="text"
              className="scada-form-input mono"
              spellCheck={false}
              value={action.dashboardPath ?? ""}
              onChange={(e) => onChange({ dashboardPath: e.target.value })}
            />
          </label>
          <label className="scada-form-field">
            <span className="scada-form-label">mimicPath</span>
            <input
              type="text"
              className="scada-form-input mono"
              spellCheck={false}
              value={action.mimicPath ?? ""}
              onChange={(e) => onChange({ mimicPath: e.target.value })}
            />
          </label>
          <label className="scada-form-field">
            <span className="scada-form-label">url</span>
            <input
              type="text"
              className="scada-form-input mono"
              spellCheck={false}
              value={action.url ?? ""}
              onChange={(e) => onChange({ url: e.target.value })}
            />
          </label>
        </>
      )}
      {action.type === "toggleLayer" && (
        <label className="scada-form-field">
          <span className="scada-form-label">{t("props.layer")}</span>
          <select
            className="scada-form-input"
            value={action.layerId ?? ""}
            onChange={(e) => onChange({ layerId: e.target.value })}
          >
            <option value="">—</option>
            {document.layers.map((layer) => (
              <option key={layer.id} value={layer.id}>
                {layer.name}
              </option>
            ))}
          </select>
        </label>
      )}
      {action.type === "cycleUnit" && (
        <label className="scada-form-field">
          <span className="scada-form-label">{t("props.unitModes")}</span>
          <input
            type="text"
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
        </label>
      )}
      {action.type === "toggleExpand" && (
        <label className="scada-form-field">
          <span className="scada-form-label">{t("props.expandProp")}</span>
          <input
            type="text"
            className="scada-form-input mono"
            placeholder="tableExpand"
            value={action.expandProp ?? ""}
            onChange={(e) => onChange({ expandProp: e.target.value })}
          />
        </label>
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
}: MimicPropertiesPanelProps) {
  const { t } = useTranslation("scada");

  const canvasSection = (
    <div className="scada-props-section scada-props-section-first">
      <h3 className="scada-props-section-title">{t("props.canvas")}</h3>
      <div className="scada-form-row">
        <label className="scada-form-field scada-form-field-half">
          <span className="scada-form-label">{t("props.canvasWidth")}</span>
          <input
            type="number"
            className="scada-form-input"
            min={100}
            max={10000}
            step={1}
            value={document.width}
            onChange={(e) => onUpdateCanvasSize(Number(e.target.value), document.height)}
          />
        </label>
        <label className="scada-form-field scada-form-field-half">
          <span className="scada-form-label">{t("props.canvasHeight")}</span>
          <input
            type="number"
            className="scada-form-input"
            min={100}
            max={10000}
            step={1}
            value={document.height}
            onChange={(e) => onUpdateCanvasSize(document.width, Number(e.target.value))}
          />
        </label>
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
        <p className="scada-props-hint">{t("props.selectHint")}</p>
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
          <label className="scada-form-field">
            <span className="scada-form-label">{t("props.strokeWidth")}</span>
            <input
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
          </label>
        </div>
        <button type="button" className="scada-btn-danger scada-btn-block" onClick={onDeleteSelected}>
          {t("props.delete")}
        </button>
      </div>
    );
  }

  if (!selectedElement) return null;
  const symbol = resolveElementSymbol(selectedElement, document.customSymbols);
  const actions = selectedElement.actions ?? [];
  const formatRules = selectedElement.formatRules ?? [];
  const bindingKeys = Object.keys(selectedElement.bindings);

  const updateBinding = (
    key: string,
    field: "objectPath" | "variableName" | "valueField" | "qualityField" | "transform",
    value: string
  ) => {
    const bindings = { ...selectedElement.bindings };
    const current = bindings[key] ?? { variableName: "" };
    if (field === "transform") {
      bindings[key] = {
        ...current,
        transform: value ? (value as "bool" | "number" | "string") : undefined,
      };
    } else {
      bindings[key] = { ...current, [field]: value };
    }
    onUpdateElement({ ...selectedElement, bindings });
  };

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

      {isCustomSvgElement(selectedElement) && (
        <CustomSvgEditor
          element={selectedElement}
          customSymbols={document.customSymbols}
          onUpdateElement={onUpdateElement}
          onAddCustomSymbol={onAddCustomSymbol}
        />
      )}

      <div className="scada-props-section">
        <h3 className="scada-props-section-title">{t("props.layout")}</h3>
        <div className="scada-form-row">
          <label className="scada-form-field scada-form-field-half">
            <span className="scada-form-label">X</span>
            <input
              type="number"
              className="scada-form-input"
              step={1}
              value={selectedElement.x}
              onChange={(e) => onUpdateElement({ ...selectedElement, x: Number(e.target.value) })}
            />
          </label>
          <label className="scada-form-field scada-form-field-half">
            <span className="scada-form-label">Y</span>
            <input
              type="number"
              className="scada-form-input"
              step={1}
              value={selectedElement.y}
              onChange={(e) => onUpdateElement({ ...selectedElement, y: Number(e.target.value) })}
            />
          </label>
        </div>
        <div className="scada-form-row">
          <label className="scada-form-field scada-form-field-half">
            <span className="scada-form-label">{t("props.sizeWidth")}</span>
            <input
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
          </label>
          <label className="scada-form-field scada-form-field-half">
            <span className="scada-form-label">{t("props.sizeHeight")}</span>
            <input
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
          </label>
        </div>
        <label className="scada-form-field scada-form-field-checkbox">
          <input
            type="checkbox"
            checked={Boolean(selectedElement.lockAspectRatio)}
            onChange={(e) =>
              onUpdateElement({ ...selectedElement, lockAspectRatio: e.target.checked || undefined })
            }
          />
          <span className="scada-form-label">{t("props.lockAspectRatio")}</span>
        </label>
        <label className="scada-form-field">
          <span className="scada-form-label">{t("props.rotation")}</span>
          <select
            className="scada-form-input"
            value={selectedElement.rotation ?? 0}
            onChange={(e) =>
              onUpdateElement({
                ...selectedElement,
                rotation: Number(e.target.value) as MimicElement["rotation"],
              })
            }
          >
            {[0, 90, 180, 270].map((r) => (
              <option key={r} value={r}>
                {r}°
              </option>
            ))}
          </select>
        </label>
        <label className="scada-form-field">
          <span className="scada-form-label">{t("props.layer")}</span>
          <select
            className="scada-form-input"
            value={selectedElement.layerId}
            onChange={(e) => onUpdateElement({ ...selectedElement, layerId: e.target.value })}
          >
            {document.layers.map((layer) => (
              <option key={layer.id} value={layer.id}>
                {layer.name}
              </option>
            ))}
          </select>
        </label>
      </div>

      <div className="scada-props-section">
        <h3 className="scada-props-section-title">{t("props.tooltip")}</h3>
        <label className="scada-form-field">
          <span className="scada-form-label">{t("props.tooltipTemplate")}</span>
          <input
            type="text"
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
        </label>
      </div>

      {(symbol?.bindingSchema ?? []).length > 0 && (
        <div className="scada-props-section">
          <h3 className="scada-props-section-title">{t("props.bindings")}</h3>
          {(symbol?.bindingSchema ?? []).map((slot) => (
            <div key={slot.key} className="scada-binding-slot">
              <strong className="scada-binding-slot-title">{t(slot.labelKey)}</strong>
              <label className="scada-form-field">
                <span className="scada-form-label">objectPath</span>
                <input
                  type="text"
                  className="scada-form-input mono"
                  spellCheck={false}
                  value={selectedElement.bindings[slot.key]?.objectPath ?? ""}
                  onChange={(e) => updateBinding(slot.key, "objectPath", e.target.value)}
                />
              </label>
              <label className="scada-form-field">
                <span className="scada-form-label">variableName</span>
                <input
                  type="text"
                  className="scada-form-input mono"
                  spellCheck={false}
                  value={selectedElement.bindings[slot.key]?.variableName ?? ""}
                  onChange={(e) => updateBinding(slot.key, "variableName", e.target.value)}
                />
              </label>
              <label className="scada-form-field">
                <span className="scada-form-label">qualityField</span>
                <input
                  type="text"
                  className="scada-form-input mono"
                  spellCheck={false}
                  placeholder="quality"
                  value={selectedElement.bindings[slot.key]?.qualityField ?? ""}
                  onChange={(e) => updateBinding(slot.key, "qualityField", e.target.value)}
                />
              </label>
              <label className="scada-form-field">
                <span className="scada-form-label">transform</span>
                <select
                  className="scada-form-input"
                  value={selectedElement.bindings[slot.key]?.transform ?? ""}
                  onChange={(e) => updateBinding(slot.key, "transform", e.target.value)}
                >
                  <option value="">—</option>
                  <option value="bool">bool</option>
                  <option value="number">number</option>
                  <option value="string">string</option>
                </select>
              </label>
            </div>
          ))}
        </div>
      )}

      <div className="scada-props-section">
        <h3 className="scada-props-section-title">{t("props.formatRules")}</h3>
        {formatRules.length === 0 ? (
          <p className="scada-props-hint scada-props-hint-compact">{t("props.formatRulesEmpty")}</p>
        ) : (
          formatRules.map((rule, index) => (
            <div key={rule.id} className="scada-binding-slot">
              <label className="scada-form-field">
                <span className="scada-form-label">{t("props.formatBindingKey")}</span>
                <input
                  type="text"
                  className="scada-form-input mono"
                  value={rule.bindingKey}
                  onChange={(e) => updateFormatRule(index, { bindingKey: e.target.value })}
                />
              </label>
              <div className="scada-form-row">
                <label className="scada-form-field scada-form-field-half">
                  <span className="scada-form-label">{t("props.formatOperator")}</span>
                  <select
                    className="scada-form-input"
                    value={rule.operator}
                    onChange={(e) =>
                      updateFormatRule(index, { operator: e.target.value as MimicFormatOperator })
                    }
                  >
                    {FORMAT_OPS.map((op) => (
                      <option key={op} value={op}>
                        {op}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="scada-form-field scada-form-field-half">
                  <span className="scada-form-label">{t("props.formatValue")}</span>
                  <input
                    type="text"
                    className="scada-form-input mono"
                    value={String(rule.value)}
                    onChange={(e) => updateFormatRule(index, { value: e.target.value })}
                  />
                </label>
              </div>
              <label className="scada-form-field">
                <span className="scada-form-label">{t("props.formatFill")}</span>
                <input
                  type="text"
                  className="scada-form-input mono"
                  value={rule.style.fill ?? ""}
                  onChange={(e) =>
                    updateFormatRule(index, { style: { ...rule.style, fill: e.target.value } })
                  }
                />
              </label>
              <button
                type="button"
                className="scada-btn-ghost scada-btn-block"
                onClick={() => removeFormatRule(index)}
              >
                {t("props.removeFormatRule")}
              </button>
            </div>
          ))
        )}
        <button type="button" className="scada-btn-ghost scada-btn-block" onClick={addFormatRule}>
          {t("props.addFormatRule")}
        </button>
      </div>

      <div className="scada-props-section">
        <h3 className="scada-props-section-title">{t("props.actions")}</h3>
        {actions.length === 0 ? (
          <button type="button" className="scada-btn-ghost scada-btn-block" onClick={addAction}>
            {t("props.addAction")}
          </button>
        ) : (
          actions.map((action, index) => (
            <div key={action.id} className="scada-binding-slot">
              <MimicActionFields
                action={action}
                document={document}
                onChange={(patch) => updateAction(index, patch)}
              />
              <button
                type="button"
                className="scada-btn-ghost scada-btn-block"
                onClick={() => removeAction(index)}
              >
                {t("props.removeAction")}
              </button>
            </div>
          ))
        )}
        {actions.length > 0 && (
          <button type="button" className="scada-btn-ghost scada-btn-block" onClick={addAction}>
            {t("props.addAction")}
          </button>
        )}
      </div>

      <button type="button" className="scada-btn-danger scada-btn-block" onClick={onDeleteSelected}>
        {t("props.delete")}
      </button>
    </div>
  );
}
