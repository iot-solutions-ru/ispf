// Data-source section shared by every widget type (object / variable / analytics binding).
import { useTranslation } from "react-i18next";
import { parseAnalyticsQueryTags } from "../../../hooks/useAnalyticsMultiSeries";
import type { DashboardWidget } from "../../../types/dashboard";
import { DATA_BINDING_HINT_KEYS, WIDGET_TYPE_HINT_KEYS, widgetDataBinding } from "../widgetEditorBinding";
import {
  ChartAnalyticsQueryTagsField,
  FieldLabel,
  FieldPairs,
  FormRow,
  PathSelect,
  Section,
  SelectionKeyInput,
  StackedSlot,
  type WidgetFieldContext,
} from "./widgetFieldPrimitives";

export function WidgetDataSourceFields(ctx: WidgetFieldContext) {
  const { t } = useTranslation(["widgets", "common"]);
  const { widget, objects, variables, variableSelectEnabled, selectionKeys, update } = ctx;
  const binding = widgetDataBinding(widget.type);
  const bindingHint = t(DATA_BINDING_HINT_KEYS[binding], { defaultValue: "" });
  const typeHintKey = WIDGET_TYPE_HINT_KEYS[widget.type];
  const typeHint = typeHintKey ? t(typeHintKey, { defaultValue: "" }) : "";
  const chartWidget = widget.type === "chart" ? widget : null;
  const multiQueryTags = chartWidget ? parseAnalyticsQueryTags(chartWidget.analyticsQueryTagsJson) : [];
  const usesMultiTagQuery = multiQueryTags.length > 0;

  return (
    <FieldPairs>
      <Section
        title={t("editor.dataSource")}
        hint={[bindingHint, typeHint].filter(Boolean).join(" ")}
      />

      {chartWidget && (
        <>
          <Section title={t("editor.section.analyticsQueryTags")} />
          <ChartAnalyticsQueryTagsField widget={chartWidget} objects={objects} update={update} />
        </>
      )}

      {(binding === "object-variable" || binding === "object-only") && !usesMultiTagQuery && (
        <>
          <FormRow>
            <PathSelect
              label={t("editor.objectPath")}
              value={widget.objectPath ?? ""}
              onChange={(path) => update({ objectPath: path || undefined, variableName: "" })}
            />
            <FieldLabel
              caption={t("editor.selectionKey")}
              code="selectionKey"
              hint={t("editor.placeholder.selectionPath")}
            >
              <div className="field-controls-slot field-controls-slot--stacked">
                <SelectionKeyInput
                  value={widget.selectionKey ?? ""}
                  keys={selectionKeys}
                  onChange={(next) => update({ selectionKey: next || undefined })}
                />
              </div>
            </FieldLabel>
          </FormRow>
          <FormRow>
            <FieldLabel caption={t("editor.contextPathKey")} hint={t("editor.placeholder.contextPathEmpty")}>
              <input
                value={widget.contextPathKey ?? ""}
                onChange={(e) => update({ contextPathKey: e.target.value || undefined })}
              />
            </FieldLabel>
            <FieldLabel caption={t("editor.modelHintPath")}>
              <select
                value={widget.modelHintPath ?? ""}
                onChange={(e) => update({ modelHintPath: e.target.value || undefined })}
              >
                <option value="">—</option>
                {objects.map((o) => (
                  <option key={o.path} value={o.path}>
                    {o.displayName}
                  </option>
                ))}
              </select>
            </FieldLabel>
          </FormRow>
        </>
      )}

      {binding === "object-variable" &&
        widget.type !== "spreadsheet" &&
        !usesMultiTagQuery &&
        !(
          widget.type === "chart" &&
          ((widget.chartType ?? widget.chartStyle) === "bubble" ||
            (widget.chartType ?? widget.chartStyle) === "radar")
        ) && (
        <FormRow>
          <FieldLabel
            caption={t("editor.variableName")}
            hint={!widget.objectPath && widget.selectionKey ? t("editor.placeholder.orEnterVariable") : undefined}
          >
            <div
              className={
                !widget.objectPath && widget.selectionKey
                  ? "field-controls"
                  : "field-controls-slot field-controls-slot--stacked"
              }
            >
              <select
                value={widget.variableName ?? ""}
                onChange={(e) => update({ variableName: e.target.value || undefined })}
                disabled={!variableSelectEnabled}
              >
                <option value="">—</option>
                {variables.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
              </select>
              {!widget.objectPath && widget.selectionKey && (
                <input
                  value={widget.variableName ?? ""}
                  onChange={(e) => update({ variableName: e.target.value || undefined })}
                />
              )}
            </div>
          </FieldLabel>
          <FieldLabel caption={t("editor.valueField")} hint="value">
            <div className="field-controls-slot field-controls-slot--stacked">
              <input
                value={widget.valueField ?? "value"}
                onChange={(e) => update({ valueField: e.target.value || undefined })}
              />
            </div>
          </FieldLabel>
        </FormRow>
      )}

      {widget.type === "spreadsheet" && (
        <p className="hint widget-editor-type-hint">{t("editor.spreadsheet.dataSourceHint")}</p>
      )}

      {binding === "parent-catalog" && (
        <FormRow>
          <PathSelect
            label={t("editor.parentPath")}
            value={(widget as { parentPath?: string }).parentPath ?? ""}
            onChange={(path) => update({ parentPath: path } as Partial<DashboardWidget>)}
            placeholder="root.platform.devices"
          />
          <FieldLabel caption={t("editor.selectionKeyOnClick")} code="selectionKey" hint="device">
            <StackedSlot>
              <SelectionKeyInput
                value={widget.selectionKey ?? ""}
                keys={selectionKeys}
                onChange={(next) => update({ selectionKey: next || undefined })}
              />
            </StackedSlot>
          </FieldLabel>
        </FormRow>
      )}

      {(binding === "session" || widget.paramKey) && (
        <label>
          {t("editor.paramKey")}
          <input
            value={widget.paramKey ?? ""}
            onChange={(e) => update({ paramKey: e.target.value || undefined })}
          />
        </label>
      )}
    </FieldPairs>
  );
}
