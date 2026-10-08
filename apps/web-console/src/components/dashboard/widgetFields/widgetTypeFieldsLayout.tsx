// Type-specific editor fields — layout widgets (13 types).
import type { TFunction } from "i18next";
import type { ReactNode } from "react";
import {
  AdvancedJsonField,
  IdLabelListEditor,
  KeyValueEditor,
  NavMenuItemsEditor,
  StringListEditor,
  TabPanelMetaEditor,
  VariableSelect,
} from "../widgetEditorStructured";
import { variableListAllowCustom } from "../widgetEditorHelpers";
import {
  DashboardPathField,
  ObjectFunctionSelect,
  Section,
  StackedSlot,
  type WidgetFieldContextFor,
  type WidgetTypeFieldsRegistry,
} from "./widgetFieldPrimitives";
import { rowNavigationFields } from "./widgetRowNavigationFields";
import ScadaMimicWidgetEditorFields from "../ScadaMimicWidgetEditorFields";
import WidgetMediaUploadField from "../WidgetMediaUploadField";
import SvgWidgetInteractiveEditor from "../SvgWidgetInteractiveEditor";

function cardGridFields(ctx: WidgetFieldContextFor<"card-grid">, t: TFunction): ReactNode {
  const { widget, update, allVariableNames } = ctx;
  return (
    <>
      <Section title={t("editor.section.cardGrid")} />
      <StringListEditor
        label={t("editor.field.variablesToShow")}
        value={widget.variablesJson}
        onChange={(v) => update({ variablesJson: v })}
        suggestions={allVariableNames}
      />
      {rowNavigationFields(ctx, "card", t)}
    </>
  );
}

function dashboardLinkFields(ctx: WidgetFieldContextFor<"dashboard-link">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.dashboardLink")} />
      <DashboardPathField
        caption={t("editor.field.targetDashboard")}
        value={widget.targetDashboardPath}
        dashboards={ctx.dashboards}
        onChange={(v) => update({ targetDashboardPath: v })}
      />
      <label>
        {t("editor.field.openMode")}
        <StackedSlot>
          <select
            value={widget.openMode ?? "navigate"}
            onChange={(e) => update({ openMode: e.target.value as "navigate" | "modal" })}
          >
            <option value="navigate">{t("editor.openMode.navigate")}</option>
            <option value="modal">{t("editor.openMode.modal")}</option>
          </select>
        </StackedSlot>
      </label>
      <label>
        {t("editor.field.buttonLabel")}
        <input
          value={widget.buttonLabel ?? ""}
          onChange={(e) => update({ buttonLabel: e.target.value })}
        />
      </label>
      <label>
        {t("editor.field.modalTitle")}
        <input
          value={widget.modalTitle ?? ""}
          onChange={(e) => update({ modalTitle: e.target.value || undefined })}
          disabled={widget.openMode !== "modal"}
        />
      </label>
      <label>
        {t("editor.field.confirmMessage")}
        <input
          value={widget.confirmMessage ?? ""}
          onChange={(e) => update({ confirmMessage: e.target.value || undefined })}
        />
      </label>
      <KeyValueEditor
        label={t("editor.contextSelection")}
        code="contextSelectionJson"
        keyCaption={t("editor.col.slotName")}
        valueCaption={t("editor.col.objectPath")}
        value={widget.contextSelectionJson}
        onChange={(v) => update({ contextSelectionJson: v })}
      />
      <KeyValueEditor
        label={t("editor.openScreenParams")}
        code="contextParamsJson"
        keyCaption={t("editor.col.sessionParam")}
        valueCaption={t("editor.col.value")}
        keySuggestions={ctx.sessionParams}
        value={widget.contextParamsJson}
        onChange={(v) => update({ contextParamsJson: v })}
      />
      <StringListEditor
        label={t("editor.requireSessionParamsNavigate")}
        code="requireSessionParamsJson"
        value={widget.requireSessionParamsJson}
        onChange={(v) => update({ requireSessionParamsJson: v || undefined })}
      />
      <p className="hint">{t("editor.deprecation.requireSessionParamsJson")}</p>
    </>
  );
}

