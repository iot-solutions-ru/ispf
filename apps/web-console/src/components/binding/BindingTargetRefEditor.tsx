import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useQuery } from "@tanstack/react-query";
import type { BindingTarget, BindingTargetKind } from "../../types";
import { fetchObjectEditor } from "../../api";
import { useVariablesQuery } from "../../hooks/useVariablesQuery";
import { ObjectPathField } from "../../ui/index";
import { fieldsFromRef, refFromFields } from "../../utils/platform/platformRef";
import { filterUserVariableNames } from "../../utils/platform/systemVariables";
import { PlatformRefPicker } from "../platform/PlatformRefPicker";

interface BindingTargetRefEditorProps {
  ruleObjectPath: string;
  kind: Extract<BindingTargetKind, "variable" | "event">;
  target: BindingTarget;
  localVariableNames?: string[];
  localEventNames?: string[];
  onChange: (target: BindingTarget) => void;
}

function resolveTargetPath(ruleObjectPath: string, target: BindingTarget): string {
  if (target.ref?.trim()) {
    const fields = fieldsFromRef(target.ref);
    if (fields.objectPath && fields.objectPath !== "self") {
      return fields.objectPath;
    }
    return ruleObjectPath;
  }
  return ruleObjectPath;
}

export default function BindingTargetRefEditor({
  ruleObjectPath,
  kind,
  target,
  localVariableNames = [],
  localEventNames = [],
  onChange,
}: BindingTargetRefEditorProps) {
  const { t } = useTranslation("inspector");

  const initialPath = resolveTargetPath(ruleObjectPath, target);
  const isSelfTarget = !target.ref?.trim()
    || fieldsFromRef(target.ref).objectPath === "self"
    || fieldsFromRef(target.ref).objectPath === ruleObjectPath;
  const displayedPath = isSelfTarget ? "" : initialPath;
  const resolvedPath = displayedPath.trim() || ruleObjectPath;

  const variablesQuery = useVariablesQuery(resolvedPath, 5000, kind === "variable");
  const editorQuery = useQuery({
    queryKey: ["object-editor", resolvedPath],
    queryFn: () => fetchObjectEditor(resolvedPath),
    enabled: kind === "event" && Boolean(resolvedPath.trim()),
    staleTime: 30_000,
  });

  const variableNames = useMemo(() => {
    if (resolvedPath === ruleObjectPath) {
      return filterUserVariableNames(localVariableNames);
    }
    return filterUserVariableNames((variablesQuery.data ?? []).map((variable) => variable.name));
  }, [resolvedPath, ruleObjectPath, localVariableNames, variablesQuery.data]);

  const eventNames = useMemo(() => {
    if (kind !== "event") {
      return [];
    }
    if (resolvedPath === ruleObjectPath) {
      return localEventNames;
    }
    return (editorQuery.data?.events ?? []).map((event) => event.name);
  }, [kind, resolvedPath, ruleObjectPath, localEventNames, editorQuery.data?.events]);

  const pickerValue =
    target.ref
    ?? (kind === "variable"
      ? refFromFields(isSelfTarget ? "@" : resolvedPath, target.variableName ?? "", target.field ?? "value")
      : refFromFields(isSelfTarget ? "@" : resolvedPath, target.eventName ?? "", undefined, "event"))
    ?? "";

  const setTargetPath = (path: string, name?: string) => {
    const trimmed = path.trim();
    const normalizedPath = !trimmed || trimmed === ruleObjectPath ? "@" : trimmed;
    const currentName =
      name
      ?? fieldsFromRef(pickerValue).name
      ?? (kind === "variable" ? target.variableName : target.eventName)
      ?? "";
    const ref = kind === "variable"
      ? refFromFields(normalizedPath, currentName, target.field ?? "value")
      : refFromFields(normalizedPath, currentName, undefined, "event");
    onChange({
      ...target,
      kind,
      ref: ref ?? null,
      variableName: kind === "variable" && normalizedPath === "@" ? currentName : null,
      eventName: kind === "event" && normalizedPath === "@" ? currentName : null,
    });
  };

  return (
    <div className="binding-target-ref-editor full">
      <ObjectPathField
        className="full"
        label={t("bindings.targetObjectPath")}
        value={displayedPath}
        allowManual
        onChange={(path) => setTargetPath(path)}
      />
      <span className="hint full">{t("bindings.targetObjectPathHint")}</span>

      <div className="platform-ref-picker-row full">
        <PlatformRefPicker
          objectPath={resolvedPath}
          kind={kind}
          value={pickerValue}
          variableNames={variableNames}
          eventNames={eventNames}
          onChange={(ref) => {
            if (!ref) {
              onChange({
                ...target,
                kind,
                ref: null,
                variableName: kind === "variable" ? "" : target.variableName,
                eventName: kind === "event" ? "" : target.eventName,
              });
              return;
            }
            const fields = fieldsFromRef(ref);
            const remote = fields.objectPath && fields.objectPath !== "self" && fields.objectPath !== ruleObjectPath;
            onChange({
              ...target,
              kind,
              ref,
              variableName: kind === "variable" && !remote ? (fields.name ?? "") : null,
              eventName: kind === "event" && !remote ? (fields.name ?? "") : null,
              field: kind === "variable" ? (fields.field ?? target.field ?? "value") : target.field,
            });
          }}
        />
        {kind === "variable" && variablesQuery.isLoading && resolvedPath !== ruleObjectPath && (
          <span className="hint">{t("bindings.activators.remoteVariablesLoading")}</span>
        )}
        {kind === "event" && editorQuery.isLoading && resolvedPath !== ruleObjectPath && (
          <span className="hint">{t("bindings.activators.remoteVariablesLoading")}</span>
        )}
      </div>
    </div>
  );
}
