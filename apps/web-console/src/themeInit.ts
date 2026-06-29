import type { ResolvedTheme, ThemePreference } from "./theme";

const THEME_STORAGE_KEY = "ispf-theme";
const THEME_QUERY = "(prefers-color-scheme: dark)";

function isThemePreference(value: string | null): value is ThemePreference {
  return value === "system" || value === "light" || value === "dark";
}

export function readStoredThemePreference(): ThemePreference {
  if (typeof window === "undefined") {
    return "system";
  }
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    return isThemePreference(stored) ? stored : "system";
  } catch {
    return "system";
  }
}

export function resolveSystemTheme(): ResolvedTheme {
  if (typeof window === "undefined" || !window.matchMedia) {
    return "dark";
  }
  return window.matchMedia(THEME_QUERY).matches ? "dark" : "light";
}

export function resolveTheme(
  preference: ThemePreference,
  systemTheme: ResolvedTheme = resolveSystemTheme()
): ResolvedTheme {
  return preference === "system" ? systemTheme : preference;
}

export function applyThemeToDocument(
  preference: ThemePreference,
  resolvedTheme: ResolvedTheme
): void {
  if (typeof document === "undefined") {
    return;
  }
  document.documentElement.dataset.theme = resolvedTheme;
  document.documentElement.dataset.themePreference = preference;
}

/** Run before React paint to avoid theme flash. */
export function initThemeOnDocument(): ThemePreference {
  const preference = readStoredThemePreference();
  const resolvedTheme = resolveTheme(preference);
  applyThemeToDocument(preference, resolvedTheme);
  return preference;
}

export { THEME_STORAGE_KEY, THEME_QUERY };
