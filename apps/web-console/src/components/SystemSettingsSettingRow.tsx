import { useTranslation } from "react-i18next";
import type { PlatformRuntimeSetting } from "../api/platformRuntimeSettings";

export function SettingRow({
  setting,
  draftValue,
  selectOptions,
  onChange,
}: {
  setting: PlatformRuntimeSetting;
  draftValue: string;
  selectOptions?: readonly string[];
  onChange: (value: string) => void;
}) {
  const { t } = useTranslation(["system", "common"]);

  const input = setting.type === "boolean" ? (
    <select
      className="system-settings-input"
      value={draftValue}
      disabled={!setting.editable}
      onChange={(event) => onChange(event.target.value)}
    >
      <option value="true">{t("common:action.yes")}</option>
      <option value="false">{t("common:action.no")}</option>
    </select>
  ) : selectOptions ? (
    <select
      className="system-settings-input"
      value={draftValue}
      disabled={!setting.editable}
      onChange={(event) => onChange(event.target.value)}
    >
      {selectOptions.map((option) => (
        <option key={option || "__auto__"} value={option}>
          {option === ""
            ? t(`settings.options.${setting.id}.auto`, "Auto")
            : t(`settings.options.${setting.id}.${option}`, option)}
        </option>
      ))}
    </select>
  ) : (
    <input
      className="system-settings-input"
      type={setting.type === "integer" ? "number" : setting.sensitive ? "password" : "text"}
      value={draftValue}
      disabled={!setting.editable}
      onChange={(event) => onChange(event.target.value)}
    />
  );

  return (
    <tr className={setting.editable ? "" : "system-settings-row-locked"}>
      <th scope="row">
        <div className="system-settings-label">{t(`settings.keys.${setting.id}`, setting.id)}</div>
        <code className="system-settings-env">{setting.envVar}</code>
      </th>
      <td>{input}</td>
      <td>
        <span className={`system-settings-source system-settings-source-${setting.source}`}>
          {t(`settings.source.${setting.source}`)}
        </span>
        {setting.hotReloadable && (
          <span className="hint system-settings-badge">{t("settings.hotReload")}</span>
        )}
        {setting.restartRequired && (
          <span className="hint system-settings-badge">{t("settings.quickToggles.restartRequired")}</span>
        )}
        {setting.overridesEnvironment && setting.environmentValue != null && (
          <span className="hint">{t("settings.envOverrideHint", { value: setting.environmentValue })}</span>
        )}
      </td>
      <td className="mono">{setting.defaultValue || t("common:empty.dash")}</td>
    </tr>
  );
}
