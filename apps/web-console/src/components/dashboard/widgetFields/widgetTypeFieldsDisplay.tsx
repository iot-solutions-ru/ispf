// Type-specific editor fields — display widgets (14 types).
import type { TFunction } from "i18next";
import type { ReactNode } from "react";
import { WIDGET_HISTORY_RANGE_OPTIONS } from "../../../types/dashboard";
import { VariableSelect } from "../widgetEditorStructured";
import { variableListAllowCustom } from "../widgetEditorHelpers";
import { RecordFieldGroup } from "./widgetBoundChoices";
import {
  Section,
  WidgetEditorCaption,
  type WidgetFieldContextFor,
  type WidgetTypeFieldsRegistry,
} from "./widgetFieldPrimitives";
import WidgetMediaUploadField from "../WidgetMediaUploadField";

function valueFields(ctx: WidgetFieldContextFor<"value">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.valueDisplay")} />
      <label>
        <WidgetEditorCaption text={t("editor.unit")} />
        <input value={widget.unit ?? ""} onChange={(e) => update({ unit: e.target.value })} />
      </label>
      <RecordFieldGroup
        objectPath={widget.objectPath ?? widget.modelHintPath}
        variableName={widget.variableName}
        allowCustom={variableListAllowCustom(widget)}
        showHints={false}
        fields={[
          {
            label: t("editor.unitField"),
            value: widget.unitField ?? "",
            onChange: (next) => update({ unitField: next || undefined }),
          },
        ]}
      />
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

function toggleFields(ctx: WidgetFieldContextFor<"toggle">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.toggle")} />
      <label>
        <WidgetEditorCaption text={t("editor.trueLabelOn")} />
        <input
          value={widget.trueLabel ?? ""}
          onChange={(e) => update({ trueLabel: e.target.value || undefined })}
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.trueLabelOff")} />
        <input
          value={widget.falseLabel ?? ""}
          onChange={(e) => update({ falseLabel: e.target.value || undefined })}
        />
      </label>
    </>
  );
}

function indicatorFields(ctx: WidgetFieldContextFor<"indicator">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.indicator")} />
      <label>
        <WidgetEditorCaption text={t("editor.trueLabel")} />
        <input
          value={widget.trueLabel ?? ""}
          onChange={(e) => update({ trueLabel: e.target.value || undefined })}
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.falseLabel")} />
        <input
          value={widget.falseLabel ?? ""}
          onChange={(e) => update({ falseLabel: e.target.value || undefined })}
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.trueColor")} />
        <input
          type="color"
          value={widget.trueColor ?? "#3fb950"}
          onChange={(e) => update({ trueColor: e.target.value })}
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.falseColor")} />
        <input
          type="color"
          value={widget.falseColor ?? "#f85149"}
          onChange={(e) => update({ falseColor: e.target.value })}
        />
      </label>
      <label>
        <input
          type="checkbox"
          checked={widget.alarmMode === true}
          onChange={(e) => update({ alarmMode: e.target.checked || undefined })}
        />
        <WidgetEditorCaption text={t("editor.structured.alarmMode")} />
      </label>
    </>
  );
}

