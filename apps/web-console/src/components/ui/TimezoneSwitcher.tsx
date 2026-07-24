import { Select } from "antd";
import { useTranslation } from "react-i18next";
import { useUserTimeZone } from "../../context/UserTimeZoneContext";
import { normalizeTimeZoneList, timeZoneLabel } from "../../i18n/timezones";

export default function TimezoneSwitcher() {
  const { t } = useTranslation("shell");
  const { timeZone, setTimeZone } = useUserTimeZone();
  const options = normalizeTimeZoneList(timeZone);
  const label = t("admin.timezone.label");

  return (
    <label className="locale-switcher timezone-switcher">
      <span className="sr-only">{label}</span>
      <Select
        className="locale-switcher-select"
        size="small"
        value={timeZone}
        aria-label={label}
        onChange={(zone) => {
          void setTimeZone(zone);
        }}
        options={options.map((zone) => ({
          value: zone,
          label: timeZoneLabel(zone),
        }))}
        popupMatchSelectWidth={false}
        showSearch
        optionFilterProp="label"
      />
    </label>
  );
}
