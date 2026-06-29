import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from "react";
import {
  applyThemeToDocument,
  readStoredThemePreference,
  resolveSystemTheme,
  resolveTheme,
  THEME_QUERY,
  THEME_STORAGE_KEY,
} from "./themeInit";

export type ThemePreference = "system" | "light" | "dark";
export type ResolvedTheme = "light" | "dark";

interface ThemeContextValue {
  preference: ThemePreference;
  resolvedTheme: ResolvedTheme;
  setPreference: (next: ThemePreference) => void;
}

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function useThemeController(): ThemeContextValue {
  const [preference, setPreferenceState] = useState<ThemePreference>(readStoredThemePreference);
  const [systemTheme, setSystemTheme] = useState<ResolvedTheme>(resolveSystemTheme);
  const resolvedTheme = resolveTheme(preference, systemTheme);

  useEffect(() => {
    if (typeof window === "undefined" || !window.matchMedia) {
      return;
    }
    const media = window.matchMedia(THEME_QUERY);
    const handleChange = () => setSystemTheme(media.matches ? "dark" : "light");
    handleChange();
    media.addEventListener("change", handleChange);
    return () => media.removeEventListener("change", handleChange);
  }, []);

  useEffect(() => {
    applyThemeToDocument(preference, resolvedTheme);
  }, [preference, resolvedTheme]);

  const setPreference = (next: ThemePreference) => {
    setPreferenceState(next);
    try {
      window.localStorage.setItem(THEME_STORAGE_KEY, next);
    } catch {
      // localStorage can be unavailable in private or embedded contexts.
    }
  };

  return useMemo(
    () => ({ preference, resolvedTheme, setPreference }),
    [preference, resolvedTheme]
  );
}

export function ThemeProvider({
  value,
  children,
}: {
  value: ThemeContextValue;
  children: ReactNode;
}) {
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme(): ThemeContextValue {
  const value = useContext(ThemeContext);
  if (!value) {
    throw new Error("useTheme must be used inside ThemeProvider");
  }
  return value;
}
