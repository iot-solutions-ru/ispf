import { useState, type ReactNode } from "react";
import { useQuery } from "@tanstack/react-query";
import { AutoComplete, Tooltip } from "antd";
import { useTranslation } from "react-i18next";
import { fetchObjectEditor, fetchVariablesBatch } from "../../api";
import type {
  ObjectTableColumn,
  SheetConfig,
  FunctionFormField,
  InputFormField,
} from "../../types/dashboard";
import {
  parseSheetConfig,
  sheetConfigToJson,
} from "./sheet/sheetConfig";
import { WIDGET_STYLE_KEYS_HINT, parseWidgetStyles } from "./widgetStyles";
import type { WidgetStyleKey } from "./widgetStyles";
import { parseJsonArray, parseJsonObject, stringifyJson } from "./widgetEditorJson";
import { recordFieldNames, splitCaptionDetail } from "./widgetEditorHelpers";
import { ObjectPathField } from "../../ui";

function joinHints(...parts: Array<string | undefined>): string | undefined {
  const text = parts.map((part) => part?.trim()).filter((part): part is string => Boolean(part));
  return text.length > 0 ? text.join("\n") : undefined;
}

/** Caption that keeps a longer explanation on hover, not inside the control. */
export function HintCaption({
  hint,
  children,
  className = "field-caption",
}: {
  hint?: string;
  children: ReactNode;
  className?: string;
}) {
  const split = typeof children === "string" ? splitCaptionDetail(children) : null;
  const shown = split?.detail ? split.title : children;
  const title = joinHints(hint, split?.detail);
  const classNames = [className, title ? "field-caption-hint" : ""].filter(Boolean).join(" ");
  const caption = <span className={classNames}>{shown}</span>;
  if (!title) return caption;
  return (
    <Tooltip title={title} overlayStyle={{ maxWidth: 420 }}>
      {caption}
    </Tooltip>
  );
}

export function OptionsSelect({
  value,
  options,
  ariaLabel,
  disabled,
  onChange,
}: {
  value: string;
  options: string[];
  ariaLabel: string;
  disabled?: boolean;
  onChange: (next: string) => void;
}) {
  const names = options.map((name) => name.trim()).filter(Boolean);
  const choices = value.trim() && !names.includes(value) ? [value, ...names] : names;
  return (
    <select
      aria-label={ariaLabel}
      value={value}
      disabled={disabled}
      onChange={(e) => onChange(e.target.value)}
    >
      <option value="">—</option>
      {choices.map((name) => (
        <option key={name} value={name}>
          {name}
        </option>
      ))}
    </select>
  );
}

function SuggestInput({
  value,
  suggestions,
  ariaLabel,
  onChange,
}: {
  value: string;
  suggestions?: string[];
  ariaLabel: string;
  onChange: (next: string) => void;
}) {
  const names = (suggestions ?? []).filter((name) => name.trim());
  if (names.length === 0) {
    return <input value={value} aria-label={ariaLabel} onChange={(e) => onChange(e.target.value)} />;
  }
  return (
    <AutoComplete
      className="selection-key-input"
      value={value}
      options={names.map((name) => ({ value: name }))}
      aria-label={ariaLabel}
      filterOption={false}
      virtual={false}
      getPopupContainer={() => document.body}
      onChange={onChange}
    />
  );
}

function MiniField({
  caption,
  hint,
  children,
}: {
  caption: string;
  hint?: string;
  children: ReactNode;
}) {
  return (
    <div className="widget-editor-mini-field">
      <HintCaption className="widget-editor-mini-caption" hint={hint}>
        {caption}
      </HintCaption>
      {children}
    </div>
  );
}

function ListActions({
  onAdd,
  addLabel,
}: {
  onAdd: () => void;
  addLabel: string;
}) {
  return (
    <div className="widget-editor-list-actions">
      <button type="button" className="btn small" onClick={onAdd}>
        {addLabel}
      </button>
    </div>
  );
}

