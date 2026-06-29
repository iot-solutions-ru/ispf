import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import type { ObjectTableColumn, SheetConfig } from "../../types/dashboard";
import type { FunctionFormField, InputFormField } from "../../types/dashboard";
import {
  parseSheetConfig,
  sheetConfigToJson,
} from "./sheet/sheetConfig";
import {
  WIDGET_STYLE_KEYS_HINT,
  type WidgetStyleKey,
  parseWidgetStyles,
} from "./widgetStyles";

export function parseJsonArray<T>(raw: string | undefined, fallback: T[] = []): T[] {
  if (!raw?.trim()) return fallback;
  try {
    const parsed = JSON.parse(raw) as unknown;
    return Array.isArray(parsed) ? (parsed as T[]) : fallback;
  } catch {
    return fallback;
  }
}

export function parseJsonObject(raw: string | undefined): Record<string, string> {
  if (!raw?.trim()) return {};
  try {
    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) return {};
    const result: Record<string, string> = {};
    for (const [k, v] of Object.entries(parsed as Record<string, unknown>)) {
      if (v !== undefined && v !== null) result[k] = String(v);
    }
    return result;
  } catch {
    return {};
  }
}

export function stringifyJson(value: unknown): string {
  return JSON.stringify(value, null, 2);
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
  value,
  onChange,
  suggestions = [],
  placeholder,
}: {
  label: string;
  value: string | undefined;
  onChange: (next: string) => void;
  suggestions?: string[];
  placeholder?: string;
}) {
  const { t } = useTranslation("widgets");
  const items = parseJsonArray<string>(value, []);

  const setItems = (next: string[]) => {
    const filtered = next.filter((s) => s.trim() !== "");
    onChange(filtered.length ? stringifyJson(filtered) : "[]");
  };

  return (
    <div className="widget-editor-structured full">
      <span className="field-caption">{label}</span>
      <div className="widget-editor-list">
        {items.map((item, index) => (
          <div key={index} className="widget-editor-list-row">
            <input
              list={suggestions.length ? `suggest-${label}` : undefined}
              value={item}
              placeholder={placeholder}
              onChange={(e) => {
                const next = [...items];
                next[index] = e.target.value;
                setItems(next);
              }}
            />
            <button
              type="button"
              className="btn small danger"
              aria-label={t("editor.structured.removeRow")}
              onClick={() => setItems(items.filter((_, i) => i !== index))}
            >
              ×
            </button>
          </div>
        ))}
        {suggestions.length > 0 && (
          <datalist id={`suggest-${label}`}>
            {suggestions.map((s) => (
              <option key={s} value={s} />
            ))}
          </datalist>
        )}
      </div>
      <ListActions onAdd={() => setItems([...items, ""])} addLabel={t("editor.structured.addItem")} />
    </div>
  );
}

export function KeyValueEditor({
  label,
  value,
  onChange,
  keyPlaceholder,
  valuePlaceholder,
}: {
  label: string;
  value: string | undefined;
  onChange: (next: string | undefined) => void;
  keyPlaceholder?: string;
  valuePlaceholder?: string;
}) {
  const { t } = useTranslation("widgets");
  const pairs = useMemo(() => {
    const obj = parseJsonObject(value);
    return Object.entries(obj).map(([k, v]) => ({ key: k, val: v }));
  }, [value]);

  const commit = (rows: { key: string; val: string }[]) => {
    const obj: Record<string, string> = {};
    for (const row of rows) {
      if (row.key.trim()) obj[row.key.trim()] = row.val;
    }
    const keys = Object.keys(obj);
    onChange(keys.length ? stringifyJson(obj) : undefined);
  };

  return (
    <div className="widget-editor-structured full">
      <span className="field-caption">{label}</span>
      <div className="widget-editor-list">
        {pairs.map((row, index) => (
          <div key={index} className="widget-editor-list-row widget-editor-kv-row">
            <input
              value={row.key}
              placeholder={keyPlaceholder ?? "key"}
              onChange={(e) => {
                const next = [...pairs];
                next[index] = { ...next[index], key: e.target.value };
                commit(next);
              }}
            />
            <input
              value={row.val}
              placeholder={valuePlaceholder ?? "value"}
              onChange={(e) => {
                const next = [...pairs];
                next[index] = { ...next[index], val: e.target.value };
                commit(next);
              }}
            />
            <button
              type="button"
              className="btn small danger"
              aria-label={t("editor.structured.removeRow")}
              onClick={() => commit(pairs.filter((_, i) => i !== index))}
            >
              ×
            </button>
          </div>
        ))}
      </div>
      <ListActions
        onAdd={() => commit([...pairs, { key: "", val: "" }])}
        addLabel={t("editor.structured.addPair")}
      />
    </div>
  );
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
      <span className="field-caption">{label}</span>
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
            placeholder={t("editor.placeholder.orEnterVariable")}
            disabled={disabled}
          />
        )}
      </div>
    </label>
  );
}

