import { useTranslation } from "react-i18next";
import type { MimicAction, MimicConnection, MimicCustomSymbol, MimicElement, ScadaMimicDocument } from "../../types/scadaMimic";
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
  const action = selectedElement.actions?.[0];

  const updateBinding = (
    key: string,
    field: "objectPath" | "variableName" | "valueField" | "transform",
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

  const updateAction = (patch: Partial<MimicAction>) => {
    const actions = [...(selectedElement.actions ?? [])];
    if (actions.length === 0) {
      actions.push({
        id: createMimicId("act"),
        type: "invokeFunction",
        ...patch,
      });
    } else {
      actions[0] = { ...actions[0], ...patch };
    }
    onUpdateElement({ ...selectedElement, actions });
  };

  const removeAction = () => {
    onUpdateElement({ ...selectedElement, actions: undefined });
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
              <option key={r} value={r}>{r}°</option>
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
              <option key={layer.id} value={layer.id}>{layer.name}</option>
            ))}
          </select>
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
        <h3 className="scada-props-section-title">{t("props.actions")}</h3>
        {!action ? (
          <button
            type="button"
            className="scada-btn-ghost scada-btn-block"
            onClick={() => updateAction({ type: "invokeFunction", functionName: "" })}
          >
            {t("props.addAction")}
          </button>
        ) : (
          <div className="scada-binding-slot">
            <label className="scada-form-field">
              <span className="scada-form-label">{t("props.actionType")}</span>
              <select
                className="scada-form-input"
                value={action.type}
                onChange={(e) => updateAction({ type: e.target.value as MimicAction["type"] })}
              >
                <option value="invokeFunction">invokeFunction</option>
                <option value="setVariable">setVariable</option>
                <option value="toggleVariable">toggleVariable</option>
              </select>
            </label>
            <label className="scada-form-field">
              <span className="scada-form-label">objectPath</span>
              <input
                type="text"
                className="scada-form-input mono"
                spellCheck={false}
                value={action.objectPath ?? ""}
                onChange={(e) => updateAction({ objectPath: e.target.value })}
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
                  onChange={(e) => updateAction({ functionName: e.target.value })}
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
                    onChange={(e) => updateAction({ variableName: e.target.value })}
                  />
                </label>
                {action.type === "setVariable" && (
                  <label className="scada-form-field">
                    <span className="scada-form-label">value</span>
                    <input
                      type="text"
                      className="scada-form-input mono"
                      value={action.value == null ? "" : String(action.value)}
                      onChange={(e) => updateAction({ value: e.target.value })}
                    />
                  </label>
                )}
              </>
            )}
            <button type="button" className="scada-btn-ghost scada-btn-block" onClick={removeAction}>
              {t("props.removeAction")}
            </button>
          </div>
        )}
      </div>

      <button type="button" className="scada-btn-danger scada-btn-block" onClick={onDeleteSelected}>
        {t("props.delete")}
      </button>
    </div>
  );
}
