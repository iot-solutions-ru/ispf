import i18n from "i18next";
import { initReactI18next } from "react-i18next";
import {
  detectInitialLocale,
  LOCALE_NAMESPACES,
  LOCALE_STORAGE_KEY,
  normalizeLocale,
  type AppLocale,
} from "./locales";

const localeModules = import.meta.glob<{ default: Record<string, string> }>(
  "../locales/*/*.json",
);

const loadedLocales = new Set<AppLocale>();

async function fetchLocaleBundles(
  locale: AppLocale,
): Promise<Record<string, Record<string, string>>> {
  const bundles: Record<string, Record<string, string>> = {};
  for (const namespace of LOCALE_NAMESPACES) {
    const path = `../locales/${locale}/${namespace}.json`;
    const loader = localeModules[path];
    if (!loader) {
      continue;
    }
    const module = await loader();
    bundles[namespace] = module.default;
  }
  return bundles;
}

async function withDomainScada(
  _locale: AppLocale,
  bundles: Record<string, Record<string, string>>,
): Promise<Record<string, Record<string, string>>> {
  return bundles;
}

export async function loadLocale(locale: AppLocale): Promise<void> {
  if (loadedLocales.has(locale)) {
    return;
  }
  const bundles = await withDomainScada(locale, await fetchLocaleBundles(locale));
  for (const [namespace, strings] of Object.entries(bundles)) {
    i18n.addResourceBundle(locale, namespace, strings, true, true);
  }
  loadedLocales.add(locale);
}

export function persistLocale(locale: AppLocale): void {
  try {
    localStorage.setItem(LOCALE_STORAGE_KEY, locale);
  } catch {
    // ignore
  }
  document.documentElement.lang = locale;
  const url = new URL(window.location.href);
  url.searchParams.set("lang", locale);
  window.history.replaceState({}, "", url.toString());
}

async function initI18n(): Promise<void> {
  const initialLocale = detectInitialLocale();

  const resources: Record<string, Record<string, Record<string, string>>> = {};

  resources.en = await withDomainScada("en", await fetchLocaleBundles("en"));
  loadedLocales.add("en");

  let activeLocale: AppLocale = "en";
  if (initialLocale !== "en") {
    try {
      resources[initialLocale] = await withDomainScada(initialLocale, await fetchLocaleBundles(initialLocale));
      loadedLocales.add(initialLocale);
      activeLocale = initialLocale;
    } catch (error) {
      console.warn(`Failed to load locale ${initialLocale}, falling back to en`, error);
    }
  }

  await i18n.use(initReactI18next).init({
    lng: activeLocale,
    fallbackLng: "en",
    defaultNS: "common",
    ns: [...LOCALE_NAMESPACES],
    resources,
    interpolation: { escapeValue: false },
    returnEmptyString: false,
  });

  if (typeof document !== "undefined") {
    document.documentElement.lang = i18n.language;
  }
  if (typeof window !== "undefined") {
    persistLocale(normalizeLocale(i18n.language) ?? "en");
    i18n.on("languageChanged", (lng) => {
      const locale = normalizeLocale(lng) ?? "en";
      persistLocale(locale);
    });
  }
}

export const i18nReady = initI18n();

export default i18n;
