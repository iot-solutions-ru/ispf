import type { TFunction } from "i18next";
import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { fetchVariables } from "../../../api";
import type { VariableEditorWidget } from "../../../types/dashboard";
import { StringListEditor } from "../widgetEditorStructured";

export function VariableEditorVariablesField({
  widget,
  update,
  t,
  fallbackSuggestions,
}: {
  widget: VariableEditorWidget;
  update: (patch: Partial<VariableEditorWidget>) => void;
  t: TFunction;
  fallbackSuggestions: string[];
}) {
  const path = (widget.objectPath || widget.modelHintPath)?.trim() ?? "";
  const variablesQuery = useQuery({
    queryKey: ["widget-editor-variable-names", path],
    queryFn: () => fetchVariables(path),
    enabled: Boolean(path),
  });
  const variableNames = useMemo(() => {
    const list = variablesQuery.data ?? [];
    return list
      .filter((variable) => variable.readable)
      .map((variable) => variable.name)
      .sort((left, right) => left.localeCompare(right));
  }, [variablesQuery.data]);

  return (
    <>
      {!path ? <p className="hint">{t("scada:props.bindingSelectObjectFirst")}</p> : null}
      {path && variablesQuery.isError ? (
        <p className="hint">{t("scada:props.bindingVariablesError")}</p>
      ) : null}
      {path && variablesQuery.isSuccess && variableNames.length === 0 ? (
        <p className="hint">{t("editor.variableNamesEmpty")}</p>
      ) : null}
      <StringListEditor
        label={t("editor.variablesJsonAll")}
        value={widget.variablesJson}
        onChange={(v) => update({ variablesJson: v || undefined })}
        suggestions={path ? undefined : fallbackSuggestions}
        options={path ? variableNames : undefined}
        optionsDisabled={Boolean(path) && variablesQuery.isLoading}
      />
    </>
  );
}
