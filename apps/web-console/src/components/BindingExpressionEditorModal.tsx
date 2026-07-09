import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { useTranslation } from "react-i18next";
import { useMutation } from "@tanstack/react-query";
import { validateExpression } from "../api";
import Modal from "../ui/Modal";
import PlatformBindingComposer from "./PlatformBindingComposer";
import {
  PLATFORM_BINDING_ENTRIES,
  filterPlatformBindings,
  suggestPlatformBindingPrefix,
  type BindingBuilderContext,
  type PlatformBindingEntry,
} from "../utils/platformBindings";

export interface BindingExpressionEditorModalProps extends BindingBuilderContext {
  open: boolean;
  title: string;
  value: string;
  placeholder?: string;
  disabled?: boolean;
  entries?: PlatformBindingEntry[];
  onValidate?: (expression: string) => Promise<{ valid: boolean; error: string | null }>;
  onClose: () => void;
  onApply: (value: string) => void;
}

function insertAtSelection(
  current: string,
  snippet: string,
  input: HTMLTextAreaElement | null
): string {
  if (!input) {
    return snippet;
  }
  const start = input.selectionStart ?? current.length;
  const end = input.selectionEnd ?? start;
  const next = `${current.slice(0, start)}${snippet}${current.slice(end)}`;
  window.requestAnimationFrame(() => {
    const caret = start + snippet.length;
    input.setSelectionRange(caret, caret);
    input.focus();
  });
  return next;
}

function replaceFunctionPrefix(current: string, snippet: string, entries: PlatformBindingEntry[]): string {
  const trimmed = current.trim();
  const paren = trimmed.indexOf("(");
  const head = paren >= 0 ? trimmed.slice(0, paren) : trimmed;
  if (!head) {
    return snippet;
  }
  const tail = paren >= 0 ? trimmed.slice(paren) : "";
  const match = entries.find((entry) => entry.name.toLowerCase() === head.toLowerCase());
  if (!match || tail.includes(")")) {
    return snippet;
  }
  return snippet;
}

