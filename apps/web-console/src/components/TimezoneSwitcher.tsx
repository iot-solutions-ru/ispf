import { useUserTimeZone } from "../context/UserTimeZoneContext";
import { normalizeTimeZoneList, timeZoneLabel } from "../i18n/timezones";

export default function TimezoneSwitcher() {
  const { timeZone, setTimeZone } = useUserTimeZone();
  const options = normalizeTimeZoneList(timeZone);

  return (
    <label className="locale-switcher timezone-switcher">
      <span className="sr-only">Timezone</span>
      <select
        className="locale-switcher-select"
        value={timeZone}
        onChange={(event) => {
          void setTimeZone(event.target.value);
        }}
        aria-label="Timezone"
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
