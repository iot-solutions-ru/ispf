import { useMemo, useRef } from "react";
import { useTranslation } from "react-i18next";
import type { SvgWidget } from "../../types/dashboard";
import type { MimicBinding, MimicBindingSlot, MimicSymbolBehavior } from "../../types/scadaMimic";
import { parseSvgUpload, sanitizeSvgMarkup } from "../../scada/customSvg";
import {
  readSvgWidgetBehaviors,
  readSvgWidgetBindings,
  readSvgWidgetBindingSchema,
  readSvgWidgetHitAreas,
  readSvgWidgetInner,
  serializeSvgInteractivePatch,
} from "../../scada/svgWidgetInteractive";
import type { TopologySvgHitArea } from "../../scada/topologySvgConfig";
import SymbolBehaviorsEditor from "../scada/SymbolBehaviorsEditor";
import MimicBindingSlotEditor from "../scada/MimicBindingSlotEditor";
import WidgetMediaUploadField from "./WidgetMediaUploadField";

interface SvgWidgetInteractiveEditorProps {
  widget: SvgWidget;
  update: (patch: Partial<SvgWidget>) => void;
}

function commit(
  update: (patch: Partial<SvgWidget>) => void,
  state: {
    behaviors: MimicSymbolBehavior[];
    bindings: Record<string, MimicBinding>;
    bindingSchema: MimicBindingSlot[];
    hitAreas: TopologySvgHitArea[];
    svgInner?: string;
    viewBox?: string;
    backgroundColor?: string;
  }
) {
  update(serializeSvgInteractivePatch(state));
}

