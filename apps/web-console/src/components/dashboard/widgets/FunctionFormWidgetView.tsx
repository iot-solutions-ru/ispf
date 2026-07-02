import type { TFunction } from "i18next";
import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchObjectEditor, fetchObjects, invokeFunction } from "../../../api";
import { runReportByPath } from "../../../api/reports";
import type {
  FunctionFormField,
  FunctionFormSelectOption,
  FunctionFormWizardStep,
  FunctionFormWidget,
} from "../../../types/dashboard";
import { buildFunctionInput, parseJsonArray, parseJsonObject, resolveWidgetPath } from "../dashboardUtils";
import { useDashboardContext } from "../DashboardContext";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface FunctionFormWidgetViewProps {
  widget: FunctionFormWidget;
  editable?: boolean;
}

function fieldMatchesShowWhen(
  field: FunctionFormField,
  values: Record<string, string>
): boolean {
  if (!field.showWhenJson?.trim()) return true;
  try {
    const rules = JSON.parse(field.showWhenJson) as Record<string, string | string[]>;
    for (const [key, expected] of Object.entries(rules)) {
      const actual = values[key] ?? "";
      if (Array.isArray(expected)) {
        if (!expected.includes(actual)) return false;
      } else if (actual !== expected) {
        return false;
      }
    }
    return true;
  } catch {
    return true;
  }
}

function wizardStepHeading(step: FunctionFormWizardStep, t: TFunction<"widgets">): string {
  const label = step.label.replace(/^\d+\.\s*/, "").trim();
  return t("view.wizardStepTitle", { id: step.id, label });
}

function fieldValue(
  values: Record<string, string>,
  field: FunctionFormField
): string {
  return values[field.name] ?? field.defaultValue ?? "";
}

function formatSummaryValue(
  field: FunctionFormField,
  raw: string,
  options?: FunctionFormSelectOption[],
  yesLabel = "Yes",
  noLabel = "No"
): string {
  if (!raw.trim()) return "—";
  if (field.type === "checkbox") {
    return raw === "true" || raw === "1" ? yesLabel : noLabel;
  }
  const selectOptions = options ?? field.selectOptions;
  if (field.type === "select" && selectOptions?.length) {
    const opt = selectOptions.find((o) => o.value === raw);
    if (opt) return opt.label;
  }
  if (field.type === "multiselect" && selectOptions?.length) {
    return raw
      .split(",")
      .map((part) => part.trim())
      .filter(Boolean)
      .map((part) => selectOptions.find((o) => o.value === part)?.label ?? part)
      .join(", ");
  }
  return raw;
}

function formatSelectLabel(
  row: Record<string, unknown>,
  valueField: string,
  labelField?: string
): string {
  const value = String(row[valueField] ?? "");
  if (!labelField) return value;
  const suffix = String(row[labelField] ?? "");
  return suffix ? `${value} - ${suffix}` : value;
}

function useFunctionFormFieldOptions(
  field: FunctionFormField,
  filterValues?: Record<string, string>
): FunctionFormSelectOption[] | undefined {
  const children = useQuery({
    queryKey: ["objects", field.optionsFrom],
    queryFn: () => fetchObjects(field.optionsFrom!),
    enabled: field.type === "select" && Boolean(field.optionsFrom),
  });

  const report = useQuery({
    queryKey: ["function-form-report-options", field.optionsFromReport],
    queryFn: () => runReportByPath(field.optionsFromReport!),
    enabled: field.type === "select" && Boolean(field.optionsFromReport),
    staleTime: 30_000,
  });

  return useMemo(() => {
    if (field.selectOptions?.length) {
      return field.selectOptions;
    }
    if (field.staticOptions?.length) {
      return field.staticOptions.map((opt) => ({ value: opt, label: opt }));
    }
    if (field.optionsFromReport && report.data?.rows) {
      const valueField = field.optionsValueField ?? "code";
      const labelField = field.optionsLabelField;
      let rows = report.data.rows as Record<string, unknown>[];
      if (field.optionsFilterField && field.optionsFilterColumn && filterValues) {
        const filterValue = filterValues[field.optionsFilterField] ?? "";
        if (filterValue) {
          rows = rows.filter(
            (row) => String(row[field.optionsFilterColumn!] ?? "") === filterValue
          );
        }
      }
      return rows.map((row) => {
        const value = String(row[valueField] ?? "");
        return {
          value,
          label: formatSelectLabel(row, valueField, labelField),
        };
      });
    }
    if (field.optionsFrom && children.data) {
      return children.data.map((obj) => {
        const leaf = obj.path.split(".").pop() ?? obj.displayName;
        return { value: leaf, label: leaf };
      });
    }
    return undefined;
  }, [field, children.data, report.data, filterValues]);
}

