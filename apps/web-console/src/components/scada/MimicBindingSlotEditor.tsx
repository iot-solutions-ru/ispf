import { useTranslation } from "react-i18next";
import { useVariablesQuery } from "../../hooks/useVariablesQuery";
import { ObjectPathField } from "../../ui";
import type { MimicBinding } from "../../types/scadaMimic";

interface MimicBindingSlotEditorProps {
  title: string;
  binding: MimicBinding | undefined;
  onUpdate: (patch: Partial<MimicBinding>) => void;
}

export default function MimicBindingSlotEditor({
  title,
  binding,
  onUpdate,
}: MimicBindingSlotEditorProps) {
  const { t } = useTranslation(["scada", "common", "widgets"]);
  const objectPath = binding?.objectPath ?? "";
  const variableName = binding?.variableName ?? "";
  const pathReady = Boolean(objectPath.trim());

  const variablesQuery = useVariablesQuery(objectPath, 5000, pathReady);
  const variableNames = (variablesQuery.data ?? [])
    .filter((v) => v.readable)
    .map((v) => v.name)
    .sort((a, b) => a.localeCompare(b));

  const hasVariableList = pathReady && !variablesQuery.isLoading && variableNames.length > 0;

  return (
    <div className="scada-binding-slot">
      <strong className="scada-binding-slot-title">{title}</strong>

      <ObjectPathField
        className="scada-object-path-field"
        label={t("props.bindingObjectPath")}
        value={objectPath}
        onChange={(path) => onUpdate({ objectPath: path })}
        allowManual
      />

      <label className="scada-form-field">
        <span className="scada-form-label">{t("props.bindingVariableName")}</span>
        {!pathReady ? (
          <p className="scada-props-hint scada-props-hint-compact">{t("props.bindingSelectObjectFirst")}</p>
        ) : variablesQuery.isLoading ? (
          <select className="scada-form-input" disabled>
            <option>{t("props.bindingVariablesLoading")}</option>
          </select>
        ) : (
          <div className="scada-binding-var-controls">
            {hasVariableList && (
              <select
                className="scada-form-input"
                value={variableName}
                onChange={(e) => onUpdate({ variableName: e.target.value })}
              >
                <option value="">—</option>
                {variableNames.map((name) => (
                  <option key={name} value={name}>
                    {name}
                  </option>
                ))}
                {variableName && !variableNames.includes(variableName) && (
                  <option value={variableName}>{variableName}</option>
                )}
              </select>
            )}
            <input
              type="text"
              className="scada-form-input mono"
              spellCheck={false}
              value={variableName}
              placeholder={
                hasVariableList
                  ? t("editor.placeholder.orEnterVariable", { ns: "widgets" })
                  : t("props.bindingVariableManual")
              }
              onChange={(e) => onUpdate({ variableName: e.target.value })}
            />
          </div>
        )}
        {pathReady && variablesQuery.isError && (
          <p className="scada-props-hint scada-props-hint-compact">{t("props.bindingVariablesError")}</p>
        )}
        {pathReady && !variablesQuery.isLoading && !variablesQuery.isError && variableNames.length === 0 && (
          <p className="scada-props-hint scada-props-hint-compact">{t("props.bindingVariablesEmpty")}</p>
        )}
      </label>

      <label className="scada-form-field">
        <span className="scada-form-label">{t("props.bindingQualityField")}</span>
        <input
          type="text"
          className="scada-form-input mono"
          spellCheck={false}
          placeholder="quality"
          value={binding?.qualityField ?? ""}
          onChange={(e) => onUpdate({ qualityField: e.target.value })}
        />
      </label>

      <label className="scada-form-field">
        <span className="scada-form-label">{t("props.bindingTransform")}</span>
        <select
          className="scada-form-input"
          value={binding?.transform ?? ""}
          onChange={(e) =>
            onUpdate({
              transform: e.target.value ? (e.target.value as MimicBinding["transform"]) : undefined,
            })
          }
        >
          <option value="">—</option>
          <option value="bool">bool</option>
          <option value="number">number</option>
          <option value="string">string</option>
        </select>
      </label>
    </div>
  );
}