export function StringListEditor({
  label,
  code,
  value,
  onChange,
  suggestions = [],
  placeholder,
  options,
  optionsDisabled,
}: {
  label: string;
  code?: string;
  value: string | undefined;
  onChange: (next: string) => void;
  suggestions?: string[];
  placeholder?: string;
  /** Closed list. Omit to keep a free-text row. */
  options?: string[];
  optionsDisabled?: boolean;
}) {
  const { t } = useTranslation("widgets");
  const [items, setItems] = useState(() => itemsFromValue(value));
  const [source, setSource] = useState(value);
  if (value !== source) {
    setSource(value);
    const saved = itemsFromValue(value);
    const pending = items.filter((item) => !item.trim());
    setItems(pending.length > 0 ? [...saved, ...pending] : saved);
  }

  const commit = (next: string[]) => {
    setItems(next);
    const nextValue = serializeItems(next);
    if (nextValue !== serializeItems(itemsFromValue(value))) {
      onChange(nextValue);
    }
  };

  return (
    <div className="widget-editor-structured full">
      <HintCaption hint={placeholder}>{label}</HintCaption>
      {code ? <span className="field-code">{code}</span> : null}
      <div className="widget-editor-list">
        {items.map((item, index) => (
          <div key={index} className="widget-editor-list-row">
            {options ? (
              <OptionsSelect
                ariaLabel={splitCaptionDetail(label).title}
                value={item}
                options={options}
                disabled={optionsDisabled}
                onChange={(nextValue) => {
                  const next = [...items];
                  next[index] = nextValue;
                  commit(next);
                }}
              />
            ) : (
              <input
                list={suggestions.length ? `suggest-${label}` : undefined}
                value={item}
                onChange={(e) => {
                  const next = [...items];
                  next[index] = e.target.value;
                  commit(next);
                }}
              />
            )}
            <button
              type="button"
              className="btn small danger"
              aria-label={t("editor.structured.removeRow")}
              onClick={() => commit(items.filter((_, i) => i !== index))}
            >
              ×
            </button>
          </div>
        ))}
        {!options && suggestions.length > 0 && (
          <datalist id={`suggest-${label}`}>
            {suggestions.map((s) => (
              <option key={s} value={s} />
            ))}
          </datalist>
        )}
      </div>
      <ListActions onAdd={() => commit([...items, ""])} addLabel={t("editor.structured.addItem")} />
    </div>
  );
}