function resolveSessionParamValue(
  paramKey: string,
  values: Record<string, string>,
  sessionParams: Record<string, unknown>,
  paramBindings: Record<string, unknown>,
  parsedFields: FunctionFormField[]
): string {
  const boundField = parsedFields.find((f) => f.paramKey?.trim() === paramKey)?.name;
  if (boundField && String(values[boundField] ?? "").trim()) {
    return String(values[boundField]);
  }
  const bindingField = Object.entries(paramBindings).find(
    ([, key]) => String(key).trim() === paramKey
  )?.[0];
  if (bindingField && String(values[bindingField] ?? "").trim()) {
    return String(values[bindingField]);
  }
  return String(sessionParams[paramKey] ?? "");
}

export default function FunctionFormWidgetView({ widget, editable }: FunctionFormWidgetViewProps) {
  const { t } = useTranslation("widgets");
  const styles = useWidgetStyles(widget.stylesJson);
  const { selection, params: sessionParams, setParams, embeddedModal, closeDashboardModal } =
    useDashboardContext();
  const queryClient = useQueryClient();
  const objectPath = resolveWidgetPath(widget.objectPath, widget.selectionKey, selection);

  const paramBindings = useMemo(
    () => parseJsonObject(widget.paramBindingsJson) ?? {},
    [widget.paramBindingsJson]
  );

  const requiredSessionParams = useMemo(
    () => parseJsonArray<string>(widget.requireSessionParamsJson, []),
    [widget.requireSessionParamsJson]
  );

  const syncFieldsToSession = useMemo(
    () => parseJsonObject(widget.syncFieldsToSessionJson) ?? {},
    [widget.syncFieldsToSessionJson]
  );

  const clearSessionParams = useMemo(
    () => parseJsonArray<string>(widget.clearSessionParamsJson, []),
    [widget.clearSessionParamsJson]
  );

  const parsedFields = useMemo(() => {
    try {
      if (!widget.fieldsJson) return [] as FunctionFormField[];
      return (JSON.parse(widget.fieldsJson) as FunctionFormField[]).map((field) => ({
        ...field,
        defaultValue: field.defaultValue ?? field.default,
      }));
    } catch {
      return [] as FunctionFormField[];
    }
  }, [widget.fieldsJson]);

  const wizardSteps = useMemo(() => {
    try {
      const parsed = widget.wizardStepsJson
        ? (JSON.parse(widget.wizardStepsJson) as FunctionFormWizardStep[])
        : [];
      return Array.isArray(parsed) ? parsed : [];
    } catch {
      return [] as FunctionFormWizardStep[];
    }
  }, [widget.wizardStepsJson]);

  const isWizard = wizardSteps.length > 0;

  const [values, setValues] = useState<Record<string, string>>({});
  const [stepIndex, setStepIndex] = useState(0);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [validating, setValidating] = useState(false);

  useEffect(() => {
    if (editable) return;
    const patch: Record<string, string> = {};
    for (const field of parsedFields) {
      const paramKey = field.paramKey?.trim();
      if (paramKey) {
        const raw = sessionParams[paramKey];
        if (raw !== undefined && raw !== null) {
          patch[field.name] = String(raw);
        }
        continue;
      }
    }
    for (const [fieldName, paramKeyRaw] of Object.entries(paramBindings)) {
      const paramKey = String(paramKeyRaw).trim();
      if (!paramKey) continue;
      const raw = sessionParams[paramKey];
      if (raw !== undefined && raw !== null) {
        patch[fieldName] = String(raw);
      }
    }
    for (const field of parsedFields) {
      if (field.paramKey?.trim() || paramBindings[field.name]) continue;
      const raw = sessionParams[field.name];
      if (raw !== undefined && raw !== null && String(raw).trim() !== "") {
        patch[field.name] = String(raw);
      }
    }
    if (Object.keys(patch).length > 0) {
      setValues((prev) => ({ ...prev, ...patch }));
    }
  }, [editable, parsedFields, paramBindings, sessionParams]);

  useEffect(() => {
    if (editable || Object.keys(syncFieldsToSession).length === 0) return;
    const patch: Record<string, unknown> = {};
    for (const [fieldName, paramKeyRaw] of Object.entries(syncFieldsToSession)) {
      const paramKey = String(paramKeyRaw).trim();
      if (!paramKey) continue;
      const raw = values[fieldName];
      if (raw !== undefined) {
        patch[paramKey] = raw;
      }
    }
    if (Object.keys(patch).length > 0) {
      setParams(patch);
    }
  }, [editable, syncFieldsToSession, values, setParams]);

  const currentStep = wizardSteps[stepIndex];
  const isLastStep = isWizard && stepIndex >= wizardSteps.length - 1;

  const visibleFields = useMemo(() => {
    const base = parsedFields.filter((field) => !field.hidden && fieldMatchesShowWhen(field, values));
    if (!isWizard || !currentStep) return base;
    if (isLastStep) return [];
    return base.filter((field) => field.step === currentStep.id);
  }, [parsedFields, isWizard, currentStep, isLastStep, values]);

  const summaryFields = useMemo(() => {
    if (!isWizard) return [];
    const lastId = wizardSteps[wizardSteps.length - 1]?.id;
    return parsedFields.filter(
      (field) =>
        !field.hidden &&
        field.step &&
        field.step !== lastId &&
        fieldMatchesShowWhen(field, values)
    );
  }, [parsedFields, isWizard, wizardSteps, values]);

  const editor = useQuery({
    queryKey: ["object-editor", objectPath],
    queryFn: () => fetchObjectEditor(objectPath),
    enabled: Boolean(objectPath),
  });

  const mutation = useMutation({
    mutationFn: async () => {
      if (!objectPath || !widget.functionName) {
        throw new Error(t("error.objectAndFunctionRequired"));
      }
      const fn = editor.data?.functions.find((f) => f.name === widget.functionName);
      const input = buildFunctionInput(parsedFields, values, fn?.inputSchema);
      return invokeFunction(objectPath, widget.functionName, input);
    },
    onSuccess: (result) => {
      const row = result.rows?.[0];
      const errorCode = row?.error_code ?? row?.errorCode;
      if (errorCode && String(errorCode) !== "OK") {
        setError(String(row?.error_message ?? row?.message ?? t("view.errorGeneric")));
        setMessage(null);
        return;
      }
      if (row?.success === false) {
        setError(String(row.message ?? t("view.errorGeneric")));
        setMessage(null);
        return;
      }
      const parts: string[] = [];
      const okMessage = row?.error_message ?? row?.message;
      if (okMessage && String(okMessage).trim()) {
        parts.push(String(okMessage));
      }
      if (row?.eventId) {
        parts.push(String(row.eventId));
      }
      setMessage(parts.length > 0 ? parts.join(" · ") : t("view.done"));
      setError(null);
      setValues({});
      setStepIndex(0);
      if (clearSessionParams.length > 0) {
        setParams(Object.fromEntries(clearSessionParams.map((key) => [key, ""])));
      }
      queryClient.invalidateQueries({ queryKey: ["variables"] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["events"] });
      queryClient.invalidateQueries({ queryKey: ["report-widget"] });
      queryClient.invalidateQueries({ queryKey: ["function-form-report-options"] });
      const shouldCloseModal =
        embeddedModal && widget.closeModalOnSuccess !== false;
      if (shouldCloseModal) {
        closeDashboardModal();
      }
    },
    onError: (err: Error) => {
      setError(err.message);
      setMessage(null);
    },
  });

  async function validateCurrentStep(): Promise<boolean> {
    for (const field of visibleFields) {
      if (field.required && !fieldValue(values, field).trim()) {
        setError(t("view.fillRequiredField", { label: field.label }));
        return false;
      }
    }
    for (const paramKey of requiredSessionParams) {
      if (
        !resolveSessionParamValue(
          paramKey,
          values,
          sessionParams,
          paramBindings,
          parsedFields
        ).trim()
      ) {
        setError(t("view.requireSessionParams"));
        return false;
      }
    }
    if (!widget.validateFunctionName || !objectPath) {
      setError(null);
      return true;
    }
    setValidating(true);
    try {
      const fn = editor.data?.functions.find((f) => f.name === widget.validateFunctionName);
      const built = buildFunctionInput(parsedFields, values, fn?.inputSchema);
      const inputRow = { ...built.rows[0], step: currentStep?.id ?? "" };
      const result = await invokeFunction(objectPath, widget.validateFunctionName, {
        schema: built.schema,
        rows: [inputRow],
      });
      const resultRow = result.rows?.[0];
      const errorCode = resultRow?.error_code ?? resultRow?.errorCode;
      if (errorCode && String(errorCode) !== "OK") {
        setError(String(resultRow?.error_message ?? resultRow?.message ?? t("view.errorGeneric")));
        return false;
      }
      setError(null);
      return true;
    } catch (err) {
      setError(err instanceof Error ? err.message : t("view.errorGeneric"));
      return false;
    } finally {
      setValidating(false);
    }
  }

  async function handleNext() {
    if (editable) return;
    const ok = await validateCurrentStep();
    if (ok) setStepIndex((i) => Math.min(i + 1, wizardSteps.length - 1));
  }

  function handleBack() {
    setError(null);
    setStepIndex((i) => Math.max(i - 1, 0));
  }

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (editable) return;
    if (isWizard && !isLastStep) {
      await handleNext();
      return;
    }
    if (widget.confirmMessage && !window.confirm(widget.confirmMessage)) return;
    const ok = await validateCurrentStep();
    if (!ok) return;
    mutation.mutate();
  };

  function isFieldReadOnly(field: FunctionFormField): boolean {
    if (editable) return false;
    if (field.paramKey?.trim()) return true;
    const boundParam = paramBindings[field.name];
    return Boolean(boundParam && String(boundParam).trim());
  }

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget function-form-widget"
      editable={editable}
    >
      {!objectPath && <p className="hint">{t("view.selectObjectInTable")}</p>}
      {isWizard && wizardSteps.length > 0 && (
        <nav className="function-form-wizard-steps" aria-label={t("view.wizardSteps")}>
          {wizardSteps.map((step, index) => (
            <span
              key={step.id}
              className={`function-form-wizard-step${
                index === stepIndex ? " active" : index < stepIndex ? " done" : ""
              }`}
            >
              {step.label}
            </span>
          ))}
        </nav>
      )}
      <form className="function-form-fields" onSubmit={handleSubmit}>
        {isWizard && currentStep && !isLastStep && (
          <h3 className="function-form-wizard-step-title">{wizardStepHeading(currentStep, t)}</h3>
        )}
        {isLastStep ? (
          <div className="function-form-wizard-summary">
            {wizardSteps
              .filter((step) => step.id !== wizardSteps[wizardSteps.length - 1]?.id)
              .map((step) => {
                const items = summaryFields
                  .filter((field) => field.step === step.id)
                  .map((field) => ({
                    field,
                    raw: fieldValue(values, field),
                  }))
                  .filter(({ raw }) => raw.trim());
                if (items.length === 0) return null;
                return (
                  <section key={step.id} className="function-form-summary-section">
                    <h4 className="function-form-summary-section-title">{step.label}</h4>
                    <div className="function-form-summary-grid">
                      {items.map(({ field, raw }) => (
                        <div key={field.name} className="function-form-summary-item">
                          <span className="function-form-summary-label">{field.label}</span>
                          <span className="function-form-summary-value">
                            <FunctionFormSummaryValue field={field} raw={raw} filterValues={values} />
                          </span>
                        </div>
                      ))}
                    </div>
                  </section>
                );
              })}
          </div>
        ) : (
          <div className="function-form-wizard-fields" style={styles.body}>
            {visibleFields.map((field) => (
              <div
                key={field.name}
                className={`function-form-field-cell${
                  field.colSpan === 1 ? " half" : " full"
                }`}
              >
                <FunctionFormFieldInput
                  field={field}
                  value={fieldValue(values, field)}
                  disabled={editable || !objectPath || isFieldReadOnly(field)}
                  filterValues={values}
                  onChange={(v) => setValues((prev) => ({ ...prev, [field.name]: v }))}
                />
              </div>
            ))}
          </div>
        )}
        <div className="function-form-actions">
          {isWizard && stepIndex > 0 && (
            <button
              type="button"
              className="btn function-form-wizard-back"
              disabled={editable || mutation.isPending || validating}
              onClick={handleBack}
            >
              ← {t("view.wizardBack")}
            </button>
          )}
          <button
            type="submit"
            className={`btn primary function-widget-btn${
              isWizard && isLastStep ? " function-form-wizard-submit" : ""
            }`}
            disabled={editable || mutation.isPending || validating || !objectPath}
          >
            {mutation.isPending || validating
              ? "…"
              : isWizard && !isLastStep
                ? t("view.wizardNext")
                : widget.buttonLabel ?? widget.functionName}
          </button>
        </div>
      </form>
      {message && <p className="function-widget-msg ok">{message}</p>}
      {error && <p className="function-widget-msg error">{error}</p>}
    </DashWidgetShell>
  );
}

