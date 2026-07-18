import { useState } from "react";
import { useTranslation } from "react-i18next";
import ObjectQuerySpecEditorModal from "./ObjectQuerySpecEditorModal";
import { prettyObjectQuerySpec } from "../../utils/object/objectQuerySpecUtils";

interface ObjectQuerySpecFieldProps {
  value: string;
  onChange: (value: string) => void;
  disabled?: boolean;
  objectPath?: string;
  variableNames?: string[];
  editorTitle?: string;
}

export default function ObjectQuerySpecField({
  value,
  onChange,
  disabled = false,
  objectPath,
  variableNames = [],
  editorTitle,
}: ObjectQuerySpecFieldProps) {
  const { t } = useTranslation("inspector");
  const [editorOpen, setEditorOpen] = useState(false);
  const preview = value.trim()
    ? prettyObjectQuerySpec(value).split("\n").slice(0, 3).join("\n")
    : t("objectQuery.placeholder");

  return (
    <div className="binding-expression-field object-query-spec-field">
      <div className="binding-expression-trigger">
        <button
          type="button"
          className={`binding-expression-trigger-btn mono${value.trim() ? "" : " is-placeholder"}`}
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
          {t("objectQuery.openEditor")}
        </button>
      </div>

      <ObjectQuerySpecEditorModal
        open={editorOpen}
        title={editorTitle ?? t("objectQuery.editorTitle")}
        value={value}
        disabled={disabled}
        objectPath={objectPath}
        variableNames={variableNames}
        onClose={() => setEditorOpen(false)}
        onApply={onChange}
      />
    </div>
  );
}