export function KeyValueEditor({
  label,
  code,
  value,
  onChange,
  keyCaption,
  valueCaption,
  keyPlaceholder,
  valuePlaceholder,
  keySuggestions,
  valueSuggestions,
  keyOptions,
  valueOptions,
  keyDisabled,
  valueDisabled,
}: {
  label: string;
  code?: string;
  value: string | undefined;
  onChange: (next: string | undefined) => void;
  keyCaption?: string;
  valueCaption?: string;
  keyPlaceholder?: string;
  valuePlaceholder?: string;
  keySuggestions?: string[];
  valueSuggestions?: string[];
  /** When set, the key is chosen from this list and cannot be typed freely. */
  keyOptions?: string[];
  /** When set, the value is chosen from this list and cannot be typed freely. */
  valueOptions?: string[];
  keyDisabled?: boolean;
  valueDisabled?: boolean;
}) {
  const { t } = useTranslation("widgets");
  const [rows, setRows] = useState(() => rowsFromValue(value));
  const [source, setSource] = useState(value);
  if (value !== source) {
    setSource(value);
    const saved = rowsFromValue(value);
    const pending = rows.filter((row) => !row.key.trim());
    setRows(pending.length > 0 ? [...saved, ...pending] : saved);
  }

  const commit = (next: { key: string; val: string }[]) => {
    setRows(next);
    const obj: Record<string, string> = {};
    for (const row of next) {
      if (row.key.trim()) obj[row.key.trim()] = row.val;
    }
    const keys = Object.keys(obj);
    const nextValue = keys.length ? stringifyJson(obj) : undefined;
    if (nextValue !== value) {
      onChange(nextValue);
    }
  };

  return (
    <div className="widget-editor-structured full">
      <HintCaption>{label}</HintCaption>
      {code ? <span className="field-code">{code}</span> : null}
      {(keyCaption || valueCaption || keyPlaceholder || valuePlaceholder) && (
        <div className="widget-editor-kv-head">
          <HintCaption className="widget-editor-mini-caption" hint={keyPlaceholder}>
            {keyCaption ?? keyPlaceholder}
          </HintCaption>
          <HintCaption className="widget-editor-mini-caption" hint={valuePlaceholder}>
            {valueCaption ?? valuePlaceholder}
          </HintCaption>
          <span />
        </div>
      )}
      <div className="widget-editor-list">
        {rows.map((row, index) => (
          <div key={index} className="widget-editor-list-row widget-editor-kv-row">
            {keyOptions ? (
              <OptionsSelect
                value={row.key}
                options={keyOptions}
                ariaLabel={keyCaption ?? keyPlaceholder ?? "key"}
                disabled={keyDisabled}
                onChange={(nextValue) => {
                  const next = [...rows];
                  next[index] = { ...next[index], key: nextValue };
                  commit(next);
                }}
              />
            ) : (
              <SuggestInput
                value={row.key}
                suggestions={keySuggestions}
                ariaLabel={keyCaption ?? keyPlaceholder ?? "key"}
                onChange={(nextValue) => {
                  const next = [...rows];
                  next[index] = { ...next[index], key: nextValue };
                  commit(next);
                }}
              />
            )}
            {valueOptions ? (
              <OptionsSelect
                value={row.val}
                options={valueOptions}
                ariaLabel={valueCaption ?? valuePlaceholder ?? "value"}
                disabled={valueDisabled}
                onChange={(nextValue) => {
                  const next = [...rows];
                  next[index] = { ...next[index], val: nextValue };
                  commit(next);
                }}
              />
            ) : (
              <SuggestInput
                value={row.val}
                suggestions={valueSuggestions}
                ariaLabel={valueCaption ?? valuePlaceholder ?? "value"}
                onChange={(nextValue) => {
                  const next = [...rows];
                  next[index] = { ...next[index], val: nextValue };
                  commit(next);
                }}
              />
            )}
            <button
              type="button"
              className="btn small danger"
              aria-label={t("editor.structured.removeRow")}
              onClick={() => commit(rows.filter((_, i) => i !== index))}
            >
              ×
            </button>
          </div>
        ))}
      </div>
      <ListActions
        onAdd={() => commit([...rows, { key: "", val: "" }])}
        addLabel={t("editor.structured.addPair")}
      />
    </div>
  );
}

function itemsFromValue(value: string | undefined): string[] {
  return parseJsonArray<string>(value, []);
}

function serializeItems(items: string[]): string {
  const filtered = items.filter((item) => item.trim() !== "");
  return filtered.length ? stringifyJson(filtered) : "[]";
}

function rowsFromValue(value: string | undefined): { key: string; val: string }[] {
  return Object.entries(parseJsonObject(value)).map(([key, val]) => ({ key, val }));
}

export function VariableSelect({
  label,
  value,
  onChange,
  variables,
  allowCustom = true,
  disabled,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  variables: string[];
  allowCustom?: boolean;
  disabled?: boolean;
}) {
  const { t } = useTranslation("widgets");
  return (
    <label>
      <HintCaption hint={allowCustom ? t("editor.placeholder.orEnterVariable") : undefined}>{label}</HintCaption>
      <div className="field-controls">
        <select value={value} onChange={(e) => onChange(e.target.value)} disabled={disabled}>
          <option value="">—</option>
          {variables.map((name) => (
            <option key={name} value={name}>
              {name}
            </option>
          ))}
        </select>
        {allowCustom && (
          <input
            value={value}
            onChange={(e) => onChange(e.target.value)}
            disabled={disabled}
          />
        )}
      </div>
      {!allowCustom && !disabled && variables.length === 0 ? (
        <p className="hint">{t("editor.variableNamesEmpty")}</p>
      ) : null}
    </label>
  );
}