function FunctionFormSummaryValue({
  field,
  raw,
  filterValues,
}: {
  field: FunctionFormField;
  raw: string;
  filterValues?: Record<string, string>;
}) {
  const { t } = useTranslation(["widgets", "common"]);
  const options = useFunctionFormFieldOptions(field, filterValues);
  return formatSummaryValue(
    field,
    raw,
    options,
    t("common:action.yes"),
    t("common:action.no")
  );
}

function FunctionFormFieldInput({
  field,
  value,
  disabled,
  filterValues,
  onChange,
}: {
  field: FunctionFormField;
  value: string;
  disabled?: boolean;
  filterValues?: Record<string, string>;
  onChange: (value: string) => void;
}) {
  const { t } = useTranslation("widgets");
  const options = useFunctionFormFieldOptions(field, filterValues);
  const loadingReport = field.type === "select" && Boolean(field.optionsFromReport) && options === undefined;

  const labelText = (
    <>
      {field.label}
      {field.required && <span className="function-form-required"> *</span>}
    </>
  );

  if (field.type === "textarea") {
    return (
      <label className="function-form-label">
        {labelText}
        <textarea
          value={value}
          disabled={disabled}
          rows={3}
          placeholder={field.hint}
          onChange={(e) => onChange(e.target.value)}
        />
        {field.hint && value.trim() && (
          <span className="function-form-hint">{field.hint}</span>
        )}
      </label>
    );
  }

  if (field.type === "checkbox") {
    return (
      <label className="function-form-label function-form-checkbox">
        <input
          type="checkbox"
          checked={value === "true" || value === "1"}
          disabled={disabled}
          onChange={(e) => onChange(e.target.checked ? "true" : "false")}
        />
        {labelText}
        {field.hint && <span className="function-form-hint">{field.hint}</span>}
      </label>
    );
  }

  if (field.type === "time") {
    return (
      <label className="function-form-label">
        {labelText}
        <input type="time" step={1} value={value} disabled={disabled} onChange={(e) => onChange(e.target.value)} />
        {field.hint && <span className="function-form-hint">{field.hint}</span>}
      </label>
    );
  }

  if (field.type === "multiselect") {
    const selectOptions = options ?? field.selectOptions ?? [];
    const selected = value
      .split(",")
      .map((part) => part.trim())
      .filter(Boolean);
    function toggleOption(optValue: string) {
      const next = selected.includes(optValue)
        ? selected.filter((v) => v !== optValue)
        : [...selected, optValue];
      onChange(next.join(", "));
    }
    return (
      <fieldset className="function-form-label function-form-multiselect">
        <legend>
          {labelText}
        </legend>
        <div className="function-form-multiselect-options">
          {selectOptions.map((opt) => (
            <label key={opt.value} className="function-form-multiselect-option">
              <input
                type="checkbox"
                checked={selected.includes(opt.value)}
                disabled={disabled || loadingReport}
                onChange={() => toggleOption(opt.value)}
              />
              {opt.label}
            </label>
          ))}
        </div>
        {loadingReport && <span className="function-form-hint">{t("view.loadingReport")}</span>}
        {field.hint && <span className="function-form-hint">{field.hint}</span>}
      </fieldset>
    );
  }

  if (field.type === "select") {
    const selectOptions = options ?? [];
    return (
      <label className="function-form-label">
        {labelText}
        <select
          value={value}
          disabled={disabled || loadingReport}
          onChange={(e) => onChange(e.target.value)}
        >
          {!field.defaultValue && <option value="">—</option>}
          {selectOptions.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
        {loadingReport && <span className="function-form-hint">{t("view.loadingReport")}</span>}
        {field.hint && <span className="function-form-hint">{field.hint}</span>}
      </label>
    );
  }

  return (
    <label className="function-form-label">
      {labelText}
      <input
        type={field.type === "number" ? "number" : "text"}
        value={value}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
      />
      {field.hint && <span className="function-form-hint">{field.hint}</span>}
    </label>
  );
}
