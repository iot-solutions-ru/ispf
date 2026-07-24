import { Alert, Button, Modal, Space, Table } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { deleteBindingRule, fetchBindingRules, saveBindingRules } from "../../api";
import type { BindingRule, BindingRuleKind, BindingTargetKind, VariableDto } from "../../types";
import BindingActivatorsEditor, { activatorsSummary } from "./BindingActivatorsEditor";
import BindingExpressionField from "./BindingExpressionField";
import BindingTargetRefEditor from "./BindingTargetRefEditor";
import { isTechnicalIdentifier } from "../../utils/ui/technicalIdentifier";
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
import { validateBindingRuleExpression } from "../../utils/binding/bindingExpressionValidation";
import { encodeHistorianTagPath } from "../../utils/analytics/analyticsPath";
import { useAnalyticsCatalog } from "../../hooks/useAnalyticsCatalog";
import { usePublishAdminFocus } from "../../hooks/usePublishAdminFocus";
import type { AdminClientFocus } from "../../context/AdminFocusContext";

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

const TARGET_KINDS: BindingTargetKind[] = ["variable", "action", "context", "event"];

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
          : kind === "action"
            ? { kind }
            : { kind, variableName: "", field: "value" as const });
    setEditing({ ...editing, target: nextTarget });
  };

  const historianCatalog = useAnalyticsCatalog("historian");
  const historianEntries = historianCatalog.entries;
  const reactiveCatalog = useAnalyticsCatalog("reactive");
  const reactiveEntries = reactiveCatalog.entries;

  const rulesList = rulesQuery.data;

  const computationsFocus = useMemo((): AdminClientFocus => {
    const rules = rulesList ?? [];
    return {
      surface: "binding",
      objectPath: path,
      priority: 45,
      detail: {
        inspectorTab: "computations",
        ruleCount: rules.length,
        rules: rules.slice(0, 25).map((rule) => ({
          id: rule.id,
          kind: ruleKind(rule),
          target: targetSummary(rule.target),
          expression:
            typeof rule.expression === "string" && rule.expression.length > 160
              ? `${rule.expression.slice(0, 160)}…`
              : rule.expression,
          enabled: rule.enabled !== false,
        })),
      },
    };
  }, [path, rulesList]);
  usePublishAdminFocus(`binding-rules:${path}`, computationsFocus, !editing);

  const ruleEditFocus = useMemo((): AdminClientFocus | null => {
    if (!editing) {
      return null;
    }
    return {
      surface: "binding-rule",
      objectPath: path,
      priority: 70,
      detail: {
        ruleId: editing.id,
        kind: ruleKind(editing),
        target: targetSummary(editing.target),
        targetRaw: editing.target,
        expression: editing.expression,
        condition: editing.condition,
        enabled: editing.enabled !== false,
        activators: editing.activators,
        formulaRef: editing.formulaRef ?? null,
      },
    };
  }, [editing, path]);
  usePublishAdminFocus(`binding-rule-edit:${path}`, ruleEditFocus, Boolean(editing));

  if (rulesQuery.isLoading) {
    return <p>{t("inspector:bindings.loading")}</p>;
  }

  const rules = rulesList ?? [];

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

  const columns: ColumnsType<BindingRule> = [
    {
      title: t("common:table.id"),
      dataIndex: "id",
      render: (value: string) => <code>{value}</code>,
    },
    {
      title: t("inspector:bindings.column.kind"),
      key: "kind",
      render: (_, rule) => (
        <span className={`inline-badge binding-rule-kind-${ruleKind(rule)}`}>
          {t(`inspector:bindings.ruleKind.${ruleKind(rule)}`)}
        </span>
      ),
    },
    {
      title: t("inspector:bindings.column.target"),
      key: "target",
      render: (_, rule) => <code>{targetSummary(rule.target)}</code>,
    },
    {
      title: t("inspector:bindings.column.expression"),
      key: "expression",
      className: "mono small",
      render: (_, rule) => (
        <span title={rule.expression}>
          {rule.formulaRef ? (
            <span className="inline-badge">{t("inspector:formula.linkedShort", { id: rule.formulaRef })}</span>
          ) : null}
          {rule.expression || "—"}
        </span>
      ),
    },
    {
      title: t("inspector:bindings.column.activators"),
      key: "activators",
      className: "small",
      render: (_, rule) => activatorsSummary(rule),
    },
    {
      title: t("common:table.enabled"),
      dataIndex: "enabled",
      render: (enabled: boolean | undefined) =>
        enabled !== false ? t("common:action.yes") : t("common:action.no"),
    },
    ...(canManage || onInspectHistorian
      ? [
          {
            title: t("common:table.actions"),
            key: "actions",
            render: (_: unknown, rule: BindingRule) => (
              <Space className="binding-rules-actions" size="small" wrap>
                {ruleKind(rule) === "historian" && onInspectHistorian && (
                  <Button
                    size="small"
                    onClick={() => onInspectHistorian(encodeHistorianTagPath(path, rule.id))}
                  >
                    {t("inspector:computations.inspect")}
                  </Button>
                )}
                {canManage && (
                  <>
                    <Button
                      size="small"
                      onClick={() => {
                        targetDrafts.current = {};
                        setEditing(rule);
                      }}
                    >
                      {t("inspector:bindings.edit")}
                    </Button>
                    <Button
                      size="small"
                      danger
                      loading={deleteMutation.isPending}
                      onClick={() => deleteMutation.mutate(rule.id)}
                    >
                      {t("common:action.delete")}
                    </Button>
                  </>
                )}
              </Space>
            ),
          } satisfies ColumnsType<BindingRule>[number],
        ]
      : []),
  ];

  return (
    <PanelTag className={panelClass}>
      {canManage && (
        <Space className="panel-toolbar" wrap>
          <Button type="primary" size="small" onClick={startNewRule}>
            {t("inspector:bindings.addRule")}
          </Button>
          {ruleTemplates.map((template) => (
            <Button key={template.id} size="small" onClick={() => applyTemplate(template)}>
              {template.label}
            </Button>
          ))}
        </Space>
      )}

      {rules.length === 0 && <p className="hint">{t("inspector:bindings.empty")}</p>}

      {rules.length > 0 && (
        <Table<BindingRule>
          size="small"
          rowKey="id"
          pagination={false}
          dataSource={rules}
          columns={columns}
        />
      )}

      <Modal
        open={Boolean(editing)}
        title={
          editing?.id
            ? t("inspector:bindings.ruleTitle", { id: editing.id })
            : t("inspector:bindings.newRule")
        }
        onCancel={() => setEditing(null)}
        width={720}
        destroyOnHidden
        footer={
          <Space>
            <Button onClick={() => setEditing(null)}>{t("common:action.cancel")}</Button>
            <Button
              type="primary"
              disabled={!editing || !isBindingRuleSaveable(editing)}
              loading={saveMutation.isPending}
              onClick={() => {
                if (!editing) return;
                saveMutation.mutate(prepareBindingRuleForSave(editing));
              }}
            >
              {t("common:action.save")}
            </Button>
          </Space>
        }
      >
        {editing && (
          <>
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
              {editingRuleKind === "reactive" && (
                <label className="full">
                  {t("inspector:bindings.targetKind")}
                  <select
                    value={editingTargetKind}
                    onChange={(e) => setTargetKind(e.target.value as BindingTargetKind)}
                  >
                    {TARGET_KINDS.filter((kind) => dashboardMode || kind !== "context").map((kind) => (
                      <option key={kind} value={kind}>
                        {t(`inspector:bindings.targetKind.${kind}`)}
                      </option>
                    ))}
                  </select>
                </label>
              )}
              {editingTargetKind === "variable" && (
                <BindingTargetRefEditor
                  ruleObjectPath={path}
                  kind="variable"
                  target={editing.target}
                  localVariableNames={variableNames}
                  onChange={(target) => setEditing({ ...editing, target })}
                />
              )}
              {editingTargetKind === "action" && (
                <p className="hint full">{t("inspector:bindings.targetActionHint")}</p>
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
                <BindingTargetRefEditor
                  ruleObjectPath={path}
                  kind="event"
                  target={editing.target}
                  localEventNames={eventNames}
                  onChange={(target) => setEditing({ ...editing, target })}
                />
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
                  focusContext={{
                    ruleId: editing.id,
                    ruleKind: editingRuleKind,
                    target: targetSummary(editing.target),
                  }}
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
                  focusContext={{
                    ruleId: editing.id,
                    ruleKind: editingRuleKind,
                    target: targetSummary(editing.target),
                  }}
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
              <Alert
                type="error"
                showIcon
                style={{ marginTop: 12 }}
                message={(saveMutation.error as Error).message}
              />
            )}
          </>
        )}
      </Modal>
    </PanelTag>
  );
}
