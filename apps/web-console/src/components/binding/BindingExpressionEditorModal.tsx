import { useEffect, useId, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { useTranslation } from "react-i18next";
import { useMutation } from "@tanstack/react-query";
import { validateExpression } from "../../api";
import Modal from "../../ui/Modal";
import PlatformBindingComposer from "../platform/PlatformBindingComposer";
import {
  PLATFORM_BINDING_ENTRIES,
  filterPlatformBindings,
  suggestPlatformBindingPrefix,
  type BindingBuilderContext,
  type PlatformBindingEntry,
} from "../../utils/platform/platformBindings";
import type { BindingExpressionValidator } from "../../utils/binding/bindingExpressionValidation";
import AnalyticsFormulaBrowser from "../analytics/AnalyticsFormulaBrowser";
import SaveAnalyticsFormulaModal from "../analytics/SaveAnalyticsFormulaModal";
import ExpressionDebuggerSection from "../objectEditor/ExpressionDebuggerSection";
import ObjectQuerySpecEditorModal from "../objectEditor/ObjectQuerySpecEditorModal";
import { PlatformRefPicker } from "../platform/PlatformRefPicker";
import { minifyObjectQuerySpec } from "../../utils/object/objectQuerySpecUtils";
import type { BindingFormulaLink, VariableDto } from "../../types";
import { useAdminFocusOptional } from "../../context/AdminFocusContext";
import { useAdminCopilotChatOptional } from "../../context/AdminCopilotChatContext";
import { usePublishAdminFocus } from "../../hooks/usePublishAdminFocus";

export interface BindingExpressionFocusContext {
  ruleId?: string;
  ruleKind?: string;
  target?: string;
}

export interface BindingExpressionEditorModalProps extends BindingBuilderContext {
  open: boolean;
  title: string;
  value: string;
  placeholder?: string;
  disabled?: boolean;
  entries?: PlatformBindingEntry[];
  analyticsCatalogKind?: "historian" | "reactive" | "all";
  inputFieldNames?: string[];
  variables?: VariableDto[];
  formulaLink?: BindingFormulaLink | null;
  /** Extra parent context (e.g. binding rule being edited). */
  focusContext?: BindingExpressionFocusContext | null;
  onValidate?: BindingExpressionValidator;
  onClose: () => void;
  onApply: (value: string, formulaLink?: BindingFormulaLink | null) => void;
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
  inputFieldNames = [],
  variables,
  entries = PLATFORM_BINDING_ENTRIES,
  analyticsCatalogKind,
  formulaLink = null,
  focusContext = null,
  onValidate,
  onClose,
  onApply,
}: BindingExpressionEditorModalProps) {
  const { t } = useTranslation(["inspector", "common", "ai"]);
  const textareaRef = useRef<HTMLTextAreaElement>(null);
  const [draft, setDraft] = useState(value);
  const [draftFormulaLink, setDraftFormulaLink] = useState<BindingFormulaLink | null>(formulaLink);
  const [catalogOpen, setCatalogOpen] = useState(true);
  const [debuggerOpen, setDebuggerOpen] = useState(false);
  const [catalogQuery, setCatalogQuery] = useState("");
  const [composingId, setComposingId] = useState<string | null>(null);
  const [saveFormulaOpen, setSaveFormulaOpen] = useState(false);
  const [objectQueryOpen, setObjectQueryOpen] = useState(false);
  const adminFocus = useAdminFocusOptional();
  const copilotChat = useAdminCopilotChatOptional();
  // Unique per mounted field — inactive sibling modals must not clear the open editor's layer.
  const focusLayerId = useId();

  useEffect(() => {
    if (open) {
      setDraft(value);
      setDraftFormulaLink(formulaLink);
      setCatalogQuery("");
      setComposingId(null);
      setDebuggerOpen(false);
      setObjectQueryOpen(false);
      window.requestAnimationFrame(() => textareaRef.current?.focus());
    }
  }, [open, value, formulaLink]);

  const expressionFocus = useMemo(
    () => ({
      surface: "expression-editor" as const,
      objectPath: objectPath || undefined,
      priority: 100,
      detail: {
        editorTitle: title,
        expression: draft.length > 800 ? `${draft.slice(0, 800)}…` : draft,
        variableCount: variableNames.length,
        sampleVariables: variableNames.slice(0, 24),
        formulaLink: draftFormulaLink?.formulaRef ?? null,
        debuggerOpen,
        ...(focusContext?.ruleId ? { ruleId: focusContext.ruleId } : {}),
        ...(focusContext?.ruleKind ? { ruleKind: focusContext.ruleKind } : {}),
        ...(focusContext?.target ? { target: focusContext.target } : {}),
      },
    }),
    [
      objectPath,
      draft,
      title,
      variableNames,
      draftFormulaLink?.formulaRef,
      debuggerOpen,
      focusContext?.ruleId,
      focusContext?.ruleKind,
      focusContext?.target,
    ]
  );
  usePublishAdminFocus(`expression-editor:${focusLayerId}`, expressionFocus, open);

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
    !disabled &&
    draft.trim().length > 0 &&
    inlineSuggestions.length > 0 &&
    inlineSuggestions.length <= 17;

  const applySnippet = (snippet: string, nextFormulaLink: BindingFormulaLink | null = null, replacePrefix = false) => {
    const next = replacePrefix
      ? replaceFunctionPrefix(draft, snippet, entries)
      : insertAtSelection(draft, snippet, textareaRef.current);
    setDraft(next);
    setDraftFormulaLink(nextFormulaLink);
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
    applySnippet(suggestion.snippet, null, true);
  };

  const handleApply = () => {
    onApply(draft, draftFormulaLink);
    onClose();
  };

  const insertObjectQueryBinding = (specJson: string, mode: "queryRows" | "queryScalar") => {
    const minified = minifyObjectQuerySpec(specJson);
    const escaped = minified.replace(/\\/g, "\\\\").replace(/'/g, "\\'");
    if (mode === "queryRows") {
      applySnippet(`queryRows('${escaped}')`);
      return;
    }
    applySnippet(`queryScalar('${escaped}', "count")`);
  };

  return (
    <>
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
            {catalogOpen ? t("catalog.hide") : t("catalog.open")}
          </button>
          <button
            type="button"
            className="btn small"
            disabled={disabled || !draft.trim() || validateMutation.isPending}
            onClick={() => validateMutation.mutate()}
          >
            {t("bindingExpression.validate")}
          </button>
          <button
            type="button"
            className={`btn small${debuggerOpen ? " primary" : ""}`}
            disabled={disabled || !objectPath}
            onClick={() => setDebuggerOpen((open) => !open)}
          >
            {debuggerOpen ? t("bindingExpression.debuggerHide") : t("bindingExpression.debuggerShow")}
          </button>
          {adminFocus && (
            <button
              type="button"
              className="btn small"
              disabled={disabled}
              onClick={() => {
                // Fresh Copilot thread so prior "уточните…" turns cannot override live UI focus.
                copilotChat?.startNewChat();
                const expr = draft.trim();
                const rule =
                  typeof focusContext?.ruleId === "string" && focusContext.ruleId.trim()
                    ? focusContext.ruleId.trim()
                    : "";
                const prompt = expr
                  ? rule
                    ? `Объясни текущее CEL-выражение правила «${rule}»:\n\`\`\`\n${expr}\n\`\`\`\nЧто оно вычисляет и как связано с self / target?`
                    : `Объясни текущее CEL-выражение в редакторе:\n\`\`\`\n${expr}\n\`\`\`\nЧто оно вычисляет?`
                  : undefined;
                adminFocus.requestOpenCopilot(prompt);
              }}
            >
              {t("ai:copilot.ask")}
            </button>
          )}
          {analyticsCatalogKind && (
            <button
              type="button"
              className="btn small"
              disabled={disabled || !draft.trim()}
              onClick={() => setSaveFormulaOpen(true)}
            >
              {t("formula.saveAs")}
            </button>
          )}
          <button
            type="button"
            className="btn small"
            disabled={disabled}
            onClick={() => setObjectQueryOpen(true)}
          >
            {t("objectQuery.insertFromBuilder")}
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

        {inputFieldNames.length > 0 && (
          <div className="binding-expression-variables">
            <span className="hint">{t("bindingExpression.inputFields")}</span>
            {inputFieldNames.map((name) => (
              <button
                key={name}
                type="button"
                className="btn btn-sm binding-expression-suggestion"
                disabled={disabled}
                title={t("bindingExpression.inputFieldHint", { name })}
                onClick={() => applySnippet(`context.${name}`)}
              >
                <code>{`context.${name}`}</code>
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
                onClick={() => applySnippet(entry.snippet, null, true)}
              >
                <code>{entry.name}</code>
              </button>
            ))}
          </div>
        )}

        {catalogOpen && analyticsCatalogKind && (
          <div className="platform-binding-catalog binding-expression-editor-catalog">
            <AnalyticsFormulaBrowser
              disabled={disabled}
              defaultKind={analyticsCatalogKind}
              fallbackEntries={entries}
              initialFormulaLink={draftFormulaLink}
              onInsert={(snippet, link) => applySnippet(snippet, link ?? null)}
            />
          </div>
        )}

        {catalogOpen && !analyticsCatalogKind && (
          <div className="platform-binding-catalog binding-expression-editor-catalog">
            <div className="platform-ref-picker-row">
              <PlatformRefPicker
                objectPath={objectPath ?? ""}
                kind="variable"
                variableNames={variableNames}
                functionNames={functionNames}
                onChange={(ref) => applySnippet(`read(${ref})`)}
              />
            </div>
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

        {draftFormulaLink?.formulaRef && (
          <p className="hint">
            {t("formula.linked", { id: draftFormulaLink.formulaRef })}
          </p>
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
    <Modal
      open={debuggerOpen && Boolean(objectPath)}
      title={t("expressionDebugger.title")}
      onClose={() => setDebuggerOpen(false)}
      wide
      stackLevel={1}
      className="expression-debugger-modal"
      footer={
        <button type="button" className="btn" onClick={() => setDebuggerOpen(false)}>
          {t("common:action.close")}
        </button>
      }
    >
      {objectPath ? (
        <ExpressionDebuggerSection
          objectPath={objectPath}
          expression={draft}
          variables={variables}
          variableNames={variableNames}
          disabled={disabled}
          embedded
        />
      ) : null}
    </Modal>
    {analyticsCatalogKind && analyticsCatalogKind !== "all" && (
      <SaveAnalyticsFormulaModal
        open={saveFormulaOpen}
        expression={draft}
        defaultKind={analyticsCatalogKind}
        onClose={() => setSaveFormulaOpen(false)}
      />
    )}
    {analyticsCatalogKind === "all" && (
      <SaveAnalyticsFormulaModal
        open={saveFormulaOpen}
        expression={draft}
        defaultKind="reactive"
        onClose={() => setSaveFormulaOpen(false)}
      />
    )}
    <ObjectQuerySpecEditorModal
      open={objectQueryOpen}
      title={t("objectQuery.insertTitle")}
      value=""
      disabled={disabled}
      objectPath={objectPath}
      variableNames={variableNames}
      onClose={() => setObjectQueryOpen(false)}
      onApply={(specJson) => {
        insertObjectQueryBinding(specJson, "queryRows");
        setObjectQueryOpen(false);
      }}
    />
    </>
  );
}