function svgWidgetFields(ctx: WidgetFieldContextFor<"svg-widget">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("type.svgWidget")} />
      <WidgetMediaUploadField
        label={t("editor.field.svgUrl")}
        value={widget.svgUrl ?? ""}
        onChange={(svgUrl) => update({ svgUrl })}
        accept=".svg,image/svg+xml,image/png,image/jpeg,image/webp,image/gif"
        placeholder={t("editor.hint.exampleSvg")}
        previewAlt={widget.title}
      />
      <label>
        {t("editor.field.clickAction")}
        <select
          value={widget.clickAction ?? ""}
          onChange={(e) =>
            update({
              clickAction: (e.target.value || undefined) as "function" | "toggle" | undefined,
            })
          }
        >
          <option value="">—</option>
          <option value="function">{t("editor.clickAction.function")}</option>
          <option value="toggle">{t("editor.clickAction.toggle")}</option>
        </select>
      </label>
      {widget.clickAction === "function" && (
        <ObjectFunctionSelect
          label={t("editor.field.functionName")}
          code="functionName"
          objectPath={widget.objectPath}
          value={widget.functionName ?? ""}
          onChange={(next) => update({ functionName: next || undefined })}
        />
      )}
      {widget.clickAction === "toggle" && (
        <VariableSelect
          label={t("editor.field.toggleVariable")}
          value={widget.toggleVariable ?? widget.variableName ?? ""}
          onChange={(v) => update({ toggleVariable: v })}
          variables={ctx.variables}
          allowCustom={variableListAllowCustom(widget)}
          disabled={!ctx.variableSelectEnabled}
        />
      )}
      <label>
        {t("editor.field.confirmMessage")}
        <input
          value={widget.confirmMessage ?? ""}
          onChange={(e) => update({ confirmMessage: e.target.value || undefined })}
        />
      </label>
      <SvgWidgetInteractiveEditor widget={widget} update={update} selectionKeys={ctx.selectionKeys} />
      <label>
        <input
          type="checkbox"
          checked={widget.showLegend !== false}
          onChange={(e) => update({ showLegend: e.target.checked })}
        />
        {t("editor.field.showLegend")}
      </label>
      <label>
        <input
          type="checkbox"
          checked={widget.panEnabled !== false}
          onChange={(e) => update({ panEnabled: e.target.checked })}
        />
        {t("editor.field.panEnabled")}
      </label>
    </>
  );
}

function subDashboardFields(ctx: WidgetFieldContextFor<"sub-dashboard">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.subDashboard")} />
      <DashboardPathField
        caption={t("editor.field.targetDashboard")}
        value={widget.targetDashboardPath ?? ""}
        dashboards={ctx.dashboards}
        onChange={(v) => update({ targetDashboardPath: v || undefined })}
      />
      <label>
        {t("editor.targetDashboardPathKeyFromParams")}
        <StackedSlot>
          <input
            value={widget.targetDashboardPathKey ?? ""}
            onChange={(e) => update({ targetDashboardPathKey: e.target.value || undefined })}
          />
        </StackedSlot>
      </label>
      <label>
        <input
          type="checkbox"
          checked={widget.inheritContext !== false}
          onChange={(e) => update({ inheritContext: e.target.checked })}
        />
        {t("editor.field.inheritContext")}
      </label>
    </>
  );
}

function panelFields(ctx: WidgetFieldContextFor<"panel">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.panel")} />
      <label>
        {t("editor.field.variant")}
        <input
          value={widget.variant ?? "simple"}
          onChange={(e) => update({ variant: e.target.value as "simple" })}
        />
      </label>
      <label className="full">
        <input
          type="checkbox"
          checked={widget.collapsible === true}
          onChange={(e) => update({ collapsible: e.target.checked })}
        />
        {t("editor.field.collapsible")}
      </label>
      <AdvancedJsonField
        label={t("editor.field.childrenJson")}
        value={widget.childrenJson}
        onChange={(v) => update({ childrenJson: v ?? "[]" })}
        rows={8}
      />
    </>
  );
}

