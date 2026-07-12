import { useState } from "react";
import { useTranslation } from "react-i18next";
import type { AnalyticsFormulaDto } from "../../api/analyticsFormulas";
import {
  useAnalyticsFormulas,
  useDeleteAnalyticsFormula,
} from "../../hooks/useAnalyticsFormulas";
import SaveAnalyticsFormulaModal from "../analytics/SaveAnalyticsFormulaModal";

export default function AnalyticsFormulasPanel() {
  const { t } = useTranslation(["system", "common", "inspector"]);
  const formulasQuery = useAnalyticsFormulas();
  const deleteMutation = useDeleteAnalyticsFormula();
  const [editorOpen, setEditorOpen] = useState(false);
  const [editing, setEditing] = useState<AnalyticsFormulaDto | null>(null);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

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
        <button type="button" className="btn primary" onClick={openCreate}>
          {t("formulas.create")}
        </button>
      </header>

      {statusMessage && <p className="hint success">{statusMessage}</p>}
      {formulasQuery.isLoading && <p className="op-muted">{t("formulas.loading")}</p>}
      {formulasQuery.error && (
        <div className="op-alert op-alert-error">{String(formulasQuery.error)}</div>
      )}

      {formulasQuery.data && (
        <div className="panel-card">
          <table className="data-table compact">
            <thead>
              <tr>
                <th>{t("formulas.column.id")}</th>
                <th>{t("formulas.column.name")}</th>
                <th>{t("formulas.column.kind")}</th>
                <th>{t("formulas.column.version")}</th>
                <th>{t("formulas.column.expression")}</th>
                <th />
              </tr>
            </thead>
            <tbody>
              {formulasQuery.data.length === 0 && (
                <tr>
                  <td colSpan={6} className="op-muted">{t("formulas.empty")}</td>
                </tr>
              )}
              {formulasQuery.data.map((formula) => (
                <tr key={formula.id}>
                  <td><code>{formula.id}</code></td>
                  <td>{formula.displayName}</td>
                  <td>{formula.kind}</td>
                  <td>{formula.version}</td>
                  <td><code className="analytics-formula-expression-cell">{formula.expression}</code></td>
                  <td className="table-actions">
                    <button type="button" className="btn small" onClick={() => openEdit(formula)}>
                      {t("common:action.edit")}
                    </button>
                    <button
                      type="button"
                      className="btn small danger"
                      disabled={deleteMutation.isPending}
                      onClick={() => void handleDelete(formula)}
                    >
                      {t("common:action.delete")}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
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