export function ObjectTableColumnsEditor({
  value,
  onChange,
  parentPath,
  objects = [],
}: {
  value: string | undefined;
  onChange: (next: string) => void;
  parentPath?: string;
  objects?: Array<{ path: string; variableNames: string[] }>;
}) {
  const { t } = useTranslation("widgets");
  const columns = parseJsonArray<ObjectTableColumn>(value, []);
  const parent = parentPath?.trim() ?? "";
  const scoped = parent
    ? objects.filter((object) => object.path === parent || object.path.startsWith(`${parent}.`))
    : [];
  const variableNames = [
    ...new Set(scoped.flatMap((object) => object.variableNames).map((name) => name.trim()).filter(Boolean)),
  ].sort((a, b) => a.localeCompare(b));
  const variablesLocked = Boolean(parent);
  const samplePaths = [
    ...new Set(
      columns.flatMap((column) => {
        const name = column.variable?.trim();
        if (!variablesLocked || !name) return [];
        const source = scoped.find((object) => object.variableNames.includes(name));
        return source ? [source.path] : [];
      }),
    ),
  ].sort();
  const schemaQuery = useQuery({
    queryKey: ["widget-editor-object-table-fields", samplePaths],
    queryFn: () => fetchVariablesBatch(samplePaths),
    enabled: samplePaths.length > 0,
  });

  const fieldsFor = (variableName: string | undefined): string[] => {
    const name = variableName?.trim();
    if (!name) return [];
    const source = scoped.find((object) => object.variableNames.includes(name));
    if (!source) return [];
    return recordFieldNames(schemaQuery.data?.[source.path], name);
  };

  const setColumns = (next: ObjectTableColumn[]) => {
    onChange(stringifyJson(next));
  };

  return (
    <div className="widget-editor-structured full">
      <HintCaption>{t("editor.structured.columns")}</HintCaption>
      {!parent ? <p className="hint">{t("editor.objectTableVariablesNeedParent")}</p> : null}
      {parent && variableNames.length === 0 ? (
        <p className="hint">{t("editor.objectTableVariablesEmpty")}</p>
      ) : null}
      <div className="widget-editor-table-editor">
        <div className="widget-editor-table-head">
          <HintCaption className="widget-editor-mini-caption" hint="sysName">
            {t("editor.structured.colVariable")}
          </HintCaption>
          <span>{t("editor.structured.colLabel")}</span>
          <HintCaption className="widget-editor-mini-caption" hint="value">
            {t("editor.structured.colField")}
          </HintCaption>
          <span />
        </div>
        {columns.map((col, index) => {
          const recordFields = fieldsFor(col.variable);
          const lockField =
            variablesLocked &&
            Boolean(col.variable?.trim()) &&
            (schemaQuery.isLoading || recordFields.length > 0);
          return (
          <div key={index} className="widget-editor-table-row">
            {variablesLocked ? (
              <OptionsSelect
                ariaLabel={t("editor.structured.colVariable")}
                value={col.variable ?? ""}
                options={variableNames}
                onChange={(nextValue) => {
                  const next = [...columns];
                  next[index] = { ...next[index], variable: nextValue || undefined };
                  setColumns(next);
                }}
              />
            ) : (
              <input
                value={col.variable ?? ""}
                aria-label={t("editor.structured.colVariable")}
                onChange={(e) => {
                  const next = [...columns];
                  next[index] = { ...next[index], variable: e.target.value || undefined };
                  setColumns(next);
                }}
              />
            )}
            <input
              value={col.label}
              onChange={(e) => {
                const next = [...columns];
                next[index] = { ...next[index], label: e.target.value };
                setColumns(next);
              }}
            />
            {lockField ? (
              <OptionsSelect
                ariaLabel={t("editor.structured.colField")}
                value={col.field ?? ""}
                options={recordFields}
                disabled={schemaQuery.isLoading}
                onChange={(nextValue) => {
                  const next = [...columns];
                  next[index] = { ...next[index], field: nextValue || undefined };
                  setColumns(next);
                }}
              />
            ) : (
              <input
                value={col.field ?? ""}
                aria-label={t("editor.structured.colField")}
                onChange={(e) => {
                  const next = [...columns];
                  next[index] = { ...next[index], field: e.target.value || undefined };
                  setColumns(next);
                }}
              />
            )}
            <button
              type="button"
              className="btn small danger"
              onClick={() => setColumns(columns.filter((_, i) => i !== index))}
            >
              ×
            </button>
          </div>
          );
        })}
      </div>
      <ListActions
        onAdd={() => setColumns([...columns, { label: "", variable: "" }])}
        addLabel={t("editor.structured.addColumn")}
      />
    </div>
  );
}

const FUNCTION_FORM_FIELD_TYPES = [
  "text",
  "number",
  "select",
  "multiselect",
  "time",
  "checkbox",
  "textarea",
] as const;