function sparklineFields(ctx: WidgetFieldContextFor<"sparkline">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.sparkline")} />
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
        <WidgetEditorCaption text={t("editor.maxPoints")} />
        <input
          type="number"
          min={10}
          max={200}
          value={widget.maxPoints ?? 40}
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
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.color")} />
        <input
          type="color"
          value={widget.color ?? "#3fb950"}
          onChange={(e) => update({ color: e.target.value })}
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

function progressFields(ctx: WidgetFieldContextFor<"progress">, t: TFunction): ReactNode {
  const { widget, update, variables } = ctx;
  return (
    <>
      <Section title={t("editor.section.progress")} />
      <VariableSelect
        label={t("editor.field.currentVariable")}
        value={widget.currentVariable}
        onChange={(v) => update({ currentVariable: v })}
        variables={variables}
        allowCustom={variableListAllowCustom(widget)}
        disabled={!ctx.variableSelectEnabled}
      />
      <VariableSelect
        label={t("editor.field.maxVariable")}
        value={widget.maxVariable}
        onChange={(v) => update({ maxVariable: v })}
        variables={variables}
        allowCustom={variableListAllowCustom(widget)}
        disabled={!ctx.variableSelectEnabled}
      />
      <label>
        <WidgetEditorCaption text={t("editor.unit")} />
        <input value={widget.unit ?? ""} onChange={(e) => update({ unit: e.target.value })} />
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

function gaugeFields(ctx: WidgetFieldContextFor<"gauge">, t: TFunction): ReactNode {
  const { widget, update, variables } = ctx;
  return (
    <>
      <Section title={t("editor.section.gauge")} />
      <VariableSelect
        label={t("editor.field.minVariable")}
        value={widget.minVariable ?? ""}
        onChange={(v) => update({ minVariable: v || undefined })}
        variables={variables}
        allowCustom={variableListAllowCustom(widget)}
        disabled={!ctx.variableSelectEnabled}
      />
      <VariableSelect
        label={t("editor.field.rangeMax")}
        value={widget.maxVariable ?? ""}
        onChange={(v) => update({ maxVariable: v || undefined })}
        variables={variables}
        allowCustom={variableListAllowCustom(widget)}
        disabled={!ctx.variableSelectEnabled}
      />
      <label>
        <WidgetEditorCaption text={t("editor.minValueNoVariable")} />
        <input
          type="number"
          value={widget.minValue ?? 0}
          onChange={(e) => update({ minValue: Number(e.target.value) })}
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.maxValueNoVariable")} />
        <input
          type="number"
          value={widget.maxValue ?? 100}
          onChange={(e) => update({ maxValue: Number(e.target.value) })}
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.unit")} />
        <input value={widget.unit ?? ""} onChange={(e) => update({ unit: e.target.value })} />
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

function linearGaugeFields(ctx: WidgetFieldContextFor<"linear-gauge">, t: TFunction): ReactNode {
  const { widget, update, variables } = ctx;
  return (
    <>
      <Section title={t("editor.section.linearGauge")} />
      <VariableSelect
        label={t("editor.field.minVariable")}
        value={widget.minVariable ?? ""}
        onChange={(v) => update({ minVariable: v || undefined })}
        variables={variables}
        allowCustom={variableListAllowCustom(widget)}
        disabled={!ctx.variableSelectEnabled}
      />
      <VariableSelect
        label={t("editor.field.rangeMax")}
        value={widget.maxVariable ?? ""}
        onChange={(v) => update({ maxVariable: v || undefined })}
        variables={variables}
        allowCustom={variableListAllowCustom(widget)}
        disabled={!ctx.variableSelectEnabled}
      />
      <label>
        <WidgetEditorCaption text={t("editor.minValueNoVariable")} />
        <input
          type="number"
          value={widget.minValue ?? 0}
          onChange={(e) => update({ minValue: Number(e.target.value) })}
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.maxValueNoVariable")} />
        <input
          type="number"
          value={widget.maxValue ?? 100}
          onChange={(e) => update({ maxValue: Number(e.target.value) })}
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.unit")} />
        <input value={widget.unit ?? ""} onChange={(e) => update({ unit: e.target.value })} />
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

function liquidGaugeFields(ctx: WidgetFieldContextFor<"liquid-gauge">, t: TFunction): ReactNode {
  const { widget, update, variables } = ctx;
  return (
    <>
      <Section title={t("editor.section.liquidGauge")} />
      <VariableSelect
        label={t("editor.field.minVariable")}
        value={widget.minVariable ?? ""}
        onChange={(v) => update({ minVariable: v || undefined })}
        variables={variables}
        allowCustom={variableListAllowCustom(widget)}
        disabled={!ctx.variableSelectEnabled}
      />
      <VariableSelect
        label={t("editor.field.rangeMax")}
        value={widget.maxVariable ?? ""}
        onChange={(v) => update({ maxVariable: v || undefined })}
        variables={variables}
        allowCustom={variableListAllowCustom(widget)}
        disabled={!ctx.variableSelectEnabled}
      />
      <label>
        <WidgetEditorCaption text={t("editor.minValueNoVariable")} />
        <input
          type="number"
          value={widget.minValue ?? 0}
          onChange={(e) => update({ minValue: Number(e.target.value) })}
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.maxValueNoVariable")} />
        <input
          type="number"
          value={widget.maxValue ?? 100}
          onChange={(e) => update({ maxValue: Number(e.target.value) })}
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

function pieChartFields(ctx: WidgetFieldContextFor<"pie-chart">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.pieChart")} />
      <RecordFieldGroup
        objectPath={widget.objectPath ?? widget.modelHintPath}
        variableName={widget.variableName}
        allowCustom={variableListAllowCustom(widget)}
        fields={[
          {
            label: t("editor.field.recordLabel"),
            value: widget.labelField ?? "name",
            onChange: (next) => update({ labelField: next }),
          },
        ]}
      />
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

function labelFields(ctx: WidgetFieldContextFor<"label">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.label")} />
      <label>
        <WidgetEditorCaption text={t("editor.textStatic")} />
        <input
          value={widget.text ?? ""}
          onChange={(e) => update({ text: e.target.value || undefined })}
        />
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.textJsonAlt")} />
        <input
          value={widget.textJson ?? ""}
          onChange={(e) => update({ textJson: e.target.value || undefined })}
        />
      </label>
    </>
  );
}

function imageFields(ctx: WidgetFieldContextFor<"image">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.image")} />
      <WidgetMediaUploadField
        label={t("editor.field.imageUrl")}
        value={widget.imageUrl ?? ""}
        onChange={(imageUrl) => update({ imageUrl: imageUrl || undefined })}
        accept="image/png,image/jpeg,image/webp,image/gif,image/svg+xml,.svg"
        previewAlt={widget.alt ?? widget.title}
      />
      <label>
        <WidgetEditorCaption text={t("editor.field.alt")} />
        <input
          value={widget.alt ?? ""}
          onChange={(e) => update({ alt: e.target.value || undefined })}
        />
      </label>
    </>
  );
}

function htmlSnippetFields(ctx: WidgetFieldContextFor<"html-snippet">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("type.htmlSnippet")} />
      <label className="full">
        <WidgetEditorCaption text={t("editor.field.html")} />
        <textarea
          rows={6}
          value={widget.htmlJson ?? ""}
          onChange={(e) => update({ htmlJson: e.target.value || undefined })}
        />
      </label>
    </>
  );
}

function timerFields(ctx: WidgetFieldContextFor<"timer">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.timer")} />
      <label>
        <WidgetEditorCaption text={t("editor.field.timerMode")} />
        <select
          value={widget.mode ?? "countdown"}
          onChange={(e) => update({ mode: e.target.value as "countdown" | "elapsed" })}
        >
          <option value="countdown">{t("editor.timerMode.countdown")}</option>
          <option value="elapsed">{t("editor.timerMode.elapsed")}</option>
        </select>
      </label>
      <label>
        <WidgetEditorCaption text={t("editor.field.durationSeconds")} />
        <input
          type="number"
          min={1}
          value={widget.durationSeconds ?? 60}
          onChange={(e) => update({ durationSeconds: Number(e.target.value) })}
        />
      </label>
    </>
  );
}

function statusBadgeFields(_ctx: WidgetFieldContextFor<"status-badge">, t: TFunction): ReactNode {
  return (
    <Section title={t("editor.section.status")} hint={t("editor.statusHint")} />
  );
}

export const DISPLAY_WIDGET_FIELDS: WidgetTypeFieldsRegistry = {
  "value": valueFields,
  "toggle": toggleFields,
  "indicator": indicatorFields,
  "sparkline": sparklineFields,
  "progress": progressFields,
  "gauge": gaugeFields,
  "linear-gauge": linearGaugeFields,
  "liquid-gauge": liquidGaugeFields,
  "pie-chart": pieChartFields,
  "label": labelFields,
  "image": imageFields,
  "html-snippet": htmlSnippetFields,
  "timer": timerFields,
  "status-badge": statusBadgeFields,
};
