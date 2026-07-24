import { Select } from "antd";
import { useTranslation } from "react-i18next";
import { loadLocale } from "../../i18n/index";
import { LOCALE_LABELS, SUPPORTED_LOCALES, type AppLocale } from "../../i18n/locales";

export default function LocaleSwitcher() {
  const { i18n, t } = useTranslation("shell");
  const label = t("admin.language.label");

  const current = (SUPPORTED_LOCALES.includes(i18n.language as AppLocale)
    ? i18n.language
    : "en") as AppLocale;

  return (
    <label className="locale-switcher">
      <span className="sr-only">{label}</span>
      <Select
        className="locale-switcher-select"
        size="small"
        value={current}
        aria-label={label}
        onChange={(locale) => {
          void loadLocale(locale).then(() => i18n.changeLanguage(locale));
        }}
        options={SUPPORTED_LOCALES.map((locale) => ({
          value: locale,
          label: LOCALE_LABELS[locale],
        }))}
        popupMatchSelectWidth={false}
      />
    </label>
  );
}
