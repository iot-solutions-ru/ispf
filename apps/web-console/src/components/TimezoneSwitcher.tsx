import { useTranslation } from "react-i18next";
import { useUserTimeZone } from "../context/UserTimeZoneContext";
import { normalizeTimeZoneList, timeZoneLabel } from "../i18n/timezones";

export default function TimezoneSwitcher() {
  const { t } = useTranslation("shell");
  const { timeZone, setTimeZone } = useUserTimeZone();
  const options = normalizeTimeZoneList(timeZone);
  const label = t("admin.timezone.label");

  return (
    <label className="locale-switcher timezone-switcher">
      <span className="sr-only">{label}</span>
      <select
        className="locale-switcher-select"
        value={timeZone}
        onChange={(event) => {
          void setTimeZone(event.target.value);
        }}
        aria-label={label}
      >
        {options.map((zone) => (
          <option key={zone} value={zone}>
            {timeZoneLabel(zone)}
          </option>
        ))}
      </select>
    </label>
  );
}
