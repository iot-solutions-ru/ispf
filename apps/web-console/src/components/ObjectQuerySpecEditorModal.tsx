import { useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import Modal from "../ui/Modal";
import { OBJECT_QUERY_EXAMPLES } from "../utils/objectQueryExamples";
import {
  prettyObjectQuerySpec,
  validateObjectQuerySpec,
  type ObjectQuerySpecValidation,
} from "../utils/objectQuerySpecUtils";

export interface ObjectQuerySpecEditorModalProps {
  open: boolean;
  title: string;
  value: string;
  disabled?: boolean;
  objectPath?: string;
  variableNames?: string[];
  onClose: () => void;
  onApply: (value: string) => void;
}

function validationMessage(
  t: (key: string) => string,
  result: ObjectQuerySpecValidation
): string {
  if (result.valid) {
    return t("objectQuery.valid");
  }
  switch (result.error) {
    case "empty":
      return t("objectQuery.empty");
    case "invalidJson":
      return t("objectQuery.invalidJson");
    case "missingFrom":
      return t("objectQuery.missingFrom");
    case "missingPattern":
      return t("objectQuery.missingPattern");
    default:
      return t("objectQuery.error");
  }
}

export default function ObjectQuerySpecEditorModal({
  open,
  title,
  value,
  disabled = false,
  objectPath,
  variableNames = [],
  onClose,
  onApply,
}: ObjectQuerySpecEditorModalProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const [draft, setDraft] = useState(value);
  const [examplesOpen, setExamplesOpen] = useState(true);
  const [helpOpen, setHelpOpen] = useState(true);
  const [exampleQuery, setExampleQuery] = useState("");
  const [validation, setValidation] = useState<ObjectQuerySpecValidation | null>(null);

  useEffect(() => {
    if (open) {
      setDraft(prettyObjectQuerySpec(value));
      setExampleQuery("");
      setValidation(null);
      window.requestAnimationFrame(() => textareaRef.current?.focus());
    }
  }, [open, value]);

  const filteredExamples = useMemo(() => {
    const q = exampleQuery.trim().toLowerCase();
    if (!q) {
      return OBJECT_QUERY_EXAMPLES;
    }
    return OBJECT_QUERY_EXAMPLES.filter(
      (example) =>
        example.id.toLowerCase().includes(q) ||
        example.tags.some((tag) => tag.toLowerCase().includes(q)) ||
        t(`objectQuery.examples.${example.id}.title`, { defaultValue: example.id })
          .toLowerCase()
          .includes(q)
    );
  }, [exampleQuery, t]);

  const runValidate = () => {
    setValidation(validateObjectQuerySpec(draft));
  };

  const handleFormat = () => {
    setDraft(prettyObjectQuerySpec(draft));
    setValidation(null);
  };

  const handleApply = () => {
    const result = validateObjectQuerySpec(draft);
    setValidation(result);
    if (!result.valid) {
      return;
    }
    onApply(JSON.stringify(JSON.parse(draft)));
    onClose();
  };

  const loadExample = (spec: Record<string, unknown>) => {
    setDraft(JSON.stringify(spec, null, 2));
    setValidation(null);
  };

  const insertPattern = (pattern: string) => {
    try {
      const parsed = JSON.parse(draft) as Record<string, unknown>;
      const from = (parsed.from ?? {}) as Record<string, unknown>;
      from.sourcePathPattern = pattern;
      parsed.from = from;
      setDraft(JSON.stringify(parsed, null, 2));
      setValidation(null);
    } catch {
      setDraft(
        JSON.stringify(
          {
            from: { alias: "row", sourcePathPattern: pattern, objectTypes: ["DEVICE"] },
            fields: [{ name: "path", source: "path", alias: "row" }],
            limit: 1000,
          },
          null,
          2
        )
      );
    }
  };

  return (
    <Modal
      open={open}
      title={title}
      onClose={onClose}
      wide
      className="binding-expression-editor-modal object-query-spec-editor-modal"
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            {t("common:action.cancel")}
          </button>
          <button type="button" className="btn primary" disabled={disabled} onClick={handleApply}>
            {t("objectQuery.apply")}
          </button>
        </>
      }
    >
      <div className="binding-expression-editor-layout object-query-spec-editor-layout">
        <textarea
          ref={textareaRef}
          className="binding-expression-textarea mono"
          value={draft}
          disabled={disabled}
          placeholder={t("objectQuery.placeholder")}
          spellCheck={false}
          onChange={(event) => {
            setDraft(event.target.value);
            setValidation(null);
          }}
        />

        <div className="binding-expression-editor-toolbar">
          <button type="button" className="btn small" disabled={disabled} onClick={handleFormat}>
            {t("objectQuery.format")}
          </button>
          <button type="button" className="btn small" disabled={disabled} onClick={runValidate}>
            {t("objectQuery.validate")}
          </button>
          <button
            type="button"
            className="btn small"
            disabled={disabled}
            onClick={() => setExamplesOpen((open) => !open)}
          >
            {examplesOpen ? t("objectQuery.hideExamples") : t("objectQuery.showExamples")}
          </button>
          <button
            type="button"
            className="btn small"
            disabled={disabled}
            onClick={() => setHelpOpen((open) => !open)}
          >
            {helpOpen ? t("objectQuery.hideHelp") : t("objectQuery.showHelp")}
          </button>
        </div>

        {objectPath && (
          <div className="object-query-pattern-row">
            <span className="hint">{t("objectQuery.patternPickerHint")}</span>
            <div className="object-query-pattern-chips">
              {[
                "root.platform.devices.*",
                "root.platform.devices.demo-sensor-01",
                `${objectPath}.*`,
              ].map((pattern) => (
                <button
                  key={pattern}
                  type="button"
                  className="btn btn-sm binding-expression-suggestion"
                  disabled={disabled}
                  onClick={() => insertPattern(pattern)}
                >
                  <code>{pattern}</code>
                </button>
              ))}
            </div>
          </div>
        )}

        {helpOpen && (
          <section className="object-query-help-panel">
            <h4>{t("objectQuery.helpTitle")}</h4>
            <ul className="object-query-help-list">
              <li>{t("objectQuery.help.from")}</li>
              <li>{t("objectQuery.help.fields")}</li>
              <li>{t("objectQuery.help.joins")}</li>
              <li>{t("objectQuery.help.historian")}</li>
              <li>{t("objectQuery.help.bindings")}</li>
            </ul>
          </section>
        )}

        {examplesOpen && (
          <div className="platform-binding-catalog binding-expression-editor-catalog object-query-examples-catalog">
            <div className="platform-binding-catalog-toolbar">
              <input
                type="search"
                value={exampleQuery}
                placeholder={t("objectQuery.searchExamples")}
                onChange={(event) => setExampleQuery(event.target.value)}
              />
              <span className="hint">{t("objectQuery.exampleCount", { count: filteredExamples.length })}</span>
            </div>
            <ul className="platform-binding-catalog-list">
              {filteredExamples.map((example) => (
                <li key={example.id}>
                  <div className="platform-binding-catalog-item-wrap">
                    <button
                      type="button"
                      className="platform-binding-catalog-item"
                      disabled={disabled}
                      onClick={() => loadExample(example.spec)}
                    >
                      <div className="platform-binding-catalog-head">
                        <strong>{t(`objectQuery.examples.${example.id}.title`, { defaultValue: example.id })}</strong>
                        <span className="platform-binding-catalog-category">
                          {example.tags.join(" · ")}
                        </span>
                      </div>
                      <p className="hint">
                        {t(`objectQuery.examples.${example.id}.desc`, { defaultValue: "" })}
                      </p>
                    </button>
                    <div className="platform-binding-catalog-actions">
                      <button
                        type="button"
                        className="btn btn-sm"
                        disabled={disabled}
                        onClick={() => loadExample(example.spec)}
                      >
                        {t("objectQuery.loadExample")}
                      </button>
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          </div>
        )}

        {validation?.valid && <span className="hint success">{validationMessage(t, validation)}</span>}
        {validation && !validation.valid && (
          <span className="hint error">{validationMessage(t, validation)}</span>
        )}
      </div>
    </Modal>
  );
}
