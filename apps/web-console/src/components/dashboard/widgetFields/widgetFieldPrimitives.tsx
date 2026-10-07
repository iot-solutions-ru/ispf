// Shared building blocks for widget editor fields: context type, layout primitives, path pickers.
import { useQuery } from "@tanstack/react-query";
import type { TFunction } from "i18next";
import { AutoComplete } from "antd";
import { Children, Fragment, isValidElement, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import type { AnalyticsQueryTagInput } from "../../../api";
import { fetchReport } from "../../../api/reports";
import { parseAnalyticsQueryTags } from "../../../hooks/useAnalyticsMultiSeries";
import type { ObjectType } from "../../../types";
import type { ChartWidget, DashboardWidget } from "../../../types/dashboard";
import { ObjectPathField } from "../../../ui";
import { AdvancedJsonField, HintCaption } from "../widgetEditorStructured";

export type ObjectOption = { path: string; displayName: string; variableNames: string[] };
export type DashboardOption = { path: string; displayName: string };
export type ReportOption = { path: string; displayName: string };

export interface WidgetFieldContext {
  widget: DashboardWidget;
  objects: ObjectOption[];
  dashboards: DashboardOption[];
  reports: ReportOption[];
  variables: string[];
  allVariableNames: string[];
  variableSelectEnabled: boolean;
  /** Selection-slot names already used by widgets on this dashboard. */
  selectionKeys: string[];
  /** session.params names already used by widgets on this dashboard. */
  sessionParams: string[];
  update: (patch: Partial<DashboardWidget>) => void;
}

export type WidgetOfType<K extends DashboardWidget["type"]> = Extract<DashboardWidget, { type: K }>;

/** Field context narrowed to one widget type — what a per-type renderer receives. */
export type WidgetFieldContextFor<K extends DashboardWidget["type"]> = Omit<WidgetFieldContext, "widget"> & {
  widget: WidgetOfType<K>;
};

export type WidgetTypeFieldsRenderer<K extends DashboardWidget["type"]> = (
  ctx: WidgetFieldContextFor<K>,
  t: TFunction
) => ReactNode;

/** Per-type field renderers; a type without an entry renders no type-specific fields. */
export type WidgetTypeFieldsRegistry = { [K in DashboardWidget["type"]]?: WidgetTypeFieldsRenderer<K> };

export function Section({ title, hint }: { title: string; hint?: string }) {
  return (
    <div className="widget-editor-section-block">
      <h5 className="widget-editor-section">{title}</h5>
      {hint && <p className="hint widget-editor-hint">{hint}</p>}
    </div>
  );
}

function flattenFieldChildren(children: ReactNode): ReactNode[] {
  const out: ReactNode[] = [];
  Children.forEach(children, (child) => {
    if (child == null || child === false) return;
    if (isValidElement<{ children?: ReactNode }>(child) && child.type === Fragment) {
      out.push(...flattenFieldChildren(child.props.children));
      return;
    }
    if (isValidElement<{ children?: ReactNode }>(child) && child.type === FieldPairs) {
      out.push(...flattenFieldChildren(child.props.children));
      return;
    }
    out.push(child);
  });
  return out;
}

function isFullWidthField(node: ReactNode): boolean {
  if (!isValidElement<{ className?: string }>(node)) return false;
  if (node.type === FormRow) return true;
  if (node.type === Section) return true;
  const className = node.props.className;
  if (typeof className === "string") {
    if (className.includes("full") || className.includes("widget-editor-section-block")) return true;
  }
  if (node.type === "h5" || node.type === "p") return true;
  return false;
}

export function FieldPairs({ children }: { children: ReactNode }) {
  const items = flattenFieldChildren(children);
  const result: ReactNode[] = [];
  let pair: ReactNode[] = [];

  const flushPair = () => {
    if (pair.length === 0) return;
    result.push(
      <FormRow key={`row-${result.length}`}>
        {pair[0]}
        {pair[1] ?? <div className="form-grid-row-spacer" aria-hidden="true" />}
      </FormRow>,
    );
    pair = [];
  };

  for (const item of items) {
    if (isFullWidthField(item)) {
      flushPair();
      result.push(item);
    } else {
      pair.push(item);
      if (pair.length === 2) flushPair();
    }
  }
  flushPair();

  return <>{result}</>;
}

export function FormRow({ children }: { children: ReactNode }) {
  return <div className="form-grid-row">{children}</div>;
}

export function StackedSlot({ children }: { children: ReactNode }) {
  return <div className="field-controls-slot field-controls-slot--stacked">{children}</div>;
}

/** Free-text selection slot name. Known keys open as a list when the field is focused. */
export function SelectionKeyInput({
  value,
  keys,
  placeholder,
  disabled,
  className,
  onChange,
}: {
  value: string;
  keys: string[];
  placeholder?: string;
  disabled?: boolean;
  className?: string;
  onChange: (next: string) => void;
}) {
  const suggestions = keys.filter((key) => key.trim());
  if (suggestions.length === 0) {
    return (
      <input
        className={className}
        value={value}
        placeholder={placeholder}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
      />
    );
  }
  return (
    <AutoComplete
      className={["selection-key-input", className].filter(Boolean).join(" ")}
      value={value}
      options={suggestions.map((key) => ({ value: key }))}
      placeholder={placeholder}
      disabled={disabled}
      filterOption={false}
      virtual={false}
      getPopupContainer={() => document.body}
      onChange={onChange}
    />
  );
}

export function FieldLabel({
  caption,
  code,
  hint,
  children,
  className,
}: {
  caption: string;
  code?: string;
  hint?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <label className={className}>
      <HintCaption hint={hint}>{caption}</HintCaption>
      {code ? <span className="field-code">{code}</span> : null}
      {children}
    </label>
  );
}

export function PathSelect({
  label,
  value,
  onChange,
  placeholder,
  filterTypes,
}: {
  label: string;
  value: string;
  onChange: (path: string) => void;
  placeholder?: string;
  filterTypes?: ObjectType[];
}) {
  const { t } = useTranslation(["widgets", "common"]);
  return (
    <ObjectPathField
      className="path-select-field"
      label={label}
      value={value}
      onChange={onChange}
      filterTypes={filterTypes}
      hint={placeholder ?? t("common:objectPath.placeholder")}
      placeholder=""
      pickerTitle={label}
    />
  );
}

export function ReportParameterHints({ reportPath }: { reportPath: string }) {
  const { t } = useTranslation(["widgets", "common"]);
  const metaQuery = useQuery({
    queryKey: ["report-widget-hints", reportPath],
    queryFn: () => fetchReport(reportPath),
    enabled: Boolean(reportPath?.trim()),
  });

  if (!reportPath?.trim()) {
    return null;
  }
  if (metaQuery.isLoading) {
    return <p className="hint full">{t("editor.reportSchemaLoading")}</p>;
  }
  if (metaQuery.error || !metaQuery.data) {
    return null;
  }

  const data = metaQuery.data;
  const isTree = data.reportType === "tree-variables";
  if (isTree) {
    return (
      <p className="hint full">
        tree-variables: <code>{data.devicePathPattern}</code> · variable{" "}
        <code>{data.variableName}</code>
        {data.hasTemplate ? t("editor.yargTemplateLoaded") : ""}
      </p>
    );
  }

  const params = data.parameters ?? [];
  const defaults =
    data.defaultParameters && Object.keys(data.defaultParameters).length > 0
      ? JSON.stringify(data.defaultParameters)
      : null;

  return (
    <p className="hint full">
      {params.length > 0 ? (
        <>
          {t("editor.sqlParameters")} <code>{params.join(", ")}</code>
          {defaults && (
            <>
              {" "}
              · defaults: <code>{defaults}</code>
            </>
          )}
        </>
      ) : (
        <>{t("editor.sqlNoParameters")}</>
      )}
      {data.dataSourcePath && (
        <>
          {" "}
          · data source: <code>{data.dataSourcePath}</code>
        </>
      )}
      {data.hasTemplate ? t("editor.yargTemplateLoaded") : ""}
    </p>
  );
}

export function DashboardPathField({
  caption,
  value,
  dashboards,
  onChange,
}: {
  caption: string;
  value: string;
  dashboards: DashboardOption[];
  onChange: (path: string) => void;
}) {
  return (
    <FieldLabel caption={caption} className="path-select-field">
      <div className="field-controls">
        <DashboardPathInput value={value} dashboards={dashboards} onChange={onChange} />
      </div>
    </FieldLabel>
  );
}

export function DashboardPathInput({
  value,
  dashboards,
  onChange,
}: {
  value: string;
  dashboards: DashboardOption[];
  onChange: (path: string) => void;
}) {
  return (
    <ObjectPathField
      value={value}
      objects={dashboards}
      onChange={onChange}
      filterTypes={["DASHBOARD"]}
      hint="root.platform.dashboards.detail"
      placeholder=""
    />
  );
}

export function ChartAnalyticsQueryTagsField({
  widget,
  objects,
  update,
}: {
  widget: ChartWidget;
  objects: ObjectOption[];
  update: (patch: Partial<DashboardWidget>) => void;
}) {
  const { t } = useTranslation("widgets");
  const tags = parseAnalyticsQueryTags(widget.analyticsQueryTagsJson);

  const setTags = (next: AnalyticsQueryTagInput[]) => {
    const filtered = next.filter((tag) => tag.path?.trim() && tag.variable?.trim());
    update({
      analyticsQueryTagsJson: filtered.length > 0 ? JSON.stringify(filtered) : undefined,
    } as Partial<DashboardWidget>);
  };

  const updateTag = (index: number, patch: Partial<AnalyticsQueryTagInput>) => {
    setTags(tags.map((tag, i) => (i === index ? { ...tag, ...patch } : tag)));
  };

  return (
    <div className="widget-editor-analytics-tags full">
      <p className="hint widget-editor-type-hint">{t("editor.analyticsQueryTagsHint")}</p>
      {tags.length > 0 ? (
        <div className="widget-editor-analytics-tag-list">
          {tags.map((tag, index) => (
            <div key={`${tag.path}-${tag.variable}-${index}`} className="widget-editor-analytics-tag-card">
              <div className="widget-editor-analytics-tag-card-head">
                <span className="widget-editor-analytics-tag-title">
                  {tag.label?.trim() || tag.path.split(".").pop() || `#${index + 1}`}
                </span>
                <button
                  type="button"
                  className="btn small danger"
                  onClick={() => setTags(tags.filter((_, i) => i !== index))}
                >
                  {t("editor.analyticsQueryTagRemove")}
                </button>
              </div>
              <ObjectPathField
                className="widget-editor-analytics-tag-path"
                label={t("editor.objectPath")}
                value={tag.path}
                onChange={(path) => updateTag(index, { path })}
                hint={t("editor.placeholder.orEnterPath")}
                placeholder=""
                pickerTitle={t("editor.objectPath")}
              />
              <label>
                <HintCaption hint="temperature">{t("editor.variableName")}</HintCaption>
                <input
                  value={tag.variable}
                  onChange={(e) => updateTag(index, { variable: e.target.value })}
                />
              </label>
              <label>
                <HintCaption hint={tag.path.split(".").pop() || undefined}>
                  {t("editor.analyticsQueryTagLabel")}
                </HintCaption>
                <input
                  value={tag.label ?? ""}
                  onChange={(e) => updateTag(index, { label: e.target.value || undefined })}
                />
              </label>
            </div>
          ))}
        </div>
      ) : (
        <p className="hint">{t("editor.analyticsQueryTagsEmpty")}</p>
      )}
      <div className="widget-editor-list-actions">
        <button
          type="button"
          className="btn small"
          onClick={() =>
            setTags([
              ...tags,
              {
                path: objects[0]?.path ?? "",
                variable: "temperature",
                field: "value",
                label: objects[0]?.displayName ?? "series",
              },
            ])
          }
        >
          {t("editor.analyticsQueryTagAdd")}
        </button>
      </div>
      <AdvancedJsonField
        label={t("editor.analyticsQueryTagsJson")}
        placeholder={t("editor.analyticsQueryTagsJsonHint")}
        value={widget.analyticsQueryTagsJson ?? ""}
        onChange={(value) => update({ analyticsQueryTagsJson: value || undefined } as Partial<DashboardWidget>)}
        rows={6}
      />
    </div>
  );
}
