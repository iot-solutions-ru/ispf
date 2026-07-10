import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import Modal from "../../ui/Modal";
import { detectFormulaParameters } from "../../api/analyticsFormulas";
import { useCreateAnalyticsFormula } from "../../hooks/useAnalyticsFormulas";
import type { AnalyticsCatalogParameterDto } from "../../api/analyticsCatalog";

interface SaveAnalyticsFormulaModalProps {
  open: boolean;
  expression: string;
  defaultKind: "historian" | "reactive";
  onClose: () => void;
  onSaved?: () => void;
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
  expression,
  defaultKind,
  onClose,
  onSaved,
}: SaveAnalyticsFormulaModalProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const createMutation = useCreateAnalyticsFormula();
  const [id, setId] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [kind, setKind] = useState<"historian" | "reactive">(defaultKind);

  useEffect(() => {
    if (!open) {
      return;
    }
    setDisplayName("");
    setKind(defaultKind);
    setId("");
  }, [open, defaultKind]);

  const parameters = useMemo<AnalyticsCatalogParameterDto[]>(() => {
    return detectFormulaParameters(expression).map((name: string) => ({
      name,
      type: "string",
      required: true,
      description: "",
      defaultValue: null,
    }));
  }, [expression]);

  const handleSave = async () => {
    const formulaId = id.trim() || slugifyId(displayName);
    if (!formulaId) {
      return;
    }
    await createMutation.mutateAsync({
      id: formulaId,
      displayName: displayName.trim() || formulaId,
      kind,
      expression: expression.trim(),
      parameters,
      version: 1,
      scope: "site",
      appId: null,
    });
    onSaved?.();
    onClose();
  };

  return (
    <Modal
      open={open}
      title={t("formula.saveTitle")}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            {t("common:action.cancel")}
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={!expression.trim() || createMutation.isPending}
            onClick={() => void handleSave()}
          >
            {t("formula.save")}
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
              if (!id.trim()) {
                setId(slugifyId(event.target.value));
              }
            }}
          />
        </label>
        <label>
          <span>{t("formula.id")}</span>
          <input type="text" value={id} onChange={(event) => setId(event.target.value)} />
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
          <textarea className="mono" readOnly value={expression} rows={4} />
        </label>
        {parameters.length > 0 && (
          <p className="hint">
            {t("formula.parametersDetected", { names: parameters.map((param) => param.name).join(", ") })}
          </p>
        )}
        {createMutation.isError && (
          <p className="hint error">{(createMutation.error as Error).message}</p>
        )}
      </div>
    </Modal>
  );
}
