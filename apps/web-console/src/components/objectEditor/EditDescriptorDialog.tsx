import { Suspense, lazy, useEffect, useMemo, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import { useMutation } from "@tanstack/react-query";
import { upsertEvent, upsertFunction } from "../../api";
import type { BindingFormulaLink, DataSchema, EventDescriptor, FunctionDescriptor, VariableDto } from "../../types";
import DataSchemaEditor from "../schema/DataSchemaEditor";
import BindingExpressionField from "../binding/BindingExpressionField";
import { cloneSchema, emptySchema, normalizeFunctionDescriptor } from "../../utils/schema/dataSchema";
import { DEFAULT_JAVA_FUNCTION_TEMPLATE } from "../../utils/functionScript/javaFunctionTemplate";
import { defaultScriptBody } from "../../utils/functionScript/functionScriptSteps";
import { DEFAULT_OBJECT_QUERY_SPEC } from "../../utils/object/objectQueryDefaults";
import { prettyObjectQuerySpec, validateObjectQuerySpec } from "../../utils/object/objectQuerySpecUtils";
import ObjectQuerySpecField from "./ObjectQuerySpecField";
import { isTechnicalIdentifier } from "../../utils/ui/technicalIdentifier";
import FunctionScriptStepsEditor from "../functionScript/FunctionScriptStepsEditor";
import { useFunctionExpressionCatalog } from "../../hooks/useAnalyticsCatalog";
import {
  parseFunctionExpressionBody,
  serializeFunctionExpressionBody,
} from "../../utils/functionScript/functionExpressionBody";
import { validateFunctionExpression } from "../../utils/binding/bindingExpressionValidation";

const JavaFunctionEditor = lazy(() => import("../functionScript/JavaFunctionEditor"));
type DescriptorKind = "function" | "event";

interface EditDescriptorDialogProps {
  objectPath: string;
  kind: DescriptorKind;
  initial?: FunctionDescriptor | EventDescriptor;
  variableNames?: string[];
  functionNames?: string[];
  variables?: VariableDto[];
  onClose: () => void;
  onSaved: () => void;
}

function defaultFunction(name = ""): FunctionDescriptor {
  const base = name || "fn";
  return {
    name: base,
    description: "",
    inputSchema: { ...emptySchema(`${base}Input`), fields: [] },
    outputSchema: {
      name: `${base}Output`,
      fields: [{ name: "result", type: "DOUBLE", description: "Result", nullable: false }],
    },
    sourceType: null,
    sourceBody: null,
    dataSourcePath: null,
    version: null,
  };
}

function defaultEvent(name = ""): EventDescriptor {
  const base = name || "event";
  return {
    name: base,
    description: "",
    payloadSchema: {
      name: `${base}Payload`,
      fields: [{ name: "message", type: "STRING", description: "Message", nullable: true }],
    },
    level: "INFO",
  };
}

export default function EditDescriptorDialog({
  objectPath,
  kind,
  initial,
  variableNames = [],
  functionNames = [],
  variables,
  onClose,
  onSaved,
}: EditDescriptorDialogProps) {
  const { t } = useTranslation(["inspector", "common"]);
  const expressionCatalog = useFunctionExpressionCatalog();
  const isFunction = kind === "function";
  const [name, setName] = useState(initial?.name ?? "");
  const [description, setDescription] = useState(initial?.description ?? "");
  const [level, setLevel] = useState(
    !isFunction && initial ? (initial as EventDescriptor).level : "INFO"
  );
  const [inputSchema, setInputSchema] = useState<DataSchema>(emptySchema("input"));
  const [outputSchema, setOutputSchema] = useState<DataSchema>(emptySchema("output"));
  const [payloadSchema, setPayloadSchema] = useState<DataSchema>(emptySchema("payload"));
  const [sourceType, setSourceType] = useState("");
  const [sourceBody, setSourceBody] = useState("");
  const [dataSourcePath, setDataSourcePath] = useState("");
  const [version, setVersion] = useState("");
  const [expressionText, setExpressionText] = useState("");
  const [expressionFormulaLink, setExpressionFormulaLink] = useState<BindingFormulaLink | null>(null);
  const [showAdvancedJson, setShowAdvancedJson] = useState(false);
  const [schemaJson, setSchemaJson] = useState("{}");
  const [parseError, setParseError] = useState<string | null>(null);
  const nameValid = isTechnicalIdentifier(name, "code");
  const sourceDrafts = useRef<Record<string, string>>({
    handler: "",
    script: "",
    java: "",
    expression: "",
    "object-query": "",
  });
  const initializedRef = useRef(false);
  const inputFieldNames = useMemo(
    () => inputSchema.fields.map((field) => field.name).filter(Boolean),
    [inputSchema.fields]
  );

  useEffect(() => {
    if (initializedRef.current) return;
    initializedRef.current = true;
    setName(initial?.name ?? "");
    setDescription(initial?.description ?? "");
    setLevel(!isFunction && initial ? (initial as EventDescriptor).level : "INFO");
    setShowAdvancedJson(false);
    setParseError(null);
    if (isFunction) {
      const fn = (initial as FunctionDescriptor | undefined) ?? defaultFunction();
      setInputSchema(cloneSchema(fn.inputSchema));
      setOutputSchema(cloneSchema(fn.outputSchema));
      const parsedExpression =
        fn.sourceType === "expression" ? parseFunctionExpressionBody(fn.sourceBody) : null;
      setExpressionText(parsedExpression?.expression ?? "");
      setExpressionFormulaLink(parsedExpression?.formulaLink ?? null);
      setSourceType(fn.sourceType ?? "");
      setSourceBody(fn.sourceBody ?? "");
      setDataSourcePath(fn.dataSourcePath ?? "");
      setVersion(fn.version ?? "");
      sourceDrafts.current = {
        handler: fn.sourceType ? "" : (fn.sourceBody ?? ""),
        script: fn.sourceType === "script" ? (fn.sourceBody ?? "") : "",
        java: fn.sourceType === "java" ? (fn.sourceBody ?? "") : "",
        expression: fn.sourceType === "expression" ? (fn.sourceBody ?? "") : "",
        "object-query": fn.sourceType === "object-query" ? (fn.sourceBody ?? "") : "",
      };
      setSchemaJson(
        JSON.stringify(
          {
            inputSchema: fn.inputSchema,
            outputSchema: fn.outputSchema,
            sourceType: fn.sourceType,
            sourceBody: fn.sourceBody,
            dataSourcePath: fn.dataSourcePath,
            version: fn.version,
          },
          null,
          2
        )
      );
    } else {
      const ev = (initial as EventDescriptor | undefined) ?? defaultEvent();
      setPayloadSchema(cloneSchema(ev.payloadSchema));
      setSchemaJson(JSON.stringify(ev.payloadSchema, null, 2));
    }
  }, [initial, isFunction]);

  function structuredJson(): string {
    if (!isFunction) return JSON.stringify(payloadSchema, null, 2);
    return JSON.stringify({
      inputSchema,
      outputSchema,
      sourceType: sourceType || null,
      sourceBody: sourceBody || null,
      dataSourcePath: dataSourcePath || null,
      version: version || null,
    }, null, 2);
  }

  function applyAdvancedJson(): boolean {
    try {
      if (isFunction) {
        const parsed = JSON.parse(schemaJson) as FunctionDescriptor;
        setInputSchema(cloneSchema(parsed.inputSchema));
        setOutputSchema(cloneSchema(parsed.outputSchema));
        const nextType = parsed.sourceType ?? "";
        const nextBody = parsed.sourceBody ?? "";
        setSourceType(nextType);
        setSourceBody(nextBody);
        sourceDrafts.current[nextType || "handler"] = nextBody;
        setDataSourcePath(parsed.dataSourcePath ?? "");
        setVersion(parsed.version ?? "");
      } else {
        setPayloadSchema(cloneSchema(JSON.parse(schemaJson) as DataSchema));
      }
      setParseError(null);
      return true;
    } catch {
      setParseError(t("descriptor.invalidSchemaJson"));
      return false;
    }
  }

  function setAdvancedMode(next: boolean) {
    if (next) {
      setSchemaJson(structuredJson());
      setParseError(null);
      setShowAdvancedJson(true);
      return;
    }
    if (applyAdvancedJson()) setShowAdvancedJson(false);
  }

  function persistExpressionDraft(expression: string, formulaLink: BindingFormulaLink | null) {
    const serialized = serializeFunctionExpressionBody(expression, formulaLink);
    sourceDrafts.current.expression = serialized;
    setSourceBody(serialized);
  }

  function handleExpressionChange(expression: string, formulaLink?: BindingFormulaLink | null) {
    setExpressionText(expression);
    setExpressionFormulaLink(formulaLink ?? null);
    persistExpressionDraft(expression, formulaLink ?? null);
  }

  function changeSourceType(next: string) {
    if (sourceType === "expression") {
      persistExpressionDraft(expressionText, expressionFormulaLink);
    } else {
      sourceDrafts.current[sourceType || "handler"] = sourceBody;
    }
    const draftKey = next || "handler";
    let nextBody = sourceDrafts.current[draftKey] ?? "";
    if (!nextBody && next === "java") nextBody = DEFAULT_JAVA_FUNCTION_TEMPLATE;
    if (!nextBody && next === "script") nextBody = defaultScriptBody();
    if (!nextBody && next === "object-query") {
      nextBody = prettyObjectQuerySpec(DEFAULT_OBJECT_QUERY_SPEC);
    }
    if (next === "expression") {
      const parsed = parseFunctionExpressionBody(nextBody);
      setExpressionText(parsed.expression);
      setExpressionFormulaLink(parsed.formulaLink);
      nextBody = nextBody || serializeFunctionExpressionBody("", null);
    }
    sourceDrafts.current[draftKey] = nextBody;
    setSourceType(next);
    setSourceBody(nextBody);
  }

  const mutation = useMutation({
    mutationFn: async () => {
      if (isFunction) {
        let fn: FunctionDescriptor;
        if (showAdvancedJson) {
          const parsed = JSON.parse(schemaJson) as FunctionDescriptor;
          fn = normalizeFunctionDescriptor({
            name: name.trim(),
            description: description.trim(),
            inputSchema: parsed.inputSchema,
            outputSchema: parsed.outputSchema,
            sourceType: parsed.sourceType,
            sourceBody: parsed.sourceBody,
            dataSourcePath: parsed.dataSourcePath,
            version: parsed.version,
          });
        } else {
          fn = normalizeFunctionDescriptor({
            name: name.trim(),
            description: description.trim(),
            inputSchema,
            outputSchema,
            sourceType: sourceType || null,
            sourceBody:
              sourceType === "object-query" && sourceBody.trim()
                ? JSON.stringify(JSON.parse(sourceBody))
                : sourceBody || null,
            dataSourcePath: dataSourcePath || null,
            version: version || null,
          });
        }
        return upsertFunction(objectPath, fn);
      }
      const schema = showAdvancedJson
        ? (JSON.parse(schemaJson) as DataSchema)
        : payloadSchema;
      const ev: EventDescriptor = {
        name: name.trim(),
        description: description.trim(),
        payloadSchema: schema,
        level,
      };
      return upsertEvent(objectPath, ev);
    },
    onSuccess: onSaved,
  });

  function handleSave() {
    if (!nameValid) return;
    try {
      if (showAdvancedJson) {
        JSON.parse(schemaJson);
      }
      if (isFunction && !showAdvancedJson) {
        const st = sourceType.trim();
        if ((st === "java" || st === "script" || st === "expression" || st === "object-query") && !sourceBody.trim()) {
          setParseError(t("descriptor.sourceBodyRequired"));
          return;
        }
        if (st === "object-query") {
          const specValidation = validateObjectQuerySpec(sourceBody);
          if (!specValidation.valid) {
            setParseError(t("descriptor.objectQueryInvalid"));
            return;
          }
        }
        if (st === "expression" && !expressionText.trim()) {
          setParseError(t("descriptor.sourceBodyRequired"));
          return;
        }
        if (st === "expression") {
          persistExpressionDraft(expressionText, expressionFormulaLink);
        }
        if (st === "script") JSON.parse(sourceBody);
      }
      setParseError(null);
      mutation.mutate();
    } catch {
      setParseError(t("descriptor.invalidSchemaJson"));
    }
  }

  const title = initial
    ? isFunction
      ? t("descriptor.functionTitle", { name: initial.name })
      : t("descriptor.eventTitle", { name: initial.name })
    : isFunction
      ? t("descriptor.newFunction")
      : t("descriptor.newEvent");

  return (
    <div className="modal-backdrop" role="presentation">
      <div className="modal wide descriptor-editor-modal" onClick={(e) => e.stopPropagation()}>
        <header>
          <h3>{title}</h3>
          <button type="button" className="icon-btn" onClick={onClose}>✕</button>
        </header>

        <section className="modal-section form-grid">
          <label>
            {t("common:table.name")}
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              readOnly={Boolean(initial)}
              pattern="[A-Za-z_][A-Za-z0-9_]*"
              required
              aria-invalid={Boolean(name) && !nameValid}
            />
            {name && !nameValid && (
              <span className="hint error">{t("common:error.invalidCodeIdentifier")}</span>
            )}
          </label>
          {!isFunction && (
            <label>
              {t("common:field.level")}
              <select value={level} onChange={(e) => setLevel(e.target.value)}>
                <option value="DEBUG">{t("common:logLevel.debug")}</option>
                <option value="INFO">{t("common:logLevel.info")}</option>
                <option value="WARNING">{t("common:logLevel.warning")}</option>
                <option value="ERROR">{t("common:logLevel.error")}</option>
                <option value="CRITICAL">{t("common:logLevel.critical")}</option>
              </select>
            </label>
          )}
          <label className="full">
            {t("common:field.description")}
            <input value={description} onChange={(e) => setDescription(e.target.value)} />
          </label>
        </section>

        {isFunction && !showAdvancedJson && (
          <>
            <section className="modal-section">
              <h4>{t("descriptor.inputSchema")}</h4>
              <DataSchemaEditor
                value={inputSchema}
                onChange={setInputSchema}
                idPrefix="fn-input"
              />
            </section>
            <section className="modal-section">
              <h4>{t("descriptor.outputSchema")}</h4>
              <DataSchemaEditor
                value={outputSchema}
                onChange={setOutputSchema}
                idPrefix="fn-output"
              />
            </section>
            <section className="modal-section form-grid">
              <h4 className="full">{t("descriptor.scriptSection")}</h4>
              <label>
                {t("descriptor.sourceType")}
                <select
                  value={sourceType}
                  onChange={(e) => changeSourceType(e.target.value)}
                >
                  <option value="">{t("descriptor.sourceTypeHandler")}</option>
                  <option value="script">{t("descriptor.sourceTypeScript")}</option>
                  <option value="java">{t("descriptor.sourceTypeJava")}</option>
                  <option value="expression">{t("descriptor.sourceTypeExpression")}</option>
                  <option value="object-query">{t("descriptor.sourceTypeObjectQuery")}</option>
                </select>
              </label>
              <label>
                {t("descriptor.version")}
                <input
                  value={version}
                  onChange={(e) => setVersion(e.target.value)}
                  placeholder="1.0.0"
                />
              </label>
              {sourceType !== "expression" && (
                <label className="full">
                  {t("descriptor.dataSourcePath")}
                  <input
                    value={dataSourcePath}
                    onChange={(e) => setDataSourcePath(e.target.value)}
                    placeholder={t("descriptor.dataSourcePathPlaceholder")}
                  />
                </label>
              )}
              {sourceType === "expression" && (
                <div className="full">
                  <span className="field-label">{t("descriptor.expressionEditor")}</span>
                  <BindingExpressionField
                    value={expressionText}
                    formulaLink={expressionFormulaLink}
                    onChange={handleExpressionChange}
                    objectPath={objectPath}
                    variableNames={variableNames}
                    inputFieldNames={inputFieldNames}
                    functionNames={functionNames}
                    variables={variables}
                    entries={expressionCatalog.entries}
                    analyticsCatalogKind="all"
                    editorTitle={t("descriptor.expressionEditor")}
                    onValidate={(expression) => validateFunctionExpression(expression, objectPath)}
                  />
                </div>
              )}
              {sourceType === "object-query" && (
                <div className="full">
                  <span className="field-label">{t("descriptor.objectQueryEditor")}</span>
                  <ObjectQuerySpecField
                    value={sourceBody}
                    onChange={setSourceBody}
                    objectPath={objectPath}
                    variableNames={variableNames}
                    editorTitle={t("descriptor.objectQueryEditor")}
                  />
                </div>
              )}
              {sourceType === "script" && (
                <div className="full">
                  <FunctionScriptStepsEditor
                    value={sourceBody || defaultScriptBody()}
                    onChange={setSourceBody}
                  />
                </div>
              )}
              {sourceType === "java" && (
                <div className="full">
                  <span className="field-label">{t("descriptor.sourceBodyJava")}</span>
                  <Suspense
                    fallback={
                      <textarea
                        className="json-editor"
                        rows={16}
                        value={sourceBody}
                        onChange={(e) => setSourceBody(e.target.value)}
                        spellCheck={false}
                      />
                    }
                  >
                    <JavaFunctionEditor
                      value={sourceBody || DEFAULT_JAVA_FUNCTION_TEMPLATE}
                      onChange={setSourceBody}
                    />
                  </Suspense>
                </div>
              )}
              {sourceType !== "script" &&
                sourceType !== "java" &&
                sourceType !== "expression" &&
                sourceType !== "object-query" &&
                sourceBody.trim() && (
                <label className="full">
                  {t("descriptor.sourceBody")}
                  <textarea
                    className="json-editor"
                    rows={10}
                    value={sourceBody}
                    onChange={(e) => setSourceBody(e.target.value)}
                    placeholder='{"steps":[{"type":"return","value":{}}]}'
                    spellCheck={false}
                  />
                </label>
              )}
              <p className="hint full">
                {sourceType === "java"
                  ? t("descriptor.javaHint")
                  : sourceType === "expression"
                    ? t("descriptor.expressionHint")
                    : sourceType === "object-query"
                      ? t("descriptor.objectQueryHint")
                      : t("descriptor.scriptHint")}
              </p>
            </section>
          </>
        )}

        {!isFunction && !showAdvancedJson && (
          <section className="modal-section">
            <h4>{t("descriptor.payloadSchema")}</h4>
            <DataSchemaEditor
              value={payloadSchema}
              onChange={setPayloadSchema}
              idPrefix="ev-payload"
            />
          </section>
        )}

        <section className="modal-section">
          <label className="checkbox-label inline">
            <input
              type="checkbox"
              checked={showAdvancedJson}
              onChange={(e) => setAdvancedMode(e.target.checked)}
            />
            {t("descriptor.advancedJson")}
          </label>
          {showAdvancedJson && (
            <textarea
              className="json-editor"
              rows={14}
              value={schemaJson}
              onChange={(e) => setSchemaJson(e.target.value)}
              spellCheck={false}
            />
          )}
        </section>

        {parseError && <p className="hint error">{parseError}</p>}
        {mutation.error && <p className="hint error">{(mutation.error as Error).message}</p>}

        <footer>
          <button type="button" className="btn" onClick={onClose}>{t("common:action.cancel")}</button>
          <button
            type="button"
            className="btn primary"
            disabled={!nameValid || mutation.isPending}
            onClick={handleSave}
          >
            {t("common:action.save")}
          </button>
        </footer>
      </div>
    </div>
  );
}
