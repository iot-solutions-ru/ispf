import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { Alert, Button, Input, Select } from "antd";
import type { TextAreaRef } from "antd/es/input/TextArea";
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
  const expressionRef = useRef<TextAreaRef>(null);
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

  useEffect(() => {
    if (!open) {
      return;
    }
    window.requestAnimationFrame(() => expressionRef.current?.focus());
  }, [open, formula]);

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
      wide
      stackLevel={1}
      title={isEdit ? t("formula.editTitle") : t("formula.saveTitle")}
      onClose={onClose}
      className="analytics-formula-editor-modal"
      footer={
        <>
          <Button onClick={onClose}>
            {t("common:action.cancel")}
          </Button>
          <Button
            type="primary"
            loading={pending}
            disabled={!expression.trim()}
            onClick={() => void handleSave()}
          >
            {isEdit ? t("common:action.save") : t("formula.save")}
          </Button>
        </>
      }
    >
      <div className="analytics-formula-save-form form-grid">
        <label className="full">
          <span>{t("formula.displayName")}</span>
          <Input
            type="text"
            value={displayName}
            placeholder={t("formula.displayNamePlaceholder")}
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
          <Input
            type="text"
            className="mono"
            value={id}
            disabled={isEdit}
            placeholder="tank-fill-rate"
            onChange={(event) => setId(event.target.value)}
          />
        </label>

        <label>
          <span>{t("formula.kind")}</span>
          <Select
            value={kind}
            onChange={(value) => setKind(value as "historian" | "reactive")}
            options={[
              { value: "historian", label: t("catalog.kind.historian") },
              { value: "reactive", label: t("catalog.kind.reactive") },
            ]}
          />
        </label>

        <div className="full analytics-formula-expression-field">
          <div className="analytics-formula-expression-head">
            <span className="analytics-formula-expression-label">{t("formula.expression")}</span>
            <span className="hint">{t("formula.expressionHint")}</span>
          </div>
          <div className="analytics-formula-expression-shell">
            <Input.TextArea
              ref={expressionRef}
              className="binding-expression-textarea mono analytics-formula-expression-input"
              value={expression}
              rows={6}
              spellCheck={false}
              placeholder={t("formula.expressionPlaceholder")}
              onChange={(event) => setExpression(event.target.value)}
            />
          </div>
          {parameters.length > 0 ? (
            <div className="analytics-formula-param-row" aria-label={t("formula.parametersLabel")}>
              <span className="hint analytics-formula-param-label">{t("formula.parametersLabel")}</span>
              <div className="analytics-formula-param-chips">
                {parameters.map((param) => (
                  <code key={param.name} className="analytics-formula-param-chip">
                    {`{{${param.name}}}`}
                  </code>
                ))}
              </div>
            </div>
          ) : (
            <p className="hint analytics-formula-param-empty">{t("formula.parametersEmpty")}</p>
          )}
        </div>

        {saveMessage && <Alert className="full" type="success" message={saveMessage} showIcon />}
        {error && <Alert className="full" type="error" message={(error as Error).message} showIcon />}
      </div>
    </Modal>
  );
}