function compositeWidgetFields(ctx: WidgetFieldContextFor<"composite-widget" | "drawer-panel">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={widget.type === "drawer-panel" ? t("editor.drawerPanel") : t("editor.composite")} />
      {widget.type === "drawer-panel" && (
        <label>
          {t("editor.field.drawerLabel")}
          <input
            value={widget.drawerLabel ?? ""}
            onChange={(e) => update({ drawerLabel: e.target.value || undefined })}
          />
        </label>
      )}
      <AdvancedJsonField
        label={t("editor.field.childrenJson")}
        value={widget.childrenJson}
        onChange={(v) => update({ childrenJson: v ?? "[]" })}
        rows={8}
      />
    </>
  );
}

function tabPanelFields(ctx: WidgetFieldContextFor<"tab-panel">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.tabs")} />
      <TabPanelMetaEditor
        value={widget.tabsJson}
        onChange={(v) => update({ tabsJson: v })}
      />
      <AdvancedJsonField
        label={t("editor.field.tabsJson")}
        value={widget.tabsJson}
        onChange={(v) => update({ tabsJson: v ?? "[]" })}
        rows={8}
      />
    </>
  );
}

function breadcrumbsFields(ctx: WidgetFieldContextFor<"breadcrumbs">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.breadcrumbs")} />
      <label>
        {t("editor.pathKeyInParams")}
        <input
          value={widget.pathKey ?? ""}
          onChange={(e) => update({ pathKey: e.target.value || undefined })}
        />
      </label>
      <label>
        {t("editor.field.separator")}
        <input
          value={widget.separator ?? " / "}
          onChange={(e) => update({ separator: e.target.value })}
        />
      </label>
    </>
  );
}

function carouselFields(ctx: WidgetFieldContextFor<"carousel">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.carousel")} />
      <AdvancedJsonField
        label={t("editor.field.slidesJson")}
        value={widget.slidesJson}
        onChange={(v) => update({ slidesJson: v ?? "[]" })}
        rows={6}
      />
      <label>
        {t("editor.autoplayOff")}
        <input
          type="number"
          min={0}
          value={widget.autoplayMs ?? 0}
          onChange={(e) => update({ autoplayMs: Number(e.target.value) })}
        />
      </label>
    </>
  );
}

function stepsPanelFields(ctx: WidgetFieldContextFor<"steps-panel">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.steps")} />
      <IdLabelListEditor
        label={t("editor.field.stepsJson")}
        value={widget.stepsJson}
        onChange={(v) => update({ stepsJson: v })}
        idPrefix="step"
      />
      <AdvancedJsonField
        label={t("editor.field.stepsJson")}
        value={widget.stepsJson}
        onChange={(v) => update({ stepsJson: v ?? "[]" })}
        rows={6}
      />
      <label>
        {t("editor.activeStepKeyInParams")}
        <input
          value={widget.activeStepKey ?? ""}
          onChange={(e) => update({ activeStepKey: e.target.value || undefined })}
        />
      </label>
    </>
  );
}

function navMenuFields(ctx: WidgetFieldContextFor<"nav-menu">, t: TFunction): ReactNode {
  const { widget, update, dashboards } = ctx;
  return (
    <>
      <Section title={t("editor.section.navMenu")} />
      <NavMenuItemsEditor
        value={widget.itemsJson}
        onChange={(v) => update({ itemsJson: v })}
        dashboards={dashboards}
      />
    </>
  );
}

function scadaMimicFields(ctx: WidgetFieldContextFor<"scada-mimic">): ReactNode {
  const { widget, update } = ctx;
  return <ScadaMimicWidgetEditorFields widget={widget} update={update} />;
}

export const LAYOUT_WIDGET_FIELDS: WidgetTypeFieldsRegistry = {
  "card-grid": cardGridFields,
  "dashboard-link": dashboardLinkFields,
  "svg-widget": svgWidgetFields,
  "sub-dashboard": subDashboardFields,
  "panel": panelFields,
  "composite-widget": compositeWidgetFields,
  "drawer-panel": compositeWidgetFields,
  "tab-panel": tabPanelFields,
  "breadcrumbs": breadcrumbsFields,
  "carousel": carouselFields,
  "steps-panel": stepsPanelFields,
  "nav-menu": navMenuFields,
  "scada-mimic": scadaMimicFields,
};
