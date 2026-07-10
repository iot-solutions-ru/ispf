import { useState } from "react";
import { useTranslation } from "react-i18next";
import BindingExpressionEditorModal from "./BindingExpressionEditorModal";
import {
  PLATFORM_BINDING_ENTRIES,
  type BindingBuilderContext,
  type PlatformBindingEntry,
} from "../utils/platformBindings";
import type { BindingFormulaLink } from "../types";
import type { BindingExpressionValidator } from "../utils/bindingExpressionValidation";

interface BindingExpressionFieldProps extends BindingBuilderContext {
  id?: string;
  value: string;
  onChange: (value: string, formulaLink?: BindingFormulaLink | null) => void;
  placeholder?: string;
  disabled?: boolean;
  entries?: PlatformBindingEntry[];
  analyticsCatalogKind?: "historian" | "reactive";
  formulaLink?: BindingFormulaLink | null;
  editorTitle?: string;
  onValidate?: BindingExpressionValidator;
}

export default function BindingExpressionField({
  id,
  value,
  onChange,
  placeholder,
  disabled = false,
  objectPath,
  variableNames = [],
  functionNames = [],
  entries = PLATFORM_BINDING_ENTRIES,
  analyticsCatalogKind,
  formulaLink = null,
  editorTitle,
  onValidate,
}: BindingExpressionFieldProps) {
  const { t } = useTranslation("inspector");
  const [editorOpen, setEditorOpen] = useState(false);
  const preview = value.trim() || placeholder || t("bindingExpression.placeholder");
  const isEmpty = !value.trim();

  return (
    <div className="binding-expression-field">
      <div className="binding-expression-trigger">
        <button
          type="button"
          id={id}
          className={`binding-expression-trigger-btn mono${isEmpty ? " is-placeholder" : ""}`}
          disabled={disabled}
          title={value || undefined}
          onClick={() => setEditorOpen(true)}
        >
          {preview}
        </button>
        <button
          type="button"
          className="btn small"
          disabled={disabled}
          onClick={() => setEditorOpen(true)}
        >
          {t("bindingExpression.openEditor")}
        </button>
      </div>

      <BindingExpressionEditorModal
        open={editorOpen}
        title={editorTitle ?? t("bindingExpression.editorTitle")}
        value={value}
        placeholder={placeholder}
        disabled={disabled}
        objectPath={objectPath}
        variableNames={variableNames}
        functionNames={functionNames}
        entries={entries}
        analyticsCatalogKind={analyticsCatalogKind}
        formulaLink={formulaLink}
        onValidate={onValidate}
        onClose={() => setEditorOpen(false)}
        onApply={onChange}
      />
    </div>
  );
}
