import { useMemo, useRef, useState, type KeyboardEvent } from "react";
import { useTranslation } from "react-i18next";
import { useMutation } from "@tanstack/react-query";
import { validateExpression } from "../api";
import PlatformBindingComposer from "./PlatformBindingComposer";
import {
  PLATFORM_BINDING_ENTRIES,
  filterPlatformBindings,
  suggestPlatformBindingPrefix,
  type BindingBuilderContext,
} from "../utils/platformBindings";

interface BindingExpressionFieldProps extends BindingBuilderContext {
  id?: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  disabled?: boolean;
}

function insertAtSelection(
  current: string,
  snippet: string,
  input: HTMLInputElement | null
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

function replaceFunctionPrefix(current: string, snippet: string): string {
  const trimmed = current.trim();
  const paren = trimmed.indexOf("(");
  const head = paren >= 0 ? trimmed.slice(0, paren) : trimmed;
  if (!head) {
    return snippet;
  }
  const tail = paren >= 0 ? trimmed.slice(paren) : "";
  const match = PLATFORM_BINDING_ENTRIES.find((entry) => entry.name.toLowerCase() === head.toLowerCase());
  if (!match || tail.includes(")")) {
    return snippet;
  }
  return snippet;
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
}: BindingExpressionFieldProps) {
  const { t } = useTranslation("inspector");
  const inputRef = useRef<HTMLInputElement>(null);
  const [catalogOpen, setCatalogOpen] = useState(false);
  const [catalogQuery, setCatalogQuery] = useState("");
  const [composingId, setComposingId] = useState<string | null>(null);

  const builderContext = useMemo(
    () => ({ objectPath, variableNames, functionNames }),
    [objectPath, variableNames, functionNames]
  );

  const validateMutation = useMutation({
    mutationFn: () => validateExpression(value.trim()),
  });

  const catalogEntries = useMemo(
    () => filterPlatformBindings(catalogQuery),
    [catalogQuery]
  );

  const inlineSuggestions = useMemo(
    () => suggestPlatformBindingPrefix(value),
    [value]
  );

  const showInlineSuggestions =
    !disabled && value.trim().length > 0 && inlineSuggestions.length > 0 && inlineSuggestions.length < 18;

  const applySnippet = (snippet: string, replacePrefix = false) => {
    const next = replacePrefix
      ? replaceFunctionPrefix(value, snippet)
      : insertAtSelection(value, snippet, inputRef.current);
    onChange(next);
    setCatalogOpen(false);
    setCatalogQuery("");
    setComposingId(null);
  };

  const handleInputKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (disabled || (event.key !== "Tab" && event.key !== "Enter")) {
      return;
    }
    const suggestion = inlineSuggestions[0];
    if (!suggestion || inlineSuggestions.length >= 18) {
      return;
    }
    event.preventDefault();
    applySnippet(suggestion.snippet, true);
  };

  return (
    <div className="binding-expression-field">
      <div className="binding-expression-input-row">
        <input
          ref={inputRef}
          id={id}
          type="text"
          className="mono"
          value={value}
          disabled={disabled}
          placeholder={placeholder ?? t("bindingExpression.placeholder")}
          list={id ? `${id}-platform-bindings` : "platform-bindings-list"}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={handleInputKeyDown}
        />
        <datalist id={id ? `${id}-platform-bindings` : "platform-bindings-list"}>
          {PLATFORM_BINDING_ENTRIES.map((entry) => (
            <option key={entry.id} value={`${entry.name}(`}>
              {entry.snippet}
            </option>
          ))}
        </datalist>
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
          disabled={disabled || !value.trim() || validateMutation.isPending}
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
          {inlineSuggestions.slice(0, 6).map((entry) => (
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
        <div className="platform-binding-catalog">
          <div className="platform-binding-catalog-toolbar">
            <input
              type="search"
              value={catalogQuery}
              placeholder={t("platformBindings.search")}
              onChange={(e) => setCatalogQuery(e.target.value)}
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
                    <p className="hint">{t(`platformBindings.${entry.id}.desc`)}</p>
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
  );
}
