import type { TFunction } from "i18next";
import { useEffect, useState, type ReactNode } from "react";
import type { ChartWidget } from "../../../types/dashboard";
import {
  parseBubblePointsJson,
  parseRadarAxesJson,
  serializeBubblePoints,
  serializeRadarAxes,
  type ChartBubblePointConfig,
  type ChartRadarAxis,
} from "../../../utils/analytics/chartRadarBubbleUtils";
import { VariableSelect } from "../widgetEditorStructured";
import { variableListAllowCustom } from "../widgetEditorHelpers";
import { WidgetEditorCaption } from "./widgetFieldPrimitives";

function useJsonRows<T>(
  value: string | undefined,
  parse: (raw: string | undefined) => T[],
  serialize: (rows: T[]) => string | undefined,
  onChange: (next: string | undefined) => void
): [T[], (next: T[]) => void] {
  const [rows, setRows] = useState<T[]>(() => parse(value));

  useEffect(() => {
    const incoming = serialize(parse(value)) ?? "";
    const current = serialize(rows) ?? "";
    if (incoming !== current) {
      setRows(parse(value));
    }
    // Re-sync only when the stored JSON changes outside this editor.
    // eslint-disable-next-line react-hooks/exhaustive-deps -- compare against the latest parent value
  }, [value]);

  const commit = (next: T[]) => {
    setRows(next);
    const serialized = serialize(next);
    const stored = serialize(parse(value));
    if (serialized !== stored) {
      onChange(serialized);
    }
  };

  return [rows, commit];
}

export function ChartBubblePointsEditor({
  widget,
  variables,
  variableSelectEnabled,
  onChange,
  t,
  className,
}: {
  widget: ChartWidget;
  variables: string[];
  variableSelectEnabled: boolean;
  onChange: (next: string | undefined) => void;
  t: TFunction;
  /** Read by the field grid so this block is not paired into a half-width cell. */
  className?: string;
}): ReactNode {
  const [rows, commit] = useJsonRows(
    widget.bubblePointsJson,
    parseBubblePointsJson,
    serializeBubblePoints,
    onChange
  );
  const allowCustom = variableListAllowCustom(widget);

  const patch = (index: number, patchRow: Partial<ChartBubblePointConfig>) => {
    commit(rows.map((row, i) => (i === index ? { ...row, ...patchRow } : row)));
  };

  return (
    <div className={["widget-editor-chart-points", className].filter(Boolean).join(" ")}>
      {rows.length === 0 ? <p className="hint">{t("editor.chartBubblePointsEmpty")}</p> : null}
      {rows.map((point, index) => (
        <div key={`bubble-point-${index}`} className="widget-editor-chart-point-block">
          <div className="widget-editor-chart-point-line">
            <label>
              <WidgetEditorCaption text={t("editor.chartPointLabel")} />
              <input value={point.label} onChange={(e) => patch(index, { label: e.target.value })} />
            </label>
            <VariableSelect
              label={t("editor.chartBubbleXVariable")}
              value={point.xVariable}
              onChange={(v) => patch(index, { xVariable: v })}
              variables={variables}
              allowCustom={allowCustom}
              disabled={!variableSelectEnabled}
            />
          </div>
          <div className="widget-editor-chart-point-line widget-editor-chart-point-line--action">
            <VariableSelect
              label={t("editor.chartBubbleYVariable")}
              value={point.yVariable}
              onChange={(v) => patch(index, { yVariable: v })}
              variables={variables}
              allowCustom={allowCustom}
              disabled={!variableSelectEnabled}
            />
            <VariableSelect
              label={t("editor.chartBubbleSizeVariable")}
              value={point.sizeVariable ?? ""}
              onChange={(v) => patch(index, { sizeVariable: v || undefined })}
              variables={variables}
              allowCustom={allowCustom}
              disabled={!variableSelectEnabled}
            />
            <button
              type="button"
              className="btn small danger widget-editor-chart-point-remove"
              onClick={() => commit(rows.filter((_, i) => i !== index))}
            >
              {t("editor.markerRule.remove")}
            </button>
          </div>
        </div>
      ))}
      <div className="widget-editor-list-actions">
        <button
          type="button"
          className="btn small"
          onClick={() => commit([...rows, { label: "", xVariable: "", yVariable: "" }])}
        >
          {t("editor.structured.addItem")}
        </button>
      </div>
    </div>
  );
}

export function ChartRadarAxesEditor({
  widget,
  variables,
  variableSelectEnabled,
  onChange,
  t,
  className,
}: {
  widget: ChartWidget;
  variables: string[];
  variableSelectEnabled: boolean;
  onChange: (next: string | undefined) => void;
  t: TFunction;
  /** Read by the field grid so this block is not paired into a half-width cell. */
  className?: string;
}): ReactNode {
  const [rows, commit] = useJsonRows(
    widget.radarAxesJson,
    parseRadarAxesJson,
    serializeRadarAxes,
    onChange
  );
  const allowCustom = variableListAllowCustom(widget);

  const patch = (index: number, patchRow: Partial<ChartRadarAxis>) => {
    commit(rows.map((row, i) => (i === index ? { ...row, ...patchRow } : row)));
  };

  return (
    <div className={["widget-editor-chart-points", className].filter(Boolean).join(" ")}>
      {rows.length === 0 ? <p className="hint">{t("editor.chartRadarAxesEmpty")}</p> : null}
      {rows.map((axis, index) => (
        <div key={`radar-axis-${index}`} className="widget-editor-chart-point-block">
          <div className="widget-editor-chart-point-line">
            <label>
              <WidgetEditorCaption text={t("editor.chartPointLabel")} />
              <input value={axis.label} onChange={(e) => patch(index, { label: e.target.value })} />
            </label>
            <VariableSelect
              label={t("editor.variableName")}
              value={axis.variableName}
              onChange={(v) => patch(index, { variableName: v })}
              variables={variables}
              allowCustom={allowCustom}
              disabled={!variableSelectEnabled}
            />
          </div>
          <div className="widget-editor-chart-point-line widget-editor-chart-point-line--end">
            <label>
              <WidgetEditorCaption text={t("editor.chartRadarAxisMax")} />
              <input
                type="number"
                min={1}
                value={axis.max ?? ""}
                onChange={(e) => {
                  const next = Number(e.target.value);
                  patch(index, {
                    max: e.target.value === "" || !Number.isFinite(next) || next <= 0 ? undefined : next,
                  });
                }}
              />
            </label>
            <button
              type="button"
              className="btn small danger widget-editor-chart-point-remove"
              onClick={() => commit(rows.filter((_, i) => i !== index))}
            >
              {t("editor.markerRule.remove")}
            </button>
          </div>
        </div>
      ))}
      <div className="widget-editor-list-actions">
        <button
          type="button"
          className="btn small"
          onClick={() => commit([...rows, { label: "", variableName: "" }])}
        >
          {t("editor.structured.addItem")}
        </button>
      </div>
    </div>
  );
}
