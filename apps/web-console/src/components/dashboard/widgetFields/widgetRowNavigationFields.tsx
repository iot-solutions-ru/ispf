// Row / card click-through navigation fields shared by table-like and map widgets.
import type { TFunction } from "i18next";
import type { ReactNode } from "react";
import { KeyValueEditor } from "../widgetEditorStructured";
import {
  DashboardPathField,
  FieldLabel,
  FormRow,
  SelectionKeyInput,
  StackedSlot,
  type WidgetFieldContext,
} from "./widgetFieldPrimitives";

export function rowNavigationFields(ctx: WidgetFieldContext, prefix: "row" | "card", t: TFunction): ReactNode {
  const w = ctx.widget;
  const update = ctx.update;

  if (prefix === "row" && w.type === "map") {
    const mw = w;
    return (
      <>
        <FormRow>
          <DashboardPathField
            caption={t("editor.rowTargetDashboard")}
            value={mw.rowTargetDashboard ?? ""}
            dashboards={ctx.dashboards}
            onChange={(v) => update({ rowTargetDashboard: v || undefined })}
          />
          <FieldLabel caption={t("editor.field.openMode")}>
            <StackedSlot>
              <select
                value={mw.rowOpenMode ?? "navigate"}
                onChange={(e) => update({ rowOpenMode: e.target.value as "navigate" | "modal" })}
                disabled={!mw.rowTargetDashboard}
              >
                <option value="navigate">{t("editor.openMode.navigate")}</option>
                <option value="modal">{t("editor.openMode.modal")}</option>
              </select>
            </StackedSlot>
          </FieldLabel>
        </FormRow>
        <FormRow>
          <FieldLabel caption={t("editor.rowSelectionSlot")}>
            <SelectionKeyInput
              value={mw.rowSelectionKey ?? ""}
              keys={ctx.selectionKeys}
              disabled={!mw.rowTargetDashboard}
              onChange={(next) => update({ rowSelectionKey: next || undefined })}
            />
          </FieldLabel>
        <KeyValueEditor
          label={t("editor.openScreenParams")}
          keyCaption={t("editor.col.sessionParam")}
          valueCaption={t("editor.col.value")}
          keySuggestions={ctx.sessionParams}
          value={mw.rowParamsJson}
          onChange={(v) => update({ rowParamsJson: v })}
        />
        </FormRow>
      </>
    );
  }

  if (prefix === "row" && w.type === "object-table") {
    const tw = w;
    return (
      <>
        <FormRow>
          <DashboardPathField
            caption={t("editor.rowTargetDashboard")}
            value={tw.rowTargetDashboard ?? ""}
            dashboards={ctx.dashboards}
            onChange={(v) => update({ rowTargetDashboard: v || undefined })}
          />
          <FieldLabel caption={t("editor.field.openMode")}>
            <StackedSlot>
              <select
                value={tw.rowOpenMode ?? "navigate"}
                onChange={(e) => update({ rowOpenMode: e.target.value as "navigate" | "modal" })}
                disabled={!tw.rowTargetDashboard}
              >
                <option value="navigate">{t("editor.openMode.navigate")}</option>
                <option value="modal">{t("editor.openMode.modal")}</option>
              </select>
            </StackedSlot>
          </FieldLabel>
        </FormRow>
        <FormRow>
          <FieldLabel caption={t("editor.rowSelectionSlot")}>
            <SelectionKeyInput
              value={tw.rowSelectionKey ?? ""}
              keys={ctx.selectionKeys}
              disabled={!tw.rowTargetDashboard}
              onChange={(next) => update({ rowSelectionKey: next || undefined })}
            />
          </FieldLabel>
        <KeyValueEditor
          label={t("editor.openScreenParams")}
          keyCaption={t("editor.col.sessionParam")}
          valueCaption={t("editor.col.value")}
          keySuggestions={ctx.sessionParams}
          value={tw.rowParamsJson}
          onChange={(v) => update({ rowParamsJson: v })}
        />
        </FormRow>
      </>
    );
  }

  if (prefix === "card" && w.type === "card-grid") {
    const cw = w;
    return (
      <>
        <FormRow>
          <DashboardPathField
            caption={t("editor.field.cardTargetDashboard")}
            value={cw.cardTargetDashboard ?? ""}
            dashboards={ctx.dashboards}
            onChange={(v) => update({ cardTargetDashboard: v || undefined })}
          />
          <FieldLabel caption={t("editor.field.openMode")}>
            <StackedSlot>
              <select
                value={cw.cardOpenMode ?? "navigate"}
                onChange={(e) => update({ cardOpenMode: e.target.value as "navigate" | "modal" })}
                disabled={!cw.cardTargetDashboard}
              >
                <option value="navigate">{t("editor.openMode.navigate")}</option>
                <option value="modal">{t("editor.openMode.modal")}</option>
              </select>
            </StackedSlot>
          </FieldLabel>
        </FormRow>
        <FormRow>
          <FieldLabel caption={t("editor.cardSelectionKey")}>
            <SelectionKeyInput
              value={cw.cardSelectionKey ?? ""}
              keys={ctx.selectionKeys}
              disabled={!cw.cardTargetDashboard}
              onChange={(next) => update({ cardSelectionKey: next || undefined })}
            />
          </FieldLabel>
        <KeyValueEditor
          label={t("editor.openScreenParams")}
          keyCaption={t("editor.col.sessionParam")}
          valueCaption={t("editor.col.value")}
          keySuggestions={ctx.sessionParams}
          value={cw.cardParamsJson}
          onChange={(v) => update({ cardParamsJson: v })}
        />
        </FormRow>
      </>
    );
  }

  return null;
}
