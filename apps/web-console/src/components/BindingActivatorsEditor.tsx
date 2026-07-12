import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import type { BindingActivators } from "../types";
import { useVariablesQuery } from "../hooks/useVariablesQuery";
import { ObjectPathField } from "../ui";
import { refFromFields, fieldsFromRef } from "../utils/platformRef";
import { filterUserVariableNames } from "../utils/systemVariables";
import { PlatformRefPicker } from "./PlatformRefPicker";
import {
  buildRemoteVariableChange,
  CUSTOM_BINDING_EVENT,
  patchBindingActivators,
  resolveOnEventAfterSelect,
  resolveOnEventSelectValue,
} from "./bindingActivatorsUtils";

export { activatorsSummary } from "./bindingActivatorsUtils";

interface BindingActivatorsEditorProps {
  activators: BindingActivators;
  eventNames: string[];
  objectPath?: string;
  variableNames?: string[];
  dashboardMode?: boolean;
  onChange: (activators: BindingActivators) => void;
}

export default function BindingActivatorsEditor({
  activators,
  eventNames,
  objectPath = "",
  dashboardMode = false,
  onChange,
}: BindingActivatorsEditorProps) {
  const { t } = useTranslation("inspector");

  const variableChangeRef = activators.onVariableChange[0];
  const storedActivatorPath = variableChangeRef?.objectPath?.trim() || "self";
  const isSelfActivator = storedActivatorPath === "self";
  const displayedActivatorPath = isSelfActivator ? "" : storedActivatorPath;
  const resolvedActivatorPath = isSelfActivator ? objectPath : storedActivatorPath;
  const activatorVariable = variableChangeRef?.variableName ?? "*";
  const onEventSelectValue = resolveOnEventSelectValue(activators, eventNames);
  const activatorPathReady = Boolean(resolvedActivatorPath.trim());

  const activatorVariablesQuery = useVariablesQuery(resolvedActivatorPath, 5000, activatorPathReady);
  const activatorVariableNames = useMemo(() => {
    const names = filterUserVariableNames(
      (activatorVariablesQuery.data ?? []).map((variable) => variable.name),
    );
    return ["*", ...names.filter((name) => name !== "*")];
  }, [activatorVariablesQuery.data]);

  const patch = (next: Partial<BindingActivators>) => {
    onChange(patchBindingActivators(activators, next));
  };

  const setActivatorPath = (path: string, variableName: string) => {
    const trimmed = path.trim();
    const normalizedPath = !trimmed || trimmed === objectPath ? "" : trimmed;
    patch({ onVariableChange: buildRemoteVariableChange(normalizedPath, variableName) });
  };

  return (
    <fieldset className="binding-activators-editor full">
      <legend>{t("bindings.activators.title")}</legend>

      <label className="checkbox-label inline full">
        <input
          type="checkbox"
          checked={activators.onStartup}
          onChange={(e) => patch({ onStartup: e.target.checked })}
        />
        {t("bindings.activators.onStartup")}
      </label>

      {dashboardMode && (
        <label className="checkbox-label inline full">
          <input
            type="checkbox"
            checked={Boolean(activators.onContextChange)}
            onChange={(e) => patch({ onContextChange: e.target.checked })}
          />
          {t("bindings.activators.onContextChange")}
        </label>
      )}

      <label className="full">
        {t("bindings.activators.periodicMs")}
        <input
          type="number"
          min={0}
          step={100}
          value={activators.periodicMs}
          onChange={(e) => patch({ periodicMs: Math.max(0, Number(e.target.value) || 0) })}
        />
        <span className="hint">{t("bindings.activators.periodicMsHint")}</span>
      </label>

      <label className="full">
        {t("bindings.activators.onEvent")}
        <select
          value={onEventSelectValue}
          onChange={(e) => {
            patch({ onEvent: resolveOnEventAfterSelect(activators.onEvent, e.target.value, eventNames) });
          }}
        >
          <option value="">{t("bindings.activators.onEventNone")}</option>
          {eventNames.map((name) => (
            <option key={name} value={name}>
              {name}
            </option>
          ))}
          <option value={CUSTOM_BINDING_EVENT}>{t("bindings.activators.onEventCustom")}</option>
        </select>
      </label>

      {onEventSelectValue === CUSTOM_BINDING_EVENT && (
        <label className="full">
          {t("bindings.activators.onEventName")}
          <input
            value={activators.onEvent ?? ""}
            onChange={(e) => patch({ onEvent: e.target.value.trim() || null })}
            placeholder="alarmRaised"
          />
        </label>
      )}

      <ObjectPathField
        className="full"
        label={t("bindings.activators.objectPath")}
        value={displayedActivatorPath}
        allowManual
        onChange={(path) => setActivatorPath(path, activatorVariable || "*")}
      />
      <span className="hint full">{t("bindings.activators.objectPathHint")}</span>

      {activatorPathReady && (
        <div className="platform-ref-picker-row full">
          <PlatformRefPicker
            objectPath={resolvedActivatorPath}
            kind="variable"
            value={
              variableChangeRef?.ref
              ?? refFromFields(isSelfActivator ? "@" : resolvedActivatorPath, activatorVariable) ?? ""
            }
            variableNames={activatorVariableNames}
            onChange={(ref) => {
              const fields = fieldsFromRef(ref);
              const path = fields.objectPath === "self" ? "" : (fields.objectPath ?? displayedActivatorPath);
              patch({
                onVariableChange: [{
                  objectPath: path.trim() || "self",
                  variableName: fields.name ?? "*",
                  ref: ref || undefined,
                }],
              });
            }}
          />
          {activatorVariablesQuery.isLoading && (
            <span className="hint">{t("bindings.activators.remoteVariablesLoading")}</span>
          )}
        </div>
      )}

      <div className="platform-ref-picker-row full">
        <PlatformRefPicker
          objectPath={objectPath}
          kind="event"
          value={
            activators.onEventRef
            ?? (activators.onEvent ? refFromFields("@", activators.onEvent, undefined, "event") ?? "" : "")
          }
          eventNames={eventNames}
          onChange={(ref) => {
            if (!ref) {
              patch({ onEventRef: null });
              return;
            }
            patch({ onEventRef: ref, onEvent: null });
          }}
        />
      </div>
    </fieldset>
  );
}