export function ObjectTableColumnsEditor({
  value,
  onChange,
  variableSuggestions = [],
}: {
  value: string | undefined;
  onChange: (next: string) => void;
  variableSuggestions?: string[];
}) {
  const { t } = useTranslation("widgets");
  const columns = parseJsonArray<ObjectTableColumn>(value, []);

  const setColumns = (next: ObjectTableColumn[]) => {
    onChange(stringifyJson(next));
  };

  return (
    <div className="widget-editor-structured full">
      <span className="field-caption">{t("editor.structured.columns")}</span>
      <div className="widget-editor-table-editor">
        <div className="widget-editor-table-head">
          <span>{t("editor.structured.colVariable")}</span>
          <span>{t("editor.structured.colLabel")}</span>
          <span>{t("editor.structured.colField")}</span>
          <span />
        </div>
        {columns.map((col, index) => (
          <div key={index} className="widget-editor-table-row">
            <input
              list="col-var-suggest"
              value={col.variable ?? ""}
              placeholder="sysName"
              onChange={(e) => {
                const next = [...columns];
                next[index] = { ...next[index], variable: e.target.value || undefined };
                setColumns(next);
              }}
            />
            <input
              value={col.label}
              onChange={(e) => {
                const next = [...columns];
                next[index] = { ...next[index], label: e.target.value };
                setColumns(next);
              }}
            />
            <input
              value={col.field ?? ""}
              placeholder="value"
              onChange={(e) => {
                const next = [...columns];
                next[index] = { ...next[index], field: e.target.value || undefined };
                setColumns(next);
              }}
            />
            <button
              type="button"
              className="btn small danger"
              onClick={() => setColumns(columns.filter((_, i) => i !== index))}
            >
              ×
            </button>
          </div>
        ))}
        <datalist id="col-var-suggest">
          {variableSuggestions.map((s) => (
            <option key={s} value={s} />
          ))}
        </datalist>
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
}: {
  mode: "function-form" | "input-form";
  value: string | undefined;
  onChange: (next: string) => void;
}) {
  const { t } = useTranslation("widgets");
  const types =
    mode === "function-form" ? FUNCTION_FORM_FIELD_TYPES : INPUT_FORM_FIELD_TYPES;

  if (mode === "function-form") {
    const fields = parseJsonArray<FunctionFormField>(value, []);
    const setFields = (next: FunctionFormField[]) => onChange(stringifyJson(next));

    return (
      <div className="widget-editor-structured full">
        <span className="field-caption">{t("editor.structured.formFields")}</span>
        {fields.map((field, index) => (
          <div key={index} className="widget-editor-field-card">
            <div className="widget-editor-list-row">
              <input
                value={field.name}
                placeholder={t("editor.structured.fieldName")}
                onChange={(e) => {
                  const next = [...fields];
                  next[index] = { ...next[index], name: e.target.value };
                  setFields(next);
                }}
              />
              <input
                value={field.label}
                placeholder={t("editor.structured.fieldLabel")}
                onChange={(e) => {
                  const next = [...fields];
                  next[index] = { ...next[index], label: e.target.value };
                  setFields(next);
                }}
              />
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
              <input
                value={field.defaultValue ?? ""}
                placeholder={t("editor.structured.defaultValue")}
                onChange={(e) => {
                  const next = [...fields];
                  next[index] = { ...next[index], defaultValue: e.target.value || undefined };
                  setFields(next);
                }}
              />
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
      <span className="field-caption">{t("editor.structured.formFields")}</span>
      {fields.map((field, index) => (
        <div key={index} className="widget-editor-field-card">
          <div className="widget-editor-list-row">
            <input
              value={field.name}
              placeholder={t("editor.structured.fieldName")}
              onChange={(e) => {
                const next = [...fields];
                next[index] = { ...next[index], name: e.target.value };
                setFields(next);
              }}
            />
            <input
              value={field.label}
              placeholder={t("editor.structured.fieldLabel")}
              onChange={(e) => {
                const next = [...fields];
                next[index] = { ...next[index], label: e.target.value };
                setFields(next);
              }}
            />
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
            <input
              value={field.variableName ?? ""}
              placeholder={t("editor.structured.targetVariable")}
              onChange={(e) => {
                const next = [...fields];
                next[index] = { ...next[index], variableName: e.target.value || undefined };
                setFields(next);
              }}
            />
            <input
              value={field.defaultValue ?? ""}
              placeholder={t("editor.structured.defaultValue")}
              onChange={(e) => {
                const next = [...fields];
                next[index] = { ...next[index], defaultValue: e.target.value || undefined };
                setFields(next);
              }}
            />
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
      <span className="field-caption">{t("editor.structured.navItems")}</span>
      {items.map((item, index) => (
        <div key={index} className="widget-editor-list-row">
          <input
            value={item.label}
            placeholder={t("editor.structured.navLabel")}
            onChange={(e) => {
              const next = [...items];
              next[index] = { ...next[index], label: e.target.value };
              setItems(next);
            }}
          />
          <select
            value={item.dashboardPath}
            onChange={(e) => {
              const next = [...items];
              next[index] = { ...next[index], dashboardPath: e.target.value };
              setItems(next);
            }}
          >
            <option value="">—</option>
            {dashboards.map((d) => (
              <option key={d.path} value={d.path}>
                {d.displayName}
              </option>
            ))}
          </select>
          <input
            value={item.dashboardPath}
            placeholder="root.platform.dashboards..."
            onChange={(e) => {
              const next = [...items];
              next[index] = { ...next[index], dashboardPath: e.target.value };
              setItems(next);
            }}
          />
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
      <span className="field-caption">{label}</span>
      <p className="hint">{t("editor.structured.nestedWidgetsHint")}</p>
      {items.map((item, index) => (
        <div key={index} className="widget-editor-list-row">
          <input
            value={item.id}
            placeholder="id"
            onChange={(e) => {
              const next = [...items];
              next[index] = { ...next[index], id: e.target.value };
              setItems(next);
            }}
          />
          <input
            value={item.label}
            placeholder={t("editor.structured.fieldLabel")}
            onChange={(e) => {
              const next = [...items];
              next[index] = { ...next[index], label: e.target.value };
              setItems(next);
            }}
          />
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
      <span className="field-caption">{t("editor.spreadsheet.gridSize")}</span>
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
      <span className="field-caption">{t("editor.styling")}</span>
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
        <label>
          fontSize
          <input
            value={String(current.fontSize ?? "")}
            onChange={(e) => patchStyle("fontSize", e.target.value)}
            placeholder="0.88rem"
          />
        </label>
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
        <summary>{t("editor.structured.stylesJsonAdvanced")}</summary>
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
      <span className="field-caption">{t("editor.structured.tabs")}</span>
      {tabs.map((tab, index) => (
        <div key={index} className="widget-editor-list-row">
          <input
            value={tab.id}
            placeholder="id"
            onChange={(e) => {
              const next = [...tabs];
              next[index] = { ...next[index], id: e.target.value };
              setTabs(next);
            }}
          />
          <input
            value={tab.label}
            placeholder={t("editor.structured.fieldLabel")}
            onChange={(e) => {
              const next = [...tabs];
              next[index] = { ...next[index], label: e.target.value };
              setTabs(next);
            }}
          />
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
      <summary>{label} ({t("editor.structured.jsonAdvanced")})</summary>
      <textarea
        rows={rows}
        className="mono"
        value={value ?? ""}
        placeholder={placeholder}
        onChange={(e) => onChange(e.target.value || undefined)}
      />
    </details>
  );
}
