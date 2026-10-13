import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchObjectEditor } from "../api";

/** Function name select backed by functions declared on the given object path. */
export default function ObjectFunctionSelect({
  label,
  objectPath,
  value,
  onChange,
  hintNamespace = "widgets",
}: {
  label: string;
  objectPath?: string;
  value?: string;
  onChange: (next: string) => void;
  /** i18n namespace for empty-path hints (widgets.editor.* or inspector.scriptSteps.*). */
  hintNamespace?: "widgets" | "inspector";
}) {
  const { t } = useTranslation([hintNamespace, "widgets", "inspector"]);
  const path = objectPath?.trim() ?? "";
  const query = useQuery({
    queryKey: ["object-function-select", path],
    queryFn: () => fetchObjectEditor(path),
    enabled: Boolean(path),
  });
  const names = [
    ...new Set(
      (query.data?.functions ?? [])
        .map((fn) => fn.name.trim())
        .filter(Boolean),
    ),
  ].sort((a, b) => a.localeCompare(b));
  const current = (value ?? "").trim();
  const options = current && !names.includes(current) ? [current, ...names] : names;

  const needObjectKey =
    hintNamespace === "inspector"
      ? "scriptSteps.functionNamesNeedObject"
      : "editor.functionNamesNeedObject";
  const emptyKey =
    hintNamespace === "inspector"
      ? "scriptSteps.functionNamesEmpty"
      : "editor.functionNamesEmpty";

  return (
    <label className="field-block">
      <span className="field-label">{label}</span>
      <select
        aria-label={label}
        value={current}
        disabled={!path || query.isLoading}
        onChange={(e) => onChange(e.target.value)}
      >
        <option value="">—</option>
        {options.map((name) => (
          <option key={name} value={name}>
            {name}
          </option>
        ))}
      </select>
      {!path ? (
        <span className="hint inline">
          {t(`${hintNamespace}:${needObjectKey}`, {
            defaultValue: t(`widgets:${needObjectKey}`),
          })}
        </span>
      ) : null}
      {path && query.isSuccess && names.length === 0 ? (
        <span className="hint inline">
          {t(`${hintNamespace}:${emptyKey}`, {
            defaultValue: t(`widgets:${emptyKey}`),
          })}
        </span>
      ) : null}
    </label>
  );
}