const INPUT_FORM_FIELD_TYPES = [
  "text",
  "number",
  "textarea",
  "select",
  "slider",
  "checkbox",
  "radio",
  "datetime",
  "time",
] as const;

export function FormFieldsEditor({
  mode,
  value,
  onChange,
  objectPath,
  functionName,
}: {
  mode: "function-form" | "input-form";
  value: string | undefined;
  onChange: (next: string) => void;
  /** Function-form field names are limited to this function's input schema. */
  objectPath?: string;
  functionName?: string;
}) {
  const { t } = useTranslation("widgets");
  const path = objectPath?.trim() ?? "";
  const fnName = functionName?.trim() ?? "";
  const inputsQuery = useQuery({
    queryKey: ["widget-editor-object-functions", path],
    queryFn: () => fetchObjectEditor(path),
    enabled: mode === "function-form" && Boolean(path) && Boolean(fnName),
  });
  const inputNames = [
    ...new Set(
      (
        inputsQuery.data?.functions.find((fn) => fn.name === fnName)?.inputSchema?.fields ?? []
      )
        .map((field) => field.name.trim())
        .filter(Boolean),
    ),
  ].sort((a, b) => a.localeCompare(b));
  const types =
    mode === "function-form" ? FUNCTION_FORM_FIELD_TYPES : INPUT_FORM_FIELD_TYPES;
  const namesLocked = mode === "function-form";
  const namesDisabled = namesLocked && (!path || !fnName || inputsQuery.isLoading);

  if (mode === "function-form") {
    const fields = parseJsonArray<FunctionFormField>(value, []);
    const setFields = (next: FunctionFormField[]) => onChange(stringifyJson(next));

    return (
      <div className="widget-editor-structured full">
        <HintCaption>{t("editor.structured.formFields")}</HintCaption>
        {!path || !fnName ? <p className="hint">{t("editor.functionInputsNeedFunction")}</p> : null}
        {path && fnName && inputsQuery.isSuccess && inputNames.length === 0 ? (
          <p className="hint">{t("editor.functionInputsEmpty")}</p>
        ) : null}
        {fields.map((field, index) => (
          <div key={index} className="widget-editor-field-card">
            <div className="widget-editor-list-row">
              <MiniField caption={t("editor.structured.fieldName")}>
                <OptionsSelect
                  ariaLabel={t("editor.structured.fieldName")}
                  value={field.name}
                  options={inputNames}
                  disabled={namesDisabled}
                  onChange={(name) => {
                    const next = [...fields];
                    next[index] = { ...next[index], name };
                    setFields(next);
                  }}
                />
              </MiniField>
              <MiniField caption={t("editor.structured.fieldLabel")}>
                <input
                  value={field.label}
                  onChange={(e) => {
                    const next = [...fields];
                    next[index] = { ...next[index], label: e.target.value };
                    setFields(next);
                  }}
                />
              </MiniField>
              <select
                value={field.type}
                onChange={(e) => {
                  const next = [...fields];
                  next[index] = {
                    ...next[index],
                    type: e.target.value as FunctionFormField["type"],
                  };
                  setFields(next);
                }}
              >
                {types.map((tp) => (
                  <option key={tp} value={tp}>
                    {tp}
                  </option>
                ))}
              </select>
              <button
                type="button"
                className="btn small danger"
                onClick={() => setFields(fields.filter((_, i) => i !== index))}
              >
                ×
              </button>
            </div>
            <div className="widget-editor-list-row">
              <MiniField caption={t("editor.structured.defaultValue")}>
                <input
                  value={field.defaultValue ?? ""}
                  onChange={(e) => {
                    const next = [...fields];
                    next[index] = { ...next[index], defaultValue: e.target.value || undefined };
                    setFields(next);
                  }}
                />
              </MiniField>
              <label className="widget-editor-inline-check">
                <input
                  type="checkbox"
                  checked={field.required === true}
                  onChange={(e) => {
                    const next = [...fields];
                    next[index] = { ...next[index], required: e.target.checked || undefined };
                    setFields(next);
                  }}
                />
                {t("editor.structured.required")}
              </label>
            </div>
          </div>
        ))}
        <ListActions
          onAdd={() =>
            setFields([...fields, { name: "", label: "", type: "text" }])
          }
          addLabel={t("editor.structured.addField")}
        />
      </div>
    );
  }

  const fields = parseJsonArray<InputFormField>(value, []);
  const setFields = (next: InputFormField[]) => onChange(stringifyJson(next));

  return (
    <div className="widget-editor-structured full">
      <HintCaption>{t("editor.structured.formFields")}</HintCaption>
      {fields.map((field, index) => (
        <div key={index} className="widget-editor-field-card">
          <div className="widget-editor-list-row">
            <MiniField caption={t("editor.structured.fieldName")}>
              <input
                value={field.name}
                onChange={(e) => {
                  const next = [...fields];
                  next[index] = { ...next[index], name: e.target.value };
                  setFields(next);
                }}
              />
            </MiniField>
            <MiniField caption={t("editor.structured.fieldLabel")}>
              <input
                value={field.label}
                onChange={(e) => {
                  const next = [...fields];
                  next[index] = { ...next[index], label: e.target.value };
                  setFields(next);
                }}
              />
            </MiniField>
            <select
              value={field.type}
              onChange={(e) => {
                const next = [...fields];
                next[index] = { ...next[index], type: e.target.value as InputFormField["type"] };
                setFields(next);
              }}
            >
              {types.map((tp) => (
                <option key={tp} value={tp}>
                  {tp}
                </option>
              ))}
            </select>
            <button
              type="button"
              className="btn small danger"
              onClick={() => setFields(fields.filter((_, i) => i !== index))}
            >
              ×
            </button>
          </div>
          <div className="widget-editor-list-row">
            <MiniField caption={t("editor.structured.targetVariable")}>
              <input
                value={field.variableName ?? ""}
                onChange={(e) => {
                  const next = [...fields];
                  next[index] = { ...next[index], variableName: e.target.value || undefined };
                  setFields(next);
                }}
              />
            </MiniField>
            <MiniField caption={t("editor.structured.defaultValue")}>
              <input
                value={field.defaultValue ?? ""}
                onChange={(e) => {
                  const next = [...fields];
                  next[index] = { ...next[index], defaultValue: e.target.value || undefined };
                  setFields(next);
                }}
              />
            </MiniField>
          </div>
        </div>
      ))}
      <ListActions
        onAdd={() => setFields([...fields, { name: "", label: "", type: "text" }])}
        addLabel={t("editor.structured.addField")}
      />
    </div>
  );
}

