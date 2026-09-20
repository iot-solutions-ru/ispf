// Type-specific editor fields — data widgets (12 types).
import type { TFunction } from "i18next";
import type { ReactNode } from "react";
import { HISTORY_TABLE_RANGE_IDS } from "../../../types/dashboard";
import {
  CALCULATOR_SHEET_CONFIG,
  DEFAULT_SHEET_CONFIG,
  FREE_SHEET_CONFIG,
  sheetConfigToJson,
} from "../sheet/sheetConfig";
import {
  AdvancedJsonField,
  FormFieldsEditor,
  KeyValueEditor,
  ObjectTableColumnsEditor,
  SheetGridSizeEditor,
  StringListEditor,
} from "../widgetEditorStructured";
import {
  DashboardPathField,
  FieldLabel,
  FormRow,
  PathSelect,
  ReportParameterHints,
  Section,
  StackedSlot,
  type WidgetFieldContextFor,
  type WidgetTypeFieldsRegistry,
} from "./widgetFieldPrimitives";
import { rowNavigationFields } from "./widgetRowNavigationFields";

function functionFields(ctx: WidgetFieldContextFor<"function">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.functionInvoke")} />
      <label>
        functionName
        <input
          value={widget.functionName}
          onChange={(e) => update({ functionName: e.target.value })}
        />
      </label>
      <label>
        buttonLabel
        <input
          value={widget.buttonLabel ?? ""}
          onChange={(e) => update({ buttonLabel: e.target.value })}
        />
      </label>
      <label>
        confirmMessage
        <input
          value={widget.confirmMessage ?? ""}
          onChange={(e) => update({ confirmMessage: e.target.value || undefined })}
        />
      </label>
      <label>
        workflowPath
        <input
          value={widget.workflowPath ?? ""}
          onChange={(e) => update({ workflowPath: e.target.value || undefined })}
          placeholder="root.platform.workflows..."
        />
      </label>
      <KeyValueEditor
        label={t("editor.inputJsonStatic")}
        value={widget.inputJson}
        onChange={(v) => update({ inputJson: v })}
      />
    </>
  );
}

function functionFormFields(ctx: WidgetFieldContextFor<"function-form">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.functionForm")} />
      <label>
        functionName
        <input
          value={widget.functionName}
          onChange={(e) => update({ functionName: e.target.value })}
        />
      </label>
      <label>
        buttonLabel
        <input
          value={widget.buttonLabel ?? ""}
          onChange={(e) => update({ buttonLabel: e.target.value })}
        />
      </label>
      <label>
        confirmMessage
        <input
          value={widget.confirmMessage ?? ""}
          onChange={(e) => update({ confirmMessage: e.target.value || undefined })}
        />
      </label>
      <label>
        validateFunctionName
        <input
          value={widget.validateFunctionName ?? ""}
          onChange={(e) => update({ validateFunctionName: e.target.value || undefined })}
        />
      </label>
      <label>
        <input
          type="checkbox"
          checked={widget.closeModalOnSuccess !== false}
          onChange={(e) => update({ closeModalOnSuccess: e.target.checked })}
        />
        closeModalOnSuccess
      </label>
      <FormFieldsEditor
        mode="function-form"
        value={widget.fieldsJson}
        onChange={(v) => update({ fieldsJson: v })}
      />
      <KeyValueEditor
        label="paramBindingsJson"
        value={widget.paramBindingsJson}
        onChange={(v) => update({ paramBindingsJson: v })}
      />
      <StringListEditor
        label="requireSessionParamsJson"
        value={widget.requireSessionParamsJson}
        onChange={(v) => update({ requireSessionParamsJson: v || undefined })}
      />
      <p className="hint">{t("editor.deprecation.requireSessionParamsJson")}</p>
      <KeyValueEditor
        label="syncFieldsToSessionJson"
        value={widget.syncFieldsToSessionJson}
        onChange={(v) => update({ syncFieldsToSessionJson: v })}
      />
      <StringListEditor
        label="clearSessionParamsJson"
        value={widget.clearSessionParamsJson}
        onChange={(v) => update({ clearSessionParamsJson: v || undefined })}
      />
      <AdvancedJsonField
        label="wizardStepsJson"
        value={widget.wizardStepsJson}
        onChange={(v) => update({ wizardStepsJson: v })}
        rows={3}
      />
    </>
  );
}

