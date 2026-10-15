// Selects whose options are already fixed by another choice in the same editor.
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchObjectEditor, fetchVariables } from "../../../api";
import { fetchReport } from "../../../api/reports";
import { recordFieldNames, splitCaptionDetail } from "../widgetEditorHelpers";
import { HintCaption, KeyValueEditor, OptionsSelect, StringListEditor } from "../widgetEditorStructured";

function useReportDefinition(reportPath: string | undefined) {
  const path = reportPath?.trim() ?? "";
  return useQuery({
    queryKey: ["report-widget-hints", path],
    queryFn: () => fetchReport(path),
    enabled: Boolean(path),
  });
}

export function FunctionInputPairs({
  objectPath,
  functionName,
  label,
  value,
  onChange,
}: {
  objectPath?: string;
  functionName?: string;
  label: string;
  value: string | undefined;
  onChange: (next: string | undefined) => void;
}) {
  const { t } = useTranslation("widgets");
  const path = objectPath?.trim() ?? "";
  const fnName = functionName?.trim() ?? "";
  const query = useQuery({
    queryKey: ["widget-editor-object-functions", path],
    queryFn: () => fetchObjectEditor(path),
    enabled: Boolean(path) && Boolean(fnName),
  });
  const names = [
    ...new Set(
      (query.data?.functions.find((fn) => fn.name === fnName)?.inputSchema?.fields ?? [])
        .map((field) => field.name.trim())
        .filter(Boolean),
    ),
  ].sort((a, b) => a.localeCompare(b));
  const waiting = !path || !fnName || query.isLoading;
  return (
    <>
      {!path || !fnName ? <p className="hint">{t("editor.functionInputsNeedFunction")}</p> : null}
      {path && fnName && query.isSuccess && names.length === 0 ? (
        <p className="hint">{t("editor.functionInputsEmpty")}</p>
      ) : null}
      <KeyValueEditor
        label={label}
        keyCaption={t("editor.col.functionInput")}
        valueCaption={t("editor.col.value")}
        keyOptions={names}
        keyDisabled={waiting}
        value={value}
        onChange={onChange}
      />
    </>
  );
}

export function ReportParameterPairs({
  reportPath,
  label,
  valueCaption,
  valueSuggestions,
  value,
  onChange,
}: {
  reportPath?: string;
  label: string;
  valueCaption: string;
  valueSuggestions?: string[];
  value: string | undefined;
  onChange: (next: string | undefined) => void;
}) {
  const { t } = useTranslation("widgets");
  const path = reportPath?.trim() ?? "";
  const query = useReportDefinition(path);
  const names = [...new Set((query.data?.parameters ?? []).map((name) => name.trim()).filter(Boolean))].sort(
    (a, b) => a.localeCompare(b),
  );
  return (
    <>
      {!path ? <p className="hint">{t("editor.reportParamsNeedReport")}</p> : null}
      {path && query.isSuccess && names.length === 0 ? (
        <p className="hint">{t("editor.reportParamsEmpty")}</p>
      ) : null}
      <KeyValueEditor
        label={label}
        keyCaption={t("editor.col.reportParam")}
        valueCaption={valueCaption}
        keyOptions={names}
        keyDisabled={!path || query.isLoading}
        valueSuggestions={valueSuggestions}
        value={value}
        onChange={onChange}
      />
    </>
  );
}

export function ReportColumnPairs({
  reportPath,
  label,
  keyCaption,
  keySuggestions,
  value,
  onChange,
}: {
  reportPath?: string;
  label: string;
  keyCaption: string;
  keySuggestions?: string[];
  value: string | undefined;
  onChange: (next: string | undefined) => void;
}) {
  const { t } = useTranslation("widgets");
  const path = reportPath?.trim() ?? "";
  const query = useReportDefinition(path);
  const columns = reportColumnNames(query.data?.columns);
  return (
    <>
      {!path ? <p className="hint">{t("editor.reportColumnsNeedReport")}</p> : null}
      {path && query.isSuccess && columns.length === 0 ? (
        <p className="hint">{t("editor.reportColumnsEmpty")}</p>
      ) : null}
      <KeyValueEditor
        label={label}
        keyCaption={keyCaption}
        valueCaption={t("editor.col.reportColumn")}
        keySuggestions={keySuggestions}
        valueOptions={columns}
        valueDisabled={!path || query.isLoading}
        value={value}
        onChange={onChange}
      />
    </>
  );
}