interface NavItem {
  label: string;
  dashboardPath: string;
}

export function NavMenuItemsEditor({
  value,
  onChange,
  dashboards,
}: {
  value: string | undefined;
  onChange: (next: string) => void;
  dashboards: Array<{ path: string; displayName: string }>;
}) {
  const { t } = useTranslation("widgets");
  const items = parseJsonArray<NavItem>(value, []);
  const setItems = (next: NavItem[]) => onChange(stringifyJson(next));

  return (
    <div className="widget-editor-structured full">
      <HintCaption>{t("editor.structured.navItems")}</HintCaption>
      {items.map((item, index) => (
        <div key={index} className="widget-editor-list-row">
          <MiniField caption={t("editor.structured.navLabel")}>
            <input
              value={item.label}
              onChange={(e) => {
                const next = [...items];
                next[index] = { ...next[index], label: e.target.value };
                setItems(next);
              }}
            />
          </MiniField>
          <MiniField caption={t("editor.col.objectPath")} hint="root.platform.dashboards...">
            <ObjectPathField
              value={item.dashboardPath}
              objects={dashboards}
              filterTypes={["DASHBOARD"]}
              placeholder=""
              onChange={(path) => {
                const next = [...items];
                next[index] = { ...next[index], dashboardPath: path };
                setItems(next);
              }}
            />
          </MiniField>
          <button
            type="button"
            className="btn small danger"
            onClick={() => setItems(items.filter((_, i) => i !== index))}
          >
            ×
          </button>
        </div>
      ))}
      <ListActions
        onAdd={() => setItems([...items, { label: "", dashboardPath: "" }])}
        addLabel={t("editor.structured.addNavItem")}
      />
    </div>
  );
}

