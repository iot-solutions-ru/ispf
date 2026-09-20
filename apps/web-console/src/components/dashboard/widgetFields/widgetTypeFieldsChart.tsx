// Type-specific editor fields — chart widgets (4 types).
import type { TFunction } from "i18next";
import type { ReactNode } from "react";
import { type NetworkGraphWidget, WIDGET_HISTORY_RANGE_OPTIONS } from "../../../types/dashboard";
import { AdvancedJsonField, VariableSelect } from "../widgetEditorStructured";
import { Section, type WidgetFieldContextFor, type WidgetTypeFieldsRegistry } from "./widgetFieldPrimitives";
import { rowNavigationFields } from "./widgetRowNavigationFields";

function chartFields(ctx: WidgetFieldContextFor<"chart">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  const chartType = widget.chartType ?? widget.chartStyle ?? "area";
  return (
    <>
      <Section title={t("editor.section.chart")} />
      <label>
        {t("editor.historyRange")}
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
        {t("editor.chartType")}
        <select
          value={chartType}
          onChange={(e) => update({ chartType: e.target.value as typeof widget.chartType })}
        >
          <option value="line">line</option>
          <option value="area">area</option>
          <option value="bar">bar</option>
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
            disabled={!ctx.variableSelectEnabled}
          />
          <VariableSelect
            label={t("editor.chartBubbleYVariable")}
            value={widget.bubbleYVariable ?? ""}
            onChange={(v) => update({ bubbleYVariable: v || undefined })}
            variables={ctx.variables}
            disabled={!ctx.variableSelectEnabled}
          />
          <VariableSelect
            label={t("editor.chartBubbleSizeVariable")}
            value={widget.bubbleSizeVariable ?? ""}
            onChange={(v) => update({ bubbleSizeVariable: v || undefined })}
            variables={ctx.variables}
            disabled={!ctx.variableSelectEnabled}
          />
          <label>
            {t("editor.chartBubbleDefaultSize")}
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
          {t("editor.chartStyle")}
          <select
            value={widget.chartStyle ?? "area"}
            onChange={(e) => update({ chartStyle: e.target.value as "line" | "area" })}
          >
            <option value="area">area</option>
            <option value="line">line</option>
          </select>
        </label>
      )}
      <label>
        {t("editor.maxPoints")}
        <input
          type="number"
          min={10}
          max={500}
          value={widget.maxPoints ?? 120}
          onChange={(e) => update({ maxPoints: Number(e.target.value) })}
        />
      </label>
      <label>
        {t("editor.sampleMode")}
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
          {t("editor.historyBucket")}
          <select
            value={widget.historyBucket ?? "auto"}
            onChange={(e) =>
              update({
                historyBucket: e.target.value === "auto" ? undefined : e.target.value,
              })
            }
          >
            <option value="auto">auto</option>
            <option value="1m">1m</option>
            <option value="5m">5m</option>
            <option value="15m">15m</option>
            <option value="30m">30m</option>
            <option value="1h">1h</option>
            <option value="6h">6h</option>
            <option value="8h">8h</option>
            <option value="1d">1d</option>
          </select>
          <span className="hint">{t("editor.historyBucketHint")}</span>
        </label>
      )}
      {((widget.sampleMode ?? "auto") === "coalesce"
        || (widget.sampleMode ?? "auto") === "auto") && (
        <label>
          {t("editor.liveCoalesceMs")}
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
        {t("editor.color")}
        <input
          type="color"
          value={widget.color ?? "#2f81f7"}
          onChange={(e) => update({ color: e.target.value })}
        />
      </label>
      <label>
        {t("editor.unit")}
        <input value={widget.unit ?? ""} onChange={(e) => update({ unit: e.target.value })} />
      </label>
      <label>
        unitField
        <input
          value={widget.unitField ?? ""}
          onChange={(e) => update({ unitField: e.target.value || undefined })}
        />
      </label>
      <label>
        {t("editor.decimals")}
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
  return (
    <>
      <Section title={t("editor.section.map")} />
      <label>
        latVariable
        <input
          value={widget.latVariable ?? "coordinates"}
          onChange={(e) => update({ latVariable: e.target.value })}
        />
      </label>
      <label>
        latField
        <input
          value={widget.latField ?? "latitude"}
          onChange={(e) => update({ latField: e.target.value })}
        />
      </label>
      <label>
        lonField
        <input
          value={widget.lonField ?? "longitude"}
          onChange={(e) => update({ lonField: e.target.value })}
        />
      </label>
      <label>
        labelVariable
        <input
          value={widget.labelVariable ?? ""}
          onChange={(e) => update({ labelVariable: e.target.value || undefined })}
        />
      </label>
      <label>
        zoom
        <input
          type="number"
          min={1}
          max={18}
          value={widget.zoom ?? 10}
          onChange={(e) => update({ zoom: Number(e.target.value) })}
        />
      </label>
      <label>
        centerLat
        <input
          type="number"
          step="any"
          value={widget.centerLat ?? 55.75}
          onChange={(e) => update({ centerLat: Number(e.target.value) })}
        />
      </label>
      <label>
        centerLon
        <input
          type="number"
          step="any"
          value={widget.centerLon ?? 37.62}
          onChange={(e) => update({ centerLon: Number(e.target.value) })}
        />
      </label>
      <label>
        mapStyleUrl
        <input
          value={widget.mapStyleUrl ?? ""}
          onChange={(e) => update({ mapStyleUrl: e.target.value || undefined })}
          placeholder={t("editor.placeholder.vectorStyle")}
        />
      </label>
      <label>
        tileUrl
        <input
          value={widget.tileUrl ?? ""}
          onChange={(e) => update({ tileUrl: e.target.value || undefined })}
          placeholder="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
        />
      </label>
      <label>
        tileAttribution
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
      <label>
        labelField
        <input
          value={widget.labelField ?? "label"}
          onChange={(e) => update({ labelField: e.target.value })}
        />
      </label>
      <label>
        startField
        <input
          value={widget.startField ?? "start"}
          onChange={(e) => update({ startField: e.target.value })}
        />
      </label>
      <label>
        endField
        <input
          value={widget.endField ?? "end"}
          onChange={(e) => update({ endField: e.target.value })}
        />
      </label>
      <label className="checkbox-row">
        <input
          type="checkbox"
          checked={widget.interactive !== false}
          onChange={(e) => update({ interactive: e.target.checked })}
        />
        {t("editor.ganttInteractive")}
      </label>
      <label className="checkbox-row">
        <input
          type="checkbox"
          checked={widget.allowBarDrag !== false}
          onChange={(e) => update({ allowBarDrag: e.target.checked })}
        />
        {t("editor.ganttAllowBarDrag")}
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
        label="nodesVariable"
        value={widget.nodesVariable ?? ""}
        onChange={(v) => update({ nodesVariable: v || undefined })}
        variables={variables}
      />
      <VariableSelect
        label="edgesVariable"
        value={widget.edgesVariable ?? ""}
        onChange={(v) => update({ edgesVariable: v || undefined })}
        variables={variables}
      />
      <label>
        labelField
        <input
          value={widget.labelField ?? "name"}
          onChange={(e) => update({ labelField: e.target.value })}
        />
      </label>
      <label>
        idField
        <input
          value={widget.idField ?? "id"}
          onChange={(e) => update({ idField: e.target.value })}
        />
      </label>
      <label>
        edgeFromField
        <input
          value={widget.edgeFromField ?? "from"}
          onChange={(e) => update({ edgeFromField: e.target.value })}
        />
      </label>
      <label>
        edgeToField
        <input
          value={widget.edgeToField ?? "to"}
          onChange={(e) => update({ edgeToField: e.target.value })}
        />
      </label>
      <label>
        layout
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
