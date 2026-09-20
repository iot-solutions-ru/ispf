// Row / card click-through navigation fields shared by table-like and map widgets.
import type { TFunction } from "i18next";
import type { ReactNode } from "react";
import { KeyValueEditor } from "../widgetEditorStructured";
import {
  DashboardPathField,
  FieldLabel,
  FormRow,
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
          <FieldLabel caption="rowOpenMode">
            <StackedSlot>
              <select
                value={mw.rowOpenMode ?? "navigate"}
                onChange={(e) => update({ rowOpenMode: e.target.value as "navigate" | "modal" })}
                disabled={!mw.rowTargetDashboard}
              >
                <option value="navigate">navigate</option>
                <option value="modal">modal</option>
              </select>
            </StackedSlot>
          </FieldLabel>
        </FormRow>
        <FormRow>
          <FieldLabel caption="rowSelectionKey">
            <input
              value={mw.rowSelectionKey ?? ""}
              onChange={(e) => update({ rowSelectionKey: e.target.value || undefined })}
              disabled={!mw.rowTargetDashboard}
            />
          </FieldLabel>
        <KeyValueEditor
          label="rowParamsJson"
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
            caption="rowTargetDashboard"
            value={tw.rowTargetDashboard ?? ""}
            dashboards={ctx.dashboards}
            onChange={(v) => update({ rowTargetDashboard: v || undefined })}
          />
          <FieldLabel caption="rowOpenMode">
            <StackedSlot>
              <select
                value={tw.rowOpenMode ?? "navigate"}
                onChange={(e) => update({ rowOpenMode: e.target.value as "navigate" | "modal" })}
                disabled={!tw.rowTargetDashboard}
              >
                <option value="navigate">navigate</option>
                <option value="modal">modal</option>
              </select>
            </StackedSlot>
          </FieldLabel>
        </FormRow>
        <FormRow>
          <FieldLabel caption="rowSelectionKey">
            <input
              value={tw.rowSelectionKey ?? ""}
              onChange={(e) => update({ rowSelectionKey: e.target.value || undefined })}
              disabled={!tw.rowTargetDashboard}
            />
          </FieldLabel>
        <KeyValueEditor
          label="rowParamsJson"
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
            caption="cardTargetDashboard"
            value={cw.cardTargetDashboard ?? ""}
            dashboards={ctx.dashboards}
            onChange={(v) => update({ cardTargetDashboard: v || undefined })}
          />
          <FieldLabel caption="cardOpenMode">
            <StackedSlot>
              <select
                value={cw.cardOpenMode ?? "navigate"}
                onChange={(e) => update({ cardOpenMode: e.target.value as "navigate" | "modal" })}
                disabled={!cw.cardTargetDashboard}
              >
                <option value="navigate">navigate</option>
                <option value="modal">modal</option>
              </select>
            </StackedSlot>
          </FieldLabel>
        </FormRow>
        <FormRow>
          <FieldLabel caption="cardSelectionKey">
            <input
              value={cw.cardSelectionKey ?? ""}
              onChange={(e) => update({ cardSelectionKey: e.target.value || undefined })}
              disabled={!cw.cardTargetDashboard}
            />
          </FieldLabel>
        <KeyValueEditor
          label="cardParamsJson"
          value={cw.cardParamsJson}
          onChange={(v) => update({ cardParamsJson: v })}
        />
        </FormRow>
      </>
    );
  }

  return null;
}
