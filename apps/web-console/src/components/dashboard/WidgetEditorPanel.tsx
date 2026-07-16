import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { DashboardWidget, WidgetType } from "../../types/dashboard";
import { newWidget, WIDGET_TYPES } from "../../types/dashboard";
import { translateWidgetType } from "./widgetI18n";
import {
  FieldPairs,
  WidgetDataSourceFields,
  WidgetTypeSpecificFields,
} from "./widgetFields";
import { WidgetStylesEditor } from "./widgetEditorStructured";
import {
  bringWidgetForward,
  bringWidgetToFront,
  sendWidgetBackward,
  sendWidgetToBack,
} from "./widgetLayerUtils";

interface WidgetEditorPanelProps {
  widget: DashboardWidget | null;
  widgets: DashboardWidget[];
  objects: Array<{ path: string; displayName: string; variableNames: string[] }>;
  dashboards?: Array<{ path: string; displayName: string }>;
  reports?: Array<{ path: string; displayName: string }>;
  onChange: (widget: DashboardWidget) => void;
  onWidgetsChange: (widgets: DashboardWidget[]) => void;
  onDelete: () => void;
}

export default function WidgetEditorPanel({
  widget,
  widgets,
  objects,
  dashboards = [],
  reports = [],
  onChange,
  onWidgetsChange,
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
                  zIndex: widget.zIndex,
                  visible: widget.visible,
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

          <h5 className="widget-editor-section">{t("editor.layerTitle")}</h5>
          <label className="widget-layer-visible">
            <input
              type="checkbox"
              checked={widget.visible !== false}
              onChange={(e) => update({ visible: e.target.checked })}
            />
            <span>{t("editor.layerVisible")}</span>
          </label>
          <label>
            <span className="field-caption">{t("editor.layerZIndex")}</span>
            <input
              type="number"
              value={widget.zIndex ?? ""}
              placeholder={t("editor.layerZIndexAuto")}
              onChange={(e) => {
                const raw = e.target.value.trim();
                update({ zIndex: raw === "" ? undefined : Number(raw) });
              }}
            />
          </label>
          <div className="widget-layer-actions full">
            <button
              type="button"
              className="btn small"
              onClick={() => onWidgetsChange(sendWidgetBackward(widgets, widget.id))}
            >
              {t("editor.layerBackward")}
            </button>
            <button
              type="button"
              className="btn small"
              onClick={() => onWidgetsChange(bringWidgetForward(widgets, widget.id))}
            >
              {t("editor.layerForward")}
            </button>
            <button
              type="button"
              className="btn small"
              onClick={() => onWidgetsChange(sendWidgetToBack(widgets, widget.id))}
            >
              {t("editor.layerToBack")}
            </button>
            <button
              type="button"
              className="btn small"
              onClick={() => onWidgetsChange(bringWidgetToFront(widgets, widget.id))}
            >
              {t("editor.layerToFront")}
            </button>
          </div>
          <p className="hint full">{t("editor.layerHint")}</p>
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
