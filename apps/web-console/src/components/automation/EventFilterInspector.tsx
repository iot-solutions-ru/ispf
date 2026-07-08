import { useMutation, useQueryClient } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { updateEventFilter, validateExpression } from "../../api";
import type { EventFilterPayload } from "../../types/automation";
import { variableBoolean, variableNumber, variableString } from "../../utils/variableFieldValue";
import { inspectorQueryLoading, useInspectorVariables } from "../../hooks/useInspectorQueries";
import ObjectFederationBindSection from "../ObjectFederationBindSection";

interface EventFilterInspectorProps {
  path: string;
  canManage?: boolean;
}

export default function EventFilterInspector({ path, canManage = false }: EventFilterInspectorProps) {
  const { t } = useTranslation(["automation", "common"]);
  const queryClient = useQueryClient();
  const variablesQuery = useInspectorVariables(path);

  const variables = variablesQuery.data ?? [];
  const form = {
    filterId: variableString(variables, "filterId"),
    eventNamePattern: variableString(variables, "eventNamePattern") || "*",
    sourceObjectPathPattern: variableString(variables, "sourceObjectPathPattern") || "root.platform.**",
    minSeverity: variableNumber(variables, "minSeverity", 0),
    maxSeverity: variableNumber(variables, "maxSeverity", 100),
    timeWindowMs: variableNumber(variables, "timeWindowMs", 0),
    filterExpression: variableString(variables, "filterExpression"),
    enabled: variableBoolean(variables, "enabled", true),
  };

  const saveMutation = useMutation({
    mutationFn: (payload: Partial<EventFilterPayload>) => updateEventFilter(path, payload),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["variables", path] });
      queryClient.invalidateQueries({ queryKey: ["objects"] });
      queryClient.invalidateQueries({ queryKey: ["event-filters"] });
    },
  });

  const validateMutation = useMutation({
    mutationFn: (expression: string) => validateExpression(expression),
  });

  if (inspectorQueryLoading(variablesQuery)) {
    return <p className="hint">{t("automation:eventFilter.loading")}</p>;
  }

  return (
    <section className="automation-inspector">
      <header className="automation-panel-head">
        <div>
          <h3>{t("automation:eventFilter.title")}</h3>
          <p className="hint">{t("automation:eventFilter.subtitle")}</p>
          <p className="hint">
            <code>{path}</code>
          </p>
        </div>
      </header>
      <form
        key={path}
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault();
          if (!canManage) {
            return;
          }
          const data = new FormData(e.currentTarget);
          saveMutation.mutate({
            filterId: String(data.get("filterId") ?? ""),
            eventNamePattern: String(data.get("eventNamePattern") ?? "*"),
            sourceObjectPathPattern: String(data.get("sourceObjectPathPattern") ?? "root.platform.**"),
            minSeverity: Number(data.get("minSeverity") ?? 0),
            maxSeverity: Number(data.get("maxSeverity") ?? 100),
            timeWindowMs: Number(data.get("timeWindowMs") ?? 0),
            filterExpression: String(data.get("filterExpression") ?? ""),
            enabled: data.get("enabled") === "on",
          });
        }}
      >
        <label>
          {t("automation:eventFilter.filterId")}
          <input name="filterId" defaultValue={form.filterId} required readOnly={!canManage} />
        </label>
        <label>
          {t("automation:eventFilter.eventNamePattern")}
          <input name="eventNamePattern" defaultValue={form.eventNamePattern} required readOnly={!canManage} />
        </label>
        <label className="full">
          {t("automation:eventFilter.sourceObjectPathPattern")}
          <input
            name="sourceObjectPathPattern"
            defaultValue={form.sourceObjectPathPattern}
            required
            readOnly={!canManage}
          />
        </label>
        <label>
          {t("automation:eventFilter.minSeverity")}
          <input
            name="minSeverity"
            type="number"
            min={0}
            max={100}
            step={1}
            defaultValue={form.minSeverity}
            readOnly={!canManage}
          />
        </label>
        <label>
          {t("automation:eventFilter.maxSeverity")}
          <input
            name="maxSeverity"
            type="number"
            min={0}
            max={100}
            step={1}
            defaultValue={form.maxSeverity}
            readOnly={!canManage}
          />
        </label>
        <label>
          {t("automation:eventFilter.timeWindowMs")}
          <input
            name="timeWindowMs"
            type="number"
            min={0}
            step={1000}
            defaultValue={form.timeWindowMs}
            readOnly={!canManage}
          />
        </label>
        <label className="full">
          {t("automation:eventFilter.filterExpression")}
          <textarea
            name="filterExpression"
            defaultValue={form.filterExpression}
            rows={3}
            readOnly={!canManage}
            placeholder={t("automation:eventFilter.filterExpressionPlaceholder")}
          />
        </label>
        <label className="checkbox">
          <input type="checkbox" name="enabled" defaultChecked={form.enabled} disabled={!canManage} />
          {t("automation:eventFilter.enabled")}
        </label>
        {canManage && (
          <div className="form-actions full">
            <button
              type="button"
              className="btn"
              onClick={() => {
                const expression = (
                  document.querySelector(
                    'textarea[name="filterExpression"]'
                  ) as HTMLTextAreaElement | null
                )?.value;
                if (expression) {
                  validateMutation.mutate(expression);
                }
              }}
            >
              {t("automation:eventFilter.validateCel")}
            </button>
            <button type="submit" className="btn primary" disabled={saveMutation.isPending}>
              {t("common:action.save")}
            </button>
          </div>
        )}
        {validateMutation.data && (
          <p className={`hint full ${validateMutation.data.valid ? "" : "error"}`}>
            {validateMutation.data.valid ? t("automation:eventFilter.celOk") : validateMutation.data.error}
          </p>
        )}
        {saveMutation.error && <p className="hint error full">{String(saveMutation.error)}</p>}
      </form>
      <ObjectFederationBindSection path={path} canManage={canManage} />
    </section>
  );
}
