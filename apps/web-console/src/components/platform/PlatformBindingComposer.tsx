import { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";
import ObjectQuerySpecEditorModal from "../objectEditor/ObjectQuerySpecEditorModal";
import {
  buildPlatformBindingExpression,
  defaultParamValues,
  type BindingBuilderContext,
  type BindingParamDef,
  type PlatformBindingEntry,
} from "../../utils/platform/platformBindings";
import {
  isObjectQuerySpecVariableRef,
  minifyObjectQuerySpec,
  prettyObjectQuerySpec,
} from "../../utils/object/objectQuerySpecUtils";

interface PlatformBindingComposerProps {
  entry: PlatformBindingEntry;
  context: BindingBuilderContext;
  disabled?: boolean;
  onInsert: (expression: string) => void;
  onClose: () => void;
}

function ParamField({
  param,
  value,
  disabled,
  variableNames,
  functionNames,
  objectPath,
  onChange,
}: {
  param: BindingParamDef;
  value: string;
  disabled?: boolean;
  variableNames: string[];
  functionNames: string[];
  objectPath?: string;
  onChange: (value: string) => void;
}) {
  const { t } = useTranslation("inspector");
  const [oqEditorOpen, setOqEditorOpen] = useState(false);

  if (param.kind === "oqSpec") {
    const displayValue = isObjectQuerySpecVariableRef(value)
      ? value
      : value.trim()
        ? prettyObjectQuerySpec(value).split("\n").slice(0, 2).join("\n")
        : "";
    return (
      <div className="platform-binding-oq-spec">
        <input
          type="text"
          className="mono"
          value={value}
          disabled={disabled}
          placeholder={t("platformBindings.oqSpecPlaceholder")}
          onChange={(event) => onChange(event.target.value)}
        />
        <button
          type="button"
          className="btn btn-sm"
          disabled={disabled}
          onClick={() => setOqEditorOpen(true)}
        >
          {t("objectQuery.openEditor")}
        </button>
        {displayValue && !isObjectQuerySpecVariableRef(value) && (
          <pre className="hint mono object-query-spec-preview">{displayValue}</pre>
        )}
        <ObjectQuerySpecEditorModal
          open={oqEditorOpen}
          title={t("objectQuery.editorTitle")}
          value={isObjectQuerySpecVariableRef(value) ? "" : value}
          disabled={disabled}
          objectPath={objectPath}
          variableNames={variableNames}
          onClose={() => setOqEditorOpen(false)}
          onApply={(next) => {
            onChange(minifyObjectQuerySpec(next));
            setOqEditorOpen(false);
          }}
        />
      </div>
    );
  }

  if (param.kind === "var" && variableNames.length > 0) {
    return (
      <select value={value} disabled={disabled} onChange={(e) => onChange(e.target.value)}>
        {variableNames.map((name) => (
          <option key={name} value={name}>
            {name}
          </option>
        ))}
      </select>
    );
  }

  if (param.kind === "func" && functionNames.length > 0) {
    return (
      <select value={value} disabled={disabled} onChange={(e) => onChange(e.target.value)}>
        {functionNames.map((name) => (
          <option key={name} value={name}>
            {name}
          </option>
        ))}
      </select>
    );
  }

  return (
    <input
      type={param.kind === "number" ? "number" : "text"}
      className={param.kind === "path" ? "mono" : undefined}
      value={value}
      disabled={disabled}
      placeholder={param.default}
      onChange={(e) => onChange(e.target.value)}
    />
  );
}

export default function PlatformBindingComposer({
  entry,
  context,
  disabled = false,
  onInsert,
  onClose,
}: PlatformBindingComposerProps) {
  const { t } = useTranslation("inspector");
  const variableNames = context.variableNames ?? [];
  const functionNames = context.functionNames ?? [];

  const [values, setValues] = useState(() => defaultParamValues(entry, context));

  const preview = useMemo(
    () => buildPlatformBindingExpression(entry, values, context),
    [entry, values, context]
  );

  const patchValue = (key: string, next: string) => {
    setValues((current) => ({ ...current, [key]: next }));
  };

  return (
    <div className="platform-binding-composer">
      <div className="platform-binding-composer-fields">
        {entry.params.map((param) => (
          <label key={param.key} className="platform-binding-composer-field">
            <span>
              {t(param.labelKey)}
              {param.optional ? ` (${t("platformBindings.optional")})` : ""}
            </span>
            <ParamField
              param={param}
              value={values[param.key] ?? ""}
              disabled={disabled}
              variableNames={variableNames}
              functionNames={functionNames}
              objectPath={context.objectPath}
              onChange={(next) => patchValue(param.key, next)}
            />
          </label>
        ))}
      </div>
      <p className="hint platform-binding-composer-preview">
        {t("platformBindings.preview")}: <code>{preview}</code>
      </p>
      <div className="platform-binding-composer-actions">
        <button type="button" className="btn small" disabled={disabled} onClick={onClose}>
          {t("platformBindings.cancel")}
        </button>
        <button
          type="button"
          className="btn small primary"
          disabled={disabled}
          onClick={() => onInsert(preview)}
        >
          {t("platformBindings.insertExpression")}
        </button>
      </div>
    </div>
  );
}
