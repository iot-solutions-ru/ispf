import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { fetchVariables, setVariable } from "../../../api";
import type { VariableEditorWidget } from "../../../types/dashboard";
import { readFieldValue } from "../../../types/dashboard";
import { useWidgetObjectPath } from "../../../hooks/useWidgetObjectPath";
import { ensureRecord, setFieldValue } from "../../../utils/ui/record";
import DashWidgetShell from "../DashWidgetShell";
import { useWidgetStyles } from "../widgetStyles";

interface VariableEditorWidgetViewProps {
  widget: VariableEditorWidget;
  refreshIntervalMs: number;
  editable?: boolean;
}

export default function VariableEditorWidgetView({
  widget,
  refreshIntervalMs,
  editable,
}: VariableEditorWidgetViewProps) {
  const { t } = useTranslation(["widgets", "common"]);
  const styles = useWidgetStyles(widget.stylesJson);
  const objectPath = useWidgetObjectPath(widget.objectPath, widget.selectionKey);
  const queryClient = useQueryClient();
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const filterNames = useMemo(() => {
    if (!widget.variablesJson?.trim()) {
      return null;
    }
    try {
      const parsed = JSON.parse(widget.variablesJson) as string[];
      return Array.isArray(parsed) ? parsed : null;
    } catch {
      return null;
    }
  }, [widget.variablesJson]);

  const variablesQuery = useQuery({
    queryKey: ["variables", objectPath],
    queryFn: () => fetchVariables(objectPath),
    enabled: Boolean(objectPath),
    refetchInterval: refreshIntervalMs,
  });

  const variables = useMemo(() => {
    const all = variablesQuery.data ?? [];
    if (!filterNames || filterNames.length === 0) {
      return all;
    }
    const allowed = new Set(filterNames);
    return all.filter((item) => allowed.has(item.name));
  }, [filterNames, variablesQuery.data]);

  const rows = useMemo(() => {
    const result: Array<{
      key: string;
      variableName: string;
      field: string;
      label: string;
      writable: boolean;
      current: string;
    }> = [];
    for (const variable of variables) {
      const fields = variable.value?.schema.fields ?? [];
      const fieldList =
        fields.length > 0
          ? fields
          : [{ name: widget.valueField ?? "value" }];
      for (const fieldDef of fieldList) {
        const field = fieldDef.name;
        const currentValue = readFieldValue(variable.value?.rows[0], field);
        result.push({
          key: `${variable.name}:${field}`,
          variableName: variable.name,
          field,
          label: fields.length > 1 ? `${variable.name}.${field}` : variable.name,
          writable: Boolean(variable.writable),
          current: currentValue != null ? String(currentValue) : "",
        });
      }
    }
    return result;
  }, [variables, widget.valueField]);

  const mutation = useMutation({
    mutationFn: async ({
      name,
      field,
      raw,
    }: {
      name: string;
      field: string;
      raw: string;
    }) => {
      const variable = variables.find((item) => item.name === name);
      if (!variable?.writable) {
        throw new Error(t("error.readOnlyVariable", { name }));
      }
      const record = setFieldValue(ensureRecord(variable), field, raw);
      return setVariable(objectPath, name, record);
    },
    onSuccess: () => {
      setMessage(t("view.saved"));
      setError(null);
      queryClient.invalidateQueries({ queryKey: ["variables", objectPath] });
    },
    onError: (err: Error) => {
      setError(err.message);
      setMessage(null);
    },
  });

  return (
    <DashWidgetShell
      title={widget.title}
      stylesJson={widget.stylesJson}
      className="dash-widget dash-widget-variable-editor"
      editable={editable}
    >
      {!objectPath ? (
        <p className="hint">{t("view.specifyObjectGeneric")}</p>
      ) : variablesQuery.isLoading ? (
        <p className="hint">{t("common:action.loading")}</p>
      ) : rows.length === 0 ? (
        <p className="hint">{t("view.noVariables")}</p>
      ) : (
        <div className="dash-variable-editor-list" style={styles.body}>
          <div className="dash-variable-editor-head" aria-hidden>
            <span>{t("view.variableName", { defaultValue: "Параметр" })}</span>
            <span>{t("view.value", { defaultValue: "Значение" })}</span>
            <span />
          </div>
          {rows.map((row) => {
            const draft = drafts[row.key] ?? row.current;
            return (
              <div key={row.key} className="dash-variable-editor-row">
                <span className="dash-variable-editor-name" title={row.label}>
                  {row.label}
                </span>
                <input
                  value={draft}
                  disabled={editable || !row.writable || mutation.isPending}
                  onChange={(event) =>
                    setDrafts((prev) => ({ ...prev, [row.key]: event.target.value }))
                  }
                />
                <button
                  type="button"
                  className="btn small"
                  disabled={
                    editable ||
                    !row.writable ||
                    mutation.isPending ||
                    draft === row.current
                  }
                  onClick={() =>
                    mutation.mutate({
                      name: row.variableName,
                      field: row.field,
                      raw: draft,
                    })
                  }
                >
                  {t("common:action.save")}
                </button>
              </div>
            );
          })}
        </div>
      )}
      {message && <p className="function-widget-msg ok">{message}</p>}
      {error && <p className="function-widget-msg error">{error}</p>}
    </DashWidgetShell>
  );
}
