import { useTranslation } from "react-i18next";
import type { BindingActivators } from "../types";

interface BindingActivatorsEditorProps {
  activators: BindingActivators;
  eventNames: string[];
  onChange: (activators: BindingActivators) => void;
}

const CUSTOM_EVENT = "__custom__";

export default function BindingActivatorsEditor({
  activators,
  eventNames,
  onChange,
}: BindingActivatorsEditorProps) {
  const { t } = useTranslation("inspector");

  const remoteRef = activators.onVariableChange.find((ref) => ref.objectPath !== "self");
  const remotePath = remoteRef?.objectPath ?? "";
  const remoteVariable = remoteRef?.variableName ?? "";

  const onEventSelectValue = (() => {
    if (!activators.onEvent) {
      return "";
    }
    if (eventNames.includes(activators.onEvent)) {
      return activators.onEvent;
    }
    return CUSTOM_EVENT;
  })();

  const patch = (next: Partial<BindingActivators>) => {
    onChange({ ...activators, ...next });
  };

  const setRemote = (path: string, variableName: string) => {
    const trimmedPath = path.trim();
    if (!trimmedPath) {
      patch({
        onVariableChange: [{ objectPath: "self", variableName: "*" }],
      });
      return;
    }
    patch({
      onVariableChange: [{
        objectPath: trimmedPath,
        variableName: variableName.trim() || "*",
      }],
    });
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
            const value = e.target.value;
            if (!value) {
              patch({ onEvent: null });
            } else if (value === CUSTOM_EVENT) {
              patch({ onEvent: activators.onEvent && !eventNames.includes(activators.onEvent) ? activators.onEvent : "" });
            } else {
              patch({ onEvent: value });
            }
          }}
        >
          <option value="">{t("bindings.activators.onEventNone")}</option>
          {eventNames.map((name) => (
            <option key={name} value={name}>
              {name}
            </option>
          ))}
          <option value={CUSTOM_EVENT}>{t("bindings.activators.onEventCustom")}</option>
        </select>
      </label>

      {onEventSelectValue === CUSTOM_EVENT && (
        <label className="full">
          {t("bindings.activators.onEventName")}
          <input
            value={activators.onEvent ?? ""}
            onChange={(e) => patch({ onEvent: e.target.value.trim() || null })}
            placeholder="alarmRaised"
          />
        </label>
      )}

      <label className="full">
        {t("bindings.activators.remotePath")}
        <input
          value={remotePath}
          placeholder="root.platform.devices.foo"
          onChange={(e) => setRemote(e.target.value, remoteVariable)}
        />
      </label>

      <label className="full">
        {t("bindings.activators.remoteVariable")}
        <input
          value={remotePath ? remoteVariable : ""}
          disabled={!remotePath}
          placeholder="*"
          onChange={(e) => setRemote(remotePath, e.target.value)}
        />
      </label>
    </fieldset>
  );
}

export function activatorsSummary(rule: { activators: BindingActivators }): string {
  const parts: string[] = [];
  if (rule.activators.onStartup) {
    parts.push("startup");
  }
  for (const ref of rule.activators.onVariableChange ?? []) {
    parts.push(`${ref.objectPath}:${ref.variableName}`);
  }
  if (rule.activators.onEvent) {
    parts.push(`event:${rule.activators.onEvent}`);
  }
  if (rule.activators.periodicMs > 0) {
    parts.push(`${rule.activators.periodicMs}ms`);
  }
  return parts.length > 0 ? parts.join(", ") : "—";
}