function objectTableFields(ctx: WidgetFieldContextFor<"object-table">, t: TFunction): ReactNode {
  const { widget, update, allVariableNames } = ctx;
  return (
    <>
      <Section title={t("editor.section.objectTable")} />
      <label>
        namePattern
        <input
          value={widget.namePattern ?? ""}
          onChange={(e) => update({ namePattern: e.target.value || undefined })}
          placeholder="gpu-*"
        />
      </label>
      <label>
        objectType
        <select
          value={widget.objectType ?? ""}
          onChange={(e) =>
            update({
              objectType: (e.target.value || undefined) as typeof widget.objectType,
            })
          }
        >
          <option value="">—</option>
          <option value="DEVICE">{t("common:objectType.DEVICE")}</option>
          <option value="FOLDER">{t("common:objectType.FOLDER")}</option>
          <option value="DASHBOARD">{t("common:objectType.DASHBOARD")}</option>
          <option value="CUSTOM">{t("common:objectType.CUSTOM")}</option>
        </select>
      </label>
      <ObjectTableColumnsEditor
        value={widget.columnsJson}
        onChange={(v) => update({ columnsJson: v })}
        variableSuggestions={allVariableNames}
      />
      {rowNavigationFields(ctx, "row", t)}
    </>
  );
}

function eventFeedFields(ctx: WidgetFieldContextFor<"event-feed">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.eventFeed")} />
      <label>
        objectPathPrefix
        <input
          value={widget.objectPathPrefix ?? ""}
          onChange={(e) => update({ objectPathPrefix: e.target.value || undefined })}
          placeholder="root.platform.devices"
        />
      </label>
      <StringListEditor
        label="eventNamesJson"
        value={widget.eventNamesJson}
        onChange={(v) => update({ eventNamesJson: v })}
      />
      <label>
        maxItems
        <input
          type="number"
          min={5}
          max={100}
          value={widget.maxItems ?? 20}
          onChange={(e) => update({ maxItems: Number(e.target.value) })}
        />
      </label>
      <label>
        payloadFilterExpr
        <input
          value={widget.payloadFilterExpr ?? ""}
          onChange={(e) => update({ payloadFilterExpr: e.target.value || undefined })}
          placeholder="payload.int > 20"
        />
      </label>
      <p className="hint">{t("editor.deprecation.payloadFilterExpr")}</p>
    </>
  );
}

function workQueueFields(ctx: WidgetFieldContextFor<"work-queue">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.workQueue")} />
      <label>
        operatorId
        <input
          value={widget.operatorId ?? "operator"}
          onChange={(e) => update({ operatorId: e.target.value })}
        />
      </label>
      <label>
        operatorAppId
        <input
          value={widget.operatorAppId ?? ""}
          onChange={(e) => update({ operatorAppId: e.target.value || undefined })}
        />
      </label>
      <label>
        maxItems
        <input
          type="number"
          min={5}
          max={100}
          value={widget.maxItems ?? 20}
          onChange={(e) => update({ maxItems: Number(e.target.value) })}
        />
      </label>
    </>
  );
}

