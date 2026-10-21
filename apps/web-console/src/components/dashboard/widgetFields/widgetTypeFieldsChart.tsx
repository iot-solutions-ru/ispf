// Type-specific editor fields — chart widgets (4 types).
import type { TFunction } from "i18next";
import { useEffect, useState, type ReactNode } from "react";
import {
  type MapMarkerRule,
  type MapMarkerRuleOp,
  type NetworkGraphWidget,
  WIDGET_HISTORY_RANGE_OPTIONS,
} from "../../../types/dashboard";
import { AdvancedJsonField, HintCaption, VariableSelect } from "../widgetEditorStructured";
import { variableListAllowCustom } from "../widgetEditorHelpers";
import { parseMarkerRules, serializeMarkerRules } from "../mapWidgetUtils";
import WidgetMediaUploadField from "../WidgetMediaUploadField";
import { RecordFieldGroup } from "./widgetBoundChoices";
import {
  Section,
  WidgetEditorCaption,
  type WidgetFieldContextFor,
  type WidgetTypeFieldsRegistry,
} from "./widgetFieldPrimitives";
import { rowNavigationFields } from "./widgetRowNavigationFields";

const MAP_MARKER_OPS: MapMarkerRuleOp[] = ["eq", "ne", "gt", "gte", "lt", "lte", "in"];

function newMarkerRule(): MapMarkerRule {
  return {
    id: `r-${Date.now().toString(36)}`,
    when: { variable: "driverStatus", op: "eq", value: "RUNNING" },
    style: { type: "color", color: "#3fb950" },
  };
}

