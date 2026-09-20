// Shared building blocks for widget editor fields: context type, layout primitives, path pickers.
import { useQuery } from "@tanstack/react-query";
import type { TFunction } from "i18next";
import { Children, Fragment, isValidElement, type ReactNode } from "react";
import { useTranslation } from "react-i18next";
import type { AnalyticsQueryTagInput } from "../../../api";
import { fetchReport } from "../../../api/reports";
import { parseAnalyticsQueryTags } from "../../../hooks/useAnalyticsMultiSeries";
import type { ChartWidget, DashboardWidget } from "../../../types/dashboard";
import { ObjectPathField } from "../../../ui";
import { AdvancedJsonField } from "../widgetEditorStructured";

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

export function FieldLabel({
  caption,
  children,
  className,
}: {
  caption: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <label className={className}>
      <span className="field-caption">{caption}</span>
      {children}
    </label>
  );
}

export function PathSelect({
  label,
  value,
  objects,
  onChange,
  placeholder,
}: {
  label: string;
  value: string;
  objects: ObjectOption[];
  onChange: (path: string) => void;
  placeholder?: string;
}) {
  const { t } = useTranslation(["widgets", "common"]);
  return (
    <ObjectPathField
      className="path-select-field"
      label={label}
      value={value}
      objects={objects.map(({ path, displayName }) => ({ path, displayName }))}
      onChange={onChange}
      placeholder={placeholder ?? t("editor.placeholder.orEnterPath")}
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
    <>
      <select value={value} onChange={(e) => onChange(e.target.value)}>
        <option value="">—</option>
        {dashboards.map((d) => (
          <option key={d.path} value={d.path}>
            {d.displayName}
          </option>
        ))}
      </select>
      <input
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="root.platform.dashboards.detail"
      />
    </>
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
                placeholder={t("editor.placeholder.orEnterPath")}
                pickerTitle={t("editor.objectPath")}
              />
              <label>
                {t("editor.variableName")}
                <input
                  value={tag.variable}
                  onChange={(e) => updateTag(index, { variable: e.target.value })}
                  placeholder="temperature"
                />
              </label>
              <label>
                {t("editor.analyticsQueryTagLabel")}
                <input
                  value={tag.label ?? ""}
                  onChange={(e) => updateTag(index, { label: e.target.value || undefined })}
                  placeholder={tag.path.split(".").pop() ?? ""}
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