function reportFields(ctx: WidgetFieldContextFor<"report">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  const rw = widget;
  return (
    <>
      <Section
        title={t("editor.section.report")}
        hint={t("editor.reportHint")}
      />
      <PathSelect
        label="reportPath"
        value={rw.reportPath}
        objects={ctx.reports.map((r) => ({ ...r, variableNames: [] }))}
        onChange={(path) => update({ reportPath: path })}
        placeholder="root.platform.reports.ready-items"
      />
      <ReportParameterHints reportPath={rw.reportPath} />
      <KeyValueEditor
        label={t("editor.parametersJsonStatic")}
        value={rw.parametersJson}
        onChange={(v) => update({ parametersJson: v })}
      />
      <KeyValueEditor
        label={t("editor.contextParamsJsonReport")}
        value={rw.contextParamsJson}
        onChange={(v) => update({ contextParamsJson: v })}
      />
      <label>
        emptyMessage
        <input
          value={rw.emptyMessage ?? ""}
          onChange={(e) => update({ emptyMessage: e.target.value || undefined })}
        />
      </label>
      <FormRow>
        <FieldLabel caption="showCsv">
          <select
            value={rw.showCsv === false ? "false" : "true"}
            onChange={(e) => update({ showCsv: e.target.value === "true" })}
          >
            <option value="true">{t("common:action.yes")}</option>
            <option value="false">{t("common:action.no")}</option>
          </select>
        </FieldLabel>
        <FieldLabel caption="showTruncatedWarning">
          <select
            value={rw.showTruncatedWarning === false ? "false" : "true"}
            onChange={(e) => update({ showTruncatedWarning: e.target.value === "true" })}
          >
            <option value="true">{t("common:action.yes")}</option>
            <option value="false">{t("common:action.no")}</option>
          </select>
        </FieldLabel>
      </FormRow>
      <FormRow>
        <FieldLabel caption="showPdf">
          <select
            value={rw.showPdf === false ? "false" : "true"}
            onChange={(e) => update({ showPdf: e.target.value === "true" })}
          >
            <option value="true">{t("editor.yesWithYarg")}</option>
            <option value="false">{t("common:action.no")}</option>
          </select>
        </FieldLabel>
        <FieldLabel caption="showXlsx">
          <select
            value={rw.showXlsx === false ? "false" : "true"}
            onChange={(e) => update({ showXlsx: e.target.value === "true" })}
          >
            <option value="true">{t("editor.yesWithYarg")}</option>
            <option value="false">{t("common:action.no")}</option>
          </select>
        </FieldLabel>
      </FormRow>
      <label>
        showHtml
        <select
          value={rw.showHtml === false ? "false" : "true"}
          onChange={(e) => update({ showHtml: e.target.value === "true" })}
        >
          <option value="true">{t("editor.yesWithYarg")}</option>
          <option value="false">{t("common:action.no")}</option>
        </select>
      </label>
      <label>
        <input
          type="checkbox"
          checked={rw.selectable === true}
          onChange={(e) => update({ selectable: e.target.checked || undefined })}
        />
        selectable
      </label>
      <label>
        <input
          type="checkbox"
          checked={rw.autoSelectFirstRow === true}
          onChange={(e) => update({ autoSelectFirstRow: e.target.checked || undefined })}
        />
        autoSelectFirstRow
      </label>
      <label>
        rowSelectionKey
        <input
          value={rw.rowSelectionKey ?? ""}
          onChange={(e) => update({ rowSelectionKey: e.target.value || undefined })}
        />
      </label>
      <label>
        selectionKey
        <input
          value={rw.selectionKey ?? ""}
          onChange={(e) => update({ selectionKey: e.target.value || undefined })}
          placeholder="device"
        />
      </label>
      <FormRow>
        <DashboardPathField
          caption={t("editor.rowTargetDashboard")}
          value={rw.rowTargetDashboard ?? ""}
          dashboards={ctx.dashboards}
          onChange={(v) => update({ rowTargetDashboard: v || undefined })}
        />
        <FieldLabel caption="rowOpenMode">
          <StackedSlot>
            <select
              value={rw.rowOpenMode ?? "navigate"}
              onChange={(e) =>
                update({ rowOpenMode: e.target.value as "navigate" | "modal" })
              }
              disabled={!rw.rowTargetDashboard}
            >
              <option value="navigate">navigate</option>
              <option value="modal">modal</option>
            </select>
          </StackedSlot>
        </FieldLabel>
      </FormRow>
      <label>
        rowTargetSelectionKey
        <input
          value={rw.rowTargetSelectionKey ?? ""}
          onChange={(e) => update({ rowTargetSelectionKey: e.target.value || undefined })}
          disabled={!rw.rowTargetDashboard}
          placeholder="device"
        />
      </label>
      <KeyValueEditor
        label="rowParamsFromRowJson"
        value={rw.rowParamsFromRowJson}
        onChange={(v) => update({ rowParamsFromRowJson: v })}
      />
      <StringListEditor
        label="statusDotColumnsJson"
        value={rw.statusDotColumnsJson}
        onChange={(v) => update({ statusDotColumnsJson: v || undefined })}
      />
    </>
  );
}

