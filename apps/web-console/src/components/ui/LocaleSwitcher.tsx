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
      <select
        className="locale-switcher-select"
        value={current}
        onChange={(event) => {
          const locale = event.target.value as AppLocale;
          void loadLocale(locale).then(() => i18n.changeLanguage(locale));
        }}
        aria-label={label}
      >
        {SUPPORTED_LOCALES.map((locale) => (
          <option key={locale} value={locale}>
            {LOCALE_LABELS[locale]}
          </option>
        ))}
      </select>
    </label>
  );
}
