import { useEffect, useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import Modal from "../../ui/Modal";
import type { AnalyticsCatalogEntryDto } from "../../api/analyticsCatalog";
import { useExpandAnalyticsFormula } from "../../hooks/useAnalyticsFormulas";

interface ApplyAnalyticsFormulaModalProps {
  open: boolean;
  entry: AnalyticsCatalogEntryDto | null;
  onClose: () => void;
  onApply: (expression: string) => void;
}

export default function ApplyAnalyticsFormulaModal({
  open,
  entry,
  onClose,
  onApply,
}: ApplyAnalyticsFormulaModalProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const expandMutation = useExpandAnalyticsFormula();
  const [values, setValues] = useState<Record<string, string>>({});

  useEffect(() => {
    if (!open || !entry) {
      return;
    }
    const initial: Record<string, string> = {};
    for (const parameter of entry.parameters) {
      initial[parameter.name] = parameter.defaultValue ?? "";
    }
    setValues(initial);
  }, [open, entry]);

  const missingRequired = useMemo(() => {
    if (!entry) {
      return [] as string[];
    }
    return entry.parameters
      .filter((parameter) => parameter.required && !values[parameter.name]?.trim())
      .map((parameter) => parameter.name);
  }, [entry, values]);

  const handleApply = async () => {
    if (!entry || missingRequired.length > 0) {
      return;
    }
    const scope = entry.pack.startsWith("app:") ? "app" : "site";
    const appId = entry.pack.startsWith("app:") ? entry.pack.slice("app:".length) : undefined;
    const result = await expandMutation.mutateAsync({
      formulaId: entry.id,
      payload: {
        parameters: values,
        scope,
        appId,
      },
    });
    onApply(result.expression);
    onClose();
  };

  if (!entry) {
    return null;
  }

  return (
    <Modal
      open={open}
      title={t("formula.applyTitle", { name: entry.displayName })}
      onClose={onClose}
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            {t("common:action.cancel")}
          </button>
          <button
            type="button"
            className="btn primary"
            disabled={missingRequired.length > 0 || expandMutation.isPending}
            onClick={() => void handleApply()}
          >
            {t("formula.apply")}
          </button>
        </>
      }
    >
      <div className="analytics-formula-apply-form">
        <code className="analytics-formula-apply-template">{entry.syntax}</code>
        {entry.parameters.map((parameter) => (
          <label key={parameter.name}>
            <span>
              {parameter.name}
              {parameter.required ? " *" : ""}
            </span>
            <input
              type="text"
              value={values[parameter.name] ?? ""}
              placeholder={parameter.defaultValue ?? `<${parameter.name}>`}
              onChange={(event) =>
                setValues((current) => ({
                  ...current,
                  [parameter.name]: event.target.value,
                }))
              }
            />
            {parameter.description ? <span className="hint">{parameter.description}</span> : null}
          </label>
        ))}
        {expandMutation.isError && (
          <p className="hint error">{(expandMutation.error as Error).message}</p>
        )}
      </div>
    </Modal>
  );
}
