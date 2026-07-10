import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import Modal from "../../ui/Modal";
import {
  detectFormulaParameters,
  type AnalyticsFormulaDto,
} from "../../api/analyticsFormulas";
import { useCreateAnalyticsFormula, useUpdateAnalyticsFormula } from "../../hooks/useAnalyticsFormulas";
import type { AnalyticsCatalogParameterDto } from "../../api/analyticsCatalog";

interface SaveAnalyticsFormulaModalProps {
  open: boolean;
  expression: string;
  defaultKind: "historian" | "reactive";
  formula?: AnalyticsFormulaDto | null;
  onClose: () => void;
  onSaved?: (reboundRules?: number) => void;
}

function slugifyId(value: string): string {
  return value
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 64);
}

export default function SaveAnalyticsFormulaModal({
  open,
  expression: expressionProp,
  defaultKind,
  formula = null,
  onClose,
  onSaved,
}: SaveAnalyticsFormulaModalProps) {
  const { t } = useTranslation(["inspector", "common", "system"]);
  const createMutation = useCreateAnalyticsFormula();
  const updateMutation = useUpdateAnalyticsFormula();
  const isEdit = formula != null;
  const [id, setId] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [kind, setKind] = useState<"historian" | "reactive">(defaultKind);
  const [expression, setExpression] = useState(expressionProp);
  const [saveMessage, setSaveMessage] = useState<string | null>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    setSaveMessage(null);
    if (formula) {
      setId(formula.id);
      setDisplayName(formula.displayName);
      setKind(formula.kind === "reactive" ? "reactive" : "historian");
      setExpression(formula.expression);
      return;
    }
    setDisplayName("");
    setKind(defaultKind);
    setId("");
    setExpression(expressionProp);
  }, [open, defaultKind, expressionProp, formula]);

  const parameters = useMemo<AnalyticsCatalogParameterDto[]>(() => {
    return detectFormulaParameters(expression).map((name: string) => ({
      name,
      type: "string",
      required: true,
      description: "",
      defaultValue: null,
    }));
  }, [expression]);

  const pending = createMutation.isPending || updateMutation.isPending;
  const error = createMutation.error ?? updateMutation.error;

  const handleSave = async () => {
    const formulaId = id.trim() || slugifyId(displayName);
    if (!formulaId || !expression.trim()) {
      return;
    }
    const payload: AnalyticsFormulaDto = {
      id: formulaId,
      displayName: displayName.trim() || formulaId,
      kind,
      expression: expression.trim(),
      parameters,
      version: formula?.version ?? 1,
      scope: formula?.scope ?? "site",
      appId: formula?.appId ?? null,
    };
    if (isEdit) {
      const result = await updateMutation.mutateAsync({
        formulaId,
        formula: payload,
        scope: payload.scope,
        appId: payload.appId ?? undefined,
      });
      if (result.reboundRules > 0) {
        setSaveMessage(t("system:formulas.reboundRules", { count: result.reboundRules }));
      }
      onSaved?.(result.reboundRules);
      onClose();
      return;
    }
    await createMutation.mutateAsync(payload);
    onSaved?.();
    onClose();
  };

  return (
    <Modal
      open={open}
      stackLevel={1}
      title={isEdit ? t("formula.editTitle") : t("formula.saveTitle")}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            {t("common:action.cancel")}
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={!expression.trim() || pending}
            onClick={() => void handleSave()}
          >
            {isEdit ? t("common:action.save") : t("formula.save")}
          </button>
        </>
      }
    >
      <div className="analytics-formula-save-form">
        <label>
          <span>{t("formula.displayName")}</span>
          <input
            type="text"
            value={displayName}
            onChange={(event) => {
              setDisplayName(event.target.value);
              if (!isEdit && !id.trim()) {
                setId(slugifyId(event.target.value));
              }
            }}
          />
        </label>
        <label>
          <span>{t("formula.id")}</span>
          <input type="text" value={id} disabled={isEdit} onChange={(event) => setId(event.target.value)} />
        </label>
        <label>
          <span>{t("formula.kind")}</span>
          <select value={kind} onChange={(event) => setKind(event.target.value as "historian" | "reactive")}>
            <option value="historian">{t("catalog.kind.historian")}</option>
            <option value="reactive">{t("catalog.kind.reactive")}</option>
          </select>
        </label>
        <label>
          <span>{t("formula.expression")}</span>
          <textarea
            className="mono"
            readOnly={!isEdit}
            value={expression}
            rows={4}
            onChange={(event) => setExpression(event.target.value)}
          />
        </label>
        {parameters.length > 0 && (
          <p className="hint">
            {t("formula.parametersDetected", { names: parameters.map((param) => param.name).join(", ") })}
          </p>
        )}
        {saveMessage && <p className="hint success">{saveMessage}</p>}
        {error && <p className="hint error">{(error as Error).message}</p>}
      </div>
    </Modal>
  );
}
