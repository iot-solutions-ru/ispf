import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { DashboardWidget, WidgetType } from "../../types/dashboard";
import { newWidget, WIDGET_TYPES } from "../../types/dashboard";
import { translateWidgetType } from "./widgetI18n";
import {
  FieldPairs,
  WidgetDataSourceFields,
  WidgetTypeSpecificFields,
} from "./widgetEditorFields";
import { WidgetStylesEditor } from "./widgetEditorStructured";

interface WidgetEditorPanelProps {
  widget: DashboardWidget | null;
  objects: Array<{ path: string; displayName: string; variableNames: string[] }>;
  dashboards?: Array<{ path: string; displayName: string }>;
  reports?: Array<{ path: string; displayName: string }>;
  onChange: (widget: DashboardWidget) => void;
  onDelete: () => void;
}

export default function WidgetEditorPanel({
  widget,
  objects,
  dashboards = [],
  reports = [],
  onChange,
  onDelete,
}: WidgetEditorPanelProps) {
  const { t } = useTranslation(["dashboard", "widgets", "common"]);

  const allVariableNames = useMemo(
    () => [...new Set(objects.flatMap((o) => o.variableNames))].sort(),
    [objects]
  );

  if (!widget) {
    return (
      <aside className="dashboard-sidebar">
        <h4>{t("editor.widgetTitle")}</h4>
        <p className="hint">{t("editor.selectWidgetHint")}</p>
      </aside>
    );
  }

  const hintPath = widget.modelHintPath || widget.objectPath;
  const variables =
    objects.find((ctx) => ctx.path === hintPath)?.variableNames ?? [];
  const variableSelectEnabled = Boolean(hintPath) || Boolean(widget.selectionKey);

  const update = (patch: Partial<DashboardWidget>) => {
    onChange({ ...widget, ...patch } as DashboardWidget);
  };

  const fieldCtx = {
    widget,
    objects,
    dashboards,
    reports,
    variables,
    allVariableNames,
    variableSelectEnabled,
    update,
  };

  return (
    <aside className="dashboard-sidebar">
      <header className="dashboard-sidebar-head">
        <h4>{t("editor.widgetEditorTitle")}</h4>
        <button type="button" className="btn danger small" onClick={onDelete}>
          {t("common:action.delete")}
        </button>
      </header>

      <div className="form-grid compact widget-editor-form">
        <FieldPairs>
          <h5 className="widget-editor-section">{t("editor.general")}</h5>
          <label>
            <span className="field-caption">{t("editor.titleField")}</span>
            <input value={widget.title} onChange={(e) => update({ title: e.target.value })} />
          </label>
          <label>
            <span className="field-caption">{t("editor.typeField")}</span>
            <select
              value={widget.type}
              onChange={(e) => {
                const nextType = e.target.value as WidgetType;
                const next = newWidget(nextType, 0);
                onChange({
                  ...next,
                  id: widget.id,
                  title: widget.title,
                  x: widget.x,
                  y: widget.y,
                  w: widget.w,
                  h: widget.h,
                });
              }}
            >
              {WIDGET_TYPES.map((item) => (
                <option key={item.type} value={item.type}>
                  {translateWidgetType(t, item.type)}
                </option>
              ))}
            </select>
          </label>
          <label>
            <span className="field-caption">x</span>
            <input
              type="number"
              min={0}
              max={11}
              value={widget.x}
              onChange={(e) => update({ x: Number(e.target.value) })}
            />
          </label>
          <label>
            <span className="field-caption">y</span>
            <input
              type="number"
              min={0}
              value={widget.y}
              onChange={(e) => update({ y: Number(e.target.value) })}
            />
          </label>
          <label>
            <span className="field-caption">w</span>
            <input
              type="number"
              min={1}
              max={12}
              value={widget.w}
              onChange={(e) => update({ w: Number(e.target.value) })}
            />
          </label>
          <label>
            <span className="field-caption">h</span>
            <input
              type="number"
              min={1}
              value={widget.h}
              onChange={(e) => update({ h: Number(e.target.value) })}
            />
          </label>
          <p className="hint full">{t("editor.layoutHint")}</p>
        </FieldPairs>

        <WidgetDataSourceFields {...fieldCtx} />
        <WidgetTypeSpecificFields {...fieldCtx} />

        <FieldPairs>
          <WidgetStylesEditor
            value={widget.stylesJson}
            onChange={(v) => update({ stylesJson: v })}
          />

          <h5 className="widget-editor-section">{t("editor.advanced")}</h5>
          <label className="full">
            {t("editor.demoPreviewLabel")}
            <textarea
              rows={3}
              className="mono"
              value={widget.demoPreviewJson ?? ""}
              onChange={(e) => update({ demoPreviewJson: e.target.value || undefined })}
            />
          </label>
        </FieldPairs>
      </div>
    </aside>
  );
}