interface IdLabelItem {
  id: string;
  label: string;
  children?: unknown[];
}

export function IdLabelListEditor({
  label,
  value,
  onChange,
  idPrefix,
}: {
  label: string;
  value: string | undefined;
  onChange: (next: string) => void;
  idPrefix: string;
}) {
  const { t } = useTranslation("widgets");
  const items = parseJsonArray<IdLabelItem>(value, []);
  const setItems = (next: IdLabelItem[]) => onChange(stringifyJson(next));

  return (
    <div className="widget-editor-structured full">
      <HintCaption hint={t("editor.structured.nestedWidgetsHint")}>{label}</HintCaption>
      {items.map((item, index) => (
        <div key={index} className="widget-editor-list-row">
          <MiniField caption="id">
            <input
              value={item.id}
              onChange={(e) => {
                const next = [...items];
                next[index] = { ...next[index], id: e.target.value };
                setItems(next);
              }}
            />
          </MiniField>
          <MiniField caption={t("editor.structured.fieldLabel")}>
            <input
              value={item.label}
              onChange={(e) => {
                const next = [...items];
                next[index] = { ...next[index], label: e.target.value };
                setItems(next);
              }}
            />
          </MiniField>
          <button
            type="button"
            className="btn small danger"
            onClick={() => setItems(items.filter((_, i) => i !== index))}
          >
            ×
          </button>
        </div>
      ))}
      <ListActions
        onAdd={() =>
          setItems([
            ...items,
            { id: `${idPrefix}${items.length + 1}`, label: "", children: [] },
          ])
        }
        addLabel={t("editor.structured.addItem")}
      />
    </div>
  );
}

export function SheetGridSizeEditor({
  sheetConfigJson,
  onChange,
}: {
  sheetConfigJson: string | undefined;
  onChange: (next: string) => void;
}) {
  const { t } = useTranslation("widgets");
  const config: SheetConfig = parseSheetConfig(sheetConfigJson) ?? {
    rows: 10,
    cols: 4,
    cells: {},
  };

  const patch = (partial: Partial<SheetConfig>) => {
    onChange(sheetConfigToJson({ ...config, ...partial }));
  };

  return (
    <div className="widget-editor-structured full">
      <HintCaption>{t("editor.spreadsheet.gridSize")}</HintCaption>
      <div className="widget-editor-list-row">
        <label>
          rows
          <input
            type="number"
            min={1}
            max={500}
            value={config.rows}
            onChange={(e) => patch({ rows: Number(e.target.value) || 1 })}
          />
        </label>
        <label>
          cols
          <input
            type="number"
            min={1}
            max={52}
            value={config.cols}
            onChange={(e) => patch({ cols: Number(e.target.value) || 1 })}
          />
        </label>
        <label>
          frozenRows
          <input
            type="number"
            min={0}
            value={config.frozenRows ?? 0}
            onChange={(e) => patch({ frozenRows: Number(e.target.value) || undefined })}
          />
        </label>
        <label>
          frozenCols
          <input
            type="number"
            min={0}
            value={config.frozenCols ?? 0}
            onChange={(e) => patch({ frozenCols: Number(e.target.value) || undefined })}
          />
        </label>
      </div>
    </div>
  );
}

const STYLE_KEYS = WIDGET_STYLE_KEYS_HINT.split(", ").map((s) => s.trim()) as WidgetStyleKey[];

