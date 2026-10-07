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
  HintCaption,
  KeyValueEditor,
  ObjectTableColumnsEditor,
  SheetGridSizeEditor,
  StringListEditor,
} from "../widgetEditorStructured";
import { ObjectPathField } from "../../../ui";
import { parseJsonArray } from "../widgetEditorJson";
import { FOLDER_OBJECT_TYPES } from "../../../ui/objectPathFilters";
import {
  DashboardPathField,
  FieldLabel,
  FormRow,
  ObjectFunctionSelect,
  PathSelect,
  ReportParameterHints,
  Section,
  SelectionKeyInput,
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
      <ObjectFunctionSelect
        label="functionName"
        code="functionName"
        objectPath={widget.objectPath}
        value={widget.functionName}
        onChange={(next) => update({ functionName: next })}
      />
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
      <ObjectPathField
        label="workflowPath"
        value={widget.workflowPath ?? ""}
        onChange={(path) => update({ workflowPath: path || undefined })}
        filterTypes={["WORKFLOW"]}
        hint="root.platform.workflows..."
        placeholder=""
      />
      <KeyValueEditor
        label={t("editor.inputJsonStatic")}
        value={widget.inputJson}
        onChange={(v) => update({ inputJson: v })}
      />
    </>
  );
}

function formFieldNames(fieldsJson: string | undefined): string[] {
  const names = new Set<string>();
  for (const field of parseJsonArray<{ name?: string }>(fieldsJson, [])) {
    const name = field?.name?.trim();
    if (name) names.add(name);
  }
  return [...names];
}

function functionFormFields(ctx: WidgetFieldContextFor<"function-form">, t: TFunction): ReactNode {
  const { widget, sessionParams, update } = ctx;
  const fieldNames = formFieldNames(widget.fieldsJson);
  return (
    <>
      <Section title={t("editor.section.functionForm")} />
      <ObjectFunctionSelect
        label="functionName"
        code="functionName"
        objectPath={widget.objectPath}
        value={widget.functionName}
        onChange={(next) => update({ functionName: next })}
      />
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
      <ObjectFunctionSelect
        label="validateFunctionName"
        code="validateFunctionName"
        objectPath={widget.objectPath}
        value={widget.validateFunctionName ?? ""}
        onChange={(next) => update({ validateFunctionName: next || undefined })}
      />
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
        label={t("editor.paramBindings")}
        code="paramBindingsJson"
        keyCaption={t("editor.col.formField")}
        valueCaption={t("editor.col.sessionParam")}
        keyOptions={fieldNames}
        valueSuggestions={sessionParams}
        value={widget.paramBindingsJson}
        onChange={(v) => update({ paramBindingsJson: v })}
      />
      <StringListEditor
        label={t("editor.requireSessionParams")}
        code="requireSessionParamsJson"
        value={widget.requireSessionParamsJson}
        onChange={(v) => update({ requireSessionParamsJson: v || undefined })}
      />
      <p className="hint">{t("editor.deprecation.requireSessionParamsJson")}</p>
      <KeyValueEditor
        label={t("editor.syncFieldsToSession")}
        code="syncFieldsToSessionJson"
        keyCaption={t("editor.col.formField")}
        valueCaption={t("editor.col.sessionParam")}
        keyOptions={fieldNames}
        valueSuggestions={sessionParams}
        value={widget.syncFieldsToSessionJson}
        onChange={(v) => update({ syncFieldsToSessionJson: v })}
      />
      <StringListEditor
        label={t("editor.clearSessionParams")}
        code="clearSessionParamsJson"
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
        <HintCaption hint="gpu-*">namePattern</HintCaption>
        <input
          value={widget.namePattern ?? ""}
          onChange={(e) => update({ namePattern: e.target.value || undefined })}
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
      <ObjectPathField
        label="objectPathPrefix"
        value={widget.objectPathPrefix ?? ""}
        onChange={(path) => update({ objectPathPrefix: path || undefined })}
        filterTypes={FOLDER_OBJECT_TYPES}
        hint="root.platform.devices"
        placeholder=""
      />
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
        <HintCaption hint="payload.int > 20">payloadFilterExpr</HintCaption>
        <input
          value={widget.payloadFilterExpr ?? ""}
          onChange={(e) => update({ payloadFilterExpr: e.target.value || undefined })}
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
  const { widget, selectionKeys, sessionParams, update } = ctx;
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
        onChange={(path) => update({ reportPath: path })}
        placeholder="root.platform.reports.ready-items"
        filterTypes={["REPORT"]}
      />
      <ReportParameterHints reportPath={rw.reportPath} />
      <KeyValueEditor
        label={t("editor.staticParams")}
        code="parametersJson"
        keyCaption={t("editor.col.reportParam")}
        valueCaption={t("editor.col.value")}
        value={rw.parametersJson}
        onChange={(v) => update({ parametersJson: v })}
      />
      <KeyValueEditor
        label={t("editor.reportContextParams")}
        code="contextParamsJson"
        keyCaption={t("editor.col.reportParam")}
        valueCaption={t("editor.col.sessionParam")}
        valueSuggestions={sessionParams}
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
      <FieldLabel caption={t("editor.rowSelectionColumn")} code="rowSelectionKey">
        <SelectionKeyInput
          value={rw.rowSelectionKey ?? ""}
          keys={selectionKeys}
          onChange={(next) => update({ rowSelectionKey: next || undefined })}
        />
      </FieldLabel>
      <FieldLabel caption={t("editor.selectionKeyOnClick")} code="selectionKey" hint="device">
        <SelectionKeyInput
          value={rw.selectionKey ?? ""}
          keys={selectionKeys}
          onChange={(next) => update({ selectionKey: next || undefined })}
        />
      </FieldLabel>
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
      <FieldLabel caption={t("editor.rowTargetSelectionKey")} code="rowTargetSelectionKey" hint="device">
        <SelectionKeyInput
          value={rw.rowTargetSelectionKey ?? ""}
          keys={selectionKeys}
          disabled={!rw.rowTargetDashboard}
          onChange={(next) => update({ rowTargetSelectionKey: next || undefined })}
        />
      </FieldLabel>
      <KeyValueEditor
        label={t("editor.rowParamsFromRow")}
        code="rowParamsFromRowJson"
        keyCaption={t("editor.col.sessionParam")}
        valueCaption={t("editor.col.reportColumn")}
        keySuggestions={sessionParams}
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
          <HintCaption hint="sheetValues">{t("editor.spreadsheet.valuesVariable")}</HintCaption>
          <input
            value={widget.valuesVariable ?? ""}
            onChange={(e) => update({ valuesVariable: e.target.value })}
          />
        </label>
      )}
      <label>
        <HintCaption hint={`sheet:${widget.id}`}>{t("editor.spreadsheet.sessionKey")}</HintCaption>
        <input
          value={widget.sessionKey ?? ""}
          onChange={(e) => update({ sessionKey: e.target.value })}
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
