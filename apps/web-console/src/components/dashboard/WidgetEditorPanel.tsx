import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { Button, Checkbox, Select, Space, Typography } from "antd";
import type { DashboardWidget, WidgetType } from "../../types/dashboard";
import { DASHBOARD_COLUMNS, newWidget, WIDGET_TYPES } from "../../types/dashboard";
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
import {
  layoutGridMaxW,
  layoutGridMaxX,
  parseLayoutGridInt,
  parseOptionalZIndex,
} from "../../utils/dashboard/widgetLayoutFieldParse";
import { collectDashboardSelectionKeys } from "./selectionKeys";

/** Local text while editing so layout fields can be cleared without snapping back. */
function LayoutGridInput({
  syncKey,
  value,
  min,
  max,
  onCommit,
}: {
  syncKey: string;
  value: number;
  min: number;
  max?: number;
  onCommit: (next: number) => void;
}) {
  const [text, setText] = useState(() => String(value));

  useEffect(() => {
    setText(String(value));
  }, [syncKey, value]);

  return (
    <input
      type="number"
      min={min}
      max={max}
      value={text}
      onChange={(e) => {
        const raw = e.target.value;
        setText(raw);
        const next = parseLayoutGridInt(raw, min, max);
        if (next !== undefined) {
          onCommit(next);
        }
      }}
      onBlur={() => {
        const next = parseLayoutGridInt(text, min, max);
        if (next !== undefined) {
          setText(String(next));
          onCommit(next);
        } else {
          setText(String(value));
        }
      }}
    />
  );
}

function OptionalZIndexInput({
  syncKey,
  value,
  placeholder,
  onCommit,
}: {
  syncKey: string;
  value: number | undefined;
  placeholder: string;
  onCommit: (next: number | undefined) => void;
}) {
  const [text, setText] = useState(() => (value === undefined ? "" : String(value)));

  useEffect(() => {
    setText(value === undefined ? "" : String(value));
  }, [syncKey, value]);

  return (
    <input
      type="number"
      value={text}
      placeholder={placeholder}
      onChange={(e) => {
        const raw = e.target.value;
        setText(raw);
        const parsed = parseOptionalZIndex(raw);
        if (parsed === null) {
          return;
        }
        onCommit(parsed);
      }}
      onBlur={() => {
        const parsed = parseOptionalZIndex(text);
        if (parsed === null) {
          setText(value === undefined ? "" : String(value));
          return;
        }
        setText(parsed === undefined ? "" : String(parsed));
        onCommit(parsed);
      }}
    />
  );
}

interface WidgetEditorPanelProps {
  widget: DashboardWidget | null;
  widgets: DashboardWidget[];
  objects: Array<{ path: string; displayName: string; variableNames: string[] }>;
  dashboards?: Array<{ path: string; displayName: string }>;
  reports?: Array<{ path: string; displayName: string }>;
  onChange: (widget: DashboardWidget) => void;
  onWidgetsChange: (widgets: DashboardWidget[]) => void;
  onDelete: () => void;
  /** Dashboard fine-grid column count (default {@link DASHBOARD_COLUMNS}). */
  gridColumns?: number;
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
  gridColumns = DASHBOARD_COLUMNS,
}: WidgetEditorPanelProps) {
  const { t } = useTranslation(["dashboard", "widgets", "common"]);

  const allVariableNames = useMemo(
    () => [...new Set(objects.flatMap((o) => o.variableNames))].sort(),
    [objects]
  );
  const selectionKeys = useMemo(() => collectDashboardSelectionKeys(widgets), [widgets]);

  if (!widget) {
    return (
      <aside className="dashboard-sidebar">
        <Typography.Title level={4}>{t("editor.widgetTitle")}</Typography.Title>
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

  const maxX = layoutGridMaxX(gridColumns, widget.w);
  const maxW = layoutGridMaxW(gridColumns, widget.x);

  const fieldCtx = {
    widget,
    objects,
    dashboards,
    reports,
    variables,
    allVariableNames,
    variableSelectEnabled,
    selectionKeys,
    update,
  };

  return (
    <aside className="dashboard-sidebar">
      <header className="dashboard-sidebar-head">
        <Typography.Title level={4}>{t("editor.widgetEditorTitle")}</Typography.Title>
        <Button danger size="small" onClick={onDelete}>
          {t("common:action.delete")}
        </Button>
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
            <Select
              value={widget.type}
              options={WIDGET_TYPES.map((item) => ({
                value: item.type,
                label: translateWidgetType(t, item.type),
              }))}
              onChange={(nextType: WidgetType) => {
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
            />
          </label>
          <label>
            <span className="field-caption">x</span>
            <LayoutGridInput
              syncKey={widget.id}
              value={widget.x}
              min={0}
              max={maxX}
              onCommit={(x) => update({ x })}
            />
          </label>
          <label>
            <span className="field-caption">y</span>
            <LayoutGridInput
              syncKey={widget.id}
              value={widget.y}
              min={0}
              onCommit={(y) => update({ y })}
            />
          </label>
          <label>
            <span className="field-caption">w</span>
            <LayoutGridInput
              syncKey={widget.id}
              value={widget.w}
              min={1}
              max={maxW}
              onCommit={(w) => update({ w })}
            />
          </label>
          <label>
            <span className="field-caption">h</span>
            <LayoutGridInput
              syncKey={widget.id}
              value={widget.h}
              min={1}
              onCommit={(h) => update({ h })}
            />
          </label>
          <p className="hint full">
            {t("editor.layoutHint")}{" "}
            {t("editor.layoutGridBounds", { columns: gridColumns, maxX, maxW })}
          </p>

          <h5 className="widget-editor-section">{t("editor.layerTitle")}</h5>
          <label className="widget-layer-visible">
            <Checkbox
              checked={widget.visible !== false}
              onChange={(e) => update({ visible: e.target.checked })}
            >
              {t("editor.layerVisible")}
            </Checkbox>
          </label>
          <label>
            <span className="field-caption">{t("editor.layerZIndex")}</span>
            <OptionalZIndexInput
              syncKey={widget.id}
              value={widget.zIndex}
              placeholder={t("editor.layerZIndexAuto")}
              onCommit={(zIndex) => update({ zIndex })}
            />
          </label>
          <Space className="widget-layer-actions full" wrap>
            <Button
              size="small"
              onClick={() => onWidgetsChange(sendWidgetBackward(widgets, widget.id))}
            >
              {t("editor.layerBackward")}
            </Button>
            <Button
              size="small"
              onClick={() => onWidgetsChange(bringWidgetForward(widgets, widget.id))}
            >
              {t("editor.layerForward")}
            </Button>
            <Button
              size="small"
              onClick={() => onWidgetsChange(sendWidgetToBack(widgets, widget.id))}
            >
              {t("editor.layerToBack")}
            </Button>
            <Button
              size="small"
              onClick={() => onWidgetsChange(bringWidgetToFront(widgets, widget.id))}
            >
              {t("editor.layerToFront")}
            </Button>
          </Space>
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
