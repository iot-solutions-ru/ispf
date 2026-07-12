import { useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { deleteBindingRule, fetchBindingRules, saveBindingRules } from "../api";
import type { BindingRule, BindingRuleKind, BindingTargetKind, VariableDto } from "../types";
import BindingActivatorsEditor, { activatorsSummary } from "./BindingActivatorsEditor";
import BindingExpressionField from "./BindingExpressionField";
import { isTechnicalIdentifier } from "../utils/technicalIdentifier";
import {
  emptyBindingRule,
  emptyDashboardContextRule,
  isBindingRuleSaveable,
  isExistingBindingRule,
  mergeBindingRules,
  prepareBindingRuleForSave,
  ruleKind,
  targetKind,
  targetSummary,
} from "./bindingRulesUtils";
import { validateBindingRuleExpression } from "../utils/bindingExpressionValidation";
import { useAnalyticsCatalog } from "../hooks/useAnalyticsCatalog";

interface RuleTemplate {
  id: string;
  label: string;
  rule: BindingRule;
}

interface BindingRulesPanelProps {
  path: string;
  canManage: boolean;
  eventNames?: string[];
  variableNames?: string[];
  variables?: VariableDto[];
  functionNames?: string[];
  dashboardMode?: boolean;
  ruleTemplates?: RuleTemplate[];
  /** When nested inside ObjectComputationsPanel, skip outer panel wrapper. */
  embedded?: boolean;
  /** Open read-only historian tag inspector (`objectPath/tag/ruleId`). */
  onInspectHistorian?: (tagPath: string) => void;
}

const TARGET_KINDS: BindingTargetKind[] = ["variable", "context", "event"];

export default function BindingRulesPanel({
  path,
  canManage,
  eventNames = [],
  variableNames = [],
  variables,
  functionNames = [],
  dashboardMode = false,
  ruleTemplates = [],
  embedded = false,
  onInspectHistorian,
}: BindingRulesPanelProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<BindingRule | null>(null);
  const targetDrafts = useRef<Partial<Record<BindingTargetKind, BindingRule["target"]>>>({});

  const rulesQuery = useQuery({
    queryKey: ["binding-rules", path],
    queryFn: () => fetchBindingRules(path),
  });

  const saveMutation = useMutation({
    mutationFn: (rule: BindingRule) => {
      const current = rulesQuery.data ?? [];
      return saveBindingRules(path, mergeBindingRules(current, rule));
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["binding-rules", path] });
      queryClient.invalidateQueries({ queryKey: ["variables", path] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-context", path] });
      setEditing(null);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (ruleId: string) => deleteBindingRule(path, ruleId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["binding-rules", path] });
      queryClient.invalidateQueries({ queryKey: ["variables", path] });
      queryClient.invalidateQueries({ queryKey: ["dashboard-context", path] });
    },
  });

  const startNewRule = () => {
    targetDrafts.current = {};
    setEditing(dashboardMode ? emptyDashboardContextRule() : emptyBindingRule());
  };

  const applyTemplate = (template: RuleTemplate) => {
    targetDrafts.current = {};
    const suffix = Date.now().toString(36).slice(-4);
    setEditing({
      ...template.rule,
      id: `${template.rule.id}-${suffix}`,
      name: template.label,
    });
  };

  const setTargetKind = (kind: BindingTargetKind) => {
    if (!editing) return;
    const currentKind = targetKind(editing);
    targetDrafts.current[currentKind] = editing.target;
    const nextTarget =
      targetDrafts.current[kind] ??
      (kind === "context"
        ? { kind, path: "params.mode" as const }
        : kind === "event"
          ? { kind, eventName: "" as const }
          : { kind, variableName: "", field: "value" as const });
    setEditing({ ...editing, target: nextTarget });
  };

  const historianCatalog = useAnalyticsCatalog("historian");
  const historianEntries = historianCatalog.entries;
  const reactiveCatalog = useAnalyticsCatalog("reactive");
  const reactiveEntries = reactiveCatalog.entries;

  if (rulesQuery.isLoading) {
    return <p>{t("inspector:bindings.loading")}</p>;
  }

  const rules = rulesQuery.data ?? [];
  const setRuleKind = (kind: BindingRuleKind) => {
    if (!editing) {
      return;
    }
    if (kind === "historian") {
      setEditing({
        ...editing,
        kind: "historian",
        windowBucket: editing.windowBucket ?? "5m",
        activators: editing.activators.periodicMs
          ? editing.activators
          : { ...editing.activators, periodicMs: 60_000 },
      });
      return;
    }
    setEditing({
      ...editing,
      kind: "reactive",
      windowBucket: null,
      rollupBuckets: null,
    });
  };

  const editingRuleKind = editing ? ruleKind(editing) : "reactive";
  const editingTargetKind = editing ? targetKind(editing) : "variable";

  const panelClass = embedded ? "binding-rules-embedded" : "panel";
  const PanelTag = embedded ? "div" : "section";

  return (
    <PanelTag className={panelClass}>
      {canManage && (
        <div className="panel-toolbar">
          <button type="button" className="btn primary small" onClick={startNewRule}>
            {t("inspector:bindings.addRule")}
          </button>
          {ruleTemplates.map((template) => (
            <button
              key={template.id}
              type="button"
              className="btn small"
              onClick={() => applyTemplate(template)}
            >
              {template.label}
            </button>
          ))}
        </div>
      )}

      {rules.length === 0 && <p className="hint">{t("inspector:bindings.empty")}</p>}

      {rules.length > 0 && (
        <div className="table-scroll">
          <table className="data-table">
            <thead>
              <tr>
                <th>{t("common:table.id")}</th>
                <th>{t("inspector:bindings.column.kind")}</th>
                <th>{t("inspector:bindings.column.target")}</th>
                <th>{t("inspector:bindings.column.expression")}</th>
                <th>{t("inspector:bindings.column.activators")}</th>
                <th>{t("common:table.enabled")}</th>
                {(canManage || onInspectHistorian) && <th aria-label={t("common:table.actions")} />}
              </tr>
            </thead>
            <tbody>
              {rules.map((rule) => (
                <tr key={rule.id}>
                  <td><code>{rule.id}</code></td>
                  <td>
                    <span className={`inline-badge binding-rule-kind-${ruleKind(rule)}`}>
                      {t(`inspector:bindings.ruleKind.${ruleKind(rule)}`)}
                    </span>
                  </td>
                  <td><code>{targetSummary(rule.target)}</code></td>
                  <td className="mono small" title={rule.expression}>
                    {rule.formulaRef ? (
                      <span className="inline-badge">{t("inspector:formula.linkedShort", { id: rule.formulaRef })}</span>
                    ) : null}
                    {rule.expression || "—"}
                  </td>
                  <td className="small">{activatorsSummary(rule)}</td>
                  <td>{rule.enabled ? t("common:action.yes") : t("common:action.no")}</td>
                  {(canManage || onInspectHistorian) && (
                    <td className="binding-rules-actions">
                      {ruleKind(rule) === "historian" && onInspectHistorian && (
                        <button
                          type="button"
                          className="btn small"
                          onClick={() => onInspectHistorian(`${path}#${rule.id}`)}
                        >
                          {t("inspector:computations.inspect")}
                        </button>
                      )}
                      {canManage && (
                        <>
                          <button type="button" className="btn small" onClick={() => { targetDrafts.current = {}; setEditing(rule); }}>{t("inspector:bindings.edit")}</button>
                          <button
                            type="button"
                            className="btn small danger"
                            disabled={deleteMutation.isPending}
                            onClick={() => deleteMutation.mutate(rule.id)}
                          >
                            {t("common:action.delete")}
                          </button>
                        </>
                      )}
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editing && (
        <div className="modal-backdrop" role="presentation">
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <header>
              <h3>{editing.id ? t("inspector:bindings.ruleTitle", { id: editing.id }) : t("inspector:bindings.newRule")}</h3>
              <button type="button" className="icon-btn" onClick={() => setEditing(null)}>✕</button>
            </header>
            <section className="modal-section form-grid">
              <label className="full">
                ID
                <input
                  value={editing.id}
                  disabled={Boolean(isExistingBindingRule(rules, editing.id))}
                  onChange={(e) => setEditing({ ...editing, id: e.target.value })}
                  pattern="[A-Za-z0-9_-]+"
                  required
                  aria-invalid={Boolean(editing.id) && !isTechnicalIdentifier(editing.id, "pathSegment")}
                />
                {editing.id && !isTechnicalIdentifier(editing.id, "pathSegment") && (
                  <span className="hint error">{t("common:error.invalidPathSegment")}</span>
                )}
              </label>
              {!dashboardMode && (
                <label className="full">
                  {t("inspector:bindings.ruleKind")}
                  <select
                    value={editingRuleKind}
                    onChange={(e) => setRuleKind(e.target.value as BindingRuleKind)}
                  >
                    <option value="reactive">{t("inspector:bindings.ruleKind.reactive")}</option>
                    <option value="historian">{t("inspector:bindings.ruleKind.historian")}</option>
                  </select>
                  <span className="hint">{t(`inspector:bindings.ruleKindHint.${editingRuleKind}`)}</span>
                </label>
              )}
              {editingRuleKind === "historian" && (
                <label className="full">
                  {t("inspector:bindings.windowBucket")}
                  <input
                    value={editing.windowBucket ?? "5m"}
                    onChange={(e) => setEditing({ ...editing, windowBucket: e.target.value })}
                    placeholder="5m"
                  />
                  <span className="hint">{t("inspector:bindings.windowBucketHint")}</span>
                </label>
              )}
              {(dashboardMode || editingTargetKind !== "variable") && (
                <label className="full">
                  {t("inspector:bindings.targetKind")}
                  <select
                    value={editingTargetKind}
                    onChange={(e) => setTargetKind(e.target.value as BindingTargetKind)}
                  >
                    {TARGET_KINDS.map((kind) => (
                      <option key={kind} value={kind}>
                        {t(`inspector:bindings.targetKind.${kind}`)}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              {editingTargetKind === "variable" && (
                <label className="full">
                  {t("inspector:bindings.targetVariable")}
                  <input
                    value={editing.target.variableName ?? ""}
                    onChange={(e) =>
                      setEditing({
                        ...editing,
                        target: { ...editing.target, kind: "variable", variableName: e.target.value },
                      })
                    }
                    required
                  />
                </label>
              )}
              {editingTargetKind === "context" && (
                <label className="full">
                  {t("inspector:bindings.targetContextPath")}
                  <input
                    value={editing.target.path ?? ""}
                    onChange={(e) =>
                      setEditing({
                        ...editing,
                        target: { ...editing.target, kind: "context", path: e.target.value },
                      })
                    }
                    placeholder={t("inspector:bindings.targetContextPathPlaceholder")}
                    required
                  />
                  <span className="hint">{t("inspector:bindings.targetContextPathHint")}</span>
                </label>
              )}
              {editingTargetKind === "event" && (
                <label className="full">
                  {t("inspector:bindings.targetEventName")}
                  <input
                    value={editing.target.eventName ?? ""}
                    onChange={(e) =>
                      setEditing({
                        ...editing,
                        target: { ...editing.target, kind: "event", eventName: e.target.value },
                      })
                    }
                    required
                  />
                </label>
              )}
              <label className="full">
                {t("inspector:bindings.column.expression")}
                <BindingExpressionField
                  value={editing.expression}
                  formulaLink={
                    editing.formulaRef
                      ? {
                          formulaRef: editing.formulaRef,
                          formulaParams: editing.formulaParams,
                          formulaScope: editing.formulaScope,
                          formulaAppId: editing.formulaAppId,
                        }
                      : null
                  }
                  onChange={(expression, formulaLink) =>
                    setEditing({
                      ...editing,
                      expression,
                      formulaRef: formulaLink?.formulaRef ?? null,
                      formulaParams: formulaLink?.formulaParams ?? null,
                      formulaScope: formulaLink?.formulaScope ?? null,
                      formulaAppId: formulaLink?.formulaAppId ?? null,
                    })
                  }
                  objectPath={path}
                  variableNames={variableNames}
                  variables={variables}
                  functionNames={functionNames}
                  editorTitle={t("inspector:bindings.column.expression")}
                  entries={
                    editingRuleKind === "historian"
                      ? historianEntries
                      : reactiveEntries
                  }
                  analyticsCatalogKind={editingRuleKind}
                  onValidate={(expression) =>
                    validateBindingRuleExpression(expression, path, editingRuleKind, "expression")
                  }
                />
              </label>
              <label className="full">
                {t("inspector:bindings.condition")}
                <BindingExpressionField
                  value={editing.condition}
                  onChange={(condition) => setEditing({ ...editing, condition })}
                  placeholder={t("inspector:bindings.conditionPlaceholder")}
                  objectPath={path}
                  variableNames={variableNames}
                  variables={variables}
                  functionNames={functionNames}
                  editorTitle={t("inspector:bindings.condition")}
                  analyticsCatalogKind={editingRuleKind}
                  onValidate={(expression) =>
                    validateBindingRuleExpression(expression, path, editingRuleKind, "condition")
                  }
                />
                {dashboardMode && (
                  <span className="hint">{t("inspector:bindings.dashboardConditionHint")}</span>
                )}
              </label>
              <BindingActivatorsEditor
                activators={editing.activators}
                eventNames={eventNames}
                objectPath={path}
                variableNames={variableNames}
                dashboardMode={dashboardMode}
                onChange={(activators) => setEditing({ ...editing, activators })}
              />
              <label className="checkbox-label inline full">
                <input
                  type="checkbox"
                  checked={editing.enabled}
                  onChange={(e) => setEditing({ ...editing, enabled: e.target.checked })}
                />
                {t("common:action.enabled")}
              </label>
            </section>
            {saveMutation.error && (
              <p className="hint error">{(saveMutation.error as Error).message}</p>
            )}
            <footer>
              <button type="button" className="btn" onClick={() => setEditing(null)}>{t("common:action.cancel")}</button>
              <button
                type="button"
                className="btn primary"
                disabled={!isBindingRuleSaveable(editing) || saveMutation.isPending}
                onClick={() => saveMutation.mutate(prepareBindingRuleForSave(editing))}
              >
                {t("common:action.save")}
              </button>
            </footer>
          </div>
        </div>
      )}
    </PanelTag>
  );
}