function MapMarkerRulesEditor({
  value,
  onChange,
  t,
}: {
  value: string | undefined;
  onChange: (next: string | undefined) => void;
  t: TFunction;
}): ReactNode {
  // Keep local draft so Color→Picture (empty URL) does not drop the row on re-parse.
  const [rules, setRules] = useState<MapMarkerRule[]>(() => parseMarkerRules(value));

  useEffect(() => {
    const incoming = serializeMarkerRules(parseMarkerRules(value)) ?? "";
    const current = serializeMarkerRules(rules) ?? "";
    if (incoming !== current) {
      setRules(parseMarkerRules(value));
    }
    // Only re-sync when parent value changes externally.
    // eslint-disable-next-line react-hooks/exhaustive-deps -- intentional: compare against value
  }, [value]);

  const commit = (next: MapMarkerRule[]) => {
    setRules(next);
    onChange(serializeMarkerRules(next));
  };

  const patch = (index: number, mut: (rule: MapMarkerRule) => MapMarkerRule) => {
    commit(rules.map((rule, i) => (i === index ? mut(rule) : rule)));
  };

  return (
    <div className="widget-editor-map-marker-rules full">
      <p className="hint">{t("editor.hint.markerRules")}</p>
      {rules.map((rule, index) => {
        const styleType = rule.style.type;
        return (
          <div key={rule.id ?? `rule-${index}`} className="widget-editor-map-marker-rule">
            <div className="widget-editor-map-marker-rule-head">
              <strong>
                {t("editor.markerRule.state")} {index + 1}
              </strong>
              <div className="widget-editor-list-actions">
                <button
                  type="button"
                  disabled={index === 0}
                  onClick={() => {
                    if (index === 0) return;
                    const next = [...rules];
                    [next[index - 1], next[index]] = [next[index], next[index - 1]];
                    commit(next);
                  }}
                >
                  ↑
                </button>
                <button
                  type="button"
                  disabled={index >= rules.length - 1}
                  onClick={() => {
                    if (index >= rules.length - 1) return;
                    const next = [...rules];
                    [next[index], next[index + 1]] = [next[index + 1], next[index]];
                    commit(next);
                  }}
                >
                  ↓
                </button>
                <button type="button" onClick={() => commit(rules.filter((_, i) => i !== index))}>
                  {t("editor.markerRule.remove")}
                </button>
              </div>
            </div>

            <div className="widget-editor-map-marker-rule-block">
              <span className="widget-editor-map-marker-rule-block-title">
                {t("editor.markerRule.condition")}
              </span>
              <div className="widget-editor-map-marker-rule-row">
                <label>
                  <WidgetEditorCaption text={t("editor.markerRule.variable")} />
                  <input
                    value={rule.when.variable}
                    onChange={(e) =>
                      patch(index, (r) => ({
                        ...r,
                        when: { ...r.when, variable: e.target.value },
                      }))
                    }
                    placeholder="driverStatus"
                  />
                </label>
                <label>
                  <WidgetEditorCaption text={t("editor.markerRule.op")} />
                  <select
                    value={rule.when.op}
                    onChange={(e) =>
                      patch(index, (r) => ({
                        ...r,
                        when: { ...r.when, op: e.target.value as MapMarkerRuleOp },
                      }))
                    }
                  >
                    {MAP_MARKER_OPS.map((op) => (
                      <option key={op} value={op}>
                        {t(`editor.markerRule.op.${op}`)}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  <WidgetEditorCaption text={t("editor.markerRule.value")} />
                  <input
                    value={String(rule.when.value ?? "")}
                    onChange={(e) => {
                      const raw = e.target.value;
                      const asNum = Number(raw);
                      const nextValue =
                        raw.trim() !== "" &&
                        Number.isFinite(asNum) &&
                        /^-?\d+(\.\d+)?$/.test(raw.trim())
                          ? asNum
                          : raw;
                      patch(index, (r) => ({ ...r, when: { ...r.when, value: nextValue } }));
                    }}
                    placeholder="RUNNING"
                  />
                </label>
              </div>
            </div>

            <div className="widget-editor-map-marker-rule-block">
              <span className="widget-editor-map-marker-rule-block-title">
                {t("editor.markerRule.appearance")}
              </span>
              <label>
                <WidgetEditorCaption text={t("editor.markerRule.styleType")} />
                <select
                  value={styleType}
                  onChange={(e) => {
                    const nextType = e.target.value as "color" | "icon";
                    patch(index, (r) =>
                      nextType === "color"
                        ? {
                            ...r,
                            style: {
                              type: "color",
                              color: r.style.type === "color" ? r.style.color : "#3fb950",
                            },
                          }
                        : {
                            ...r,
                            style: {
                              type: "icon",
                              url: r.style.type === "icon" ? r.style.url : "",
                              size: r.style.type === "icon" ? r.style.size : 24,
                            },
                          }
                    );
                  }}
                >
                  <option value="color">{t("editor.markerRule.styleColor")}</option>
                  <option value="icon">{t("editor.markerRule.styleIcon")}</option>
                </select>
              </label>
              {styleType === "color" ? (
                <label>
                  <WidgetEditorCaption text={t("editor.markerRule.color")} />
                  <input
                    type="color"
                    value={
                      rule.style.type === "color" && /^#[0-9a-fA-F]{6}$/.test(rule.style.color)
                        ? rule.style.color
                        : "#3fb950"
                    }
                    onChange={(e) =>
                      patch(index, (r) => ({
                        ...r,
                        style: { type: "color", color: e.target.value },
                      }))
                    }
                  />
                  <input
                    value={rule.style.type === "color" ? rule.style.color : "#3fb950"}
                    onChange={(e) =>
                      patch(index, (r) => ({
                        ...r,
                        style: { type: "color", color: e.target.value },
                      }))
                    }
                    placeholder="#3fb950"
                  />
                </label>
              ) : (
                <>
                  <WidgetMediaUploadField
                    label={t("editor.markerRule.iconUrl")}
                    value={rule.style.type === "icon" ? rule.style.url : ""}
                    onChange={(url) =>
                      patch(index, (r) => ({
                        ...r,
                        style: {
                          type: "icon",
                          url,
                          size: r.style.type === "icon" ? r.style.size : 24,
                        },
                      }))
                    }
                    accept="image/png,image/jpeg,image/webp,image/gif,image/svg+xml,.svg"
                    placeholder={t("editor.markerRule.iconUrlHint")}
                    previewAlt={t("editor.markerRule.styleIcon")}
                  />
                  <label>
                    <WidgetEditorCaption text={t("editor.markerRule.iconSize")} />
                    <input
                      type="number"
                      min={12}
                      max={96}
                      value={rule.style.type === "icon" ? (rule.style.size ?? 24) : 24}
                      onChange={(e) =>
                        patch(index, (r) => ({
                          ...r,
                          style: {
                            type: "icon",
                            url: r.style.type === "icon" ? r.style.url : "",
                            size: Number(e.target.value) || 24,
                          },
                        }))
                      }
                    />
                  </label>
                </>
              )}
            </div>
          </div>
        );
      })}
      <button type="button" onClick={() => commit([...rules, newMarkerRule()])}>
        {t("editor.markerRule.add")}
      </button>
    </div>
  );
}

function chartFields(ctx: WidgetFieldContextFor<"chart">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  const chartType = widget.chartType ?? widget.chartStyle ?? "area";
  return (
    <>
      <Section title={t("editor.section.chart")} />
      <label>
        <WidgetEditorCaption text={t("editor.historyRange")} />
        <select
          value={widget.historyRange ?? "live"}
          onChange={(e) => update({ historyRange: e.target.value as typeof widget.historyRange })}
        >
          {WIDGET_HISTORY_RANGE_OPTIONS.map((item) => (
            <option key={item.id} value={item.id}>
              {t(`history.${item.id}`)}
            </option>
          ))}
        </select>
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.chartType")} />
        <select
          value={chartType}
          onChange={(e) => update({ chartType: e.target.value as typeof widget.chartType })}
        >
          <option value="line">{t("editor.chartStyle.line")}</option>
          <option value="area">{t("editor.chartStyle.area")}</option>
          <option value="bar">{t("editor.chartType.bar")}</option>
          <option value="range">{t("editor.chartTypeRange")}</option>
          <option value="candlestick">{t("editor.chartTypeCandlestick")}</option>
          <option value="bubble">{t("editor.chartTypeBubble")}</option>
          <option value="radar">{t("editor.chartTypeRadar")}</option>
        </select>
        {chartType === "range" && (
          <span className="hint">{t("editor.chartTypeRangeHint")}</span>
        )}
        {chartType === "candlestick" && (
          <span className="hint">{t("editor.chartTypeCandlestickHint")}</span>
        )}
        {chartType === "bubble" && (
          <span className="hint">{t("editor.chartTypeBubbleHint")}</span>
        )}
        {chartType === "radar" && (
          <span className="hint">{t("editor.chartTypeRadarHint")}</span>
        )}
      </label>
      {chartType === "bubble" && (
        <>
          <Section title={t("editor.section.chartBubble")} />
          <VariableSelect
            label={t("editor.chartBubbleXVariable")}
            value={widget.bubbleXVariable ?? ""}
            onChange={(v) => update({ bubbleXVariable: v || undefined })}
            variables={ctx.variables}
            allowCustom={variableListAllowCustom(widget)}
            disabled={!ctx.variableSelectEnabled}
          />
          <VariableSelect
            label={t("editor.chartBubbleYVariable")}
            value={widget.bubbleYVariable ?? ""}
            onChange={(v) => update({ bubbleYVariable: v || undefined })}
            variables={ctx.variables}
            allowCustom={variableListAllowCustom(widget)}
            disabled={!ctx.variableSelectEnabled}
          />
          <VariableSelect
            label={t("editor.chartBubbleSizeVariable")}
            value={widget.bubbleSizeVariable ?? ""}
            onChange={(v) => update({ bubbleSizeVariable: v || undefined })}
            variables={ctx.variables}
            allowCustom={variableListAllowCustom(widget)}
            disabled={!ctx.variableSelectEnabled}
          />
          <label>
            <WidgetEditorCaption text={t("editor.chartBubbleDefaultSize")} />
            <input
              type="number"
              min={10}
              max={1000}
              value={widget.bubbleDefaultSize ?? 80}
              onChange={(e) => update({ bubbleDefaultSize: Number(e.target.value) })}
            />
          </label>
          <AdvancedJsonField
            label={t("editor.chartBubblePointsJson")}
            placeholder={t("editor.chartBubblePointsJsonHint")}
            value={widget.bubblePointsJson ?? ""}
            onChange={(v) => update({ bubblePointsJson: v || undefined })}
            rows={5}
          />
        </>
      )}
      {chartType === "radar" && (
        <>
          <Section title={t("editor.section.chartRadar")} />
          <AdvancedJsonField
            label={t("editor.chartRadarAxesJson")}
            placeholder={t("editor.chartRadarAxesJsonHint")}
            value={widget.radarAxesJson ?? ""}
            onChange={(v) => update({ radarAxesJson: v || undefined })}
            rows={6}
          />
        </>
      )}
      {chartType !== "bubble" && chartType !== "radar" && (
        <label>
          <WidgetEditorCaption text={t("editor.chartStyle")} />
          <select
            value={widget.chartStyle ?? "area"}
            onChange={(e) => update({ chartStyle: e.target.value as "line" | "area" })}
          >
            <option value="area">{t("editor.chartStyle.area")}</option>
            <option value="line">{t("editor.chartStyle.line")}</option>
          </select>
        </label>
      )}
      <label>
        <WidgetEditorCaption text={t("editor.maxPoints")} />
        <input
          type="number"
          min={10}
          max={500}
          value={widget.maxPoints ?? 120}
          onChange={(e) => update({ maxPoints: Number(e.target.value) })}
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.sampleMode")} />
        <select
          value={widget.sampleMode ?? "auto"}
          onChange={(e) =>
            update({
              sampleMode: e.target.value === "auto"
                ? undefined
                : (e.target.value as typeof widget.sampleMode),
            })
          }
        >
          <option value="auto">{t("editor.sampleModeAuto")}</option>
          <option value="aggregate">{t("editor.sampleModeAggregate")}</option>
          <option value="coalesce">{t("editor.sampleModeCoalesce")}</option>
          <option value="raw">{t("editor.sampleModeRaw")}</option>
        </select>
        <span className="hint">{t("editor.sampleModeHint")}</span>
      </label>
      {(widget.sampleMode ?? "auto") !== "raw"
        && (widget.sampleMode ?? "auto") !== "coalesce" && (
        <label>
          <WidgetEditorCaption text={t("editor.historyBucket")} />
          <select
            value={widget.historyBucket ?? "auto"}
            onChange={(e) =>
              update({
                historyBucket: e.target.value === "auto" ? undefined : e.target.value,
              })
            }
          >
            <option value="auto">{t("editor.historyBucket.auto")}</option>
            <option value="1m">{t("editor.historyBucket.1m")}</option>
            <option value="5m">{t("editor.historyBucket.5m")}</option>
            <option value="15m">{t("editor.historyBucket.15m")}</option>
            <option value="30m">{t("editor.historyBucket.30m")}</option>
            <option value="1h">{t("editor.historyBucket.1h")}</option>
            <option value="6h">{t("editor.historyBucket.6h")}</option>
            <option value="8h">{t("editor.historyBucket.8h")}</option>
            <option value="1d">{t("editor.historyBucket.1d")}</option>
          </select>
          <span className="hint">{t("editor.historyBucketHint")}</span>
        </label>
      )}
      {((widget.sampleMode ?? "auto") === "coalesce"
        || (widget.sampleMode ?? "auto") === "auto") && (
        <label>
          <WidgetEditorCaption text={t("editor.liveCoalesceMs")} />
          <input
            type="number"
            min={200}
            max={60_000}
            step={100}
            value={widget.liveCoalesceMs ?? 1000}
            onChange={(e) => {
              const value = Number(e.target.value);
              update({
                liveCoalesceMs: Number.isFinite(value) && value !== 1000 ? value : undefined,
              });
            }}
          />
          <span className="hint">{t("editor.liveCoalesceMsHint")}</span>
        </label>
      )}
      <label>
        <WidgetEditorCaption text={t("editor.color")} />
        <input
          type="color"
          value={widget.color ?? "#2f81f7"}
          onChange={(e) => update({ color: e.target.value })}
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.unit")} />
        <input value={widget.unit ?? ""} onChange={(e) => update({ unit: e.target.value })} />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.unitField")} />
        <input
          value={widget.unitField ?? ""}
          onChange={(e) => update({ unitField: e.target.value || undefined })}
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.decimals")} />
        <input
          type="number"
          min={0}
          max={6}
          value={widget.decimals ?? 1}
          onChange={(e) => update({ decimals: Number(e.target.value) })}
        />
      </label>
    </>
  );
}

function mapFields(ctx: WidgetFieldContextFor<"map">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  const popupMode =
    widget.popupMode ?? (widget.popupVariablesJson?.trim() ? "variables" : "label");
  return (
    <>
      <Section title={t("editor.section.map")} />
      <label>
        <WidgetEditorCaption text={t("editor.field.latVariable")} />
        <input
          value={widget.latVariable ?? "coordinates"}
          onChange={(e) => update({ latVariable: e.target.value })}
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.field.latField")} />
        <input
          value={widget.latField ?? "latitude"}
          onChange={(e) => update({ latField: e.target.value })}
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.field.lonField")} />
        <input
          value={widget.lonField ?? "longitude"}
          onChange={(e) => update({ lonField: e.target.value })}
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.field.labelVariable")} />
        <input
          value={widget.labelVariable ?? ""}
          onChange={(e) => update({ labelVariable: e.target.value || undefined })}
        />
      </label>

      <Section title={t("editor.section.mapFilter")} />
      <label>
        <HintCaption>{t("editor.field.namePattern")}</HintCaption>
        <input
          value={widget.namePattern ?? ""}
          onChange={(e) => update({ namePattern: e.target.value || undefined })}
          placeholder="modbus-localhost*"
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.field.objectType")} />
        <select
          value={widget.objectType ?? ""}
          onChange={(e) =>
            update({
              objectType: (e.target.value || undefined) as typeof widget.objectType,
            })
          }
        >
          <option value="">—</option>
          <option value="DEVICE">{t("common:objectType.DEVICE")}</option>
          <option value="FOLDER">{t("common:objectType.FOLDER")}</option>
          <option value="DASHBOARD">{t("common:objectType.DASHBOARD")}</option>
          <option value="CUSTOM">{t("common:objectType.CUSTOM")}</option>
        </select>
      </label>
      <AdvancedJsonField
        label={t("editor.field.includePathsJson")}
        value={widget.includePathsJson}
        onChange={(v) => update({ includePathsJson: v })}
        rows={2}
      />
      <AdvancedJsonField
        label={t("editor.field.excludePathsJson")}
        value={widget.excludePathsJson}
        onChange={(v) => update({ excludePathsJson: v })}
        rows={2}
      />

      <Section title={t("editor.section.mapMarkerRules")} />
      <MapMarkerRulesEditor
        value={widget.markerRulesJson}
        onChange={(v) => update({ markerRulesJson: v })}
        t={t}
      />

      <Section title={t("editor.section.mapPopup")} />
      <label>
        <WidgetEditorCaption text={t("editor.field.popupMode")} />
        <select
          value={popupMode}
          onChange={(e) =>
            update({
              popupMode: (e.target.value || undefined) as typeof widget.popupMode,
            })
          }
        >
          <option value="none">{t("editor.popupMode.none")}</option>
          <option value="label">{t("editor.popupMode.label")}</option>
          <option value="variables">{t("editor.popupMode.variables")}</option>
        </select>
      </label>
      {popupMode === "variables" && (
        <AdvancedJsonField
          label={t("editor.field.popupVariablesJson")}
          value={widget.popupVariablesJson}
          onChange={(v) => update({ popupVariablesJson: v })}
          rows={3}
        />
      )}

      <label>
        <WidgetEditorCaption text={t("editor.field.zoom")} />
        <input
          type="number"
          min={1}
          max={18}
          value={widget.zoom ?? 10}
          onChange={(e) => update({ zoom: Number(e.target.value) })}
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.field.centerLat")} />
        <input
          type="number"
          step="any"
          value={widget.centerLat ?? 55.75}
          onChange={(e) => update({ centerLat: Number(e.target.value) })}
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.field.centerLon")} />
        <input
          type="number"
          step="any"
          value={widget.centerLon ?? 37.62}
          onChange={(e) => update({ centerLon: Number(e.target.value) })}
        />
      </label>
      <label>
        <HintCaption>{t("editor.field.mapStyleUrl")}</HintCaption>
        <input
          value={widget.mapStyleUrl ?? ""}
          onChange={(e) => update({ mapStyleUrl: e.target.value || undefined })}
        />
      </label>
      <label>
        <HintCaption>{t("editor.field.tileUrl")}</HintCaption>
        <input
          value={widget.tileUrl ?? ""}
          onChange={(e) => update({ tileUrl: e.target.value || undefined })}
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.field.tileAttribution")} />
        <input
          value={widget.tileAttribution ?? ""}
          onChange={(e) => update({ tileAttribution: e.target.value || undefined })}
        />
      </label>
      {rowNavigationFields(ctx, "row", t)}
    </>
  );
}

function ganttChartFields(ctx: WidgetFieldContextFor<"gantt-chart">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.gantt")} />
      <RecordFieldGroup
        objectPath={widget.objectPath ?? widget.modelHintPath}
        variableName={widget.variableName}
        allowCustom={variableListAllowCustom(widget)}
        fields={[
          {
            label: t("editor.field.recordLabel"),
            value: widget.labelField ?? "label",
            onChange: (next) => update({ labelField: next }),
          },
          {
            label: t("editor.field.recordStart"),
            value: widget.startField ?? "start",
            onChange: (next) => update({ startField: next }),
          },
          {
            label: t("editor.field.recordEnd"),
            value: widget.endField ?? "end",
            onChange: (next) => update({ endField: next }),
          },
        ]}
      />
      <label className="checkbox-row">
        <input
          type="checkbox"
          checked={widget.interactive !== false}
          onChange={(e) => update({ interactive: e.target.checked })}
        />
        <WidgetEditorCaption text={t("editor.ganttInteractive")} />
      </label>
      <label className="checkbox-row">
        <input
          type="checkbox"
          checked={widget.allowBarDrag !== false}
          onChange={(e) => update({ allowBarDrag: e.target.checked })}
        />
        <WidgetEditorCaption text={t("editor.ganttAllowBarDrag")} />
      </label>
    </>
  );
}

function networkGraphFields(ctx: WidgetFieldContextFor<"network-graph">, t: TFunction): ReactNode {
  const { widget, update, variables } = ctx;
  return (
    <>
      <Section title={t("editor.section.networkGraph")} />
      <VariableSelect
        label={t("editor.field.nodesVariable")}
        value={widget.nodesVariable ?? ""}
        onChange={(v) => update({ nodesVariable: v || undefined })}
        variables={variables}
        allowCustom={variableListAllowCustom(widget)}
        disabled={!ctx.variableSelectEnabled}
      />
      <VariableSelect
        label={t("editor.field.edgesVariable")}
        value={widget.edgesVariable ?? ""}
        onChange={(v) => update({ edgesVariable: v || undefined })}
        variables={variables}
        allowCustom={variableListAllowCustom(widget)}
        disabled={!ctx.variableSelectEnabled}
      />
      <RecordFieldGroup
        objectPath={widget.objectPath ?? widget.modelHintPath}
        variableName={widget.nodesVariable}
        allowCustom={variableListAllowCustom(widget)}
        fields={[
          {
            label: t("editor.field.recordLabel"),
            value: widget.labelField ?? "name",
            onChange: (next) => update({ labelField: next }),
          },
          {
            label: t("editor.field.recordId"),
            value: widget.idField ?? "id",
            onChange: (next) => update({ idField: next }),
          },
        ]}
      />
      <RecordFieldGroup
        objectPath={widget.objectPath ?? widget.modelHintPath}
        variableName={widget.edgesVariable}
        allowCustom={variableListAllowCustom(widget)}
        fields={[
          {
            label: t("editor.field.edgeFrom"),
            value: widget.edgeFromField ?? "from",
            onChange: (next) => update({ edgeFromField: next }),
          },
          {
            label: t("editor.field.edgeTo"),
            value: widget.edgeToField ?? "to",
            onChange: (next) => update({ edgeToField: next }),
          },
        ]}
      />
      <label>
        <WidgetEditorCaption text={t("editor.field.graphLayout")} />
        <select
          value={widget.layout ?? "cose"}
          onChange={(e) =>
            update({
              layout: e.target.value as NetworkGraphWidget["layout"],
            })
          }
        >
          <option value="cose">{t("editor.networkGraphLayout.cose")}</option>
          <option value="circle">{t("editor.networkGraphLayout.circle")}</option>
          <option value="grid">{t("editor.networkGraphLayout.grid")}</option>
          <option value="breadthfirst">
            {t("editor.networkGraphLayout.breadthfirst")}
          </option>
        </select>
      </label>
    </>
  );
}

export const CHART_WIDGET_FIELDS: WidgetTypeFieldsRegistry = {
  "chart": chartFields,
  "map": mapFields,
  "gantt-chart": ganttChartFields,
  "network-graph": networkGraphFields,
};