export default function BindingExpressionEditorModal({
  open,
  title,
  value,
  placeholder,
  disabled = false,
  objectPath,
  variableNames = [],
  functionNames = [],
  entries = PLATFORM_BINDING_ENTRIES,
  onValidate,
  onClose,
  onApply,
}: BindingExpressionEditorModalProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const [draft, setDraft] = useState(value);
  const [catalogOpen, setCatalogOpen] = useState(true);
  const [catalogQuery, setCatalogQuery] = useState("");
  const [composingId, setComposingId] = useState<string | null>(null);

  useEffect(() => {
    if (open) {
      setDraft(value);
      setCatalogQuery("");
      setComposingId(null);
      window.requestAnimationFrame(() => textareaRef.current?.focus());
    }
  }, [open, value]);

  const builderContext = useMemo(
    () => ({ objectPath, variableNames, functionNames }),
    [objectPath, variableNames, functionNames]
  );

  const validateMutation = useMutation({
    mutationFn: () => {
      const trimmed = draft.trim();
      if (!trimmed) {
        return Promise.resolve({ valid: false, error: t("bindingExpression.empty") });
      }
      if (onValidate) {
        return onValidate(trimmed);
      }
      return validateExpression(trimmed);
    },
  });

  const catalogEntries = useMemo(
    () => filterPlatformBindings(catalogQuery, entries),
    [catalogQuery, entries]
  );

  const inlineSuggestions = useMemo(
    () => suggestPlatformBindingPrefix(draft, entries),
    [draft, entries]
  );

  const showInlineSuggestions =
    !disabled && draft.trim().length > 0 && inlineSuggestions.length > 0 && inlineSuggestions.length < 18;

  const applySnippet = (snippet: string, replacePrefix = false) => {
    const next = replacePrefix
      ? replaceFunctionPrefix(draft, snippet, entries)
      : insertAtSelection(draft, snippet, textareaRef.current);
    setDraft(next);
    setComposingId(null);
  };

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (disabled || (event.key !== "Tab" && event.key !== "Enter") || event.shiftKey || event.ctrlKey) {
      return;
    }
    const suggestion = inlineSuggestions[0];
    if (!suggestion || inlineSuggestions.length >= 18) {
      return;
    }
    event.preventDefault();
    applySnippet(suggestion.snippet, true);
  };

  const handleApply = () => {
    onApply(draft);
    onClose();
  };

  return (
    <Modal
      open={open}
      title={title}
      onClose={onClose}
      wide
      className="binding-expression-editor-modal"
      footer={
        <>
          <button type="button" className="btn" onClick={onClose}>
            {t("common:action.cancel")}
          </button>
          <button type="button" className="btn primary" disabled={disabled} onClick={handleApply}>
            {t("bindingExpression.apply")}
          </button>
        </>
      }
    >
      <div className="binding-expression-editor-layout">
        <textarea
          ref={textareaRef}
          className="binding-expression-textarea mono"
          value={draft}
          disabled={disabled}
          placeholder={placeholder ?? t("bindingExpression.placeholder")}
          spellCheck={false}
          onChange={(event) => setDraft(event.target.value)}
          onKeyDown={handleKeyDown}
        />

        <div className="binding-expression-editor-toolbar">
          <button
            type="button"
            className="btn small"
            disabled={disabled}
            onClick={() => setCatalogOpen((open) => !open)}
          >
            {catalogOpen ? t("platformBindings.hideCatalog") : t("platformBindings.showCatalog")}
          </button>
          <button
            type="button"
            className="btn small"
            disabled={disabled || !draft.trim() || validateMutation.isPending}
            onClick={() => validateMutation.mutate()}
          >
            {t("bindingExpression.validate")}
          </button>
        </div>

        {variableNames.length > 0 && (
          <div className="binding-expression-variables">
            <span className="hint">{t("platformBindings.variables")}</span>
            {variableNames.map((name) => (
              <button
                key={name}
                type="button"
                className="btn btn-sm binding-expression-suggestion"
                disabled={disabled}
                onClick={() => applySnippet(name)}
              >
                <code>{name}</code>
              </button>
            ))}
          </div>
        )}

        {showInlineSuggestions && (
          <div className="binding-expression-suggestions">
            {inlineSuggestions.slice(0, 8).map((entry) => (
              <button
                key={entry.id}
                type="button"
                className="btn btn-sm binding-expression-suggestion"
                disabled={disabled}
                onClick={() => applySnippet(entry.snippet, true)}
              >
                <code>{entry.name}</code>
              </button>
            ))}
          </div>
        )}

        {catalogOpen && (
          <div className="platform-binding-catalog binding-expression-editor-catalog">
            <div className="platform-binding-catalog-toolbar">
              <input
                type="search"
                value={catalogQuery}
                placeholder={t("platformBindings.search")}
                onChange={(event) => setCatalogQuery(event.target.value)}
              />
              <span className="hint">{t("platformBindings.count", { count: catalogEntries.length })}</span>
            </div>
            <ul className="platform-binding-catalog-list">
              {catalogEntries.map((entry) => (
                <li key={entry.id}>
                  <div className="platform-binding-catalog-item-wrap">
                    <button
                      type="button"
                      className="platform-binding-catalog-item"
                      disabled={disabled}
                      onClick={() => setComposingId((current) => (current === entry.id ? null : entry.id))}
                    >
                      <div className="platform-binding-catalog-head">
                        <code>{entry.name}</code>
                        <span className="platform-binding-catalog-category">
                          {t(`platformBindings.category.${entry.category}`)}
                        </span>
                        {entry.stateful ? (
                          <span className="inline-badge">{t("platformBindings.stateful")}</span>
                        ) : null}
                      </div>
                      <code className="platform-binding-catalog-snippet">{entry.snippet}</code>
                      <p className="hint">{t(`platformBindings.${entry.id}.desc`, { defaultValue: "" })}</p>
                    </button>
                    <div className="platform-binding-catalog-actions">
                      <button
                        type="button"
                        className="btn btn-sm"
                        disabled={disabled}
                        onClick={() => applySnippet(entry.snippet)}
                      >
                        {t("platformBindings.insertTemplate")}
                      </button>
                      <button
                        type="button"
                        className="btn btn-sm"
                        disabled={disabled}
                        onClick={() => setComposingId(entry.id)}
                      >
                        {t("platformBindings.build")}
                      </button>
                    </div>
                  </div>
                  {composingId === entry.id && (
                    <PlatformBindingComposer
                      entry={entry}
                      context={builderContext}
                      disabled={disabled}
                      onInsert={(expression) => applySnippet(expression)}
                      onClose={() => setComposingId(null)}
                    />
                  )}
                </li>
              ))}
            </ul>
          </div>
        )}

        {validateMutation.data?.valid && (
          <span className="hint success">{t("bindingExpression.valid")}</span>
        )}
        {validateMutation.data && !validateMutation.data.valid && (
          <span className="hint error">{validateMutation.data.error ?? t("bindingExpression.error")}</span>
        )}
        {validateMutation.error && (
          <span className="hint error">{(validateMutation.error as Error).message}</span>
        )}
      </div>
    </Modal>
  );
}
