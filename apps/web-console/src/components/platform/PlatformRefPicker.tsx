import { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { formatPlatformRef, parsePlatformRef, type PlatformRefKind } from "../../utils/platform/platformRef";

export interface PlatformRefPickerProps {
  objectPath: string;
  kind: PlatformRefKind;
  value?: string;
  variableNames?: string[];
  functionNames?: string[];
  eventNames?: string[];
  onChange: (ref: string) => void;
}

export function PlatformRefPicker({
  objectPath,
  kind,
  value,
  variableNames = [],
  functionNames = [],
  eventNames = [],
  onChange,
}: PlatformRefPickerProps) {
  const { t } = useTranslation("inspector");

  const names = useMemo(() => {
    switch (kind) {
      case "function":
        return functionNames;
      case "event":
        return eventNames;
      default:
        return variableNames;
    }
  }, [kind, variableNames, functionNames, eventNames]);

  const parsed = value ? parsePlatformRef(value) : null;
  const selectedName = parsed?.name ?? "";

  const handleNameChange = (name: string) => {
    if (!name) {
      onChange("");
      return;
    }
    const object = !objectPath?.trim() || objectPath.trim() === "self" ? "@" : objectPath.trim();
    const ref =
      kind === "variable"
        ? formatPlatformRef({ object, kind, name, field: parsed?.field ?? "value" })
        : formatPlatformRef({ object, kind, name });
    onChange(ref);
  };

  return (
    <div className="platform-ref-picker">
      <label className="form-label">{t(`platformRef.${kind}Label`)}</label>
      <select
        className="form-select form-select-sm"
        value={selectedName}
        onChange={(e) => handleNameChange(e.target.value)}
      >
        <option value="">{t("platformRef.selectPlaceholder")}</option>
        {selectedName && !names.includes(selectedName) && (
          <option value={selectedName}>{selectedName}</option>
        )}
        {names.map((name) => (
          <option key={name} value={name}>
            {name}
          </option>
        ))}
      </select>
      {value ? (
        <input
          className="form-control form-control-sm mt-1"
          readOnly
          value={value}
          title={t("platformRef.canonicalRef")}
        />
      ) : null}
    </div>
  );
}