export default function SvgWidgetInteractiveEditor({ widget, update }: SvgWidgetInteractiveEditorProps) {
  const { t } = useTranslation(["scada", "widgets"]);
  const fileRef = useRef<HTMLInputElement>(null);

  const behaviors = useMemo(() => readSvgWidgetBehaviors(widget), [widget.behaviorsJson, widget.topologyJson]);
  const bindings = useMemo(() => readSvgWidgetBindings(widget), [widget.bindingsJson, widget.topologyJson]);
  const bindingSchema = useMemo(
    () => readSvgWidgetBindingSchema(widget, behaviors),
    [widget.bindingSchemaJson, widget.behaviorsJson, widget.topologyJson, behaviors]
  );
  const hitAreas = useMemo(() => readSvgWidgetHitAreas(widget), [widget.hitAreasJson, widget.topologyJson]);
  const svgInner = useMemo(() => readSvgWidgetInner(widget) ?? "", [widget.svgInnerJson, widget.topologyJson]);

  const pushState = (patch: Partial<{
    behaviors: MimicSymbolBehavior[];
    bindings: Record<string, MimicBinding>;
    bindingSchema: MimicBindingSlot[];
    hitAreas: TopologySvgHitArea[];
    svgInner: string;
    viewBox: string;
    backgroundColor: string;
  }>) => {
    commit(update, {
      behaviors: patch.behaviors ?? behaviors,
      bindings: patch.bindings ?? bindings,
      bindingSchema: patch.bindingSchema ?? bindingSchema,
      hitAreas: patch.hitAreas ?? hitAreas,
      svgInner: patch.svgInner ?? svgInner,
      viewBox: patch.viewBox ?? widget.viewBox,
      backgroundColor: patch.backgroundColor ?? widget.backgroundColor,
    });
  };

  const handleSvgUpload = async (file: File) => {
    const text = await file.text();
    const parsed = parseSvgUpload(text);
    pushState({
      svgInner: parsed.svg,
      viewBox: parsed.viewBox,
    });
    update({ svgUrl: undefined });
  };

  const updateBinding = (key: string, patch: Partial<MimicBinding>) => {
    const next = { ...bindings };
    const current = next[key] ?? { variableName: "" };
    next[key] = { ...current, ...patch };
    pushState({ bindings: next });
  };

  const updateHitArea = (index: number, patch: Partial<TopologySvgHitArea>) => {
    const next = hitAreas.map((area, i) => (i === index ? { ...area, ...patch } : area));
    pushState({ hitAreas: next });
  };

  const addHitArea = () => {
    pushState({
      hitAreas: [
        ...hitAreas,
        { nodeName: "node", objectPath: "", id: "back_node" },
      ],
    });
  };

  const removeHitArea = (index: number) => {
    pushState({ hitAreas: hitAreas.filter((_, i) => i !== index) });
  };

  return (
    <div className="svg-widget-interactive-editor scada-editor-panel">
      <h3 className="scada-props-section-title">{t("editor.section.svgInteractive", { ns: "widgets", defaultValue: "Интерактивный SVG (как SCADA-символ)" })}</h3>
      <p className="scada-props-hint scada-props-hint-compact">
        {t("props.behaviorsHint", { ns: "scada" })}
      </p>

      <WidgetMediaUploadField
        label="svgUrl"
        value={widget.svgUrl ?? ""}
        onChange={(svgUrl) => update({ svgUrl })}
        accept=".svg,image/svg+xml"
        placeholder="/itm-assets/m11/main_topology.svg"
        previewAlt={widget.title}
      />

      <input
        ref={fileRef}
        type="file"
        accept=".svg,image/svg+xml"
        className="scada-file-input-hidden"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) void handleSvgUpload(file);
          e.target.value = "";
        }}
      />
      <button type="button" className="scada-btn-ghost scada-btn-block" onClick={() => fileRef.current?.click()}>
        {t("props.customSvgUpload", { ns: "scada" })}
      </button>

      <label className="scada-form-field">
        <span className="scada-form-label">svgInnerJson</span>
        <textarea
          className="scada-form-input mono"
          rows={4}
          spellCheck={false}
          value={svgInner}
          onChange={(e) => pushState({ svgInner: sanitizeSvgMarkup(e.target.value) })}
        />
      </label>

      <div className="scada-form-row">
        <label className="scada-form-field scada-form-field-half">
          <span className="scada-form-label">viewBox</span>
          <input
            type="text"
            className="scada-form-input mono"
            value={widget.viewBox ?? ""}
            onChange={(e) => update({ viewBox: e.target.value || undefined })}
          />
        </label>
        <label className="scada-form-field scada-form-field-half">
          <span className="scada-form-label">backgroundColor</span>
          <input
            type="text"
            className="scada-form-input mono"
            value={widget.backgroundColor ?? ""}
            onChange={(e) => update({ backgroundColor: e.target.value || undefined })}
          />
        </label>
      </div>

      <SymbolBehaviorsEditor
        svg={svgInner}
        behaviors={behaviors}
        bindingSchema={bindingSchema}
        onChange={({ behaviors: nextBehaviors, bindingSchema: nextSchema }) => {
          const nextBindings = { ...bindings };
          for (const key of Object.keys(nextBindings)) {
            if (!nextSchema.some((slot) => slot.key === key)) {
              delete nextBindings[key];
            }
          }
          for (const slot of nextSchema) {
            if (!nextBindings[slot.key]) {
              nextBindings[slot.key] = { variableName: "", valueField: "value", transform: "bool" };
            }
          }
          pushState({
            behaviors: nextBehaviors,
            bindingSchema: nextSchema,
            bindings: nextBindings,
          });
        }}
      />

      {bindingSchema.length > 0 && (
        <div className="scada-props-section">
          <h3 className="scada-props-section-title">{t("props.bindings", { ns: "scada" })}</h3>
          {bindingSchema.map((slot) => (
            <MimicBindingSlotEditor
              key={slot.key}
              title={slot.key}
              binding={bindings[slot.key]}
              onUpdate={(patch) => updateBinding(slot.key, patch)}
            />
          ))}
        </div>
      )}

      <div className="scada-props-section">
        <div className="scada-props-section-head">
          <h3 className="scada-props-section-title">{t("editor.clickAreas", { ns: "widgets", defaultValue: "Кликабельные области" })}</h3>
          <button type="button" className="scada-btn-ghost scada-btn-compact" onClick={addHitArea}>
            {t("props.behaviorsAdd", { ns: "scada" })}
          </button>
        </div>
        {hitAreas.length === 0 ? (
          <p className="scada-props-hint scada-props-hint-compact">
            {t("editor.clickAreasEmpty", { ns: "widgets", defaultValue: "Добавьте #back_* для выбора узла." })}
          </p>
        ) : (
          hitAreas.map((area, index) => (
            <div key={`${area.id ?? area.nodeName}-${index}`} className="scada-binding-slot scada-behavior-card">
              <div className="scada-behavior-card-head">
                <strong className="scada-binding-slot-title">{area.nodeName}</strong>
                <button
                  type="button"
                  className="scada-btn-ghost scada-btn-compact scada-btn-danger-text"
                  onClick={() => removeHitArea(index)}
                >
                  {t("props.behaviorsRemove", { ns: "scada" })}
                </button>
              </div>
              <label className="scada-form-field">
                <span className="scada-form-label">target id</span>
                <input
                  type="text"
                  className="scada-form-input mono"
                  value={area.id ?? `back_${area.nodeName}`}
                  onChange={(e) => updateHitArea(index, { id: e.target.value })}
                />
              </label>
              <label className="scada-form-field">
                <span className="scada-form-label">objectPath</span>
                <input
                  type="text"
                  className="scada-form-input mono"
                  value={area.objectPath}
                  onChange={(e) => updateHitArea(index, { objectPath: e.target.value })}
                />
              </label>
            </div>
          ))
        )}
      </div>

      <label className="scada-form-field">
        <span className="scada-form-label">selectionKey</span>
        <input
          type="text"
          className="scada-form-input mono"
          value={widget.selectionKey ?? ""}
          onChange={(e) => update({ selectionKey: e.target.value || undefined })}
        />
      </label>
    </div>
  );
}