function historyTableFields(ctx: WidgetFieldContextFor<"history-table">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.historyTable")} />
      <label>
        {t("editor.historyRange")}
        <select
          value={widget.historyRange ?? "5m"}
          onChange={(e) =>
            update({ historyRange: e.target.value as typeof widget.historyRange })
          }
        >
          {HISTORY_TABLE_RANGE_IDS.map((id) => (
            <option key={id} value={id}>
              {t(`history.${id}`)}
            </option>
          ))}
        </select>
      </label>
      <label>
        decimals
        <input
          type="number"
          min={0}
          max={6}
          value={widget.decimals ?? 2}
          onChange={(e) => update({ decimals: Number(e.target.value) })}
        />
      </label>
    </>
  );
}

function variableEditorFields(ctx: WidgetFieldContextFor<"variable-editor">, t: TFunction): ReactNode {
  const { widget, update, variables } = ctx;
  return (
    <>
      <Section title={t("editor.section.variableEditor")} />
      <StringListEditor
        label={t("editor.variablesJsonAll")}
        value={widget.variablesJson}
        onChange={(v) => update({ variablesJson: v || undefined })}
        suggestions={variables}
        placeholder={t("editor.structured.emptyMeansAll")}
      />
    </>
  );
}

function spreadsheetFields(ctx: WidgetFieldContextFor<"spreadsheet">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.spreadsheet")} />
      <p className="hint">{t("editor.spreadsheet.bindingHistoryHint")}</p>
      <label>
        {t("editor.spreadsheet.sheetMode")}
        <select
          value={widget.sheetMode ?? "free"}
          onChange={(e) =>
            update({
              sheetMode: e.target.value as "free" | "configured",
            })
          }
        >
          <option value="free">{t("editor.spreadsheet.sheetModeFree")}</option>
          <option value="configured">{t("editor.spreadsheet.sheetModeConfigured")}</option>
        </select>
      </label>
      <label>
        {t("editor.spreadsheet.persistMode")}
        <select
          value={widget.persistMode ?? "session"}
          onChange={(e) =>
            update({
              persistMode: e.target.value as "session" | "variable",
            })
          }
        >
          <option value="session">{t("editor.spreadsheet.persistSession")}</option>
          <option value="variable">{t("editor.spreadsheet.persistVariable")}</option>
        </select>
      </label>
      {widget.persistMode === "variable" && (
        <label>
          {t("editor.spreadsheet.valuesVariable")}
          <input
            value={widget.valuesVariable ?? ""}
            onChange={(e) => update({ valuesVariable: e.target.value })}
            placeholder="sheetValues"
          />
        </label>
      )}
      <label>
        {t("editor.spreadsheet.sessionKey")}
        <input
          value={widget.sessionKey ?? ""}
          onChange={(e) => update({ sessionKey: e.target.value })}
          placeholder={`sheet:${widget.id}`}
        />
      </label>
      <label>
        <input
          type="checkbox"
          checked={widget.editable !== false}
          onChange={(e) => update({ editable: e.target.checked })}
        />
        {t("editor.editableAllowWrite")}
      </label>
      <label>
        <input
          type="checkbox"
          checked={widget.live === true}
          data-testid="spreadsheet-live-toggle"
          onChange={(e) => update({ live: e.target.checked })}
        />
        {t("editor.spreadsheet.liveRefresh")}
      </label>
      {widget.live === true && (
        <label>
          {t("editor.spreadsheet.liveRefreshIntervalMs")}
          <input
            type="number"
            min={250}
            step={250}
            value={widget.liveRefreshIntervalMs ?? 2000}
            data-testid="spreadsheet-live-interval"
            onChange={(e) => {
              const parsed = Number(e.target.value);
              update({
                liveRefreshIntervalMs: Number.isFinite(parsed) ? parsed : 2000,
              });
            }}
          />
        </label>
      )}
      <SheetGridSizeEditor
        sheetConfigJson={widget.sheetConfigJson}
        onChange={(v) => update({ sheetConfigJson: v })}
      />
      <AdvancedJsonField
        label={t("editor.spreadsheet.sheetConfigJson")}
        value={widget.sheetConfigJson}
        onChange={(v) => update({ sheetConfigJson: v })}
        rows={10}
        placeholder={t("editor.spreadsheet.sheetConfigPlaceholder")}
      />
      <div className="widget-editor-actions">
        <button
          type="button"
          onClick={() =>
            update({
              sheetMode: "free",
              sheetConfigJson: sheetConfigToJson(FREE_SHEET_CONFIG),
            })
          }
        >
          {t("editor.spreadsheet.insertFreeTemplate")}
        </button>
        <button
          type="button"
          onClick={() =>
            update({
              sheetMode: "configured",
              sheetConfigJson: sheetConfigToJson(DEFAULT_SHEET_CONFIG),
            })
          }
        >
          {t("editor.spreadsheet.insertDefaultTemplate")}
        </button>
        <button
          type="button"
          onClick={() =>
            update({
              sheetMode: "configured",
              sheetConfigJson: sheetConfigToJson(CALCULATOR_SHEET_CONFIG),
            })
          }
        >
          {t("editor.spreadsheet.insertCalculatorTemplate")}
        </button>
      </div>
    </>
  );
}

