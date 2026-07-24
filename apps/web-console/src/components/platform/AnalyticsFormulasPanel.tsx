import { useMemo, useState } from "react";
import { Alert, Button, Space, Table } from "antd";
import { useTranslation } from "react-i18next";
import type { AnalyticsFormulaDto } from "../../api/analyticsFormulas";
import {
  useAnalyticsFormulas,
  useDeleteAnalyticsFormula,
} from "../../hooks/useAnalyticsFormulas";
import SaveAnalyticsFormulaModal from "../analytics/SaveAnalyticsFormulaModal";
import { useSystemTabFocus } from "../../hooks/useSystemTabFocus";

export default function AnalyticsFormulasPanel() {
  const { t } = useTranslation(["system", "common", "inspector"]);
  const formulasQuery = useAnalyticsFormulas();
  const deleteMutation = useDeleteAnalyticsFormula();
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<AnalyticsFormulaDto | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  const focusDetail = useMemo(
    () => ({
      formulaCount: formulasQuery.data?.length ?? 0,
      formulas: (formulasQuery.data ?? []).slice(0, 20).map((formula) => ({
        id: formula.id,
        kind: formula.kind,
        displayName: formula.displayName,
        expression:
          typeof formula.expression === "string" && formula.expression.length > 160
            ? `${formula.expression.slice(0, 160)}…`
            : formula.expression,
      })),
      editorOpen,
      editingFormulaId: editing?.id ?? null,
      editingExpression: editing?.expression?.slice(0, 400) ?? null,
      screenHint:
        "Analytics formulas catalog — help author CEL formulas, clone/adapt expressions, suggest kind/scope",
      helpIntents: ["draftFormula", "explainFormula", "adaptExpression"],
    }),
    [formulasQuery.data, editorOpen, editing]
  );
  useSystemTabFocus("system-formulas", "formulas", focusDetail, {
    priority: editorOpen ? 85 : 65,
    screenTitle: editorOpen
      ? `System › Formulas › ${editing?.id ?? "new"}`
      : "System › Analytics formulas",
  });

  const openCreate = () => {
    setEditing(null);
    setEditorOpen(true);
  };

  const openEdit = (formula: AnalyticsFormulaDto) => {
    setEditing(formula);
    setEditorOpen(true);
  };

  const handleDelete = async (formula: AnalyticsFormulaDto) => {
    if (!window.confirm(t("formulas.deleteConfirm", { id: formula.id }))) {
      return;
    }
    await deleteMutation.mutateAsync({
      formulaId: formula.id,
      scope: formula.scope || "site",
      appId: formula.appId ?? undefined,
    });
    setStatusMessage(t("formulas.deleted", { id: formula.id }));
  };

  return (
    <section className="system-panel analytics-formulas-panel">
      <header className="analytics-formulas-panel-head">
        <div>
          <h3>{t("formulas.title")}</h3>
          <p className="op-muted">{t("formulas.subtitle")}</p>
        </div>
        <Button type="primary" onClick={openCreate}>
          {t("formulas.create")}
        </Button>
      </header>

      {statusMessage && <p className="hint success">{statusMessage}</p>}
      {formulasQuery.isLoading && <p className="op-muted">{t("formulas.loading")}</p>}
      {formulasQuery.error && <Alert type="error" showIcon message={String(formulasQuery.error)} />}

      {formulasQuery.data && (
        <div className="panel-card">
          <Table
            size="small"
            rowKey="id"
            dataSource={formulasQuery.data}
            locale={{ emptyText: t("formulas.empty") }}
            columns={[
              {
                title: t("formulas.column.id"),
                dataIndex: "id",
                render: (value: string) => <code>{value}</code>,
              },
              { title: t("formulas.column.name"), dataIndex: "displayName" },
              { title: t("formulas.column.kind"), dataIndex: "kind" },
              { title: t("formulas.column.version"), dataIndex: "version" },
              {
                title: t("formulas.column.expression"),
                dataIndex: "expression",
                render: (value: string) => <code className="analytics-formula-expression-cell">{value}</code>,
              },
              {
                title: "",
                render: (_, formula) => (
                  <Space className="table-actions">
                    <Button size="small" onClick={() => openEdit(formula)}>
                      {t("common:action.edit")}
                    </Button>
                    <Button
                      size="small"
                      danger
                      disabled={deleteMutation.isPending}
                      onClick={() => void handleDelete(formula)}
                    >
                      {t("common:action.delete")}
                    </Button>
                  </Space>
                ),
              },
            ]}
          />
        </div>
      )}

      <SaveAnalyticsFormulaModal
        open={editorOpen}
        expression={editing?.expression ?? "avg({{sourcePath}}/{{sourceVariable}}, 5m)"}
        defaultKind={editing?.kind === "reactive" ? "reactive" : "historian"}
        formula={editing}
        onClose={() => {
          setEditorOpen(false);
          setEditing(null);
        }}
        onSaved={(reboundRules?: number) => {
          if (reboundRules && reboundRules > 0) {
            setStatusMessage(t("formulas.reboundRules", { count: reboundRules }));
          } else {
            setStatusMessage(t("formulas.saved"));
          }
        }}
      />
    </section>
  );
}