export function ReportColumnSelect({
  reportPath,
  label,
  value,
  onChange,
}: {
  reportPath?: string;
  label: string;
  value: string;
  onChange: (next: string) => void;
}) {
  const { t } = useTranslation("widgets");
  const path = reportPath?.trim() ?? "";
  const query = useReportDefinition(path);
  const columns = reportColumnNames(query.data?.columns);
  return (
    <label>
      <HintCaption>{label}</HintCaption>
      <OptionsSelect
        ariaLabel={label}
        value={value}
        options={columns}
        disabled={!path || query.isLoading}
        onChange={onChange}
      />
      {!path ? <p className="hint">{t("editor.reportColumnsNeedReport")}</p> : null}
      {path && query.isSuccess && columns.length === 0 ? (
        <p className="hint">{t("editor.reportColumnsEmpty")}</p>
      ) : null}
    </label>
  );
}

export function ReportColumnList({
  reportPath,
  label,
  value,
  onChange,
}: {
  reportPath?: string;
  label: string;
  value: string | undefined;
  onChange: (next: string) => void;
}) {
  const { t } = useTranslation("widgets");
  const path = reportPath?.trim() ?? "";
  const query = useReportDefinition(path);
  const columns = reportColumnNames(query.data?.columns);
  return (
    <>
      {!path ? <p className="hint">{t("editor.reportColumnsNeedReport")}</p> : null}
      {path && query.isSuccess && columns.length === 0 ? (
        <p className="hint">{t("editor.reportColumnsEmpty")}</p>
      ) : null}
      <StringListEditor
        label={label}
        value={value}
        onChange={onChange}
        options={columns}
        optionsDisabled={!path || query.isLoading}
      />
    </>
  );
}

function reportColumnNames(columns: Array<{ field: string }> | undefined): string[] {
  return [...new Set((columns ?? []).map((column) => column.field.trim()).filter(Boolean))].sort((a, b) =>
    a.localeCompare(b),
  );
}

export function RecordFieldGroup({
  objectPath,
  variableName,
  allowCustom,
  showHints = true,
  fields,
}: {
  objectPath?: string;
  variableName?: string;
  allowCustom: boolean;
  showHints?: boolean;
  fields: Array<{ label: string; value: string; onChange: (next: string) => void }>;
}) {
  const { t } = useTranslation("widgets");
  const path = objectPath?.trim() ?? "";
  const name = variableName?.trim() ?? "";
  const query = useQuery({
    queryKey: ["widget-editor-variable-fields", path],
    queryFn: () => fetchVariables(path),
    enabled: Boolean(path) && !allowCustom,
  });
  const recordFields = allowCustom ? [] : recordFieldNames(query.data, name);
  const missing = !allowCustom && (!path || !name);
  const unknown =
    !allowCustom && Boolean(path) && Boolean(name) && query.isSuccess && recordFields.length === 0;
  const disabled = missing || query.isLoading;
  return (
    <div>
      {showHints && missing ? <p className="hint">{t("editor.recordFieldsNeedVariable")}</p> : null}
      {showHints && unknown ? <p className="hint">{t("editor.recordFieldsEmpty")}</p> : null}
      {showHints && fields.length > 0 ? <p className="hint">{t("editor.hint.recordVariableField")}</p> : null}
      {fields.map((field) => (
        <label key={field.label}>
          <HintCaption>{field.label}</HintCaption>
          {allowCustom || unknown ? (
            <input
              aria-label={splitCaptionDetail(field.label).title}
              value={field.value}
              onChange={(e) => field.onChange(e.target.value)}
            />
          ) : (
            <OptionsSelect
              ariaLabel={splitCaptionDetail(field.label).title}
              value={field.value}
              options={recordFields}
              disabled={disabled}
              onChange={field.onChange}
            />
          )}
        </label>
      ))}
    </div>
  );
}