function objectTreeFields(ctx: WidgetFieldContextFor<"object-tree">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.objectTree")} />
      <label>
        maxDepth
        <input
          type="number"
          min={1}
          max={10}
          value={widget.maxDepth ?? 3}
          onChange={(e) => update({ maxDepth: Number(e.target.value) })}
        />
      </label>
    </>
  );
}

function contextListFields(_ctx: WidgetFieldContextFor<"context-list">, t: TFunction): ReactNode {
  return (
    <Section
      title={t("editor.section.sessionContext")}
      hint={t("editor.section.sessionContextHint")}
    />
  );
}

function inputFormFields(ctx: WidgetFieldContextFor<"input-form">, t: TFunction): ReactNode {
  const { widget, update } = ctx;
  return (
    <>
      <Section title={t("editor.section.inputForm")} />
      <label>
        buttonLabel
        <input
          value={widget.buttonLabel ?? ""}
          onChange={(e) => update({ buttonLabel: e.target.value || undefined })}
        />
      </label>
      <FormFieldsEditor
        mode="input-form"
        value={widget.fieldsJson}
        onChange={(v) => update({ fieldsJson: v })}
      />
    </>
  );
}

export const DATA_WIDGET_FIELDS: WidgetTypeFieldsRegistry = {
  "function": functionFields,
  "function-form": functionFormFields,
  "object-table": objectTableFields,
  "event-feed": eventFeedFields,
  "work-queue": workQueueFields,
  "report": reportFields,
  "history-table": historyTableFields,
  "variable-editor": variableEditorFields,
  "spreadsheet": spreadsheetFields,
  "object-tree": objectTreeFields,
  "context-list": contextListFields,
  "input-form": inputFormFields,
};
