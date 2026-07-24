import { Select } from "antd";
import { useTranslation } from "react-i18next";
import { useTheme, type ThemePreference } from "../../theme";

const OPTIONS: ThemePreference[] = ["system", "light", "dark"];

export default function ThemeSwitcher() {
  const { t } = useTranslation("shell");
  const { preference, setPreference } = useTheme();

  return (
    <label className="theme-switcher" title={t("admin.theme.label")}>
      <span className="sr-only">{t("admin.theme.label")}</span>
      <Select
        className="theme-switcher-select"
        size="small"
        value={preference}
        aria-label={t("admin.theme.label")}
        onChange={(value) => setPreference(value)}
        options={OPTIONS.map((option) => ({
          value: option,
          label: t(`admin.theme.${option}`),
        }))}
        popupMatchSelectWidth={false}
      />
    </label>
  );
}