export function WidgetStylesEditor({
  value,
  onChange,
}: {
  value: string | undefined;
  onChange: (next: string | undefined) => void;
}) {
  const { t } = useTranslation("widgets");
  const styles = parseWidgetStyles(value);
  const [activeKey, setActiveKey] = useState<WidgetStyleKey>("value");

  const current = styles[activeKey] ?? {};

  const patchStyle = (prop: string, propValue: string) => {
    const nextMap = { ...styles };
    const el = { ...(nextMap[activeKey] ?? {}) } as Record<string, string>;
    if (propValue.trim()) {
      el[prop] = propValue;
    } else {
      delete el[prop];
    }
    if (Object.keys(el).length) {
      nextMap[activeKey] = el;
    } else {
      delete nextMap[activeKey];
    }
    const keys = Object.keys(nextMap);
    onChange(keys.length ? JSON.stringify(nextMap) : undefined);
  };

  return (
    <div className="widget-editor-structured full">
      <HintCaption>{t("editor.styling")}</HintCaption>
      <label>
        {t("editor.structured.styleElement")}
        <select value={activeKey} onChange={(e) => setActiveKey(e.target.value as WidgetStyleKey)}>
          {STYLE_KEYS.map((k) => (
            <option key={k} value={k}>
              {k}
            </option>
          ))}
        </select>
      </label>
      <div className="widget-editor-list-row">
        <MiniField caption="fontSize" hint="0.88rem">
          <input
            value={String(current.fontSize ?? "")}
            onChange={(e) => patchStyle("fontSize", e.target.value)}
          />
        </MiniField>
        <label>
          color
          <input
            type="color"
            value={typeof current.color === "string" && current.color.startsWith("#") ? current.color : "var(--text)"}
            onChange={(e) => patchStyle("color", e.target.value)}
          />
        </label>
      </div>
      <div className="widget-editor-list-row">
        <label>
          display
          <select
            value={String(current.display ?? "")}
            onChange={(e) => patchStyle("display", e.target.value)}
          >
            <option value="">—</option>
            <option value="block">block</option>
            <option value="none">none</option>
            <option value="flex">flex</option>
          </select>
        </label>
        <label>
          whiteSpace
          <select
            value={String(current.whiteSpace ?? "")}
            onChange={(e) => patchStyle("whiteSpace", e.target.value)}
          >
            <option value="">—</option>
            <option value="nowrap">nowrap</option>
            <option value="normal">normal</option>
          </select>
        </label>
      </div>
      <details className="widget-editor-advanced-json">
        <summary>
          <HintCaption className="widget-editor-mini-caption">
            {t("editor.structured.stylesJsonAdvanced")}
          </HintCaption>
        </summary>
        <textarea
          rows={4}
          className="mono"
          value={value ?? ""}
          onChange={(e) => onChange(e.target.value || undefined)}
        />
      </details>
    </div>
  );
}

export function TabPanelMetaEditor({
  value,
  onChange,
}: {
  value: string | undefined;
  onChange: (next: string) => void;
}) {
  const { t } = useTranslation("widgets");
  type TabRow = { id: string; label: string; children?: unknown[] };
  const tabs = parseJsonArray<TabRow>(value, []);

  const setTabs = (next: TabRow[]) => {
    onChange(stringifyJson(next));
  };

  return (
    <div className="widget-editor-structured full">
      <HintCaption>{t("editor.structured.tabs")}</HintCaption>
      {tabs.map((tab, index) => (
        <div key={index} className="widget-editor-list-row">
          <MiniField caption="id">
            <input
              value={tab.id}
              onChange={(e) => {
                const next = [...tabs];
                next[index] = { ...next[index], id: e.target.value };
                setTabs(next);
              }}
            />
          </MiniField>
          <MiniField caption={t("editor.structured.fieldLabel")}>
            <input
              value={tab.label}
              onChange={(e) => {
                const next = [...tabs];
                next[index] = { ...next[index], label: e.target.value };
                setTabs(next);
              }}
            />
          </MiniField>
          <button
            type="button"
            className="btn small danger"
            onClick={() => setTabs(tabs.filter((_, i) => i !== index))}
          >
            ×
          </button>
        </div>
      ))}
      <ListActions
        onAdd={() => setTabs([...tabs, { id: `tab${tabs.length + 1}`, label: "", children: [] }])}
        addLabel={t("editor.structured.addTab")}
      />
    </div>
  );
}

export function AdvancedJsonField({
  label,
  value,
  onChange,
  rows = 4,
  placeholder,
}: {
  label: string;
  value: string | undefined;
  onChange: (next: string | undefined) => void;
  rows?: number;
  placeholder?: string;
}) {
  const { t } = useTranslation("widgets");
  return (
    <details className="widget-editor-advanced-json full">
      <summary>
        <HintCaption
          className="widget-editor-mini-caption"
          hint={joinHints(placeholder, t("editor.structured.jsonAdvanced"))}
        >
          {label}
        </HintCaption>
      </summary>
      <textarea
        rows={rows}
        className="mono"
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value || undefined)}
      />
    </details>
  );
}
